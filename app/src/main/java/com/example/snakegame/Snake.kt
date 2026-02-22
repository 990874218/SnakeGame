package com.example.snakegame

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * 蛇类
 * 使用网格坐标系统（不是像素坐标）
 */
class Snake(
    private val gridWidth: Int, // 网格宽度（格子数）
    private val gridHeight: Int, // 网格高度（格子数）
) {
    // 蛇的身体（每个元素是一个网格坐标点）
    // 第一个元素是蛇头，最后一个元素是蛇尾
    internal val body = mutableListOf<Point>()

    // 当前移动方向
    internal var currentDirection: Direction = Direction.RIGHT

    // 下一个方向（用于处理快速连续的方向改变）
    internal var nextDirection: Direction = Direction.RIGHT

    // 是否需要在下次移动时增长（吃到食物后）
    private var shouldGrow: Boolean = false

    init {
        // 初始化蛇：在屏幕中央创建一条长度为3的蛇
        val startX = gridWidth / 2
        val startY = gridHeight / 2

        // 蛇的初始位置：水平排列，向右移动
        body.add(Point(startX, startY)) // 蛇头
        body.add(Point(startX - 1, startY)) // 身体
        body.add(Point(startX - 2, startY)) // 尾巴
    }

    companion object {
        /**
         * 从身体坐标列表重建蛇（用于网络同步）
         */
        fun fromBody(
            gridWidth: Int,
            gridHeight: Int,
            bodyPoints: List<Point>,
        ): Snake {
            val snake = Snake(gridWidth, gridHeight)
            snake.body.clear()
            snake.body.addAll(bodyPoints)
            // 根据身体推断方向
            if (bodyPoints.size >= 2) {
                val head = bodyPoints[0]
                val neck = bodyPoints[1]
                snake.currentDirection =
                    when {
                        head.x > neck.x -> Direction.RIGHT
                        head.x < neck.x -> Direction.LEFT
                        head.y > neck.y -> Direction.DOWN
                        head.y < neck.y -> Direction.UP
                        else -> Direction.RIGHT
                    }
                snake.nextDirection = snake.currentDirection
            }
            return snake
        }
    }

    /**
     * 获取蛇头位置
     */
    fun getHead(): Point = body[0]

    /**
     * 获取蛇的身体（包括头部）
     */
    fun getBody(): List<Point> {
        return body.toList() // 返回副本，防止外部修改
    }

    fun overrideBody(points: List<Point>) {
        if (points.isEmpty()) return
        body.clear()
        body.addAll(points)
    }

    /**
     * 改变移动方向
     * @param direction 新的方向
     */
    fun changeDirection(direction: Direction) {
        // 防止反向移动（例如：正在向右移动时，不能直接向左）
        if (isOppositeDirection(direction, currentDirection)) {
            return
        }

        // 设置下一个方向（在下次移动时生效）
        nextDirection = direction
    }

    /**
     * 检查两个方向是否相反
     */
    private fun isOppositeDirection(
        dir1: Direction,
        dir2: Direction,
    ): Boolean =
        when {
            (dir1 == Direction.UP && dir2 == Direction.DOWN) -> true
            (dir1 == Direction.DOWN && dir2 == Direction.UP) -> true
            (dir1 == Direction.LEFT && dir2 == Direction.RIGHT) -> true
            (dir1 == Direction.RIGHT && dir2 == Direction.LEFT) -> true
            else -> false
        }

    /**
     * 移动蛇
     * @return 新的蛇头位置
     */
    fun move(allowWallPass: Boolean = false): Point {
        currentDirection = nextDirection
        val head = body[0]
        var newHead =
            when (currentDirection) {
                Direction.UP -> Point(head.x, head.y - 1)
                Direction.DOWN -> Point(head.x, head.y + 1)
                Direction.LEFT -> Point(head.x - 1, head.y)
                Direction.RIGHT -> Point(head.x + 1, head.y)
            }
        if (allowWallPass) {
            newHead =
                Point(
                    (newHead.x + gridWidth) % gridWidth,
                    (newHead.y + gridHeight) % gridHeight,
                )
        }
        body.add(0, newHead)
        if (!shouldGrow) body.removeAt(body.size - 1) else shouldGrow = false
        return newHead
    }

    /**
     * 让蛇增长（吃到食物后调用）
     */
    fun grow() {
        shouldGrow = true
    }

    /**
     * 检查蛇头是否撞到自己的身体
     */
    fun checkSelfCollision(): Boolean {
        val head = body[0]
        // 检查蛇头是否与身体的其他部分重叠
        // 从索引1开始检查（跳过蛇头本身）
        for (i in 1 until body.size) {
            if (head.x == body[i].x && head.y == body[i].y) {
                return true
            }
        }
        return false
    }

    /**
     * 检查蛇头是否撞到墙壁
     */
    fun checkWallCollision(allowWallPass: Boolean = false): Boolean {
        if (allowWallPass) return false
        val head = body[0]
        return head.x < 0 || head.x >= gridWidth || head.y < 0 || head.y >= gridHeight
    }

    /**
     * 检查蛇头是否在指定位置（用于检测是否吃到食物）
     */
    fun isHeadAt(
        x: Int,
        y: Int,
    ): Boolean {
        val head = body[0]
        return head.x == x && head.y == y
    }

    /**
     * 重置蛇到初始状态
     */
    fun reset() {
        body.clear()
        val startX = gridWidth / 2
        val startY = gridHeight / 2
        body.add(Point(startX, startY))
        body.add(Point(startX - 1, startY))
        body.add(Point(startX - 2, startY))
        currentDirection = Direction.RIGHT
        nextDirection = Direction.RIGHT
        shouldGrow = false
    }

    /**
     * 绘制蛇
     * @param canvas 画布
     * @param paint 画笔
     * @param gridSize 每个格子的大小（像素）
     */
    fun draw(
        canvas: Canvas,
        paint: Paint,
        gridSize: Int,
    ) {
        // 绘制蛇的每一节
        for (i in body.indices) {
            val point = body[i]

            // 计算像素坐标
            val left = point.x * gridSize.toFloat()
            val top = point.y * gridSize.toFloat()
            val right = left + gridSize
            val bottom = top + gridSize

            // 蛇头用不同颜色
            if (i == 0) {
                paint.color = 0xFF4CAF50.toInt() // 绿色蛇头
            } else {
                paint.color = 0xFF8BC34A.toInt() // 浅绿色身体
            }

            // 绘制圆角矩形（更美观）
            val rect = RectF(left + 2, top + 2, right - 2, bottom - 2)
            canvas.drawRoundRect(rect, 4f, 4f, paint)
        }
    }
}

/**
 * 简单的坐标点类
 */
data class Point(
    val x: Int, // 网格X坐标
    val y: Int, // 网格Y坐标
)
