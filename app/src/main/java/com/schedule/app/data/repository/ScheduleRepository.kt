package com.schedule.app.data.repository

import com.schedule.app.data.model.ScheduleFile
import com.schedule.app.data.remote.GitHubApi
import com.schedule.app.data.remote.YandexDiskApi
import com.schedule.app.util.scheduleFileFromName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ─── ScheduleRepository ───────────────────────────────────────────────────────
// 1. Пробует Яндекс.Диск (publicKey из настроек)
// 2. При ошибке — GitHub (LomKich1/scheduletxt / schedule/)
// Результат кешируется в памяти до явного refresh().

object ScheduleRepository {

    // Кеш: publicKey → список файлов
    private var cachedKey: String = ""
    private var cachedFiles: List<ScheduleFile> = emptyList()

    // Источник последней успешной загрузки (для отладки / UI)
    var lastSource: String = ""
        private set

    /**
     * Возвращает список файлов расписания.
     * [publicKey] — URL публичной папки Я.Диска.
     * [forceRefresh] — сбросить кеш и загрузить заново.
     */
    suspend fun getFiles(
        publicKey: String,
        forceRefresh: Boolean = false,
    ): List<ScheduleFile> = withContext(Dispatchers.IO) {

        if (!forceRefresh && cachedKey == publicKey && cachedFiles.isNotEmpty()) {
            return@withContext cachedFiles
        }

        // ── Попытка 1: Яндекс.Диск ───────────────────────────────────────
        val yandexResult = runCatching {
            YandexDiskApi.listFiles(publicKey).map { remote ->
                // download URL получим лениво при клике; сейчас храним path
                scheduleFileFromName(remote.name, downloadUrl = "yadisk:${remote.path}")
            }.filterNotNull()
        }

        if (yandexResult.isSuccess) {
            val files = yandexResult.getOrThrow()
            cachedKey   = publicKey
            cachedFiles = files
            lastSource  = "Яндекс.Диск"
            return@withContext files
        }

        android.util.Log.w("ScheduleRepository",
            "Я.Диск недоступен: ${yandexResult.exceptionOrNull()?.message}, пробуем GitHub")

        // ── Попытка 2: GitHub ─────────────────────────────────────────────
        val githubResult = runCatching {
            GitHubApi.listFiles().map { remote ->
                scheduleFileFromName(remote.name, downloadUrl = remote.downloadUrl)
            }.filterNotNull()
        }

        if (githubResult.isSuccess) {
            val files = githubResult.getOrThrow()
            cachedKey   = publicKey
            cachedFiles = files
            lastSource  = "GitHub"
            return@withContext files
        }

        // Оба источника недоступны
        throw Exception(
            "Нет соединения.\nЯ.Диск: ${yandexResult.exceptionOrNull()?.message}\n" +
            "GitHub: ${githubResult.exceptionOrNull()?.message}"
        )
    }

    /**
     * Скачивает байты файла. Определяет источник по префиксу downloadUrl:
     * - "yadisk:..."  → Яндекс.Диск (getDownloadUrl + downloadBytes)
     * - всё остальное → прямой raw URL (GitHub или кастомный)
     */
    suspend fun downloadFile(
        publicKey: String,
        file: ScheduleFile,
        onProgress: (Float) -> Unit = {},
    ): ByteArray = withContext(Dispatchers.IO) {
        val url = file.downloadUrl
        if (url.startsWith("yadisk:")) {
            val path = url.removePrefix("yadisk:")
            val href = YandexDiskApi.getDownloadUrl(publicKey, path)
            YandexDiskApi.downloadBytes(href, onProgress)
        } else {
            // GitHub raw URL — с зеркалами
            GitHubApi.downloadBytes(url, onProgress)
        }
    }

    fun clearCache() {
        cachedKey   = ""
        cachedFiles = emptyList()
    }
}
