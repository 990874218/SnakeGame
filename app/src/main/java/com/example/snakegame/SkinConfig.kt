package com.example.snakegame

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

data class SkinColors(
    // 正常状态（基础颜色）：蛇身体
    val normalBody: Color = Color(0xFF4CAF50),
    // 正常状态蛇头 = normalBody 深一号（运行时派生，不持久化）
    val accent: Color = Color(0xFFFF9800), // 加速/吃到食物 时的统一高亮色
    val food: Color = Color(0xFFFF9800),   // 保持与 accent 一致
) {
    fun withNormalBody(newBody: Color): SkinColors = copy(normalBody = newBody)
    fun withAccentAndFood(newAccent: Color): SkinColors = copy(accent = newAccent, food = newAccent)
}

// 颜色工具
object SkinColorUtil {
    // 将颜色“加深一号”：降低亮度、稍增饱和
    fun darker(c: Color, factor: Float = 0.85f): Color {
        return Color(
            red = (c.red * factor).coerceIn(0f, 1f),
            green = (c.green * factor).coerceIn(0f, 1f),
            blue = (c.blue * factor).coerceIn(0f, 1f),
            alpha = c.alpha
        )
    }

    fun random(): Color {
        // 避免过暗或过亮
        val r = Random.nextInt(64, 224)
        val g = Random.nextInt(64, 224)
        val b = Random.nextInt(64, 224)
        return Color(r, g, b)
    }
}

// 持久化
object SkinStore {
    private const val PREF = "skin_prefs"
    private const val KEY_NORMAL_R = "normal_r"
    private const val KEY_NORMAL_G = "normal_g"
    private const val KEY_NORMAL_B = "normal_b"
    private const val KEY_ACCENT_R = "accent_r"
    private const val KEY_ACCENT_G = "accent_g"
    private const val KEY_ACCENT_B = "accent_b"

    fun load(context: Context): SkinColors {
        val sp = context.getSharedPreferences(PREF, 0)
        val normal = Color(
            sp.getInt(KEY_NORMAL_R, 0x4C),
            sp.getInt(KEY_NORMAL_G, 0xAF),
            sp.getInt(KEY_NORMAL_B, 0x50),
        )
        val accent = Color(
            sp.getInt(KEY_ACCENT_R, 0xFF),
            sp.getInt(KEY_ACCENT_G, 0x98),
            sp.getInt(KEY_ACCENT_B, 0x00),
        )
        return SkinColors(normalBody = normal, accent = accent, food = accent)
    }

    fun save(context: Context, skin: SkinColors) {
        val sp = context.getSharedPreferences(PREF, 0)
        sp.edit()
            .putInt(KEY_NORMAL_R, (skin.normalBody.red * 255).toInt())
            .putInt(KEY_NORMAL_G, (skin.normalBody.green * 255).toInt())
            .putInt(KEY_NORMAL_B, (skin.normalBody.blue * 255).toInt())
            .putInt(KEY_ACCENT_R, (skin.accent.red * 255).toInt())
            .putInt(KEY_ACCENT_G, (skin.accent.green * 255).toInt())
            .putInt(KEY_ACCENT_B, (skin.accent.blue * 255).toInt())
            .apply()
    }
}