package com.inventorytransfer.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class InventoryInfo(val fileName: String, val itemCount: Int, val importedAt: Long)

class PendingImport(
    val fileName: String,
    val sheets: List<Sheet>,
    val detection: DetectionResult,
)

/**
 * Stores the imported POS inventory (as a compact TSV in app storage, so it is
 * available offline and survives restarts) and the in-progress transfer.
 */
class InventoryRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("inventory", Context.MODE_PRIVATE)
    private val inventoryFile = File(context.filesDir, "inventory.tsv")
    private val sessionFile = File(context.filesDir, "current_transfer.json")
    val invoiceDir: File = File(context.filesDir, "invoices").apply { mkdirs() }

    // ------------------------------------------------------------ inventory

    fun readForImport(uri: Uri): PendingImport {
        val fileName = displayName(uri) ?: "inventory file"
        val sheets = context.contentResolver.openInputStream(uri)?.use {
            SpreadsheetReader.read(it, context.cacheDir)
        } ?: throw UnsupportedFileException("Could not open the selected file.")
        if (sheets.all { it.rows.isEmpty() }) throw UnsupportedFileException("The file is empty.")
        return PendingImport(fileName, sheets, ColumnDetector.detect(sheets))
    }

    fun completeImport(pending: PendingImport, sheetIndex: Int, mapping: ColumnMapping): InventoryIndex {
        val rows = pending.sheets[sheetIndex].rows
        val items = rows.drop(mapping.headerRow + 1).mapNotNull { r ->
            val upc = r.getOrNull(mapping.upcCol)?.trim().orEmpty()
            if (upc.isEmpty()) return@mapNotNull null
            val name = r.getOrNull(mapping.nameCol)?.trim().orEmpty()
            val cost = mapping.costCol?.let { r.getOrNull(it) }?.let(::parseMoney)
            InventoryItem(upc, name, cost)
        }
        if (items.isEmpty()) throw UnsupportedFileException("No items found with a barcode in the chosen column.")

        val tmp = File(context.filesDir, "inventory.tsv.tmp")
        tmp.bufferedWriter().use { w ->
            for (item in items) {
                w.append(clean(item.upc)).append('\t')
                    .append(clean(item.name)).append('\t')
                    .append(item.cost?.toPlainString().orEmpty()).append('\n')
            }
        }
        tmp.renameTo(inventoryFile)
        prefs.edit()
            .putString("file_name", pending.fileName)
            .putInt("item_count", items.size)
            .putLong("imported_at", System.currentTimeMillis())
            .apply()
        return InventoryIndex(items)
    }

    fun loadInventory(): InventoryIndex? {
        if (!inventoryFile.exists()) return null
        val items = ArrayList<InventoryItem>()
        inventoryFile.bufferedReader().useLines { lines ->
            for (line in lines) {
                val parts = line.split('\t')
                if (parts.isEmpty() || parts[0].isEmpty()) continue
                items += InventoryItem(
                    upc = parts[0],
                    name = parts.getOrNull(1).orEmpty(),
                    cost = parts.getOrNull(2)?.toBigDecimalOrNull(),
                )
            }
        }
        return InventoryIndex(items)
    }

    fun inventoryInfo(): InventoryInfo? {
        if (!inventoryFile.exists()) return null
        return InventoryInfo(
            prefs.getString("file_name", "inventory").orEmpty(),
            prefs.getInt("item_count", 0),
            prefs.getLong("imported_at", 0),
        )
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    // ------------------------------------------------------------ store names & invoice numbers

    var lastFromStore: String
        get() = prefs.getString("from_store", "").orEmpty()
        set(v) = prefs.edit().putString("from_store", v).apply()

    var lastToStore: String
        get() = prefs.getString("to_store", "").orEmpty()
        set(v) = prefs.edit().putString("to_store", v).apply()

    var lastPreparedBy: String
        get() = prefs.getString("prepared_by", "").orEmpty()
        set(v) = prefs.edit().putString("prepared_by", v).apply()

    fun nextInvoiceNumber(): Int {
        val n = prefs.getInt("invoice_counter", 0) + 1
        prefs.edit().putInt("invoice_counter", n).apply()
        return n
    }

    // ------------------------------------------------------------ in-progress transfer

    fun saveSession(session: TransferSession?) {
        if (session == null) { sessionFile.delete(); return }
        val json = JSONObject()
            .put("from", session.fromStore)
            .put("to", session.toStore)
            .put("by", session.preparedBy)
            .put("startedAt", session.startedAt)
            .put("lines", JSONArray().apply {
                session.lines.forEach { l ->
                    put(JSONObject()
                        .put("id", l.id)
                        .put("barcode", l.barcode)
                        .put("name", l.name)
                        .put("qty", l.qty)
                        .put("cost", l.unitCost.toPlainString()))
                }
            })
        sessionFile.writeText(json.toString())
    }

    fun loadSession(): TransferSession? = try {
        if (!sessionFile.exists()) null else {
            val json = JSONObject(sessionFile.readText())
            val arr = json.getJSONArray("lines")
            TransferSession(
                fromStore = json.getString("from"),
                toStore = json.getString("to"),
                preparedBy = json.optString("by"),
                startedAt = json.getLong("startedAt"),
                lines = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    TransferLine(
                        id = o.getLong("id"),
                        barcode = o.getString("barcode"),
                        name = o.getString("name"),
                        qty = o.getInt("qty"),
                        unitCost = o.getString("cost").toBigDecimal(),
                    )
                },
            )
        }
    } catch (_: Exception) {
        null
    }

    fun invoices(): List<File> =
        invoiceDir.listFiles { f -> f.extension == "pdf" }?.sortedByDescending { it.lastModified() }.orEmpty()
}
