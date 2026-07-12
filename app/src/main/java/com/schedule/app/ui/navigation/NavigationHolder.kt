package com.schedule.app.ui.navigation

import com.schedule.app.data.model.ScheduleFile
import com.schedule.app.ui.components.ScheduleMode

// Простой синглтон для передачи объекта при навигации.
// Альтернатива URL-параметрам и Parcelable — достаточно для нашего случая.
object NavigationHolder {
    var pendingFile: ScheduleFile? = null

    // Режим, выбранный переключателем "Ученики/Преподаватели" на главном
    // экране в момент тапа по файлу — решает, какой экран открыть дальше
    // (см. AppScaffold: развилка ScheduleScreen / TeacherScheduleScreen).
    var pendingMode: ScheduleMode = ScheduleMode.STUDENT
}
