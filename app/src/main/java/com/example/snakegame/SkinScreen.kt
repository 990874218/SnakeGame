package com.example.snakegame

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SkinScreen(
    initial: SkinColors,
    onApply: (SkinColors) -> Unit,  // 保存并返回
    onBack: () -> Unit,             // 返回菜单
) {
    var skin by remember { mutableStateOf(initial) }

    // 预览模式：false=正常，true=加速
    var previewSpeed by remember { mutableStateOf(false) }

    // 当前拾色对象：null=不显示；"normal" 或 "accent"
    var pickerTarget by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("配色设置", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        // 预览区域
        PreviewSnake(
            normalBody = skin.normalBody,
            normalHead = SkinColorUtil.darker(skin.normalBody),
            accent = skin.accent,
            speedMode = previewSpeed
        )
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (previewSpeed) "预览：加速/吃到食物" else "预览：正常")
            Spacer(Modifier.width(8.dp))
            Button(onClick = { previewSpeed = !previewSpeed }) {
                Text("切换预览")
            }
        }

        Spacer(Modifier.height(16.dp))

        // 单个"设置配色"按钮，根据当前预览态决定设置哪个
        Button(onClick = { 
            pickerTarget = if (previewSpeed) "accent" else "normal"
        }) { 
            Text(if (previewSpeed) "设置加速/食物颜色" else "设置正常(身体)颜色")
        }

        Spacer(Modifier.height(16.dp))

        Row {
            Button(onClick = {
                onApply(skin)
                onBack()
            }) { Text("保存") }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onBack) { Text("返回") }
        }
    }

    // 简易RGB拾色器
    if (pickerTarget != null) {
        val current = if (pickerTarget == "normal") skin.normalBody else skin.accent
        ColorPickerDialog(
            title = if (pickerTarget == "normal") "选择正常(身体)颜色" else "选择加速/食物颜色",
            init = current,
            skin = skin, // 传递 skin 以便监听变化
            pickerTarget = pickerTarget,
            onConfirm = { c ->
                skin = if (pickerTarget == "normal") {
                    skin.withNormalBody(c)
                } else {
                    skin.withAccentAndFood(c)
                }
                pickerTarget = null
            },
            onCancel = { pickerTarget = null },
            onRandom = {
                val randomColor = SkinColorUtil.random()
                if (pickerTarget == "normal") {
                    skin = skin.withNormalBody(randomColor)
                } else {
                    skin = skin.withAccentAndFood(randomColor)
                }
            }
        )
    }
}

@Composable
private fun PreviewSnake(
    normalBody: Color,
    normalHead: Color,
    accent: Color,
    speedMode: Boolean
) {
    val bodyColor = if (speedMode) accent else normalBody
    val headColor = if (speedMode) accent else normalHead

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(Color(0xFF101010)),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).background(bodyColor))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(28.dp).background(bodyColor))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(28.dp).background(headColor)) // 头部
        }
    }
}

@Composable
private fun ColorPickerDialog(
    title: String,
    init: Color,
    skin: SkinColors,
    pickerTarget: String?,
    onConfirm: (Color) -> Unit,
    onCancel: () -> Unit,
    onRandom: () -> Unit
) {
    var r by remember { mutableStateOf((init.red * 255).toInt()) }
    var g by remember { mutableStateOf((init.green * 255).toInt()) }
    var b by remember { mutableStateOf((init.blue * 255).toInt()) }

    // 当皮肤颜色改变时同步更新 r, g, b（用于随机配色后）
    val currentColor = if (pickerTarget == "normal") skin.normalBody else skin.accent
    LaunchedEffect(currentColor) {
        r = (currentColor.red * 255).toInt()
        g = (currentColor.green * 255).toInt()
        b = (currentColor.blue * 255).toInt()
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("R: $r")
                    // 循环图标样式的随机配色按钮
                    Button(onClick = onRandom) {
                        Text("🔄 ")
                    }
                }
                Slider(value = r / 255f, onValueChange = { r = (it * 255).toInt() })
                
                Text("G: $g")
                Slider(value = g / 255f, onValueChange = { g = (it * 255).toInt() })
                
                Text("B: $b")
                Slider(value = b / 255f, onValueChange = { b = (it * 255).toInt() })
                
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .background(Color(r, g, b))
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(Color(r, g, b)) }) { Text("确定") }
        },
        dismissButton = {
            Button(onClick = onCancel) { Text("取消") }
        }
    )
}