package com.inventorytransfer.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val vm: TransferViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { App(vm) } }
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val blue = Color(0xFF1565C0)
    val scheme = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFF90CAF9))
    else lightColorScheme(primary = blue)
    MaterialTheme(colorScheme = scheme) { Surface(Modifier.fillMaxSize(), content = content) }
}

@Composable
private fun App(vm: TransferViewModel) {
    when (val screen = vm.screen) {
        Screen.Home -> HomeScreen(vm)
        Screen.Transfer -> TransferScreen(vm)
        Screen.Scanner -> ScannerScreen(onScanned = vm::onBarcode, onClose = vm::closeScanner)
        is Screen.Done -> DoneScreen(vm, screen)
    }

    vm.busy?.let { text ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text(text)
                }
            },
        )
    }

    vm.message?.let { text ->
        AlertDialog(
            onDismissRequest = { vm.message = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { vm.message = null }) { Text("OK") } },
        )
    }
}
