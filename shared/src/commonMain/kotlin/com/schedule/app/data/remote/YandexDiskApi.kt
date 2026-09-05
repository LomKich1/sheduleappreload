package com.schedule.app.data.remote

import com.schedule.app.data.parser.DocParser
import com.schedule.app.util.IsDebugBuild
import com.schedule.app.util.Logger
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object YandexDiskApi {

    private const val TAG = "YaDisk"
    private const val USER_AGENT = "ScheduleApp/1.0"
    private val client = createPlatformHttpClient(connectTimeoutMs = 12_000, requestTimeoutMs = 30_000)
    private val json = Json { ignoreUnknownKeys = true }

    data class RemoteFile(
        val name: String,
        val path: String,
        val size: Long,
    )

    suspend fun listFiles(publicKey: String): List<RemoteFile> {
        val url = apiUrl("resources") {
            parameters.append("public_key", publicKey)
            parameters.append("limit", "100")
            parameters.append("sort", "name")
        }
        Logger.d(TAG, "listFiles() → GET $url")

        val response = client.get(url) { header(HttpHeaders.UserAgent, USER_AGENT) }
        val body = response.bodyAsText()
        Logger.d(TAG, "listFiles() ← HTTP ${response.status.value}, body[${body.length}]: ${body.take(400)}")
        if (!response.status.isSuccess()) {
            val message = runCatching {
                json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.contentOrNull
            }.getOrNull() ?: "HTTP ${response.status.value}"
            Logger.e(TAG, "listFiles() ОШИБКА: $message")
            throw Exception("Яндекс.Диск: $message")
        }

        val items = try {
            json.parseToJsonElement(body).jsonObject["_embedded"]
                ?.jsonObject?.get("items")?.jsonArray
                ?: error("items не найдены")
        } catch (error: Exception) {
            Logger.e(TAG, "listFiles() не удалось распарсить JSON: ${error.message}")
            throw Exception("Яндекс.Диск: неверный формат ответа — ${error.message}")
        }

        return items.mapNotNull { element ->
            val item = element.jsonObject
            val name = item["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val isAccepted = name.endsWith(".doc", ignoreCase = true) ||
                (IsDebugBuild && name.endsWith(".json", ignoreCase = true))
            if (!isAccepted) return@mapNotNull null

            RemoteFile(
                name = name,
                path = item["path"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                size = item["size"]?.jsonPrimitive?.longOrNull ?: 0L,
            )
        }.also { Logger.d(TAG, "listFiles() итого файлов: ${it.size}") }
    }

    suspend fun getDownloadUrl(publicKey: String, path: String): String {
        val url = apiUrl("resources/download") {
            parameters.append("public_key", publicKey)
            parameters.append("path", path)
        }
        Logger.d(TAG, "getDownloadUrl() → GET $url")
        val response = client.get(url) { header(HttpHeaders.UserAgent, USER_AGENT) }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw Exception("Яндекс.Диск download: HTTP ${response.status.value}")
        }
        return json.parseToJsonElement(body).jsonObject["href"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotEmpty() }
            ?: throw Exception("Яндекс.Диск не вернул href")
    }

    suspend fun downloadBytes(href: String, onProgress: (Float) -> Unit = {}): ByteArray {
        Logger.d(TAG, "downloadBytes() → ${href.take(80)}...")
        onProgress(0.1f)
        val response = client.get(href) { header(HttpHeaders.UserAgent, USER_AGENT) }
        if (!response.status.isSuccess()) throw Exception("Скачивание: HTTP ${response.status.value}")

        val declaredSize = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
        if (declaredSize > DocParser.MAX_DOC_SIZE_BYTES) {
            throw Exception("Файл слишком большой ($declaredSize байт)")
        }
        onProgress(0.5f)
        val bytes: ByteArray = response.body()
        if (bytes.size > DocParser.MAX_DOC_SIZE_BYTES) {
            throw Exception("Файл слишком большой (${bytes.size} байт)")
        }
        onProgress(1f)
        Logger.d(TAG, "downloadBytes() скачано ${bytes.size} байт")
        return bytes
    }

    private fun apiUrl(path: String, configure: URLBuilder.() -> Unit): String =
        URLBuilder("https://cloud-api.yandex.net/v1/disk/public/$path").apply(configure).buildString()
}
