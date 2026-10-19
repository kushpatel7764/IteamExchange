package com.inventorytransfer.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inventorytransfer.app.data.ColumnMapping
import com.inventorytransfer.app.data.InventoryIndex
import com.inventorytransfer.app.data.InventoryInfo
import com.inventorytransfer.app.data.InventoryRepository
import com.inventorytransfer.app.data.PendingImport
import com.inventorytransfer.app.data.TransferLine
import com.inventorytransfer.app.data.TransferSession
import com.inventorytransfer.app.data.parseMoney
import com.inventorytransfer.app.pdf.InvoicePdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface Screen {
    data object Home : Screen
    data object Transfer : Screen
    data object Scanner : Screen
    data class Done(val invoice: File, val invoiceNumber: String, val session: TransferSession) : Screen
}

/** The item being added/edited in the scan dialog. */
data class ItemDraft(
    val barcode: String,
    val name: String,
    val foundInInventory: Boolean,
    val costText: String,
    val qtyText: String,
    /** Set when editing an existing line from the list. */
    val editingLineId: Long? = null,
    /** Set when a newly scanned barcode is already in the list (qty will be added). */
    val existingLine: TransferLine? = null,
)

class TransferViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = InventoryRepository(app)

    var screen by mutableStateOf<Screen>(Screen.Home); private set
    var inventory by mutableStateOf<InventoryIndex?>(null); private set
    var inventoryInfo by mutableStateOf<InventoryInfo?>(repo.inventoryInfo()); private set
    var busy by mutableStateOf<String?>(null); private set
    var message by mutableStateOf<String?>(null)
    var pendingImport by mutableStateOf<PendingImport?>(null); private set
    var session by mutableStateOf<TransferSession?>(repo.loadSession()); private set
    var draft by mutableStateOf<ItemDraft?>(null)
    var invoices by mutableStateOf(repo.invoices()); private set

    val lastFromStore get() = repo.lastFromStore
    val lastToStore get() = repo.lastToStore
    val lastPreparedBy get() = repo.lastPreparedBy

    init {
        viewModelScope.launch {
            busy = "Loading inventory…"
            inventory = withContext(Dispatchers.IO) { runCatching { repo.loadInventory() }.getOrNull() }
            busy = null
        }
    }

    // ------------------------------------------------------------ import

    fun importFile(uri: Uri) = viewModelScope.launch {
        busy = "Reading file…"
        try {
            pendingImport = withContext(Dispatchers.IO) { repo.readForImport(uri) }
        } catch (e: Exception) {
            message = e.message ?: "Could not read that file."
        } finally {
            busy = null
        }
    }

    fun cancelImport() { pendingImport = null }

    fun confirmImport(sheetIndex: Int, mapping: ColumnMapping) {
        val pending = pendingImport ?: return
        pendingImport = null
        viewModelScope.launch {
            busy = "Importing items…"
            try {
                inventory = withContext(Dispatchers.IO) { repo.completeImport(pending, sheetIndex, mapping) }
                inventoryInfo = repo.inventoryInfo()
                message = "Imported ${inventory?.size ?: 0} items."
            } catch (e: Exception) {
                message = e.message ?: "Import failed."
            } finally {
                busy = null
            }
        }
    }

    // ------------------------------------------------------------ transfer

    fun startTransfer(from: String, to: String, preparedBy: String) {
        repo.lastFromStore = from
        repo.lastToStore = to
        repo.lastPreparedBy = preparedBy
        updateSession(TransferSession(from.trim(), to.trim(), preparedBy.trim(), System.currentTimeMillis()))
        screen = Screen.Transfer
    }

    fun resumeTransfer() { if (session != null) screen = Screen.Transfer }

    fun cancelTransfer() {
        updateSession(null)
        screen = Screen.Home
    }

    fun openScanner() { screen = Screen.Scanner }

    fun closeScanner() { screen = Screen.Transfer }

    /** Called by the camera scanner, a hardware (keyboard) scanner or manual entry. */
    fun onBarcode(raw: String) {
        val code = raw.trim()
        if (code.isEmpty()) return
        screen = Screen.Transfer
        val item = inventory?.lookup(code)
        val existing = session?.lines?.firstOrNull { it.barcode == code }
        draft = ItemDraft(
            barcode = code,
            name = existing?.name ?: item?.name.orEmpty(),
            foundInInventory = item != null,
            costText = existing?.unitCost?.toPlainString() ?: item?.cost?.toPlainString().orEmpty(),
            qtyText = "1",
            existingLine = existing,
        )
    }

    fun editLine(line: TransferLine) {
        draft = ItemDraft(
            barcode = line.barcode,
            name = line.name,
            foundInInventory = true,
            costText = line.unitCost.toPlainString(),
            qtyText = line.qty.toString(),
            editingLineId = line.id,
        )
    }

    /** Returns an error message, or null if the draft was saved. */
    fun saveDraft(d: ItemDraft, scanNext: Boolean): String? {
        val s = session ?: return "No transfer in progress."
        val qty = d.qtyText.trim().toIntOrNull()
        val cost = parseMoney(d.costText)
        if (d.name.isBlank()) return "Enter the item name."
        if (qty == null || qty <= 0) return "Enter a quantity of 1 or more."
        if (cost == null) return "Enter the cost."

        val lines = s.lines.toMutableList()
        when {
            d.editingLineId != null -> {
                val i = lines.indexOfFirst { it.id == d.editingLineId }
                if (i >= 0) lines[i] = lines[i].copy(name = d.name.trim(), qty = qty, unitCost = cost)
            }
            d.existingLine != null -> {
                val i = lines.indexOfFirst { it.id == d.existingLine.id }
                if (i >= 0) {
                    val old = lines.removeAt(i)
                    lines.add(0, old.copy(name = d.name.trim(), qty = old.qty + qty, unitCost = cost))
                }
            }
            else -> lines.add(0, TransferLine(System.nanoTime(), d.barcode, d.name.trim(), qty, cost))
        }
        updateSession(s.copy(lines = lines))
        draft = null
        if (scanNext) screen = Screen.Scanner
        return null
    }

    fun removeLine(id: Long) {
        val s = session ?: return
        updateSession(s.copy(lines = s.lines.filterNot { it.id == id }))
        draft = null
    }

    fun submit() {
        val s = session ?: return
        if (s.lines.isEmpty()) { message = "Scan at least one item first."; return }
        viewModelScope.launch {
            busy = "Creating invoice…"
            try {
                val number = "TR-" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) +
                    "-" + repo.nextInvoiceNumber().toString().padStart(4, '0')
                val safeTo = s.toStore.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').take(30)
                val file = File(repo.invoiceDir, if (safeTo.isEmpty()) "$number.pdf" else "${number}_$safeTo.pdf")
                withContext(Dispatchers.IO) { InvoicePdfGenerator.generate(s, number, file) }
                updateSession(null)
                invoices = repo.invoices()
                screen = Screen.Done(file, number, s)
            } catch (e: Exception) {
                message = "Could not create the invoice: ${e.message}"
            } finally {
                busy = null
            }
        }
    }

    fun goHome() {
        invoices = repo.invoices()
        screen = Screen.Home
    }

    fun deleteInvoice(file: File) {
        file.delete()
        invoices = repo.invoices()
    }

    private fun updateSession(s: TransferSession?) {
        session = s
        repo.saveSession(s)
    }
}
