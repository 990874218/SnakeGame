package com.example.snakegame.multiplayer
import com.example.snakegame.Snake

/**
 * 游戏会话配置
 * 用于存储当前游戏会话的设置参数
 */
data class GameSessionConfig(
    val maxPlayers: Int = 2,
    val allowWallPass: Boolean = false,
    val baseSpeed: Float = 1.0f,
    val boostMultiplier: Float = 1.5f,
) {
    companion object {
        /**
         * 根据玩家数量计算应该有多少个食物
         * 规则：场上食物数量 = max(1, 玩家总数-1)
         */
        fun calculateFoodCount(playerCount: Int): Int = maxOf(1, playerCount - 1)
    }
}

/**
 * 玩家状态
 */
enum class PlayerStatus {
    ALIVE, // 存活
    DEAD, // 死亡（观战模式）
}

/**
 * 游戏结果
 */
sealed class GameResult {
    object Playing : GameResult() // 游戏中

    data class Victory(
        val winnerId: String,
    ) : GameResult() // 有获胜者

    object Draw : GameResult() // 平局（所有人同时死亡）
}

/**
 * 玩家信息
 */
data class PlayerInfo(
    val id: String,
    val name: String,
    val status: PlayerStatus = PlayerStatus.ALIVE,
    val snake: Snake? = null,
)
