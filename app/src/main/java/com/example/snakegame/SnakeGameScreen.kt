package com.example.snakegame

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.snakegame.multiplayer.GameResult
import com.example.snakegame.multiplayer.GameSessionConfig
import com.example.snakegame.multiplayer.PlayerInfo
import com.example.snakegame.multiplayer.PlayerStatus
import com.example.snakegame.multiplayer.bluetooth.BluetoothController
import com.example.snakegame.multiplayer.bluetooth.BluetoothLobbyScreen
import com.example.snakegame.multiplayer.bluetooth.BluetoothPermissionHelper
import com.example.snakegame.multiplayer.bluetooth.ConnectionSelectScreen
import com.example.snakegame.multiplayer.bluetooth.FoodState
import com.example.snakegame.multiplayer.bluetooth.NetProtocol
import com.example.snakegame.multiplayer.bluetooth.SnakeState
import com.example.snakegame.multiplayer.lan.LanLobbyScreen
import com.example.snakegame.multiplayer.lan.LanLobbyViewModel
import com.example.snakegame.multiplayer.room.CreateRoomScreen
import com.example.snakegame.multiplayer.room.RoomInfo
import com.example.snakegame.multiplayer.room.RoomPasswordDialog
import com.example.snakegame.settings.SettingsRepository
import com.example.snakegame.settings.SettingsScreen
import com.example.snakegame.settings.SettingsViewModel
import com.example.snakegame.settings.VibrationLevel
import com.example.snakegame.settings.settingsDataStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val inSettings = remember { mutableStateOf(false) }
    val inConnectionSelect = remember { mutableStateOf(false) }
    val inBluetoothLobby = remember { mutableStateOf(false) }
    val inCreateRoom = remember { mutableStateOf(false) } // 创建房间界面
    val inLanLobby = remember { mutableStateOf(false) }
    val inLanCreateRoom = remember { mutableStateOf(false) }
    val pendingJoinDevice = remember { mutableStateOf<android.bluetooth.BluetoothDevice?>(null) } // 待加入的设备
    val pendingRoomPassword = remember { mutableStateOf<String?>(null) } // 待验证的密码
    val pendingLanRoom = remember { mutableStateOf<RoomInfo?>(null) }

    // 获取 context（必须在使用之前声明）
    val context = LocalContext.current
    val settingsRepository = remember(context) { SettingsRepository(context.settingsDataStore) }
    val settingsViewModel: SettingsViewModel =
        viewModel(factory = SettingsViewModel.factory(settingsRepository))
    val settingsState by settingsViewModel.state.collectAsState()
    val btController = remember(context) { BluetoothController(context) }
    val btState by btController.state.collectAsState()
    val discoveredDevices by btController.discoveredDevices.collectAsState()
    val isDiscovering by btController.isDiscovering.collectAsState()
    val bluetoothMessage = remember { mutableStateOf<String?>(null) }
    val hasScanPermission = remember { mutableStateOf(BluetoothPermissionHelper.hasScanPermissions(context)) }
    val bluetoothEnabledState = remember { mutableStateOf(BluetoothPermissionHelper.isBluetoothEnabled()) }
    val lanLobbyViewModel: LanLobbyViewModel = viewModel()
    val lanUiState by lanLobbyViewModel.uiState.collectAsState()
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result.values.all { it }
            hasScanPermission.value = granted
            if (!granted) {
                bluetoothMessage.value = "需要蓝牙权限才能继续"
            } else {
                bluetoothMessage.value = null
            }
        }
    val enableBtLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            bluetoothEnabledState.value = BluetoothPermissionHelper.isBluetoothEnabled()
            if (!bluetoothEnabledState.value) {
                bluetoothMessage.value = "请开启蓝牙"
            } else {
                bluetoothMessage.value = null
            }
        }
    DisposableEffect(btController) {
        onDispose {
            btController.stop()
        }
    }

    LaunchedEffect(
        inBluetoothLobby.value,
        hasScanPermission.value,
        bluetoothEnabledState.value,
        isDiscovering,
    ) {
        if (inBluetoothLobby.value &&
            hasScanPermission.value &&
            bluetoothEnabledState.value &&
            !isDiscovering
        ) {
            val success = btController.startDiscovery()
            if (!success) {
                bluetoothMessage.value = "无法开始扫描，请确认蓝牙已开启并授权"
            } else {
                bluetoothMessage.value = null
            }
        }
    }

    LaunchedEffect(inLanLobby.value) {
        if (inLanLobby.value) {
            lanLobbyViewModel.startDiscovery()
        } else {
            lanLobbyViewModel.stopDiscovery()
            pendingLanRoom.value = null
        }
    }

    val highScoreState: MutableState<Int> = remember { mutableStateOf(0) }

    // 版本检测状态（只在应用启动时检测一次）
    val versionStatusState: MutableState<String?> = remember { mutableStateOf(null) }
    val currentVersion = remember { VersionChecker.getCurrentVersion(context) }

    // 食物与分数（改为多食物系统）
    val foodsState: MutableState<List<Food>> = remember { mutableStateOf(emptyList()) }
    val foodState: MutableState<Food?> = remember { mutableStateOf(null) } // 保留以兼容旧代码
    val scoreState: MutableState<Int> = remember { mutableStateOf(0) }

    // 游戏会话配置
    val sessionConfig =
        remember {
            mutableStateOf(
                GameSessionConfig(
                    maxPlayers = 2,
                    allowWallPass = false,
                    baseSpeed = settingsState.normalSpeed,
                    boostMultiplier = settingsState.boostMultiplier,
                ),
            )
        }

    // 多人游戏状态
    val isMultiplayerMode = remember { derivedStateOf { btState.connected } }
    val remoteSnakeState: MutableState<Snake?> = remember { mutableStateOf(null) }
    val localPlayerId = remember { mutableStateOf("local_${System.currentTimeMillis()}") }
    val remotePlayerId = remember { mutableStateOf<String?>(null) }

    // 在状态声明区域添加（大约在第190行附近）
// 玩家状态管理
    val playersState: MutableState<Map<String, PlayerInfo>> =
        remember {
            mutableStateOf(emptyMap())
        }
    val gameResult: MutableState<GameResult> =
        remember {
            mutableStateOf(GameResult.Playing)
        }
    val isSpectating: MutableState<Boolean> =
        remember {
            mutableStateOf(false)
        }
    val deadSnakesState: MutableState<Map<String, Snake>> =
        remember {
            mutableStateOf(emptyMap())
        } // 存储死亡玩家的蛇（用于观战模式显示）

// 初始化玩家信息（在连接成功后）
    LaunchedEffect(btState.connected, localPlayerId.value, remotePlayerId.value) {
        if (btState.connected) {
            val players = mutableMapOf<String, PlayerInfo>()
            players[localPlayerId.value] =
                PlayerInfo(
                    id = localPlayerId.value,
                    name = "本地玩家",
                    status = PlayerStatus.ALIVE,
                    snake = snakeState.value,
                )
            remotePlayerId.value?.let { remoteId ->
                players[remoteId] =
                    PlayerInfo(
                        id = remoteId,
                        name = btState.deviceName ?: "远程玩家",
                        status = PlayerStatus.ALIVE,
                        snake = remoteSnakeState.value,
                    )
            }
            playersState.value = players
            gameResult.value = GameResult.Playing
            isSpectating.value = false
        }
    }

// 检查胜利条件（在游戏循环中，大约在第1100行附近，碰撞检测之后）
    fun checkVictoryCondition(): GameResult {
        val players = playersState.value
        val alivePlayers = players.values.filter { it.status == PlayerStatus.ALIVE }

        return when {
            alivePlayers.isEmpty() -> GameResult.Draw // 所有人死亡，平局
            alivePlayers.size == 1 -> {
                // 只剩一个玩家，获胜
                GameResult.Victory(alivePlayers.first().id)
            }
            else -> GameResult.Playing // 继续游戏
        }
    }

// 处理玩家死亡（在碰撞检测后调用）
    @Suppress("UNUSED")
    fun handlePlayerDeath(
        playerId: String,
        deathPosition: Point,
    ) {
        val players = playersState.value.toMutableMap()
        val player = players[playerId] ?: return

        // 标记为死亡
        players[playerId] = player.copy(status = PlayerStatus.DEAD)
        playersState.value = players

        // 如果是本地玩家死亡，进入观战模式
        if (playerId == localPlayerId.value) {
            isSpectating.value = true
            // 保存死亡玩家的蛇（用于显示）
            snakeState.value?.let { deadSnake ->
                deadSnakesState.value =
                    deadSnakesState.value.toMutableMap().apply {
                        put(playerId, deadSnake)
                    }
            }
        } else {
            // 远程玩家死亡，也保存其蛇
            remoteSnakeState.value?.let { deadSnake ->
                deadSnakesState.value =
                    deadSnakesState.value.toMutableMap().apply {
                        put(playerId, deadSnake)
                    }
            }
        }

        // 在死亡位置生成食物（由Host决定）
        val isHost = btState.isHost == true
        val isMultiplayer = btState.connected
        if ((isMultiplayer && isHost) || !isMultiplayer) {
            val occupied =
                mutableSetOf<Point>().apply {
                    // 收集所有存活玩家的蛇体
                    players.values.filter { it.status == PlayerStatus.ALIVE }.forEach { p ->
                        p.snake?.getBody()?.let { addAll(it) }
                    }
                    // 收集现有食物位置
                    foodsState.value.forEach { add(Point(it.x, it.y)) }
                }

            // 在死亡位置生成食物（如果位置可用）
            if (!occupied.contains(deathPosition)) {
                val newFood = Food(deathPosition.x, deathPosition.y)
                val newFoods = foodsState.value.toMutableList()
                newFoods.add(newFood)
                foodsState.value = newFoods
                foodState.value = newFoods.firstOrNull()
            } else {
                // 如果死亡位置被占用，在附近生成
                val nearbyFood = Food.spawn(gridWidth.value, gridHeight.value, occupied)
                if (nearbyFood != null) {
                    val newFoods = foodsState.value.toMutableList()
                    newFoods.add(nearbyFood)
                    foodsState.value = newFoods
                    foodState.value = newFoods.firstOrNull()
                }
            }
        }

        // 检查胜利条件
        gameResult.value = checkVictoryCondition()

        // 如果游戏结束，设置 isGameOver
        when (gameResult.value) {
            is GameResult.Victory, is GameResult.Draw -> {
                isGameOver.value = true
            }
            else -> {}
        }
    }

    // 加载最高分
    LaunchedEffect(Unit) {
        highScoreState.value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_HIGH_SCORE, 0)
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

    // 加速时持续震动：进入加速启动波形震动，退出加速停止
    // （已移动并改造：持续震动逻辑见下方更靠后位置）

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

    // 皮肤与换色导航
    val skinState = remember { mutableStateOf(SkinStore.load(context)) }
    val inSkin = remember { mutableStateOf(false) }
    // 组合作用域中进行 dp->px 计算，供 Canvas 内部使用
    val density = LocalDensity.current
    val targetCellPx = with(density) { GameConfig.TARGET_CELL_DP.dp.toPx() }
    val minCellPx = with(density) { GameConfig.MIN_CELL_DP.dp.toPx() }
    val maxCellPx = with(density) { GameConfig.MAX_CELL_DP.dp.toPx() }

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

    // 背景音乐（循环播放）
    val bgmPlayer =
        remember {
            android.media.MediaPlayer.create(context, R.raw.bgm)?.apply {
                isLooping = true
                val initialVolume =
                    (settingsState.masterVolume * settingsState.musicVolume).coerceIn(0f, 1f)
                setVolume(initialVolume, initialVolume)
                val attrs =
                    android.media.AudioAttributes
                        .Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_GAME)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                setAudioAttributes(attrs)
                // 兜底：若循环被打断，完成时重启
                setOnCompletionListener { mp ->
                    try {
                        mp.seekTo(0)
                        mp.start()
                    } catch (_: Exception) {
                    }
                }
            }
        }

    // 背景音量控制（支持临时压低并延时恢复）
    val bgmNormalVolume =
        remember {
            mutableStateOf(
                (settingsState.masterVolume * settingsState.musicVolume).coerceIn(0f, 1f),
            )
        }
    val bgmDuckVolume = 0.3f
    val bgmDuckRestoreMs = 350L
    val scope = rememberCoroutineScope()
    val bgmDuckJob = remember { mutableStateOf<Job?>(null) }
    // 播放音效时临时压低BGM，稍后恢复
    val duckBgm: () -> Unit = duck@{
        val mp = bgmPlayer ?: return@duck
        try {
            mp.setVolume(bgmDuckVolume, bgmDuckVolume)
        } catch (_: Exception) {
        }
        bgmDuckJob.value?.cancel()
        bgmDuckJob.value =
            scope.launch {
                delay(bgmDuckRestoreMs)
                try {
                    mp.setVolume(bgmNormalVolume.value, bgmNormalVolume.value)
                } catch (_: Exception) {
                }
            }
    }

    LaunchedEffect(settingsState.masterVolume, settingsState.musicVolume) {
        val target = (settingsState.masterVolume * settingsState.musicVolume).coerceIn(0f, 1f)
        bgmNormalVolume.value = target
        val mp = bgmPlayer
        if (mp != null) {
            try {
                mp.setVolume(target, target)
            } catch (_: Exception) {
            }
        }
    }

    // SoundPool（音效）
    val soundPool = remember { SoundPool.Builder().setMaxStreams(2).build() }
    val soundEat =
        remember {
            try {
                soundPool.load(context, R.raw.eat, 1)
            } catch (_: Exception) {
                -1 // 如果文件不存在，返回-1
            }
        }
    val soundDie =
        remember {
            try {
                soundPool.load(context, R.raw.die, 1)
            } catch (_: Exception) {
                -1
            }
        }

    fun vibrationAmplitude(level: VibrationLevel): Int =
        when (level) {
            VibrationLevel.LOW -> 90
            VibrationLevel.MEDIUM -> 160
            VibrationLevel.HIGH -> 255
        }

    fun currentSfxVolume(): Float = (settingsState.masterVolume * settingsState.sfxVolume).coerceIn(0f, 1f)

    DisposableEffect(Unit) {
        onDispose {
            soundPool.release()
            vibrator?.cancel() // 退出时确保停止震动
            bgmPlayer?.release() // 释放背景音乐
        }
    }

    val previewSfx: () -> Unit = {
        if (soundEat >= 0) {
            duckBgm()
            val volume = currentSfxVolume()
            soundPool.play(soundEat, volume, volume, 1, 0, 1f)
        }
    }

    // 连接成功后自动进入游戏
    LaunchedEffect(btState.connected, inBluetoothLobby.value, btState.roomInfo, settingsState) {
        if (btState.connected && inBluetoothLobby.value) {
            // 更新 sessionConfig
            val roomInfo = btState.roomInfo
            sessionConfig.value =
                GameSessionConfig(
                    maxPlayers = roomInfo?.maxPlayers ?: 2,
                    allowWallPass = roomInfo?.allowWallPass ?: false,
                    baseSpeed = settingsState.normalSpeed,
                    boostMultiplier = settingsState.boostMultiplier,
                )
            // 延迟一小段时间让连接稳定
            delay(500)
            // 退出大厅，进入游戏
            inBluetoothLobby.value = false
            inMenu.value = false
            isPaused.value = false
            isGameOver.value = false
            // 重置游戏状态
            if (snakeState.value == null && gridWidth.value > 0 && gridHeight.value > 0) {
                val snake = Snake(gridWidth.value, gridHeight.value)
                snakeState.value = snake
                val occupied = snake.getBody().toSet()
                // 初始化食物（单人模式1个，多人模式根据玩家数）
                val playerCount = if (btState.connected) 2 else 1
                val foodCount = GameSessionConfig.calculateFoodCount(playerCount)
                foodsState.value =
                    (0 until foodCount).mapNotNull {
                        Food.spawn(gridWidth.value, gridHeight.value, occupied)
                    }
                foodState.value = foodsState.value.firstOrNull() // 兼容旧代码
                scoreState.value = 0
            }
        }
    }

    // 接收蓝牙消息并更新远程状态
    LaunchedEffect(btState.connected) {
        if (!btState.connected) {
            remoteSnakeState.value = null
            remotePlayerId.value = null
            return@LaunchedEffect
        }

        btController.incomingMessages.collect { line ->
            val payload = NetProtocol.decodeGameState(line) ?: return@collect

            // 找到远程玩家的蛇（不是本地ID的蛇）
            val remoteSnake = payload.snakes.firstOrNull { it.id != localPlayerId.value }
            if (remoteSnake != null) {
                remotePlayerId.value = remoteSnake.id
                // 重建远程蛇对象
                val gridW = gridWidth.value.coerceAtLeast(1)
                val gridH = gridHeight.value.coerceAtLeast(1)
                val bodyPoints = remoteSnake.body.map { Point(it.first, it.second) }
                val newRemoteSnake = Snake.fromBody(gridW, gridH, bodyPoints)
                remoteSnakeState.value = newRemoteSnake
            }

            // 如果是Client，同步食物位置（Host决定）
            if (btState.isHost == false && payload.foods.isNotEmpty()) {
                foodsState.value = payload.foods.map { Food(it.x, it.y) }
                foodState.value = foodsState.value.firstOrNull() // 兼容旧代码
            }
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

    // 背景音乐状态机：进入游戏播放；暂停/死亡/菜单时暂停
    LaunchedEffect(inMenu.value, isPaused.value, isGameOver.value) {
        val shouldPlay = !inMenu.value && !isPaused.value && !isGameOver.value
        val mp = bgmPlayer
        if (mp != null) {
            if (shouldPlay) {
                if (!mp.isPlaying) {
                    try {
                        // 恢复正常音量后再启动
                        mp.setVolume(bgmNormalVolume.value, bgmNormalVolume.value)
                        mp.start()
                    } catch (_: Exception) {
                    }
                }
            } else {
                if (mp.isPlaying) {
                    try {
                        mp.pause()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    // 加速时持续震动（强度随持续时间渐进增强）
    LaunchedEffect(isSpeedUp.value) {
        if (!isSpeedUp.value) {
            vibrator?.cancel()
            return@LaunchedEffect
        }

        val start = System.currentTimeMillis()
        // 一个周期：震动与休息（可调）
        val onMs = 30L
        val offMs = 70L
        // 渐进到满强度所需时间（可调）
        val rampUpMs = 2000L
        // 最小/最大强度依据玩家设置
        val targetAmp = vibrationAmplitude(settingsState.vibrationLevel)
        val minAmp = (targetAmp * 0.5f).toInt().coerceIn(60, targetAmp)
        val maxAmp = targetAmp

        while (isSpeedUp.value) {
            val elapsed = System.currentTimeMillis() - start
            val t = (elapsed.coerceAtMost(rampUpMs).toFloat() / rampUpMs.toFloat())
            val amp = (minAmp + (maxAmp - minAmp) * t).toInt().coerceIn(minAmp, maxAmp)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(onMs, amp)
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(onMs)
            }
            delay(offMs)
        }

        vibrator?.cancel()
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
        // 重置为单人模式食物（1个）
        val occupied = newSnake.getBody().toSet()
        val food =
            Food.spawn(
                gridWidth.value.coerceAtLeast(1),
                gridHeight.value.coerceAtLeast(1),
                occupied,
            )
        foodsState.value = listOf(food)
        foodState.value = food
        tick.value++
    }

    // 在状态声明后添加
    val restartGame: () -> Unit = {
        isPaused.value = false
        isGameOver.value = false
        // 更新 sessionConfig（单人模式）
        sessionConfig.value =
            GameSessionConfig(
                maxPlayers = 1,
                allowWallPass = false,
                baseSpeed = settingsState.normalSpeed,
                boostMultiplier = settingsState.boostMultiplier,
            )
        // 重新创建蛇
        val newSnake =
            Snake(
                gridWidth.value.coerceAtLeast(1),
                gridHeight.value.coerceAtLeast(1),
            )
        snakeState.value = newSnake
        // 重置分数
        scoreState.value = 0
        // 重新生成食物（避开蛇体）- 单人模式1个食物
        val occupied = newSnake.getBody().toSet()
        val food =
            Food.spawn(
                gridWidth.value.coerceAtLeast(1),
                gridHeight.value.coerceAtLeast(1),
                occupied,
            )
        foodsState.value = listOf(food)
        foodState.value = food
        // 触发一次重绘
        tick.value++
    }

    // 若正在换色，单独展示换色界面（优先于菜单检查）
    if (inSkin.value) {
        SkinScreen(
            initial = skinState.value,
            onApply = { newSkin ->
                skinState.value = newSkin
                SkinStore.save(context, newSkin)
            },
            onBack = { inSkin.value = false },
        )
        return
    }

    if (inSettings.value) {
        SettingsScreen(
            viewModel = settingsViewModel,
            onBack = { inSettings.value = false },
            onPreviewSfx = previewSfx,
        )
        return
    }

    if (inConnectionSelect.value) {
        ConnectionSelectScreen(
            onBluetooth = {
                hasScanPermission.value = BluetoothPermissionHelper.hasScanPermissions(context)
                bluetoothEnabledState.value = BluetoothPermissionHelper.isBluetoothEnabled()
                inConnectionSelect.value = false
                inBluetoothLobby.value = true
                bluetoothMessage.value = null
            },
            onLan = {
                inConnectionSelect.value = false
                inLanLobby.value = true
                inLanCreateRoom.value = false
                lanLobbyViewModel.startDiscovery()
            },
            onBack = {
                inConnectionSelect.value = false
                // 直接返回主菜单
            },
            statusText = bluetoothMessage.value,
        )
        return
    }

    if (inLanLobby.value) {
        if (inLanCreateRoom.value) {
            CreateRoomScreen(
                connectionType = RoomInfo.ConnectionType.LAN,
                onConfirm = { result ->
                    val hostName = Build.MODEL ?: "LAN Host"
                    lanLobbyViewModel.hostRoom(hostName, result)
                    inLanCreateRoom.value = false
                },
                onCancel = { inLanCreateRoom.value = false },
            )
            return
        }

        pendingLanRoom.value?.takeIf { it.isPasswordProtected }?.let { room ->
            RoomPasswordDialog(
                roomName = room.name,
                onConfirm = { password ->
                    pendingLanRoom.value = null
                    lanLobbyViewModel.connectTo(room, password)
                },
                onCancel = { pendingLanRoom.value = null },
            )
        }

        LanLobbyScreen(
            uiState = lanUiState,
            onCreateRoom = {
                inLanCreateRoom.value = true
            },
            onJoinRoom = { room ->
                if (room.isPasswordProtected) {
                    pendingLanRoom.value = room
                } else {
                    lanLobbyViewModel.connectTo(room)
                }
            },
            onRefresh = { lanLobbyViewModel.startDiscovery() },
            onExit = {
                inLanLobby.value = false
                inConnectionSelect.value = true
                lanLobbyViewModel.stopDiscovery()
                lanLobbyViewModel.disconnectClient()
                lanLobbyViewModel.stopHosting()
            },
            onStopHosting = { lanLobbyViewModel.stopHosting() },
        )
        return
    }

    if (inBluetoothLobby.value) {
        // 显示创建房间界面
        if (inCreateRoom.value) {
            CreateRoomScreen(
                connectionType = RoomInfo.ConnectionType.BLUETOOTH,
                onConfirm = { result ->
                    @Suppress("DEPRECATION")
                    val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                    val deviceName =
                        try {
                            adapter?.name ?: "未知设备"
                        } catch (_: SecurityException) {
                            "未知设备"
                        }
                    @SuppressLint("MissingPermission", "HardwareIds")
                    val deviceAddress =
                        try {
                            // Android 12+ 无法获取 MAC 地址，直接使用备用方案
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                result.name.hashCode().toString()
                            } else {
                                adapter?.address?.takeIf { it.isNotBlank() }
                                    ?: result.name.hashCode().toString()
                            }
                        } catch (_: SecurityException) {
                            result.name.hashCode().toString()
                        }
                    val roomInfo =
                        RoomInfo(
                            id = deviceAddress,
                            name = result.name,
                            connectionType = RoomInfo.ConnectionType.BLUETOOTH,
                            hostAddress = deviceAddress,
                            hostName = deviceName,
                            maxPlayers = result.maxPlayers,
                            currentPlayers = 1,
                            allowWallPass = result.allowWallPass,
                            password = result.password,
                            hasPassword = !result.password.isNullOrBlank(),
                            port = null,
                        )
                    btController.setRoomInfo(roomInfo)
                    btController.startHost(roomInfo)
                    inCreateRoom.value = false
                    bluetoothMessage.value = null
                },
                onCancel = {
                    inCreateRoom.value = false
                },
            )
            return
        }

        // 显示密码输入对话框
        pendingJoinDevice.value?.let { device ->
            // 注意：在蓝牙模式下，我们无法直接从设备获取房间信息
            // 这里简化处理，假设需要密码时弹出对话框
            // 实际应用中，可以通过设备名称或其他方式传递房间信息
            val deviceName =
                try {
                    device.name ?: "未知房间"
                } catch (_: SecurityException) {
                    "未知房间"
                }
            RoomPasswordDialog(
                roomName = deviceName,
                onConfirm = { password ->
                    pendingRoomPassword.value = password
                    // 创建房间信息（简化处理，实际应该从设备获取）
                    val roomInfo =
                        btController.createRoomInfoFromDevice(
                            device = device,
                            roomName = deviceName,
                            password = password,
                        )
                    btController.setRoomInfo(roomInfo)
                    btController.connectTo(device)
                    pendingJoinDevice.value = null
                },
                onCancel = {
                    pendingJoinDevice.value = null
                },
            )
        }

        if (!hasScanPermission.value) {
            BluetoothRequirementScreen(
                title = "需要授权",
                message = "扫描附近设备需要授予蓝牙/定位权限。",
                buttonText = "去授权",
                onConfirm = {
                    permissionLauncher.launch(BluetoothPermissionHelper.scanPermissions)
                },
                onBack = {
                    inBluetoothLobby.value = false
                    inConnectionSelect.value = true
                },
            )
            return
        }

        if (!bluetoothEnabledState.value) {
            BluetoothRequirementScreen(
                title = "开启蓝牙",
                message = "请先开启蓝牙以继续多人联机。",
                buttonText = "打开蓝牙",
                onConfirm = {
                    val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    enableBtLauncher.launch(intent)
                },
                onBack = {
                    inBluetoothLobby.value = false
                    inConnectionSelect.value = true
                },
            )
            return
        }

        val statusText =
            bluetoothMessage.value
                ?: when {
                    btState.error != null -> "错误：${btState.error}"
                    btState.connected ->
                        "已连接${btState.deviceName?.let { "：$it" } ?: ""}"
                    btState.isHost == true -> "等待玩家加入..."
                    btState.isHost == false ->
                        "正在连接${btState.deviceName?.let { "：$it" } ?: ""}"
                    else -> "未连接"
                }
        BluetoothLobbyScreen(
            isHosting = btState.isHost,
            onHost = {
                bluetoothMessage.value = null
                inCreateRoom.value = true // 打开创建房间界面
            },
            onScan = {
                bluetoothMessage.value = null
                val success = btController.startDiscovery()
                if (!success) {
                    bluetoothMessage.value = "无法开始扫描，请确认蓝牙已开启并授权"
                }
            },
            onJoin = { device ->
                bluetoothMessage.value = null
                // 简化处理：直接尝试连接，如果需要密码会在连接时处理
                // 实际应用中，应该先检查设备是否有密码保护
                pendingJoinDevice.value = device
            },
            onBack = {
                inBluetoothLobby.value = false
                inConnectionSelect.value = true
                bluetoothMessage.value = null
                btController.stop()
            },
            statusText = statusText,
            pairedDevices = btController.bondedDevices().toList(),
            discoveredDevices = discoveredDevices,
            isScanning = isDiscovering,
            isConnected = btState.connected,
            onStartGame = {
                inBluetoothLobby.value = false
                inMenu.value = false
                isPaused.value = false
                isGameOver.value = false
            },
            roomInfo = btState.roomInfo, // 传递房间信息
        )
        return
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
            onOpenSkin = {
                inSkin.value = true
            },
            onOpenSettings = {
                inSettings.value = true
            },
            onOpenMultiplayer = {
                hasScanPermission.value = BluetoothPermissionHelper.hasScanPermissions(context)
                bluetoothEnabledState.value = BluetoothPermissionHelper.isBluetoothEnabled()
                // 直接进入连接选择界面，跳过模式选择
                inConnectionSelect.value = true
                inBluetoothLobby.value = false
                bluetoothMessage.value = null
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
                            vibrate(30, vibrationAmplitude(settingsState.vibrationLevel)) // 20~40ms 均可
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
                        val longPressThreshold = 200L // 加速判定阈值
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
                                vibrate(20, vibrationAmplitude(settingsState.vibrationLevel))
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
            food = foodState.value, // 保留以兼容旧代码
            isBlink = (eatBlinkFrames.value > 0) || isSpeedUp.value, // 修改：加速也触发同款闪烁
            normalBodyColor = skinState.value.normalBody,
            normalHeadColor = SkinColorUtil.darker(skinState.value.normalBody),
            accentColor = skinState.value.accent,
            foodColor = skinState.value.food,
            tick = tick.value,
            remoteSnake = if (isMultiplayerMode.value) remoteSnakeState.value else null,
            foods = foodsState.value, // 传递多食物列表
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
                    // 初次生成食物（单人模式1个）
                    val occupied = snake.getBody().toSet()
                    val food = Food.spawn(gridWidth.value, gridHeight.value, occupied)
                    foodsState.value = listOf(food)
                    foodState.value = food
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
    LaunchedEffect(gridWidth.value, gridHeight.value, btState.connected, sessionConfig.value) {
        // 等待初始化完成：检查网格尺寸和蛇是否已创建
        if (gridWidth.value == 0 || gridHeight.value == 0 || snakeState.value == null) {
            return@LaunchedEffect
        }

        val isMultiplayer = btState.connected
        val isHost = btState.isHost == true
        val allowWallPass = sessionConfig.value.allowWallPass

        while (true) {
            if (!isPaused.value && !isGameOver.value && !inMenu.value) {
                val localSnake = snakeState.value
                if (localSnake != null) {
                    // 使用 sessionConfig 的 allowWallPass 参数
                    val head = localSnake.move(allowWallPass = allowWallPass)

                    // 关键修复：强制更新状态以触发重组
                    snakeState.value = localSnake

                    // 多人模式：检查撞到远程蛇
                    if (isMultiplayer) {
                        val remoteSnake = remoteSnakeState.value
                        if (remoteSnake != null) {
                            val remoteBody = remoteSnake.getBody()
                            if (remoteBody.any { it.x == head.x && it.y == head.y }) {
                                isGameOver.value = true
                                // 死亡音效和震动
                                if (soundDie >= 0) {
                                    duckBgm()
                                    val volume = currentSfxVolume()
                                    soundPool.play(soundDie, volume, volume, 1, 0, 1f)
                                }
                                val deathAmp =
                                    (vibrationAmplitude(settingsState.vibrationLevel) * 0.8f)
                                        .toInt()
                                        .coerceIn(40, 255)
                                vibrate(80, deathAmp)
                                // 继续循环，等待下一帧
                                val baseTick =
                                    (GameConfig.TICK_MS.toFloat() / sessionConfig.value.baseSpeed.coerceAtLeast(0.1f))
                                        .toLong()
                                        .coerceAtLeast(30L)
                                val boostTick =
                                    (baseTick.toFloat() / sessionConfig.value.boostMultiplier.coerceAtLeast(1f))
                                        .toLong()
                                        .coerceAtLeast(15L)
                                delay(if (isSpeedUp.value) boostTick else baseTick)
                                continue
                            }
                        }
                    }

                    // 碰撞：墙/自身（使用 sessionConfig 的 allowWallPass 参数）
                    if (localSnake.checkWallCollision(allowWallPass = allowWallPass) || localSnake.checkSelfCollision()) {
                        isGameOver.value = true
                        // 死亡音效 + 震动
                        if (soundDie >= 0) {
                            // 立即压低 BGM，随后自动恢复（若已暂停将保持暂停）
                            duckBgm()
                            val volume = currentSfxVolume()
                            soundPool.play(soundDie, volume, volume, 1, 0, 1f)
                        }
                        val deathAmp =
                            (vibrationAmplitude(settingsState.vibrationLevel) * 0.8f)
                                .toInt()
                                .coerceIn(40, 255)
                        vibrate(80, deathAmp) // 80ms
                    } else {
                        // 吃食物：检查是否吃到任何食物
                        val currentFoods = foodsState.value
                        val eatenFoodIndex =
                            currentFoods.indexOfFirst { food ->
                                localSnake.isHeadAt(food.x, food.y)
                            }

                        if (eatenFoodIndex >= 0) {
                            // 吃到食物
                            localSnake.grow()
                            scoreState.value += GameConfig.SCORE_PER_FOOD

                            // 最高分持久化（仅单人模式）
                            if (!isMultiplayer && scoreState.value > highScoreState.value) {
                                highScoreState.value = scoreState.value
                                context
                                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                    .edit()
                                    .putInt(KEY_HIGH_SCORE, highScoreState.value)
                                    .apply()
                            }

                            // 音效 + 震动 + 闪烁
                            if (soundEat >= 0) {
                                // 立即压低 BGM，随后自动恢复
                                duckBgm()
                                val volume = currentSfxVolume()
                                soundPool.play(soundEat, volume, volume, 1, 0, 1f)
                            }
                            vibrate(20, vibrationAmplitude(settingsState.vibrationLevel)) // 20ms 轻微震动
                            eatBlinkFrames.value = GameConfig.EAT_BLINK_FRAMES

                            // 移除被吃的食物
                            val newFoods = currentFoods.toMutableList()
                            newFoods.removeAt(eatenFoodIndex)
                            foodsState.value = newFoods
                            foodState.value = newFoods.firstOrNull() // 兼容旧代码

                            // 规则：只有场上没有食物时才刷新新食物
                            // 多人模式：只有Host决定食物位置
                            if (newFoods.isEmpty() && (isMultiplayer && isHost || !isMultiplayer)) {
                                val occupied =
                                    mutableSetOf<Point>().apply {
                                        addAll(localSnake.getBody())
                                        if (isMultiplayer) {
                                            remoteSnakeState.value?.getBody()?.let { addAll(it) }
                                        }
                                        // 添加现有食物位置（虽然已经为空，但保留逻辑）
                                        newFoods.forEach { add(Point(it.x, it.y)) }
                                    }

                                // 计算应该有多少个食物
                                val playerCount = if (isMultiplayer) 2 else 1
                                val targetFoodCount = GameSessionConfig.calculateFoodCount(playerCount)

                                // 生成新食物直到达到目标数量
                                val spawnedFoods = mutableListOf<Food>()
                                var attempts = 0
                                while (spawnedFoods.size < targetFoodCount && attempts < 1000) {
                                    val newFood = Food.spawn(gridWidth.value, gridHeight.value, occupied)
                                    if (!spawnedFoods.any { it.x == newFood.x && it.y == newFood.y }) {
                                        spawnedFoods.add(newFood)
                                        occupied.add(Point(newFood.x, newFood.y))
                                    }
                                    attempts++
                                }

                                foodsState.value = spawnedFoods
                                foodState.value = spawnedFoods.firstOrNull() // 兼容旧代码
                            }
                        }
                    }

                    // 多人模式：发送游戏状态
                    if (isMultiplayer && localSnake != null) {
                        val snakes =
                            buildList<SnakeState> {
                                add(
                                    SnakeState(
                                        localPlayerId.value,
                                        localSnake.getBody().map { it.x to it.y },
                                    ),
                                )
                                remoteSnakeState.value?.let { remote ->
                                    add(
                                        SnakeState(
                                            remotePlayerId.value ?: "remote",
                                            remote.getBody().map { it.x to it.y },
                                        ),
                                    )
                                }
                            }
                        val foods = foodsState.value.map { FoodState(it.x, it.y) }
                        val stateJson =
                            NetProtocol.encodeGameState(
                                snakes = snakes,
                                foods = foods, // 改为多食物列表
                                score = scoreState.value,
                                tick = tick.value,
                            )
                        btController.send(stateJson)
                    }

                    // 闪烁帧数衰减（在状态更新后）
                    if (eatBlinkFrames.value > 0) {
                        eatBlinkFrames.value = eatBlinkFrames.value - 1
                    }

                    // 触发重绘（确保状态更新后再重绘）
                    tick.value++
                }
            }
            // 使用 sessionConfig 的速度参数
            val baseTick =
                (GameConfig.TICK_MS.toFloat() / sessionConfig.value.baseSpeed.coerceAtLeast(0.1f))
                    .toLong()
                    .coerceAtLeast(30L)
            val boostTick =
                (baseTick.toFloat() / sessionConfig.value.boostMultiplier.coerceAtLeast(1f))
                    .toLong()
                    .coerceAtLeast(15L)
            delay(if (isSpeedUp.value) boostTick else baseTick)
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

@Composable
private fun BluetoothRequirementScreen(
    title: String,
    message: String,
    buttonText: String,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onConfirm) { Text(buttonText) }
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onBack) { Text("返回") }
    }
}
