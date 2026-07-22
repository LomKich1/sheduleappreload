package com.schedule.app.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

// ─── GitHub — fallback источник файлов расписания ────────────────────────────
// Репо: LomKich1/scheduletxt, папка schedule/
// Contents API: GET /repos/{owner}/{repo}/contents/{path}
// Скачивание: raw.githubusercontent.com (+ зеркала при недоступности)

object GitHubApi {

    const val OWNER  = "LomKich1"
    const val REPO   = "scheduletxt"
    const val BRANCH = "main"
    const val FOLDER = "schedule"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    data class RemoteFile(
        val name: String,        // "Понедельник 09.06.doc"
        val downloadUrl: String, // прямой raw URL
        val size: Long,
    )

    /**
     * Получает список .doc-файлов из папки [FOLDER] репозитория.
     * Бросает исключение при ошибке.
     */
    fun listFiles(): List<RemoteFile> {
        val url = "https://api.github.com/repos/$OWNER/$REPO/contents/$FOLDER"
        val req = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "ScheduleApp/1.0")
            .build()

        val body = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("GitHub API: HTTP ${resp.code}")
            resp.body!!.string()
        }

        val arr = JSONArray(body)
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val name = obj.optString("name", "")
                if (!name.endsWith(".doc", ignoreCase = true)) continue
                add(RemoteFile(
                    name        = name,
                    downloadUrl = obj.optString("download_url", ""),
                    size        = obj.optLong("size", 0L),
                ))
            }
        }
    }

    /**
     * Скачивает файл. Пробует основной raw URL и три зеркала.
     */
    fun downloadBytes(rawUrl: String, onProgress: (Float) -> Unit = {}): ByteArray {
        // Зеркала на случай, если raw.githubusercontent.com недоступен
        val mirrors = listOf(
            rawUrl,
            rawUrl.replace("raw.githubusercontent.com", "mirror.ghproxy.com/raw.githubusercontent.com"),
            rawUrl.replace("raw.githubusercontent.com", "ghfast.top/raw.githubusercontent.com"),
        )

        var lastErr: Exception? = null
        mirrors.forEachIndexed { idx, url ->
            try {
                onProgress(0.1f + idx * 0.15f)
                val req = Request.Builder().url(url)
                    .header("User-Agent", "ScheduleApp/1.0")
                    .build()
                return client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                    onProgress(0.8f)
                    val bytes = resp.body!!.bytes()
                    onProgress(1f)
                    bytes
                }
            } catch (e: Exception) {
                lastErr = e
            }
        }
        throw Exception("GitHub недоступен: ${lastErr?.message}")
    }
}
