package com.example.snakegame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun GameHUD(
    score: Int,
    highScore: Int,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Color(0x801A1A1A))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = "分数: $score    最高分: $highScore",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Button(
            onClick = onTogglePause,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
        ) {
            Text(if (isPaused) "继续" else "暂停")
        }
    }
}

