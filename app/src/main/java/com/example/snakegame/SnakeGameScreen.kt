package com.example.snakegame

import android.app.Activity
import android.content.Context
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

private const val PREFS_NAME = "snake_prefs"
private const val KEY_HIGH_SCORE = "high_score"

@Composable
fun SnakeGameScreen() {
    val gridWidth: MutableState<Int> = remember { mutableStateOf(0) }
    val gridHeight: MutableState<Int> = remember { mutableStateOf(0) }
    val cellSizePx: MutableState<Float> = remember { mutableStateOf(30f) }

    val isPaused = remember { mutableStateOf(false) }
    val isGameOver = remember { mutableStateOf(false) }
    val snakeState: MutableState<Snake?> = remember { mutableStateOf(null) }
    val inMenu = remember { mutableStateOf(true) }

    // 获取 context（必须在使用之前声明）
    val context = LocalContext.current
    val highScoreState: MutableState<Int> = remember { mutableStateOf(0) }

    // 版本检测状态（只在应用启动时检测一次）
    val versionStatusState: MutableState<String?> = remember { mutableStateOf(null) }
    val currentVersion = remember { VersionChecker.getCurrentVersion(context) }

    // 食物与分数
    val foodState: MutableState<Food?> = remember { mutableStateOf(null) }
    val scoreState: MutableState<Int> = remember { mutableStateOf(0) }


// 加载最高分
    LaunchedEffect(Unit) {
        highScoreState.value = context.getSharedPreferences(PREFS_NAME, 0).getInt(KEY_HIGH_SCORE, 0)
    }

    // 只在应用启动时检测一次版本
    LaunchedEffect(Unit) {
        val latestVersion = VersionChecker.getLatestVersion()
        versionStatusState.value =
            if (latestVersion != null) {
                if (VersionChecker.isUpdateAvailable(currentVersion, latestVersion)) {
                    "检测到最新版本v$latestVersion"
                } else {
                    "当前为最新版本"
                }
            } else {
                null // 网络错误时不显示版本信息
            }
    }

    // 每帧自增，驱动重绘
    val tick = remember { mutableStateOf(0) }

    // 大屏优化：网格绘制步长（1=全绘，2=隔一条绘）
    val renderStride = remember { mutableStateOf(1) }

    // 吃到食物后触发若干帧的闪烁效果
    val eatBlinkFrames = remember { mutableStateOf(0) }

    // 初始化完成标志
    val isInitialized = remember { mutableStateOf(false) }

    // 长按加速状态
    val isSpeedUp = remember { mutableStateOf(false) }

    // 组合作用域中进行 dp->px 计算，供 Canvas 内部使用
    val density = LocalDensity.current
    val targetCellPx = with(density) { GameConfig.TARGET_CELL_DP.dp.toPx() }
    val minCellPx = with(density) { GameConfig.MIN_CELL_DP.dp.toPx() }
    val maxCellPx = with(density) { GameConfig.MAX_CELL_DP.dp.toPx() }

    // SoundPool（音效）
    val soundPool = remember { SoundPool.Builder().setMaxStreams(2).build() }
    val soundEat =
        remember {
            try {
                soundPool.load(context, R.raw.eat, 1)
            } catch (e: Exception) {
                -1 // 如果文件不存在，返回-1
            }
        }
    val soundDie =
        remember {
            try {
                soundPool.load(context, R.raw.die, 1)
            } catch (e: Exception) {
                -1
            }
        }
    DisposableEffect(Unit) {
        onDispose { soundPool.release() }
    }

    // Vibrator（震动）
    val vibrator: Vibrator? =
        remember {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }

    fun vibrate(
        ms: Long,
        amplitude: Int? = null,
    ) {
        vibrator?.let { v ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect =
                    if (amplitude != null) {
                        VibrationEffect.createOneShot(ms, amplitude)
                    } else {
                        VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
                    }
                v.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        }
    }

    // 统一的"返回菜单（暂停+切菜单+重置一局+重绘）"
    val goToMenu: () -> Unit = {
        isPaused.value = true
        inMenu.value = true
        isGameOver.value = false

        val newSnake =
            Snake(
                gridWidth.value.coerceAtLeast(1),
                gridHeight.value.coerceAtLeast(1),
            )
        snakeState.value = newSnake
        scoreState.value = 0
        foodState.value =
            Food.spawn(
                gridWidth.value.coerceAtLeast(1),
                gridHeight.value.coerceAtLeast(1),
                newSnake.getBody().toSet(),
            )
        tick.value++
    }

    // 在状态声明后添加
    val restartGame: () -> Unit = {
        isPaused.value = false
        isGameOver.value = false
        // 重新创建蛇
        val newSnake =
            Snake(
                gridWidth.value.coerceAtLeast(1),
                gridHeight.value.coerceAtLeast(1),
            )
        snakeState.value = newSnake
        // 重置分数
        scoreState.value = 0
        // 重新生成食物（避开蛇体）
        val occupied = newSnake.getBody().toSet()
        foodState.value =
            Food.spawn(
                gridWidth.value.coerceAtLeast(1),
                gridHeight.value.coerceAtLeast(1),
                occupied,
            )
        // 触发一次重绘
        tick.value++
    }

    // 如果当前在菜单页，显示开始菜单并提前返回
    if (inMenu.value) {
        StartMenu(
            highScore = highScoreState.value,
            versionStatus = versionStatusState.value, // 传递版本状态
            onStart = {
                restartGame() // 重置一切
                inMenu.value = false // 进入游戏
            },
            onExit = {
                (context as? Activity)?.finish()
            },
        )
        return
    }

    // 滑动判定
    val touchStartX = remember { mutableStateOf(0f) }
    val touchStartY = remember { mutableStateOf(0f) }
    val swipeThreshold = 30f

    // 双击检测相关
    val lastTapTime = remember { mutableStateOf(0L) }
    val lastTapPosition = remember { mutableStateOf(Offset(0f, 0f)) }
    val doubleTapTimeout = 300L // 双击时间间隔（毫秒）
    val doubleTapDistanceThreshold = 50f // 双击位置距离阈值（像素）

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val currentTime = System.currentTimeMillis()
                        val currentPosition = down.position

                        // 检查是否是双击（欧氏距离）
                        val timeSinceLastTap = currentTime - lastTapTime.value
                        val tapDx = currentPosition.x - lastTapPosition.value.x
                        val tapDy = currentPosition.y - lastTapPosition.value.y
                        val tapDistance = sqrt(tapDx * tapDx + tapDy * tapDy)

                        if (timeSinceLastTap < doubleTapTimeout && tapDistance < doubleTapDistanceThreshold) {
                            // 检测到双击，切换暂停状态 + 轻微震动反馈
                            isPaused.value = !isPaused.value
                            vibrate(30) // 20~40ms 均可
                            lastTapTime.value = 0L // 重置，防止三击
                            return@awaitEachGesture
                        }

                        // 记录本次点击时间和位置
                        lastTapTime.value = currentTime
                        lastTapPosition.value = currentPosition

                        // 原有的滑动控制逻辑
                        touchStartX.value = down.position.x
                        touchStartY.value = down.position.y

                        var upX = touchStartX.value
                        var upY = touchStartY.value

                // 长按检测参数
                val longPressThreshold = 300L
                val pressStartTime = System.currentTimeMillis()
                var longPressTriggered = false

                // 等待手指抬起（期间检测长按）
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) {
                                upX = change.position.x
                                upY = change.position.y
                                break
                            }

                    // 检测长按：超过阈值触发加速与震动
                    if (!longPressTriggered &&
                        System.currentTimeMillis() - pressStartTime >= longPressThreshold
                    ) {
                        longPressTriggered = true
                        isSpeedUp.value = true
                        vibrate(20)
                    }
                        } while (true)

                // 抬起后，如果是长按，关闭加速并结束该手势处理
                if (longPressTriggered) {
                    isSpeedUp.value = false
                    return@awaitEachGesture
                }

                        val swipeDx = upX - touchStartX.value
                        val swipeDy = upY - touchStartY.value

// 只有在不是暂停状态时才处理滑动
                        if (!isPaused.value && !isGameOver.value) {
                            if (abs(swipeDx) >= swipeThreshold || abs(swipeDy) >= swipeThreshold) {
                                if (abs(swipeDx) > abs(swipeDy)) {
                                    if (swipeDx > 0) {
                                        snakeState.value?.changeDirection(Direction.RIGHT)
                                    } else {
                                        snakeState.value?.changeDirection(Direction.LEFT)
                                    }
                                } else {
                                    if (swipeDy > 0) {
                                        snakeState.value?.changeDirection(Direction.DOWN)
                                    } else {
                                        snakeState.value?.changeDirection(Direction.UP)
                                    }
                                }
                            }
                        }
                    }
                },
    ) {
        // 使用 GameCanvas 组件绘制游戏
        GameCanvas(
            gridWidth = gridWidth.value,
            gridHeight = gridHeight.value,
            cellSizePx = cellSizePx.value,
            renderStride = renderStride.value,
            snake = snakeState.value,
            food = foodState.value,
            isBlink = eatBlinkFrames.value > 0,
            tick = tick.value,
            onInit = { w, h ->
                // 初始化网格和蛇/食物（根据屏幕尺寸自适应网格与单元格像素，并做大屏优化）
                if (gridWidth.value == 0 || gridHeight.value == 0) {
                    val gw0 = floor(w / targetCellPx).toInt().coerceIn(18, 40)
                    val stepX = (w / gw0).coerceIn(minCellPx, maxCellPx)
                    val gh0 = floor(h / stepX).toInt().coerceAtLeast(10)
                    val finalStep = min(w / gw0, h / gh0)

                    gridWidth.value = gw0
                    gridHeight.value = gh0
                    cellSizePx.value = finalStep

                    val totalCells = gw0 * gh0
                    renderStride.value =
                        when {
                            totalCells > GameConfig.STRIDE_THRESHOLD3 -> 3
                            totalCells > GameConfig.STRIDE_THRESHOLD2 -> 2
                            else -> 1
                        }

                    val snake = Snake(gridWidth.value, gridHeight.value)
                    snakeState.value = snake
                    // 初次生成食物
                    val occupied = snake.getBody().toSet()
                    foodState.value = Food.spawn(gridWidth.value, gridHeight.value, occupied)
                    scoreState.value = 0

                    // 标记初始化完成
                    isInitialized.value = true
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // 使用 GameHUD 组件显示顶部信息栏和暂停按钮
        GameHUD(
            score = scoreState.value,
            highScore = highScoreState.value,
            modifier = Modifier.align(Alignment.TopStart),
        )

        // 暂停遮罩：暂停时显示"继续游戏 / 返回菜单"
        if (isPaused.value && !isGameOver.value) {
            ModalOverlay(
                title = "暂停中",
                primaryText = "继续游戏",
                onPrimary = { isPaused.value = false },
                secondaryText = "返回菜单",
                onSecondary = goToMenu,
            )
        }

        if (isGameOver.value) {
            ModalOverlay(
                title = "游戏结束",
                primaryText = "重新开始",
                onPrimary = { restartGame() },
                secondaryText = "返回菜单",
                onSecondary = goToMenu,
            )
        }
    }

    // 游戏循环：移动/吃食物/重绘
    LaunchedEffect(gridWidth.value, gridHeight.value) {
        // 移除 snakeState.value 依赖
        // 等待初始化完成：检查网格尺寸和蛇是否已创建
        if (gridWidth.value == 0 || gridHeight.value == 0 || snakeState.value == null) {
            return@LaunchedEffect
        }
        while (true) {
            if (!isPaused.value && !isGameOver.value && !inMenu.value) {
                val s = snakeState.value
                if (s != null) {
                    val head = s.move() // 移动蛇

                    // 关键修复：强制更新状态以触发重组
                    // 通过创建一个新的 Snake 对象或者使用 tick 来触发重组
                    snakeState.value = s

                    // 碰撞：墙/自身
                    if (s.checkWallCollision() || s.checkSelfCollision()) {
                        isGameOver.value = true
                        // 死亡音效 + 震动
                        if (soundDie >= 0) {
                            soundPool.play(soundDie, 1f, 1f, 1, 0, 1f)
                        }
                        vibrate(80) // 80ms
                    } else {
                        // 吃食物：蛇头命中食物
                        val f = foodState.value
                        if (f != null && s.isHeadAt(f.x, f.y)) {
                            s.grow()
                            scoreState.value += GameConfig.SCORE_PER_FOOD

                            // 最高分持久化
                            if (scoreState.value > highScoreState.value) {
                                highScoreState.value = scoreState.value
                                context
                                    .getSharedPreferences(PREFS_NAME, 0)
                                    .edit()
                                    .putInt(KEY_HIGH_SCORE, highScoreState.value)
                                    .apply()
                            }

                            // 音效 + 震动 + 闪烁
                            if (soundEat >= 0) {
                                soundPool.play(soundEat, 1f, 1f, 1, 0, 1f)
                            }
                            vibrate(20) // 20ms 轻微震动
                            eatBlinkFrames.value = GameConfig.EAT_BLINK_FRAMES

                            // 重新生成食物（避开蛇体）
                            val occupied = s.getBody().toSet()
                            foodState.value =
                                Food.spawn(gridWidth.value, gridHeight.value, occupied)
                        }
                    }

                    // 闪烁帧数衰减（在状态更新后）
                    if (eatBlinkFrames.value > 0) {
                        eatBlinkFrames.value = eatBlinkFrames.value - 1
                    }

                    // 触发重绘（确保状态更新后再重绘）
                    tick.value++
                }
            }
            delay(if (isSpeedUp.value) GameConfig.TICK_MS_FAST else GameConfig.TICK_MS)
        }
    }
}

@Composable
private fun ModalOverlay(
    title: String,
    primaryText: String,
    onPrimary: () -> Unit,
    secondaryText: String,
    onSecondary: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0x66000000)),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center),
        ) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.padding(top = 12.dp))
            Button(onClick = onPrimary) { Text(primaryText) }
            Spacer(modifier = Modifier.padding(top = 8.dp))
            Button(onClick = onSecondary) { Text(secondaryText) }
        }
    }
}
