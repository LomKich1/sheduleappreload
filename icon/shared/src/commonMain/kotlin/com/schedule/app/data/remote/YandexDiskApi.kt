package com.schedule.app.data.remote

import com.schedule.app.util.Logger
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object YandexDiskApi {

    private const val TAG = "YaDisk"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class RemoteFile(
        val name: String,
        val path: String,
        val size: Long,
    )

    fun listFiles(publicKey: String): List<RemoteFile> {
        val enc = java.net.URLEncoder.encode(publicKey, "UTF-8")
        val url = "https://cloud-api.yandex.net/v1/disk/public/resources" +
                  "?public_key=$enc&limit=100&sort=name"

        Logger.d(TAG, "listFiles() → GET $url")

        val req = Request.Builder().url(url)
            .header("User-Agent", "ScheduleApp/1.0")
            .build()

        val (code, body) = client.newCall(req).execute().use { resp ->
            val b = resp.body?.string() ?: ""
            Logger.d(TAG, "listFiles() ← HTTP ${resp.code}, body[${b.length}]: ${b.take(400)}")
            resp.code to b
        }

        if (code !in 200..299) {
            val msg = runCatching { JSONObject(body).optString("message") }.getOrNull()
                ?: "HTTP $code"
            Logger.e(TAG, "listFiles() ОШИБКА: $msg")
            throw Exception("Яндекс.Диск: $msg")
        }

        val items = try {
            JSONObject(body).getJSONObject("_embedded").getJSONArray("items")
        } catch (e: Exception) {
            Logger.e(TAG, "listFiles() не удалось распарсить JSON: ${e.message}\nbody=$body")
            throw Exception("Яндекс.Диск: неверный формат ответа — ${e.message}")
        }

        Logger.d(TAG, "listFiles() всего items в ответе: ${items.length()}")

        return buildList {
            for (i in 0 until items.length()) {
                val obj  = items.getJSONObject(i)
                val name = obj.optString("name", "")
                val type = obj.optString("type", "")
                Logger.d(TAG, "  item[$i]: name='$name' type='$type'")
                if (!name.endsWith(".doc", ignoreCase = true)) {
                    Logger.d(TAG, "  ↳ пропущен (не .doc)")
                    continue
                }
                val remote = RemoteFile(
                    name = name,
                    path = obj.optString("path", ""),
                    size = obj.optLong("size", 0L),
                )
                Logger.d(TAG, "  ↳ добавлен: path='${remote.path}' size=${remote.size}")
                add(remote)
            }
        }.also { Logger.d(TAG, "listFiles() итого .doc файлов: ${it.size}") }
    }

    fun getDownloadUrl(publicKey: String, path: String): String {
        val enc     = java.net.URLEncoder.encode(publicKey, "UTF-8")
        val pathEnc = java.net.URLEncoder.encode(path, "UTF-8")
        val url = "https://cloud-api.yandex.net/v1/disk/public/resources/download" +
                  "?public_key=$enc&path=$pathEnc"

        Logger.d(TAG, "getDownloadUrl() → GET $url")

        val req = Request.Builder().url(url)
            .header("User-Agent", "ScheduleApp/1.0")
            .build()

        val body = client.newCall(req).execute().use { resp ->
            val b = resp.body?.string() ?: ""
            Logger.d(TAG, "getDownloadUrl() ← HTTP ${resp.code}, body: ${b.take(200)}")
            if (!resp.isSuccessful) throw Exception("Яндекс.Диск download: HTTP ${resp.code}")
            b
        }

        return (JSONObject(body).optString("href").takeIf { it.isNotEmpty() }
            ?: throw Exception("Яндекс.Диск не вернул href"))
            .also { Logger.d(TAG, "getDownloadUrl() href получен (${it.length} символов)") }
    }

    fun downloadBytes(href: String, onProgress: (Float) -> Unit = {}): ByteArray {
        Logger.d(TAG, "downloadBytes() → ${href.take(80)}...")
        onProgress(0.1f)
        val req = Request.Builder().url(href)
            .header("User-Agent", "ScheduleApp/1.0")
            .build()

        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Скачивание: HTTP ${resp.code}")
            onProgress(0.5f)
            val bytes = resp.body!!.bytes()
            onProgress(1f)
            Logger.d(TAG, "downloadBytes() скачано ${bytes.size} байт")
            bytes
        }
    }
}
