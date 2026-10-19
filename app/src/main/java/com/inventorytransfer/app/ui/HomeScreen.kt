package com.inventorytransfer.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.inventorytransfer.app.data.ColumnDetector
import com.inventorytransfer.app.data.ColumnMapping
import com.inventorytransfer.app.data.PendingImport
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: TransferViewModel) {
    val context = LocalContext.current
    var showStart by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<File?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFile(uri)
    }
    val openPicker = {
        picker.launch(
            arrayOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel",
                "text/csv", "text/comma-separated-values", "text/plain",
                "application/octet-stream",
            )
        )
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Inventory Transfer") }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("POS inventory file", style = MaterialTheme.typography.titleMedium)
                        val info = vm.inventoryInfo
                        if (info == null) {
                            Text("No inventory loaded yet. Import the Excel (.xlsx) or CSV export from your POS so scanned items show their names.")
                        } else {
                            Text(info.fileName, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${info.itemCount} items • imported " +
                                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(info.importedAt)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        OutlinedButton(onClick = openPicker) {
                            Text(if (info == null) "Import inventory file" else "Replace inventory file")
                        }
                    }
                }
            }

            item {
                val session = vm.session
                if (session != null) {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Transfer in progress", style = MaterialTheme.typography.titleMedium)
                            Text("${session.fromStore} → ${session.toStore} • ${session.lines.size} items scanned")
                            Button(onClick = vm::resumeTransfer, Modifier.fillMaxWidth()) { Text("Resume transfer") }
                        }
                    }
                } else {
                    Button(
                        onClick = { showStart = true },
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                    ) { Text("Start Inventory Transfer", style = MaterialTheme.typography.titleMedium) }
                    if (vm.inventory == null && vm.busy == null) {
                        Text(
                            "Tip: you can still transfer without an inventory file — you'll type item names yourself.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }

            if (vm.invoices.isNotEmpty()) {
                item {
                    Text("Past invoices", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                }
                items(vm.invoices, key = { it.name }) { file ->
                    Card(Modifier.fillMaxWidth().clickable { FileActions.open(context, file) }) {
                        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(file.nameWithoutExtension, fontWeight = FontWeight.SemiBold)
                                Text(
                                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(file.lastModified())),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            IconButton(onClick = { FileActions.share(context, file) }) { Icon(Icons.Default.Share, "Share") }
                            IconButton(onClick = { confirmDelete = file }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                    }
                }
            }
        }
    }

    if (showStart) {
        StartTransferDialog(
            initialFrom = vm.lastFromStore,
            initialTo = vm.lastToStore,
            initialBy = vm.lastPreparedBy,
            onDismiss = { showStart = false },
            onStart = { from, to, by -> showStart = false; vm.startTransfer(from, to, by) },
        )
    }

    vm.pendingImport?.let { pending ->
        ImportColumnsDialog(pending, onCancel = vm::cancelImport, onConfirm = vm::confirmImport)
    }

    confirmDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete invoice?") },
            text = { Text(file.name) },
            confirmButton = { TextButton(onClick = { vm.deleteInvoice(file); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StartTransferDialog(
    initialFrom: String,
    initialTo: String,
    initialBy: String,
    onDismiss: () -> Unit,
    onStart: (String, String, String) -> Unit,
) {
    var from by rememberSaveable { mutableStateOf(initialFrom) }
    var to by rememberSaveable { mutableStateOf(initialTo) }
    var by by rememberSaveable { mutableStateOf(initialBy) }
    val words = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New transfer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(from, { from = it }, label = { Text("From store") }, singleLine = true, keyboardOptions = words)
                OutlinedTextField(to, { to = it }, label = { Text("To store") }, singleLine = true, keyboardOptions = words)
                OutlinedTextField(by, { by = it }, label = { Text("Your name (optional)") }, singleLine = true, keyboardOptions = words)
            }
        },
        confirmButton = {
            Button(onClick = { onStart(from, to, by) }, enabled = from.isNotBlank() && to.isNotBlank()) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Lets the user confirm (or fix) which spreadsheet columns hold the barcode, name and cost. */
@Composable
private fun ImportColumnsDialog(
    pending: PendingImport,
    onCancel: () -> Unit,
    onConfirm: (Int, ColumnMapping) -> Unit,
) {
    val d = pending.detection
    var sheetIndex by remember { mutableIntStateOf(d.sheetIndex) }
    var detection by remember { mutableStateOf(d) }
    var upc by remember { mutableStateOf(d.upcCol) }
    var name by remember { mutableStateOf(d.nameCol) }
    var cost by remember { mutableStateOf(d.costCol) }

    fun selectSheet(i: Int) {
        sheetIndex = i
        detection = ColumnDetector.detect(pending.sheets[i].rows, i)
        upc = detection.upcCol; name = detection.nameCol; cost = detection.costCol
    }

    val labels = detection.columnLabels
    val sampleRow = pending.sheets[sheetIndex].rows.drop(detection.headerRow + 1).firstOrNull { r -> r.any { it.isNotBlank() } }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Check columns") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(pending.fileName, style = MaterialTheme.typography.bodySmall)
                if (pending.sheets.size > 1) {
                    ColumnPicker("Sheet", pending.sheets.map { it.name }, sheetIndex, allowNone = false) { if (it != null) selectSheet(it) }
                }
                ColumnPicker("Barcode / UPC column", labels, upc, allowNone = false) { upc = it }
                ColumnPicker("Item name column", labels, name, allowNone = false) { name = it }
                ColumnPicker("Cost column (optional)", labels, cost, allowNone = true) { cost = it }
                if (sampleRow != null && upc != null && name != null) {
                    HorizontalDivider()
                    Text("First item preview", style = MaterialTheme.typography.labelMedium)
                    Text("Barcode: ${sampleRow.getOrNull(upc!!).orEmpty()}")
                    Text("Name: ${sampleRow.getOrNull(name!!).orEmpty()}")
                    cost?.let { Text("Cost: ${sampleRow.getOrNull(it).orEmpty()}") }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = upc != null && name != null,
                onClick = { onConfirm(sheetIndex, ColumnMapping(detection.headerRow, upc!!, name!!, cost)) },
            ) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun ColumnPicker(label: String, options: List<String>, selected: Int?, allowNone: Boolean, onSelect: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected?.let { options.getOrNull(it) } ?: "None", Modifier.weight(1f), maxLines = 1)
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                if (allowNone) DropdownMenuItem(text = { Text("None") }, onClick = { onSelect(null); open = false })
                options.forEachIndexed { i, o ->
                    DropdownMenuItem(text = { Text(o) }, onClick = { onSelect(i); open = false })
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
