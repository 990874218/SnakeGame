package com.example.snakegame.multiplayer.lan

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
fun LanLobbyScreen(
    uiState: LanLobbyUiState,
    onCreateRoom: () -> Unit,
    onJoinRoom: (RoomInfo) -> Unit,
    onRefresh: () -> Unit,
    onExit: () -> Unit,
    onStopHosting: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "局域网大厅", style = MaterialTheme.typography.titleLarge)

        uiState.errorMessage?.let { error ->
            Text(text = error, color = MaterialTheme.colorScheme.error)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = onCreateRoom,
                enabled = !uiState.isHosting,
            ) {
                Text("创建房间")
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = onRefresh,
            ) {
                Text(if (uiState.isDiscovering) "刷新中..." else "刷新列表")
            }
        }

        uiState.hostedRoom?.let { room ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("已创建房间：${room.name}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "端口：${room.port ?: "-"} · 玩家：${uiState.connectedClients.size + 1}/${room.maxPlayers}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (room.allowWallPass) {
                        Text("穿墙：已开启", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onStopHosting) {
                        Text("关闭房间")
                    }
                }
            }
        }

        if (uiState.availableRooms.isEmpty()) {
            Text("暂无可加入的房间")
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.availableRooms) { room ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            Text(room.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${room.currentPlayers}/${room.maxPlayers} 人 · ${if (room.allowWallPass) "穿墙开启" else "穿墙关闭"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { onJoinRoom(room) }) {
                                Text(if (room.isPasswordProtected) "加入（需密码）" else "加入")
                            }
                        }
                    }
                }
            }
        }

        uiState.connectedRoom?.let { room ->
            Text("已连接房间：${room.name}")
        }

        Button(onClick = onExit) {
            Text("返回")
        }
    }
}

