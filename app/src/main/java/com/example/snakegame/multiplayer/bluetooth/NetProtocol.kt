package com.example.snakegame.multiplayer.bluetooth

import org.json.JSONArray
import org.json.JSONObject

// 仅示例：位置用坐标列表；可后续压缩
data class SnakeState(
    val id: String,
    val body: List<Pair<Int, Int>>,
)

data class FoodState(
    val x: Int,
    val y: Int,
)

data class GameStatePayload(
    val snakes: List<SnakeState>,
    val foods: List<FoodState>, // 改为多食物列表
    val score: Int,
    val tick: Int,
)

object NetProtocol {

    fun encodeGameState(
        snakes: List<SnakeState>,
        foods: List<FoodState>, // 改为多食物列表
        score: Int,
        tick: Int,
    ): String {
        val snakeArray =
            JSONArray().apply {
                snakes.forEach { snake ->
                    val bodyArray =
                        JSONArray().apply {
                            snake.body.forEach { (x, y) ->
                                put(JSONArray().put(x).put(y))
                            }
                        }
                    put(
                        JSONObject()
                            .put("id", snake.id)
                            .put("body", bodyArray),
                    )
                }
            }
        val foodArray = JSONArray().apply {
            foods.forEach { food ->
                put(JSONObject().put("x", food.x).put("y", food.y))
            }
        }
        return JSONObject()
            .put("type", "state")
            .put("snakes", snakeArray)
            .put("foods", foodArray) // 改为 foods 数组
            .put("score", score)
            .put("tick", tick)
            .toString()
    }

    fun decodeGameState(line: String): GameStatePayload? =
        runCatching {
            val obj = JSONObject(line)
            if (obj.optString("type") != "state") return@runCatching null
            val snakesJson = obj.getJSONArray("snakes")
            val snakes =
                buildList {
                    for (i in 0 until snakesJson.length()) {
                        val snakeObj = snakesJson.getJSONObject(i)
                        val bodyJson = snakeObj.getJSONArray("body")
                        val body =
                            buildList {
                                for (j in 0 until bodyJson.length()) {
                                    val coord = bodyJson.getJSONArray(j)
                                    add(coord.getInt(0) to coord.getInt(1))
                                }
                            }
                        add(SnakeState(snakeObj.getString("id"), body))
                    }
                }
            // 兼容旧版本（单个食物）和新版本（多食物）
            val foods = if (obj.has("foods")) {
                val foodArray = obj.getJSONArray("foods")
                buildList {
                    for (i in 0 until foodArray.length()) {
                        val foodObj = foodArray.getJSONObject(i)
                        add(FoodState(foodObj.getInt("x"), foodObj.getInt("y")))
                    }
                }
            } else if (obj.has("food")) {
                // 兼容旧版本：单个食物
                val foodObj = obj.getJSONObject("food")
                listOf(FoodState(foodObj.getInt("x"), foodObj.getInt("y")))
            } else {
                emptyList()
            }
            GameStatePayload(
                snakes = snakes,
                foods = foods, // 改为多食物列表
                score = obj.optInt("score", 0),
                tick = obj.optInt("tick", 0),
            )
        }.getOrNull()

    fun encodePlayerDeath(
        playerId: String,
        deathPosition: Pair<Int, Int>,
    ): String =
        JSONObject()
            .put("type", "death")
            .put("playerId", playerId)
            .put("x", deathPosition.first)
            .put("y", deathPosition.second)
            .toString()

    fun decodePlayerDeath(line: String): Pair<String, Pair<Int, Int>>? =
        runCatching {
            val obj = JSONObject(line)
            if (obj.optString("type") != "death") return@runCatching null
            val playerId = obj.getString("playerId")
            val x = obj.getInt("x")
            val y = obj.getInt("y")
            playerId to (x to y)
        }.getOrNull()
}
