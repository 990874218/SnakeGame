package com.example.snakegame.multiplayer.bluetooth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ModeSelectScreen(
    onSingle: () -> Unit,
    onMulti: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("选择模式", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSingle) { Text("单人") }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onMulti) { Text("多人") }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) { Text("返回") }
    }
}
