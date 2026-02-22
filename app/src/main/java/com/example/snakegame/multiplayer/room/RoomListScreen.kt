package com.example.snakegame.multiplayer.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 房间列表组件
 * 显示可用房间列表
 */
@Composable
fun RoomListSection(
    title: String,
    rooms: List<RoomInfo>,
    onJoin: (RoomInfo) -> Unit,
    enabled: Boolean = true,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        if (rooms.isEmpty()) {
            Text(
                "暂无房间",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rooms) { room ->
                    RoomItem(
                        room = room,
                        onClick = { onJoin(room) },
                        enabled = enabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomItem(
    room: RoomInfo,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    room.name,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${room.currentPlayers}/${room.maxPlayers} 人",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (room.connectionType) {
                            RoomInfo.ConnectionType.BLUETOOTH -> "蓝牙"
                            RoomInfo.ConnectionType.LAN -> "局域网"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (room.allowWallPass) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "🏃 穿墙已开启",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (room.isPasswordProtected) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "需要密码",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
