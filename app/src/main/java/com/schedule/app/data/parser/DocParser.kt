package com.schedule.app.data.parser

import com.schedule.app.data.model.LessonEntry
import com.schedule.app.data.model.ScheduleDay
import com.schedule.app.data.model.ScheduleParseResult
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar

// ══════════════════════════════════════════════════════════════════════════════
//  DocParser  —  OLE2 / .doc  →  List<ScheduleDay>
//
//  Надёжные отличия от старого:
//  1. readSectors() не уходит в бесконечный цикл при битых FAT-цепочках
//  2. getCells() не дропает ячейки с «некрасивым» соотношением символов
//  3. parseDoc() находит блок расписания даже если заголовок без "Расписание"
//  4. buildScheduleDay() правильно обрабатывает пустые ячейки (Окно)
//  5. parseTeacher() — отдельная, чистая функция
// ══════════════════════════════════════════════════════════════════════════════

object DocParser {

    // ── Расписание звонков ────────────────────────────────────────────────────
    // [timeStart, timeEnd, breakStart?, breakEnd?]

    private val BELLS_MON = mapOf(
        "I"   to listOf("09:00", "09:45", "09:50", "10:35"),
        "II"  to listOf("10:45", "11:30", "11:35", "12:20"),
        "III" to listOf("12:50", "13:35", "13:40", "14:25"),
        "IV"  to listOf("14:35", "15:35", null,    null   ),
        "V"   to listOf("15:45", "16:45", null,    null   ),
        "VI"  to listOf("16:55", "17:55", null,    null   ),
    )
    private val BELLS_TUE = mapOf(
        "I"   to listOf("08:30", "09:15", "09:20", "10:05"),
        "II"  to listOf("10:15", "11:00", "11:05", "11:50"),
        "III" to listOf("12:20", "13:05", "13:10", "13:55"),
        "IV"  to listOf("14:05", "15:05", null,    null   ),
        "V"   to listOf("15:15", "16:15", null,    null   ),
        "VI"  to listOf("16:25", "17:25", null,    null   ),
    )
    private val BELLS_SAT = mapOf(
        "I"   to listOf("08:30", "09:30", null, null),
        "II"  to listOf("09:40", "10:40", null, null),
        "III" to listOf("10:50", "11:50", null, null),
        "IV"  to listOf("12:00", "13:00", null, null),
        "V"   to listOf("13:10", "14:10", null, null),
        "VI"  to listOf("14:20", "15:20", null, null),
    )

    private val ROMAN = setOf("I", "II", "III", "IV", "V", "VI")
    private val GRP_RE = Regex("""^\d{0,2}[А-ЯЁа-яёA-Za-z]{1,6}-?\d-\d{2}$""")
    private val HDR_RE = Regex("расписание занятий", RegexOption.IGNORE_CASE)

    // ── OLE2: читает один named-стрим целиком ───────────────────────────────
    // Возвращает Triple(streamBytes, fatList, sectorSize) — fat/sectorSize нужны
    // вызывающему коду, чтобы не парсить заголовок повторно для других стримов.

    private class Ole2Reader(private val data: ByteArray) {
        val sectorSize: Int
        private val fat: List<Long>
        private val dirEntries: List<DirEntry>

        data class DirEntry(val name: String, val startSector: Long, val size: Long)

        init {
            sectorSize = 1 shl u16(data, 30)

            val numFatSectors = u32(data, 44).toInt()
            val difat = mutableListOf<Long>()
            for (i in 0 until minOf(109, numFatSectors)) {
                val v = u32(data, 76 + i * 4)
                if (v >= 0xFFFFFFFCL) break
                difat.add(v)
            }

            val fatList = mutableListOf<Long>()
            for (sn in difat) {
                val off = 512 + sn.toInt() * sectorSize
                if (off + sectorSize > data.size) continue
                var i = 0
                while (i * 4 + 4 <= sectorSize) {
                    fatList.add(u32(data, off + i * 4))
                    i++
                }
            }
            fat = fatList

            val dirSector = u32(data, 48)
            val dirBytes = readChain(dirSector)
            val entries = mutableListOf<DirEntry>()
            val entryCount = dirBytes.size / 128
            for (i in 0 until entryCount) {
                val e = dirBytes.copyOfRange(i * 128, (i + 1) * 128)
                if (e.size < 128) break
                val nl = (e[64].toInt() and 0xFF) or ((e[65].toInt() and 0xFF) shl 8)
                if (nl < 2) continue
                val name = runCatching { String(e.copyOfRange(0, nl - 2), Charsets.UTF_16LE) }
                    .getOrNull() ?: continue
                val buf = ByteBuffer.wrap(e).order(ByteOrder.LITTLE_ENDIAN)
                val start = buf.getInt(116).toLong() and 0xFFFFFFFFL
                val size  = buf.getLong(120)
                entries += DirEntry(name, start, size)
            }
            dirEntries = entries
        }

        fun readChain(startSec: Long, maxBytes: Int = 0): ByteArray {
            val chunks = mutableListOf<ByteArray>()
            var total = 0
            var sec = startSec
            val visited = mutableSetOf<Long>()
            while (sec < 0xFFFFFFFCL && sec !in visited) {
                visited += sec
                val off = 512 + sec.toInt() * sectorSize
                if (off >= data.size) break
                val len = minOf(sectorSize, data.size - off)
                chunks += data.copyOfRange(off, off + len)
                total += len
                if (maxBytes > 0 && total >= maxBytes) break
                sec = if (sec < fat.size) fat[sec.toInt()] else 0xFFFFFFFEL
            }
            val limit = if (maxBytes > 0) minOf(total, maxBytes) else total
            val out = ByteArray(limit)
            var pos = 0
            for (c in chunks) {
                val take = minOf(c.size, out.size - pos)
                System.arraycopy(c, 0, out, pos, take)
                pos += take
                if (pos >= out.size) break
            }
            return out
        }

        /** Читает стрим по имени (например "WordDocument", "1Table", "0Table"). */
        fun readStream(name: String): ByteArray? {
            val entry = dirEntries.firstOrNull { it.name == name } ?: return null
            if (entry.startSector < 0 || entry.size <= 0) return ByteArray(0)
            return readChain(entry.startSector, entry.size.toInt())
        }
    }

    // ── 1. OLE2 → строка текста (с учётом piece table / CLX) ────────────────
    // Наивное чтение fcMin..fcMin+ccpText*2 работает только для однокускового
    // документа. Реальные .doc почти всегда multi-piece (правки, вставки) —
    // нужно собирать текст через PlcPcd (Piece table) из стрима *Table.

    fun extractText(data: ByteArray): String {
        val sig = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
                              0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte())
        if (data.size < 512) return ""
        if (sig.indices.any { data[it] != sig[it] }) return ""

        val ole = runCatching { Ole2Reader(data) }.getOrNull() ?: return ""
        val wd  = ole.readStream("WordDocument") ?: return ""
        if (wd.size < 0x200) return ""

        // fWhichTblStm — бит 9 (0x0200) в FIB flags (offset 0x0A) выбирает
        // между "0Table" и "1Table" (зависит от того, сохранён файл как
        // черновик правок или финально).
        val flags = u16(wd, 0x0A)
        val fWhichTblStm = (flags shr 9) and 1
        val tableName = if (fWhichTblStm == 1) "1Table" else "0Table"
        val table = ole.readStream(tableName)
            ?: ole.readStream(if (tableName == "1Table") "0Table" else "1Table")

        // fcClx/lcbClx лежат в fibRgFcLcb97, смещение 0x1A2 от начала WordDocument
        if (table != null && wd.size >= 0x1A2 + 8) {
            val fcClx  = u32(wd, 0x1A2).toInt()
            val lcbClx = u32(wd, 0x1A6).toInt()
            if (fcClx >= 0 && lcbClx > 0 && fcClx + lcbClx <= table.size) {
                val text = extractTextViaClx(wd, table, fcClx, lcbClx)
                if (text.isNotBlank()) return text
            }
        }

        // Фоллбэк: если CLX не нашёлся/повреждён — наивное однокусковое чтение
        val fcMin   = u32(wd, 24).toInt()
        val ccpText = u32(wd, 28).toInt()
        val start = fcMin.coerceIn(0, wd.size)
        val end   = (start + ccpText * 2).coerceAtMost(wd.size)
        if (start >= end) return ""
        return runCatching { String(wd.copyOfRange(start, end), Charsets.UTF_16LE) }
            .getOrElse { "" }
    }

    /**
     * Разбирает Clx → Pcdt → PlcPcd и собирает полный текст документа,
     * склеивая все piece'ы в порядке их CP (character position).
     * Каждый piece читается либо как cp1251 (сжатый, 1 байт/символ),
     * либо как UTF-16LE (несжатый, 2 байта/символ) — определяется битом
     * компрессии в fc (старший бит после маскирования 0x40000000).
     */
    private fun extractTextViaClx(wd: ByteArray, table: ByteArray, fcClx: Int, lcbClx: Int): String {
        val clx = table.copyOfRange(fcClx, fcClx + lcbClx)

        // Clx = последовательность Prc (tag=1) и Pcdt (tag=2, ровно один)
        var i = 0
        var pcdt: ByteArray? = null
        while (i < clx.size) {
            when (clx[i].toInt() and 0xFF) {
                1 -> {
                    if (i + 3 > clx.size) break
                    val size = u16(clx, i + 1)
                    i += 3 + size
                }
                2 -> {
                    if (i + 5 > clx.size) break
                    val size = u32(clx, i + 1).toInt()
                    val from = i + 5
                    val to = (from + size).coerceAtMost(clx.size)
                    pcdt = clx.copyOfRange(from, to)
                    i = to
                }
                else -> i = clx.size // неизвестный тег — останавливаемся
            }
        }
        val pcdtBytes = pcdt ?: return ""
        if (pcdtBytes.size < 4) return ""

        // PlcPcd: (n+1) x int32 CP-границы, затем n x 8-byte PCD
        val n = (pcdtBytes.size - 4) / 12
        if (n <= 0) return ""

        val sb = StringBuilder()
        for (k in 0 until n) {
            val cpStart = u32(pcdtBytes, k * 4).toInt()
            val cpEnd   = u32(pcdtBytes, (k + 1) * 4).toInt()
            val nChars  = cpEnd - cpStart
            if (nChars <= 0) continue

            val pcdOff = 4 * (n + 1) + k * 8
            if (pcdOff + 8 > pcdtBytes.size) break
            val fcRaw = u32(pcdtBytes, pcdOff + 2).toInt()
            val isCompressed = (fcRaw shr 30) and 1 == 1
            val fc = fcRaw and 0x3FFFFFFF

            val piece = if (isCompressed) {
                // 1 байт/символ, обычно cp1252/cp1251; смещение в потоке = fc/2
                val byteOff = fc / 2
                val end = (byteOff + nChars).coerceAtMost(wd.size)
                if (byteOff >= wd.size || byteOff >= end) "" else
                    runCatching { String(wd, byteOff, end - byteOff, charset("windows-1251")) }
                        .getOrElse { "" }
            } else {
                val byteOff = fc
                val end = (byteOff + nChars * 2).coerceAtMost(wd.size)
                if (byteOff >= wd.size || byteOff >= end) "" else
                    runCatching { String(wd, byteOff, end - byteOff, Charsets.UTF_16LE) }
                        .getOrElse { "" }
            }
            sb.append(piece)
        }
        return sb.toString()
    }

    // ── 2. Текст → ячейки таблицы ────────────────────────────────────────────
    // Разделитель ячеек в Word-таблицах — символ \x07

    fun getCells(data: ByteArray): List<String> {
        val raw = extractText(data)
        if (raw.isEmpty()) return emptyList()

        return raw.split('\u0007').map { cell ->
            cell
                .replace('\r', '\n')
                // Убираем непечатные управляющие символы (кроме \t и \n)
                .replace(Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]"), "")
                .lines()
                .joinToString("\n") { it.trim() }
                .trim()
                // Сворачиваем 3+ подряд пустых строк в двойной перевод
                .replace(Regex("\n{3,}"), "\n\n")
        }
    }

    // ── 3. Ячейки → расписание группы ────────────────────────────────────────

    /**
     * Ищет в ячейках блок "Расписание занятий", находит колонку [group],
     * возвращает пары [(roman, lessonText)].
     *
     * Логика:
     *  - Сканируем ячейки, ищем ту, что содержит "Расписание занятий" (HDR_RE)
     *  - После заголовка ищем строку с группами (ячейки вида ГР-1-24)
     *  - Определяем offset колонки нашей группы
     *  - Дальше: строки с римскими цифрами — номера пар; берём ячейку [ri + offset]
     */
    fun parseDoc(data: ByteArray, group: String): Pair<List<Pair<String, String>>, String> {
        val cells = getCells(data)
        if (cells.isEmpty()) return emptyList<Pair<String, String>>() to ""

        val normGroup = normalize(group)

        // Находим все индексы заголовков блоков расписания
        val hdrIndices = cells.indices.filter { HDR_RE.containsMatchIn(cells[it]) }
        if (hdrIndices.isEmpty()) return emptyList<Pair<String, String>>() to ""

        // Добавляем sentinel в конец
        val boundaries = hdrIndices + listOf(cells.size)

        for (bi in 0 until boundaries.size - 1) {
            val blockStart = boundaries[bi]
            val blockEnd   = boundaries[bi + 1]

            // Ищем строку с группами: первая ячейка после заголовка, где есть GRP_RE
            var groupRowStart = blockStart + 1
            while (groupRowStart < blockEnd && !looksLikeGroup(cells[groupRowStart])) {
                groupRowStart++
            }
            if (groupRowStart >= blockEnd) continue

            // Собираем все ячейки-группы подряд (одна строка таблицы = несколько ячеек)
            var groupRowEnd = groupRowStart
            while (groupRowEnd < blockEnd && looksLikeGroup(cells[groupRowEnd])) {
                groupRowEnd++
            }

            // Ищем нашу группу
            val colIdx = (groupRowStart until groupRowEnd).firstOrNull {
                normalize(cells[it]) == normGroup
            } ?: continue

            // Смещение колонки данных урока относительно ячейки с римской цифрой.
            // Между строкой "I/II/..." и строкой групп есть техническая ячейка-
            // разделитель (там, где у самой строки с номером пары стоит сама
            // римская цифра) — поэтому реальные данные смещены на +1 относительно
            // (позиция группы в своей строке).
            val offset = (colIdx - groupRowStart) + 1

            // Заголовок (день/дата) — строка, содержащая "Расписание занятий";
            // перед ней может стоять «Утверждаю» / ФИО директора — их пропускаем.
            val headerLine = cells[blockStart]
                .lines()
                .firstOrNull { HDR_RE.containsMatchIn(it) }
                ?.trim()
                ?: cells[blockStart].lines().firstOrNull { it.isNotBlank() }?.trim()
                ?: ""

            // Собираем пары: ячейки с римской цифрой — начало строки таблицы
            val result = mutableListOf<Pair<String, String>>()
            val seenRoman = mutableSetOf<String>()
            var ri = groupRowEnd
            while (ri < blockEnd) {
                val cell = cells[ri].trim()
                if (cell in ROMAN && cell !in seenRoman) {
                    seenRoman += cell
                    val lessonIdx = ri + offset
                    val lessonText = if (lessonIdx < blockEnd) cells[lessonIdx].trim() else ""
                    result += cell to lessonText
                }
                ri++
            }

            if (result.isNotEmpty()) return result to headerLine
        }

        return emptyList<Pair<String, String>>() to ""
    }

    // ── 4. Пары → ScheduleDay ─────────────────────────────────────────────────
    //
    // isNow / isNext / remainText / progressPct больше НЕ вычисляются здесь.
    // Живые значения считаются в ScheduleScreen через ScheduleViewModel.clockMin
    // каждые 30 секунд — иначе они замерзают на момент открытия файла.

    fun buildScheduleDay(
        rawPairs: List<Pair<String, String>>,
        headerLine: String,
        fileName: String,
    ): ScheduleDay? {
        if (rawPairs.isEmpty()) return null

        val bells   = bellsForFile(fileName)
        val cal     = Calendar.getInstance()
        val isToday = checkIsToday(headerLine, cal)

        val lessons = rawPairs.map { (roman, rawText) ->
            val b = bells[roman]

            // Время пары из таблицы звонков
            val tStart = b?.getOrNull(0) ?: ""
            val tEnd   = b?.getOrNull(1) ?: ""
            val bStart = b?.getOrNull(2)
            val bEnd   = b?.getOrNull(3)

            // Абсолютные минуты от полуночи — нужны для live статуса в Screen
            // Для «Окна» (tStart == "") ставим -1, чтобы не пересекаться с реальным временем
            val startMin = if (tStart.isNotEmpty()) toMin(tStart) else -1
            val endMin   = if (tStart.isNotEmpty()) toMin(bEnd ?: tEnd) else -1

            // Разбор текста ячейки
            val lines = rawText.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }

            val isEmpty = lines.isEmpty()

            val (subject, teacherRaw) = if (isEmpty) {
                "Окно" to null
            } else {
                splitSubjectTeacher(lines)
            }

            val (teacher, room) = if (teacherRaw != null) {
                parseTeacher(teacherRaw)
            } else {
                null to null
            }

            LessonEntry(
                num        = roman,
                timeStart  = tStart,
                timeEnd    = tEnd,
                breakStart = bStart,
                breakEnd   = bEnd,
                startMin   = startMin,
                endMin     = endMin,
                subject    = subject,
                teacher    = teacher,
                room       = room,
                isWindow   = isEmpty,
            )
        }

        val header = formatHeader(headerLine)
        return ScheduleDay(header = header, lessons = lessons, isToday = isToday)
    }

    private val PRACTICE_RE = Regex("""на практике\s*:\s*(.+)""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /**
     * Парсит список групп "на практике" из текста документа (обычно в самом
     * низу, после "Диспетчер ___"). Возвращает нормализованные имена групп.
     */
    fun detectPracticeGroups(data: ByteArray): Set<String> {
        val cells = getCells(data)
        for (cell in cells.asReversed()) {
            val m = PRACTICE_RE.find(cell) ?: continue
            return m.groupValues[1]
                .replace('\n', ',')
                .split(',')
                .map { it.trim() }
                // Отбрасываем явный мусор из футера (номер страницы и т.п.) —
                // настоящий шифр группы содержит хотя бы одну цифру И хотя бы
                // одну букву, а "PAGE 4" — это просто слово + число без дефиса.
                .filter { token ->
                    token.isNotBlank() &&
                        token.any { it.isDigit() } &&
                        token.any { it.isLetter() } &&
                        !token.contains(' ')
                }
                .map { normalize(it) }
                .toSet()
        }
        return emptySet()
    }

    /**
     * Полный разбор файла для конкретной группы: сначала проверяет, не на
     * практике ли группа, затем ищет её в обычной сетке расписания.
     */
    fun parseForGroup(data: ByteArray, group: String, fileName: String): ScheduleParseResult {
        val practiceGroups = detectPracticeGroups(data)
        if (normalize(group) in practiceGroups) {
            val cells = getCells(data)
            val headerLine = cells.firstOrNull { HDR_RE.containsMatchIn(it) }
                ?.lines()?.firstOrNull { HDR_RE.containsMatchIn(it) }
                ?.trim() ?: ""
            return ScheduleParseResult.OnPractice(formatHeader(headerLine))
        }

        val (pairs, headerLine) = parseDoc(data, group)
        val day = buildScheduleDay(pairs, headerLine, fileName)
        return if (day != null) ScheduleParseResult.Found(day) else ScheduleParseResult.NotFound
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun normalize(s: String) =
        s.trim().uppercase().replace(Regex("""[\s\-.]"""), "")

    private fun looksLikeGroup(cell: String): Boolean {
        val line = cell.lines().firstOrNull { it.isNotBlank() }?.trim() ?: return false
        return GRP_RE.matches(line)
    }

    private fun bellsForFile(fileName: String): Map<String, List<String?>> {
        val upper = fileName.uppercase()
        return when {
            "ПОНЕДЕЛЬНИК" in upper -> BELLS_MON
            "СУББОТ"      in upper -> BELLS_SAT
            else                   -> BELLS_TUE
        }
    }

    private fun toMin(t: String): Int {
        val parts = t.split(':')
        if (parts.size < 2) return 0
        return parts[0].toIntOrNull()?.times(60)?.plus(parts[1].toIntOrNull() ?: 0) ?: 0
    }

    private fun checkIsToday(header: String, cal: Calendar): Boolean {
        val m = Regex("""(\d{2})\.(\d{2})\.(\d{4})""").find(header) ?: return false
        val d = m.groupValues[1].toInt()
        val mo = m.groupValues[2].toInt() - 1
        val y  = m.groupValues[3].toInt()
        return d  == cal.get(Calendar.DAY_OF_MONTH) &&
               mo == cal.get(Calendar.MONTH) &&
               y  == cal.get(Calendar.YEAR)
    }

    /** Извлекает название предмета и строку преподавателя из строк ячейки */
    private fun splitSubjectTeacher(lines: List<String>): Pair<String, String?> {
        if (lines.size == 1) {
            // Захватываем ФИО + весь хвост строки (кабинет часто идёт слитно,
            // без пробела: "Титова М.В.к.22(2)")
            val m = Regex(
                """^(.+?)\s+([А-ЯЁ][а-яё]+(?:\s+[А-ЯЁ][а-яё]+)?\s+[А-ЯЁ]\.\s?[А-ЯЁ]\..*)$"""
            ).find(lines[0])
            if (m != null) return m.groupValues[1].trim() to m.groupValues[2].trim()
            return lines[0] to null
        }
        // Первая строка — предмет, остальные — преподаватель + кабинет
        return lines[0] to lines.drop(1).joinToString("\n")
    }

    /** Разбирает строку вида "Фамилия И.О..к.214(2)" или "Фамилия И.О.спортзал (к.2)" → (teacher, room) */
    private fun parseTeacher(raw: String): Pair<String?, String?> {
        // Склеиваем все непустые строки в одну — кабинет иногда переносится отдельно
        val line = raw.lines().map { it.trim() }.filter { it.isNotBlank() }.joinToString(" ")
        if (line.isBlank()) return null to null

        // Кабинет: "к.NNбукв?(корпус)?" в любом порядке, опционально в скобках
        val m = Regex("""\(?\s*к\.\s*(\d+[а-яa-zА-ЯA-Z]?)\s*(?:\((\d)\))?\s*\)?""", RegexOption.IGNORE_CASE)
            .find(line) ?: return line.trim() to null

        var teacher = line.substring(0, m.range.first).trim()
        // Двойная точка на стыке "И.О." + "." — убираем только лишнюю
        if (teacher.endsWith("..")) teacher = teacher.dropLast(1)
        teacher = teacher.trim().ifBlank { null }

        val num = m.groupValues[1]
        val korpus = m.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
        val room = if (korpus != null) "к.$num($korpus)" else "к.$num"

        return teacher to room
    }

    private val WEEKDAYS = listOf(
        "понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье",
    )

    /** Форматирует строку заголовка в "Вторник · 16.06.2026" */
    private fun formatHeader(raw: String): String {
        if (raw.isBlank()) return raw
        val date = Regex("""(\d{2}\.\d{2}\.\d{4})""").find(raw)?.value

        // Ищем день недели как отдельное слово где угодно в строке (регистр любой)
        val dayWord = Regex("""[А-ЯЁа-яё]+""").findAll(raw)
            .map { it.value }
            .firstOrNull { it.lowercase() in WEEKDAYS }
            ?.lowercase()
            ?.replaceFirstChar { it.uppercaseChar() }

        return when {
            dayWord != null && date != null -> "$dayWord · $date"
            date != null                    -> date
            else                            -> raw.trim()
        }
    }

    /** Список групп, найденных в файле (для отладки / будущего выбора группы) */
    fun detectGroups(data: ByteArray): List<String> {
        val seen = linkedSetOf<String>()
        for (cell in getCells(data)) {
            cell.lines().forEach { line ->
                val t = line.trim()
                if (GRP_RE.matches(t)) seen += t
            }
        }
        return seen.sorted()
    }
}
