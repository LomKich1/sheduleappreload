package com.schedule.app.data.repository

import com.schedule.app.util.Logger
import com.schedule.app.data.model.ScheduleFile
import com.schedule.app.data.remote.GitHubApi
import com.schedule.app.data.remote.YandexDiskApi
import com.schedule.app.util.scheduleFileFromName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ScheduleRepository {

    private const val TAG = "ScheduleRepo"

    private var cachedKey: String = ""
    private var cachedFiles: List<ScheduleFile> = emptyList()

    var lastSource: String = ""
        private set

    suspend fun getFiles(
        publicKey: String,
        forceRefresh: Boolean = false,
    ): List<ScheduleFile> = withContext(Dispatchers.IO) {

        Logger.d(TAG, "getFiles() publicKey='$publicKey' forceRefresh=$forceRefresh")

        if (!forceRefresh && cachedKey == publicKey && cachedFiles.isNotEmpty()) {
            Logger.d(TAG, "getFiles() → из кеша (${cachedFiles.size} файлов)")
            return@withContext cachedFiles
        }

        // ── Попытка 1: Яндекс.Диск ───────────────────────────────────────
        Logger.d(TAG, "Пробуем Яндекс.Диск...")
        val yandexResult = runCatching {
            val remotes = YandexDiskApi.listFiles(publicKey)
            Logger.d(TAG, "YaDisk вернул ${remotes.size} .doc файлов, парсим имена...")
            remotes.mapNotNull { remote ->
                val file = scheduleFileFromName(remote.name, downloadUrl = "yadisk:${remote.path}")
                if (file == null) {
                    Logger.w(TAG, "  имя '${remote.name}' не распознано — пропускаем")
                } else {
                    Logger.d(TAG, "  ✓ '${remote.name}' → ${file.dayLabel} ${file.dateLabel}")
                }
                file
            }
        }

        if (yandexResult.isSuccess) {
            val files = yandexResult.getOrThrow()
            Logger.d(TAG, "Яндекс.Диск: успех, ${files.size} файлов в итоге")
            if (files.isEmpty()) {
                Logger.w(TAG, "Список пустой! Проверь что в папке есть файлы вида dd_MM_yyyy_ДЕНЬ.doc")
            }
            cachedKey   = publicKey
            cachedFiles = files
            lastSource  = "Яндекс.Диск"
            return@withContext files
        }

        val yandexErr = yandexResult.exceptionOrNull()
        Logger.w(TAG, "Яндекс.Диск ОШИБКА: ${yandexErr?.message}", yandexErr)

        // ── Попытка 2: GitHub ─────────────────────────────────────────────
        Logger.d(TAG, "Пробуем GitHub...")
        val githubResult = runCatching {
            GitHubApi.listFiles().mapNotNull { remote ->
                val file = scheduleFileFromName(remote.name, downloadUrl = remote.downloadUrl)
                if (file == null) Logger.w(TAG, "  GitHub: имя '${remote.name}' не распознано")
                file
            }
        }

        if (githubResult.isSuccess) {
            val files = githubResult.getOrThrow()
            Logger.d(TAG, "GitHub: успех, ${files.size} файлов")
            cachedKey   = publicKey
            cachedFiles = files
            lastSource  = "GitHub"
            return@withContext files
        }

        val githubErr = githubResult.exceptionOrNull()
        Logger.e(TAG, "GitHub ОШИБКА: ${githubErr?.message}", githubErr)

        val errorMsg = "Нет соединения.\n" +
            "Я.Диск: ${yandexErr?.message}\n" +
            "GitHub: ${githubErr?.message}"
        Logger.e(TAG, "Оба источника недоступны: $errorMsg")
        throw Exception(errorMsg)
    }

    suspend fun downloadFile(
        publicKey: String,
        file: ScheduleFile,
        onProgress: (Float) -> Unit = {},
    ): ByteArray = withContext(Dispatchers.IO) {
        val url = file.downloadUrl
        Logger.d(TAG, "downloadFile() '${file.name}' url='${url.take(60)}...'")
        if (url.startsWith("yadisk:")) {
            val path = url.removePrefix("yadisk:")
            Logger.d(TAG, "  источник: Яндекс.Диск, path='$path'")
            val href = YandexDiskApi.getDownloadUrl(publicKey, path)
            YandexDiskApi.downloadBytes(href, onProgress)
        } else {
            Logger.d(TAG, "  источник: прямой URL (GitHub)")
            GitHubApi.downloadBytes(url, onProgress)
        }
    }

    fun clearCache() {
        Logger.d(TAG, "clearCache()")
        cachedKey   = ""
        cachedFiles = emptyList()
    }
}
