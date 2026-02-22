package com.example.snakegame.multiplayer.bluetooth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ConnectionSelectScreen(
    onBluetooth: () -> Unit,
    onLan: () -> Unit,
    onBack: () -> Unit,
    statusText: String?,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("连接方式", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBluetooth) { Text("蓝牙联机") }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onLan) { Text("局域网联机") }
        statusText?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) { Text("返回") }
    }
}
