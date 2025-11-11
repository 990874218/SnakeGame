package com.example.snakegame

import kotlin.random.Random

/**
 * 食物数据
 */
data class Food(
    val x: Int,
    val y: Int,
) {
    companion object {
        /**
         * 在网格内随机生成一个不与蛇身体重叠的位置
         */
        fun spawn(
            gridWidth: Int,
            gridHeight: Int,
            occupied: Set<Point>,
        ): Food {
            // 最多尝试若干次（极端情况下防止死循环）
            repeat(1000) {
                val rx = Random.nextInt(0, gridWidth)
                val ry = Random.nextInt(0, gridHeight)
                val p = Point(rx, ry)
                if (!occupied.contains(p)) {
                    return Food(rx, ry)
                }
            }
            // 如果实在找不到（几乎不可能），就放在(0,0)
            return Food(0, 0)
        }
    }
}
