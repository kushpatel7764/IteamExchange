package com.inventorytransfer.app.ui

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inventorytransfer.app.pdf.InvoicePdfGenerator.fmt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoneScreen(vm: TransferViewModel, done: Screen.Done) {
    val context = LocalContext.current
    val s = done.session
    BackHandler { vm.goHome() }

    Scaffold(topBar = { TopAppBar(title = { Text("Invoice created") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(done.invoiceNumber, style = MaterialTheme.typography.titleLarge)
                    Text("${s.fromStore} → ${s.toStore}")
                    Text("${s.lines.size} line items • total quantity ${s.totalQty}")
                    Text("Total cost: ${fmt(s.totalCost)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            Button(onClick = { FileActions.open(context, done.invoice) }, Modifier.fillMaxWidth().height(56.dp)) { Text("Open PDF") }
            OutlinedButton(onClick = { FileActions.share(context, done.invoice) }, Modifier.fillMaxWidth().height(56.dp)) {
                Text("Share / Email / Print")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                OutlinedButton(
                    onClick = {
                        val ok = FileActions.saveToDownloads(context, done.invoice)
                        Toast.makeText(
                            context,
                            if (ok) "Saved to Downloads/InventoryTransfer" else "Could not save to Downloads",
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Save to Downloads") }
            }
            TextButton(onClick = vm::goHome, Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}
