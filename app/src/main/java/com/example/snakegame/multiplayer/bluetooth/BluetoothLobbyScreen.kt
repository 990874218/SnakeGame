package com.example.snakegame.multiplayer.bluetooth

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.snakegame.multiplayer.room.RoomInfo

@Composable
fun BluetoothLobbyScreen(
    isHosting: Boolean?,
    onHost: () -> Unit,
    onScan: () -> Unit,
    onJoin: (BluetoothDevice) -> Unit,
    onBack: () -> Unit,
    statusText: String,
    pairedDevices: List<BluetoothDevice>,
    discoveredDevices: List<BluetoothDevice>,
    isScanning: Boolean,
    isConnected: Boolean,
    onStartGame: () -> Unit,
    roomInfo: RoomInfo? = null, // 当前房间信息
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("蓝牙大厅", style = MaterialTheme.typography.titleLarge)
        
        // 显示当前房间信息
        roomInfo?.let { room ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        "房间：${room.name}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "玩家：${room.currentPlayers}/${room.maxPlayers}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (room.allowWallPass) {
                        Text(
                            "穿墙：已开启",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    if (room.isPasswordProtected) {
                        Text(
                            "🔒 已设置密码",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
        
        Text("状态：$statusText")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onHost, enabled = isHosting != true) { Text("创建房间") }
            Button(
                onClick = onScan,
                enabled = !isScanning,
            ) {
                Text(if (isScanning) "扫描中..." else "扫描设备")
            }
        }

        DeviceListSection(
            title = "已配对设备",
            devices = pairedDevices,
            enabled = isHosting != false,
            onJoin = onJoin,
        )
        DeviceListSection(
            title = "附近设备",
            devices = discoveredDevices,
            enabled = isHosting != false,
            onJoin = onJoin,
        )

        if (isConnected) {
            Button(onClick = onStartGame) { Text("开始游戏") }
        }

        Button(onClick = onBack) { Text("返回") }
    }
}

@Composable
private fun DeviceListSection(
    title: String,
    devices: List<BluetoothDevice>,
    enabled: Boolean,
    onJoin: (BluetoothDevice) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (devices.isEmpty()) {
            Text("暂无设备", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(devices) { device ->
                    Button(
                        onClick = { onJoin(device) },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(device.displayName())
                    }
                }
            }
        }
    }
}

private fun BluetoothDevice.displayName(): String =
    name ?: address ?: "未知设备"
