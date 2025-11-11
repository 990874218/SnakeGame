package com.example.snakegame

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun GameCanvas(
    gridWidth: Int,
    gridHeight: Int,
    cellSizePx: Float,
    renderStride: Int,
    snake: Snake?,
    food: Food?,
    isBlink: Boolean,
    tick: Int = 0,
    onInit: ((Float, Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // 读取 tick 确保重组（触发重绘）
    // 确保 tick 被实际使用，而不仅仅是赋值
    

    Canvas(modifier = modifier) {
        val currentTick = tick
        // 初始化回调（如果提供）
        onInit?.invoke(size.width, size.height)

        // 背景
        drawRect(color = Color.Black)

        // 网格
        val gridColor = Color(0xFF1A1A1A)
        val stroke = Stroke(width = 1f)
        val step = cellSizePx
        val stride = renderStride

        var i = 0
        while (i <= gridWidth) {
            val x = i * step
            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), stroke.width)
            i += stride
        }
        var j = 0
        while (j <= gridHeight) {
            val y = j * step
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), stroke.width)
            j += stride
        }

        // 食物
        food?.let { f ->
            val left = f.x * step
            val top = f.y * step
            drawRoundRect(
                color = Color(0xFFE53935),
                topLeft = Offset(left + 4f, top + 4f),
                size =
                    androidx.compose.ui.geometry
                        .Size(step - 8f, step - 8f),
                cornerRadius =
                    androidx.compose.ui.geometry
                        .CornerRadius(6f, 6f),
            )
        }

        // 蛇
        snake?.let { s ->
            val body = s.getBody()
            body.forEachIndexed { index, point ->
                val left = point.x * step
                val top = point.y * step
                val color =
                    if (isBlink) {
                        Color(0xFFFFFFFF)
                    } else {
                        if (index == 0) Color(0xFF4CAF50) else Color(0xFF8BC34A)
                    }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left + 2f, top + 2f),
                    size =
                        androidx.compose.ui.geometry
                            .Size(step - 4f, step - 4f),
                    cornerRadius =
                        androidx.compose.ui.geometry
                            .CornerRadius(4f, 4f),
                )
            }
        }
    }
}
