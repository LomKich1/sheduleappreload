package com.schedule.app.data.remote

import com.schedule.app.data.parser.DocParser
import com.schedule.app.util.IsDebugBuild
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

// ─── GitHub — fallback источник файлов расписания ────────────────────────────
object GitHubApi {

    const val OWNER = "LomKich1"
    const val REPO = "scheduletxt"
    const val BRANCH = "main"
    const val FOLDER = "schedule"

    private const val USER_AGENT = "ScheduleApp/1.0"
    private val client = createPlatformHttpClient(connectTimeoutMs = 12_000, requestTimeoutMs = 60_000)
    private val json = Json { ignoreUnknownKeys = true }

    data class RemoteFile(
        val name: String,
        val downloadUrl: String,
        val size: Long,
        val sha: String,
    )

    suspend fun listFiles(): List<RemoteFile> {
        val url = "https://api.github.com/repos/$OWNER/$REPO/contents/$FOLDER"
        val response = client.get(url) {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header(HttpHeaders.UserAgent, USER_AGENT)
        }
        if (!response.status.isSuccess()) {
            throw Exception("GitHub API: HTTP ${response.status.value}")
        }

        return json.parseToJsonElement(response.bodyAsText()).jsonArray.mapNotNull { element ->
            val item = element.jsonObject
            val name = item["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val isAccepted = name.endsWith(".doc", ignoreCase = true) ||
                (IsDebugBuild && name.endsWith(".json", ignoreCase = true))
            if (!isAccepted) return@mapNotNull null

            RemoteFile(
                name = name,
                downloadUrl = item["download_url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                size = item["size"]?.jsonPrimitive?.longOrNull ?: 0L,
                sha = item["sha"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }
    }

    /** Downloads from GitHub or a mirror, always verifying Git's blob SHA-1. */
    suspend fun downloadBytes(
        rawUrl: String,
        expectedSha: String,
        onProgress: (Float) -> Unit = {},
    ): ByteArray {
        val mirrors = listOf(
            rawUrl,
            rawUrl.replace("raw.githubusercontent.com", "mirror.ghproxy.com/raw.githubusercontent.com"),
            rawUrl.replace("raw.githubusercontent.com", "ghfast.top/raw.githubusercontent.com"),
        )

        var lastError: Exception? = null
        for ((index, url) in mirrors.withIndex()) {
            try {
                onProgress(0.1f + index * 0.15f)
                val response = client.get(url) { header(HttpHeaders.UserAgent, USER_AGENT) }
                if (!response.status.isSuccess()) throw Exception("HTTP ${response.status.value}")

                val declaredSize = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
                if (declaredSize > DocParser.MAX_DOC_SIZE_BYTES) {
                    throw Exception("Файл слишком большой ($declaredSize байт), пропускаем: $url")
                }
                onProgress(0.8f)
                val bytes: ByteArray = response.body()
                if (bytes.size > DocParser.MAX_DOC_SIZE_BYTES) {
                    throw Exception("Файл слишком большой (${bytes.size} байт) после скачивания: $url")
                }
                if (expectedSha.isNotEmpty() && !gitBlobSha1(bytes).equals(expectedSha, ignoreCase = true)) {
                    throw Exception("Целостность файла нарушена (SHA не совпал, источник: $url)")
                }

                onProgress(1f)
                return bytes
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw Exception("GitHub недоступен: ${lastError?.message}")
    }
}
