package com.schedule.app.data.remote

import com.schedule.app.data.parser.DocParser
import com.schedule.app.util.IsDebugBuild
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.security.MessageDigest
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
        val sha: String,         // git blob SHA1 — для проверки целостности после скачивания
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
                // .json — параллельный debug-only путь (JsonScheduleParser),
                // см. IsDebugBuild. В релизе игнорируется, даже если случайно
                // окажется в этой папке репозитория.
                val isAccepted = name.endsWith(".doc", ignoreCase = true) ||
                    (IsDebugBuild && name.endsWith(".json", ignoreCase = true))
                if (!isAccepted) continue
                add(RemoteFile(
                    name        = name,
                    downloadUrl = obj.optString("download_url", ""),
                    size        = obj.optLong("size", 0L),
                    sha         = obj.optString("sha", ""),
                ))
            }
        }
    }

    /**
     * git blob SHA1 файла — тот же алгоритм, что использует сам git и что
     * GitHub API возвращает в поле "sha" для контента репозитория:
     * sha1("blob " + размер_в_байтах + "\u0000" + содержимое).
     */
    private fun gitBlobSha1(bytes: ByteArray): String {
        val header = "blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(header)
        digest.update(bytes)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Скачивает файл. Пробует основной raw URL и зеркала (mirror.ghproxy.com,
     * ghfast.top — используются, когда raw.githubusercontent.com недоступен
     * из сети пользователя).
     *
     * ВАЖНО: зеркала — сторонние прокси, не под нашим контролем. Владелец
     * такого домена технически мог бы подменить содержимое файла на лету.
     * Поэтому после скачивания С ЛЮБОГО источника (включая основной)
     * содержимое сверяется по SHA1 с тем, что вернул официальный
     * GitHub Contents API (см. [RemoteFile.sha]) — если хэш не совпал,
     * файл не тот, что ожидался, пробуем следующее зеркало. Так подмена
     * содержимого технически невозможна, независимо от того, насколько
     * можно доверять конкретному зеркалу.
     */
    fun downloadBytes(rawUrl: String, expectedSha: String, onProgress: (Float) -> Unit = {}): ByteArray {
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
                val bytes = client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                    // Рубеж №1: смотрим заявленный размер ДО чтения тела в память —
                    // если сервер/зеркало прислали Content-Length больше разумного
                    // для файла расписания, не тратим память и трафик на скачивание.
                    val declaredSize = resp.body?.contentLength() ?: -1L
                    if (declaredSize > DocParser.MAX_DOC_SIZE_BYTES) {
                        throw Exception("Файл слишком большой ($declaredSize байт), пропускаем: $url")
                    }
                    onProgress(0.8f)
                    resp.body!!.bytes()
                }

                // Рубеж №2: на случай chunked-ответа без Content-Length.
                if (bytes.size > DocParser.MAX_DOC_SIZE_BYTES) {
                    throw Exception("Файл слишком большой (${bytes.size} байт) после скачивания: $url")
                }

                if (expectedSha.isNotEmpty()) {
                    val actualSha = gitBlobSha1(bytes)
                    if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                        throw Exception("Целостность файла нарушена (SHA не совпал, источник: $url)")
                    }
                }

                onProgress(1f)
                return bytes
            } catch (e: Exception) {
                lastErr = e
            }
        }
        throw Exception("GitHub недоступен: ${lastErr?.message}")
    }
}
