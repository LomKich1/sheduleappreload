package com.schedule.app.data.repository

import com.schedule.app.util.Logger
import com.schedule.app.data.model.ScheduleFile
import com.schedule.app.data.remote.GitHubApi
import com.schedule.app.data.remote.YandexDiskApi
import com.schedule.app.util.scheduleFileFromName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

object ScheduleRepository {

    private const val TAG = "ScheduleRepo"

    private var cachedKey: String = ""
    private var cachedFiles: List<ScheduleFile> = emptyList()

    var lastSource: String = ""
        private set

    /** Подмешивает debug-файл (если есть) в начало списка — см. DebugFileStore. */
    private fun withDebugFile(files: List<ScheduleFile>): List<ScheduleFile> =
        DebugFileStore.injectedFile?.let { debugFile -> listOf(debugFile) + files } ?: files

    suspend fun getFiles(
        publicKey: String,
        forceRefresh: Boolean = false,
    ): List<ScheduleFile> = withContext(Dispatchers.Default) {

        Logger.d(TAG, "getFiles() publicKey='$publicKey' forceRefresh=$forceRefresh")

        if (!forceRefresh && cachedKey == publicKey && cachedFiles.isNotEmpty()) {
            Logger.d(TAG, "getFiles() → из кеша (${cachedFiles.size} файлов)")
            return@withContext withDebugFile(cachedFiles)
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
            return@withContext withDebugFile(files)
        }

        val yandexErr = yandexResult.exceptionOrNull()
        Logger.w(TAG, "Яндекс.Диск ОШИБКА: ${yandexErr?.message}", yandexErr)

        // ── Попытка 2: GitHub ─────────────────────────────────────────────
        Logger.d(TAG, "Пробуем GitHub...")
        val githubResult = runCatching {
            GitHubApi.listFiles().mapNotNull { remote ->
                val file = scheduleFileFromName(remote.name, downloadUrl = remote.downloadUrl, sha = remote.sha)
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
            return@withContext withDebugFile(files)
        }

        val githubErr = githubResult.exceptionOrNull()
        Logger.e(TAG, "GitHub ОШИБКА: ${githubErr?.message}", githubErr)

        // Оба реальных источника недоступны — но если есть debug-файл,
        // хотя бы его показать вместо голой ошибки (удобно тестировать
        // JSON-парсинг вообще без сети/VPN).
        DebugFileStore.injectedFile?.let { debugFile ->
            Logger.w(TAG, "Оба источника недоступны, но есть debug-файл — показываем только его")
            return@withContext listOf(debugFile)
        }

        val errorMsg = "Нет соединения.\n" +
            "Я.Диск: ${yandexErr?.message}\n" +
            "GitHub: ${githubErr?.message}"
        Logger.e(TAG, "Оба источника недоступны: $errorMsg")
        throw Exception(errorMsg)
    }

    // Экран файла запускает загрузку сразу у двух ViewModel (группы и преподы),
    // причём это один и тот же файл. Две одновременные закачки с Я.Диска
    // приводили к HTTP 429: одна из колонок оставалась без данных. Теперь закачки
    // идут строго по очереди, а вторая берёт байты первой из короткого кеша
    // (TTL небольшой — чтобы не отдать устаревший файл при обновлении).
    // Ошибки не кешируются — «Повторить» качает заново.
    private val downloadMutex = Mutex()
    private var lastDownloadKey: String? = null
    private var lastDownloadBytes: ByteArray? = null
    private var lastDownloadAt: Long = 0L
    private const val DOWNLOAD_REUSE_MS = 30_000L

    suspend fun downloadFile(
        publicKey: String,
        file: ScheduleFile,
        onProgress: (Float) -> Unit = {},
    ): ByteArray = withContext(Dispatchers.Default) {
        // Debug-файл лежит в памяти, качать нечего — и кешировать нельзя (его
        // содержимое меняют прямо во время теста).
        if (DebugFileStore.isDebugUrl(file.downloadUrl)) {
            return@withContext downloadFileUncached(publicKey, file, onProgress)
        }
        val key = "$publicKey|${file.downloadUrl}|${file.sha}"
        downloadMutex.withLock {
            val now = Clock.System.now().toEpochMilliseconds()
            val cached = lastDownloadBytes
            if (cached != null && lastDownloadKey == key && now - lastDownloadAt < DOWNLOAD_REUSE_MS) {
                Logger.d(TAG, "downloadFile() '${file.name}' → из недавнего кеша")
                onProgress(1f)
                return@withLock cached
            }
            val bytes = downloadFileUncached(publicKey, file, onProgress)
            lastDownloadKey = key
            lastDownloadBytes = bytes
            lastDownloadAt = Clock.System.now().toEpochMilliseconds()
            bytes
        }
    }

    private suspend fun downloadFileUncached(
        publicKey: String,
        file: ScheduleFile,
        onProgress: (Float) -> Unit,
    ): ByteArray = withContext(Dispatchers.Default) {
        val url = file.downloadUrl
        Logger.d(TAG, "downloadFile() '${file.name}' url='${url.take(60)}...'")
        if (DebugFileStore.isDebugUrl(url)) {
            Logger.d(TAG, "  источник: debug-файл (из памяти, без сети)")
            onProgress(1f)
            DebugFileStore.bytesFor(url) ?: throw Exception("Debug-файл потерян (перезапуск приложения?)")
        } else if (url.startsWith("yadisk:")) {
            val path = url.removePrefix("yadisk:")
            Logger.d(TAG, "  источник: Яндекс.Диск, path='$path'")
            val href = YandexDiskApi.getDownloadUrl(publicKey, path)
            YandexDiskApi.downloadBytes(href, onProgress)
        } else {
            Logger.d(TAG, "  источник: прямой URL (GitHub)")
            GitHubApi.downloadBytes(url, expectedSha = file.sha, onProgress)
        }
    }

    fun clearCache() {
        Logger.d(TAG, "clearCache()")
        cachedKey   = ""
        cachedFiles = emptyList()
    }
}
