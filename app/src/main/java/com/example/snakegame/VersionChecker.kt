package com.example.snakegame

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object VersionChecker {
    private const val GITHUB_REPO_OWNER = "990874218"
    private const val GITHUB_REPO_NAME = "SnakeGame"
    private const val GITHUB_API_URL = "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"
    
    /**
     * 获取当前应用版本号
     */
    fun getCurrentVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0"
        }
    }
    
    /**
     * 从GitHub获取最新版本号
     */
    suspend fun getLatestVersion(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(GITHUB_API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.setRequestProperty("User-Agent", "SnakeGame-Android")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val tagName = json.getString("tag_name")
                    // 移除 "v" 前缀（如果有）
                    tagName.removePrefix("v")
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * 比较版本号
     * @return true 如果 currentVersion < latestVersion
     */
    fun isUpdateAvailable(currentVersion: String, latestVersion: String): Boolean {
        return try {
            val current = parseVersion(currentVersion)
            val latest = parseVersion(latestVersion)
            
            when {
                latest.major > current.major -> true
                latest.major == current.major && latest.minor > current.minor -> true
                latest.major == current.major && latest.minor == current.minor && latest.patch > current.patch -> true
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private data class VersionParts(val major: Int, val minor: Int, val patch: Int)
    
    private fun parseVersion(version: String): VersionParts {
        val parts = version.split(".").map { it.toIntOrNull() ?: 0 }
        return VersionParts(
            major = parts.getOrElse(0) { 0 },
            minor = parts.getOrElse(1) { 0 },
            patch = parts.getOrElse(2) { 0 }
        )
    }
}