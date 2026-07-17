package com.schedule.app.ui.navigation

import com.schedule.app.data.model.ScheduleFile

// Простой синглтон для передачи объекта при навигации.
// Альтернатива URL-параметрам и Parcelable — достаточно для нашего случая.
object NavigationHolder {
    var pendingFile: ScheduleFile? = null

    // pendingMode раньше решал, какой из двух экранов открыть (Ученики или
    // Преподаватели) — теперь оба открываются вместе внутри ScheduleHostScreen,
    // а тумблер живёт на самом экране файла (см. ScheduleHostScreen.kt),
    // так что решение здесь больше не нужно.
}
