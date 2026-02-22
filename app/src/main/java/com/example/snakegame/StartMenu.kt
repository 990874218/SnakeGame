package com.example.snakegame

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun StartMenu(
    highScore: Int,
    versionStatus: String?,
    onStart: () -> Unit,
    onExit: () -> Unit,
    onOpenSkin: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMultiplayer: () -> Unit,
) {
    val context = LocalContext.current

    // 版本状态到达后，弹出一次自动消失的提示（无交互）
    LaunchedEffect(versionStatus) {
        versionStatus?.let { msg ->
            // 例如：msg = "当前为最新版本" 或 "检测到最新版本v1.2.0"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    val githubUrl = "https://github.com/990874218/SnakeGame"

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "贪吃蛇",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "最高分: $highScore",
                color = Color(0xFFEEEEEE),
                style = MaterialTheme.typography.titleMedium,
            )

            

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onStart) { Text("开始游戏") }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onOpenSkin) { Text("换色") }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onOpenSettings) { Text("设置") }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onOpenMultiplayer) { Text("多人") }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onExit) { Text("退出游戏") }
        }

        // GitHub 按钮（右上角）
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
                        context.startActivity(intent)
                    },
        ) {
            // 使用 GitHub 图标
            Image(
                painter = painterResource(id = R.drawable.github_icon), // 替换为你的图标文件名（不含扩展名）
                contentDescription = "GitHub",
                modifier = Modifier.size(32.dp),
                colorFilter = ColorFilter.tint(Color.White), // 将图标设置为白色
            )
        }
    }
}
