package com.example.snakegame.multiplayer.room

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

data class RoomCreationResult(
    val name: String,
    val password: String?,
    val maxPlayers: Int,
    val allowWallPass: Boolean,
)

/**
 * 创建房间界面
 * 通用的房间创建UI，可用于蓝牙和局域网模式
 */
@Composable
fun CreateRoomScreen(
    connectionType: RoomInfo.ConnectionType,
    onConfirm: (RoomCreationResult) -> Unit,
    onCancel: () -> Unit,
) {
    var roomName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var maxPlayers by remember { mutableStateOf(4f) }
    var allowWallPass by remember { mutableStateOf(false) }
    
    val connectionTypeName = when (connectionType) {
        RoomInfo.ConnectionType.BLUETOOTH -> "蓝牙"
        RoomInfo.ConnectionType.LAN -> "局域网"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "创建${connectionTypeName}房间",
            style = MaterialTheme.typography.titleLarge,
        )
        
        Spacer(Modifier.height(24.dp))
        
        // 房间名称输入
        OutlinedTextField(
            value = roomName,
            onValueChange = { roomName = it },
            label = { Text("房间名称（必填）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("请输入房间名称") },
        )
        
        // 房间密码输入
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("房间密码（选填）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("留空则无需密码") },
            visualTransformation = if (showPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) {
                            Icons.Filled.Visibility
                        } else {
                            Icons.Filled.VisibilityOff
                        },
                        contentDescription = if (showPassword) "隐藏密码" else "显示密码",
                    )
                }
            },
        )
        
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("最大玩家数：${maxPlayers.toInt()}")
            Slider(
                value = maxPlayers,
                onValueChange = { maxPlayers = it },
                valueRange = 2f..8f,
                steps = 5,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("允许穿墙")
            Switch(
                checked = allowWallPass,
                onCheckedChange = { allowWallPass = it },
            )
        }

        Spacer(Modifier.height(24.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                ),
            ) {
                Text("取消")
            }
            Button(
                onClick = {
                    val finalPassword = password.takeIf { it.isNotBlank() }
                    onConfirm(
                        RoomCreationResult(
                            name = roomName.trim(),
                            password = finalPassword,
                            maxPlayers = maxPlayers.toInt(),
                            allowWallPass = allowWallPass,
                        ),
                    )
                },
                enabled = roomName.trim().isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Text("创建")
            }
        }
    }
}

/**
 * 房间密码输入对话框
 */
@Composable
fun RoomPasswordDialog(
    roomName: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("输入密码") },
        text = {
            Column {
                Text(
                    "房间：$roomName",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { 
                        password = it
                        errorMessage = null
                    },
                    label = { Text("房间密码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = if (errorMessage != null) {
                        { Text(errorMessage!!) }
                    } else null,
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) {
                                    Icons.Default.Visibility
                                } else {
                                    Icons.Default.VisibilityOff
                                },
                                contentDescription = if (showPassword) "隐藏密码" else "显示密码",
                            )
                        }
                    },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (password.isBlank()) {
                        errorMessage = "密码不能为空"
                    } else {
                        onConfirm(password)
                    }
                },
            ) {
                Text("加入")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("取消")
            }
        },
    )
}
