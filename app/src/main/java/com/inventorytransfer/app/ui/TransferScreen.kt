package com.inventorytransfer.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inventorytransfer.app.data.parseMoney
import com.inventorytransfer.app.pdf.InvoicePdfGenerator.fmt
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(vm: TransferViewModel) {
    val session = vm.session ?: return
    var manualCode by remember { mutableStateOf("") }
    var confirmSubmit by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }

    BackHandler { vm.goHome() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Transfer", style = MaterialTheme.typography.titleLarge)
                        Text("${session.fromStore} → ${session.toStore}", style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = { IconButton(onClick = vm::goHome) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { TextButton(onClick = { confirmCancel = true }) { Text("Discard") } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${session.lines.size} items • qty ${session.totalQty}", Modifier.weight(1f))
                        Text("Total ${fmt(session.totalCost)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = vm::openScanner, Modifier.weight(1f).height(56.dp)) { Text("Scan item") }
                        OutlinedButton(
                            onClick = { confirmSubmit = true },
                            enabled = session.lines.isNotEmpty(),
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) { Text("Submit") }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                // Works for typing a code and for Bluetooth/USB scanners that act as a keyboard.
                OutlinedTextField(
                    value = manualCode,
                    onValueChange = { v ->
                        if (v.contains('\n')) { vm.onBarcode(v.replace("\n", "")); manualCode = "" } else manualCode = v
                    },
                    label = { Text("Type or scan barcode") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.onBarcode(manualCode); manualCode = "" }),
                    trailingIcon = {
                        IconButton(onClick = { vm.onBarcode(manualCode); manualCode = "" }, enabled = manualCode.isNotBlank()) {
                            Icon(Icons.Default.Add, "Add")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (session.lines.isEmpty()) {
                item {
                    Text(
                        "No items yet. Tap \"Scan item\" to scan the first item.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
            }
            items(session.lines, key = { it.id }) { line ->
                Card(Modifier.fillMaxWidth().clickable { vm.editLine(line) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(line.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(line.barcode, style = MaterialTheme.typography.bodySmall)
                            Text("${line.qty} × ${fmt(line.unitCost)}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(fmt(line.lineTotal), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    vm.draft?.let { d -> ItemDialog(vm, d) }

    if (confirmSubmit) {
        AlertDialog(
            onDismissRequest = { confirmSubmit = false },
            title = { Text("Submit transfer?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${session.fromStore} → ${session.toStore}")
                    Text("${session.lines.size} line items, total quantity ${session.totalQty}")
                    Text("Total cost: ${fmt(session.totalCost)}", fontWeight = FontWeight.Bold)
                    Text("An invoice PDF will be created.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { Button(onClick = { confirmSubmit = false; vm.submit() }) { Text("Create invoice") } },
            dismissButton = { TextButton(onClick = { confirmSubmit = false }) { Text("Keep scanning") } },
        )
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Discard this transfer?") },
            text = { Text("All ${session.lines.size} scanned items will be removed.") },
            confirmButton = { TextButton(onClick = { confirmCancel = false; vm.cancelTransfer() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun ItemDialog(vm: TransferViewModel, initial: ItemDraft) {
    var name by remember(initial) { mutableStateOf(initial.name) }
    var cost by remember(initial) { mutableStateOf(initial.costText) }
    var qty by remember(initial) { mutableStateOf(initial.qtyText) }
    var error by remember(initial) { mutableStateOf<String?>(null) }
    val costFocus = remember { FocusRequester() }
    val editing = initial.editingLineId != null

    // Name is known, so jump straight to the cost field.
    LaunchedEffect(initial) { if (initial.name.isNotBlank()) runCatching { costFocus.requestFocus() } }

    fun current() = initial.copy(name = name, costText = cost, qtyText = qty)
    fun save(scanNext: Boolean) { error = vm.saveDraft(current(), scanNext) }

    AlertDialog(
        onDismissRequest = { vm.draft = null },
        title = { Text(if (editing) "Edit item" else "Add item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Barcode: ${initial.barcode}", style = MaterialTheme.typography.bodyMedium)
                if (!initial.foundInInventory && !editing) {
                    Text(
                        "Not found in the inventory file — enter the item name.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                initial.existingLine?.let {
                    Text(
                        "Already in the list (qty ${it.qty}). The quantity below will be added to it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                OutlinedTextField(
                    name, { name = it }, label = { Text("Item name") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        cost, { cost = it }, label = { Text("Unit cost") }, singleLine = true, prefix = { Text("$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1f).focusRequester(costFocus),
                    )
                    OutlinedTextField(
                        qty, { qty = it.filter(Char::isDigit) }, label = { Text("Qty") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { save(false) }),
                        modifier = Modifier.weight(0.7f),
                    )
                }
                val c = parseMoney(cost)
                val q = qty.toIntOrNull()
                if (c != null && q != null && q > 0) {
                    Text("Line total: ${fmt(c.multiply(BigDecimal(q)))}", fontWeight = FontWeight.SemiBold)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (editing) {
                    HorizontalDivider()
                    TextButton(onClick = { vm.removeLine(initial.editingLineId!!) }) {
                        Text("Remove from list", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Button(onClick = { save(false) }) { Text(if (editing) "Save" else "Add to list") }
                if (!editing) TextButton(onClick = { save(true) }) { Text("Add & scan next") }
            }
        },
        dismissButton = { TextButton(onClick = { vm.draft = null }) { Text("Cancel") } },
    )
}
