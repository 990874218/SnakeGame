package com.example.snakegame

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 内容延伸到系统栏下方（配合沉浸式）
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        setContent {
            Surface(color = Color.Black) {
                SnakeGameScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 某些情况下返回前台后系统栏会恢复，这里确保重新隐藏
        hideSystemBars()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清除常亮标志
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        // 隐藏状态栏和导航栏
        controller.hide(WindowInsetsCompat.Type.systemBars())
        // 允许通过手势暂时显示系统栏（下拉/上滑），松手后自动隐藏
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}