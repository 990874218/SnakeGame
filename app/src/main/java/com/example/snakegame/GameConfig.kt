package com.example.snakegame

object GameConfig {
    const val SCORE_PER_FOOD = 1

    // 网格自适应：单元格尺寸（dp）
    const val TARGET_CELL_DP = 24f
    const val MIN_CELL_DP = 16f
    const val MAX_CELL_DP = 32f

    // 网格绘制步长阈值
    const val STRIDE_THRESHOLD2 = 1600   // >1600 隔一行绘
    const val STRIDE_THRESHOLD3 = 3000   // >3000 隔两行绘

    // 循环间隔（毫秒）
    const val TICK_MS = 150L

    // 吃到食物闪烁帧数
    const val EAT_BLINK_FRAMES = 6
}

