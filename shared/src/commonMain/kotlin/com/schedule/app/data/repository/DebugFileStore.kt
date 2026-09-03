package com.schedule.app.data.repository

import com.schedule.app.data.model.ScheduleFile
import com.schedule.app.util.scheduleFileFromName

// ─── DebugFileStore ───────────────────────────────────────────────────────────
// Держит РОВНО ОДИН JSON-файл, "подсунутый" вручную через пикер на
// DebugSettingsScreen (см. JsonTestSection). Живёт только в оперативной
// памяти процесса — ровно как обычный кеш ScheduleRepository.cachedFiles —
// и пропадает при перезапуске приложения. Никакого диска, никакой БД: если
// понадобится второй такой файл одновременно — усложняем тогда же, не раньше.
//
// Подключается к общему потоку данных в двух точках ScheduleRepository:
// getFiles() подмешивает injectedFile в список (чтобы он был виден на
// FilesScreen наравне с настоящими файлами с Я.Диска/GitHub), downloadFile()
// отдаёт injectedBytes напрямую по префиксу URL, без единого сетевого вызова.

object DebugFileStore {
    private const val URL_PREFIX = "debug-json:"

    var injectedFile: ScheduleFile? = null
        private set
    private var injectedBytes: ByteArray? = null

    /**
     * Сохраняет файл. Возвращает получившийся ScheduleFile, либо null — если
     * имя файла не распознано как день расписания (см. scheduleFileFromName,
     * формат "dd_MM_yyyy_ДЕНЬ.ext" или "dd.MM.yyyy ДЕНЬ.ext").
     */
    fun save(fileName: String, content: String): ScheduleFile? {
        val file = scheduleFileFromName(fileName, downloadUrl = "$URL_PREFIX$fileName") ?: return null
        injectedFile  = file
        injectedBytes = content.encodeToByteArray()
        return file
    }

    fun clear() {
        injectedFile  = null
        injectedBytes = null
    }

    fun isDebugUrl(url: String): Boolean = url.startsWith(URL_PREFIX)

    fun bytesFor(url: String): ByteArray? = if (isDebugUrl(url)) injectedBytes else null
}
