package com.schedule.app.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ─── Яндекс.Диск — публичная папка ───────────────────────────────────────────
// GET /v1/disk/public/resources?public_key=...&limit=100
// Возвращает список (name, downloadUrl). downloadUrl — временная прямая ссылка.

object YandexDiskApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class RemoteFile(
        val name: String,       // "Понедельник 09.06.doc"
        val path: String,       // "disk:/папка/Понедельник 09.06.doc"  — нужен для скачивания
        val size: Long,
    )

    /**
     * Получает список .doc-файлов из публичной папки.
     * [publicKey] — полный URL вида https://disk.yandex.ru/d/xxxx
     * Бросает исключение при сетевой ошибке или неверном ответе.
     */
    fun listFiles(publicKey: String): List<RemoteFile> {
        val enc = java.net.URLEncoder.encode(publicKey, "UTF-8")
        val url = "https://cloud-api.yandex.net/v1/disk/public/resources" +
                  "?public_key=$enc&limit=100&sort=name"

        val req = Request.Builder().url(url)
            .header("User-Agent", "ScheduleApp/1.0")
            .build()

        val body = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val msg = resp.body?.string()?.let {
                    runCatching { JSONObject(it).optString("message") }.getOrNull()
                } ?: "HTTP ${resp.code}"
                throw Exception("Яндекс.Диск: $msg")
            }
            resp.body!!.string()
        }

        val items = JSONObject(body)
            .getJSONObject("_embedded")
            .getJSONArray("items")

        return buildList {
            for (i in 0 until items.length()) {
                val obj = items.getJSONObject(i)
                val name = obj.optString("name", "")
                if (!name.endsWith(".doc", ignoreCase = true)) continue
                add(RemoteFile(
                    name = name,
                    path = obj.optString("path", ""),
                    size = obj.optLong("size", 0L),
                ))
            }
        }
    }

    /**
     * Получает временную прямую ссылку на скачивание файла по его [path].
     * [publicKey] — тот же URL папки.
     */
    fun getDownloadUrl(publicKey: String, path: String): String {
        val enc     = java.net.URLEncoder.encode(publicKey, "UTF-8")
        val pathEnc = java.net.URLEncoder.encode(path, "UTF-8")
        val url = "https://cloud-api.yandex.net/v1/disk/public/resources/download" +
                  "?public_key=$enc&path=$pathEnc"

        val req = Request.Builder().url(url)
            .header("User-Agent", "ScheduleApp/1.0")
            .build()

        val body = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Яндекс.Диск download: HTTP ${resp.code}")
            resp.body!!.string()
        }

        return JSONObject(body).optString("href")
            ?: throw Exception("Яндекс.Диск не вернул href")
    }

    /**
     * Скачивает файл по прямому URL. Используется после [getDownloadUrl].
     */
    fun downloadBytes(href: String, onProgress: (Float) -> Unit = {}): ByteArray {
        onProgress(0.1f)
        val req = Request.Builder().url(href)
            .header("User-Agent", "ScheduleApp/1.0")
            .build()

        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Скачивание: HTTP ${resp.code}")
            onProgress(0.5f)
            val bytes = resp.body!!.bytes()
            onProgress(1f)
            bytes
        }
    }
}
