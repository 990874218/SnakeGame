package com.example.snakegame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class GameView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
        // 游戏状态
        var isPaused: Boolean = false
            private set
        private var isGameOver: Boolean = false

        // 游戏循环
        private val handler = Handler(Looper.getMainLooper())
        private var gameRunnable: Runnable? = null
        private val gameSpeed: Long = 150L // 毫秒，越小越快

        // 画笔
        private val paint = Paint().apply { isAntiAlias = true }

        // 尺寸与网格
        private var gameWidth: Int = 0
        private var gameHeight: Int = 0
        private val gridSize = 30
        private var gridWidth: Int = 0
        private var gridHeight: Int = 0

        // 蛇
        private lateinit var snake: Snake

        // 触摸滑动判定
        private var touchStartX = 0f
        private var touchStartY = 0f
        private val swipeThreshold = 30f // 像素阈值：超过认为是滑动

        init {
            isFocusable = true
            isFocusableInTouchMode = true
        }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            gameWidth = w
            gameHeight = h
            gridWidth = gameWidth / gridSize
            gridHeight = gameHeight / gridSize
            initGame()
        }

        private fun initGame() {
            isPaused = false
            isGameOver = false

            // 初始化蛇
            snake = Snake(gridWidth, gridHeight)

            // 初始化完成后再启动循环
            startGameLoop()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            // 背景
            canvas.drawColor(0xFF000000.toInt())

            // 网格（可关）
            drawGrid(canvas)

            // 绘制蛇
            snake.draw(canvas, paint, gridSize)

            // 游戏结束提示（后续可添加）
            // 分数绘制（后续可添加）
        }

        private fun drawGrid(canvas: Canvas) {
            paint.color = 0xFF1A1A1A.toInt()
            paint.strokeWidth = 1f

            for (i in 0..gridWidth) {
                val x = i * gridSize.toFloat()
                canvas.drawLine(x, 0f, x, gameHeight.toFloat(), paint)
            }
            for (i in 0..gridHeight) {
                val y = i * gridSize.toFloat()
                canvas.drawLine(0f, y, gameWidth.toFloat(), y, paint)
            }
        }

        // 每帧更新
        private fun updateGame() {
            if (isPaused || isGameOver) return
            if (!::snake.isInitialized) return

            // 移动蛇
            snake.move()

            // 撞墙
            if (snake.checkWallCollision()) {
                isGameOver = true
            }

            // 撞自己
            if (snake.checkSelfCollision()) {
                isGameOver = true
            }

            // TODO: 吃食物（下一步加入 Food 后实现）
            // if (food at newHead) { snake.grow(); spawn new food; add score }

            invalidate()
        }

        private fun startGameLoop() {
            if (gameRunnable != null) return
            gameRunnable =
                object : Runnable {
                    override fun run() {
                        if (!isPaused && !isGameOver) {
                            updateGame()
                        }
                        handler.postDelayed(this, gameSpeed)
                    }
                }
            handler.post(gameRunnable!!)
        }

        private fun stopGameLoop() {
            gameRunnable?.let {
                handler.removeCallbacks(it)
                gameRunnable = null
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            stopGameLoop()
        }

        // 触摸滑动控制方向：按下记录起点，抬起比较位移确定方向
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    touchStartY = event.y
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    val dx = event.x - touchStartX
                    val dy = event.y - touchStartY
                    if (abs(dx) < swipeThreshold && abs(dy) < swipeThreshold) {
                        // 位移太小，忽略
                        return super.onTouchEvent(event)
                    }
                    if (abs(dx) > abs(dy)) {
                        // 水平滑动
                        if (dx > 0) {
                            snake.changeDirection(Direction.RIGHT)
                        } else {
                            snake.changeDirection(Direction.LEFT)
                        }
                    } else {
                        // 垂直滑动
                        if (dy > 0) {
                            snake.changeDirection(Direction.DOWN)
                        } else {
                            snake.changeDirection(Direction.UP)
                        }
                    }
                    // 调用performClick以支持无障碍访问
                    performClick()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        @Suppress("unused")
        fun pauseGame() {
            isPaused = true
        }

        @Suppress("unused")
        fun resumeGame() {
            isPaused = false
            if (gameRunnable == null) startGameLoop()
        }

        @Suppress("unused")
        fun restartGame() {
            isGameOver = false
            isPaused = false
            initGame()
            invalidate()
        }
    }
