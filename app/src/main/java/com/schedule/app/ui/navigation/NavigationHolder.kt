package com.schedule.app.ui.navigation

import com.schedule.app.data.model.ScheduleFile

// Простой синглтон для передачи объекта при навигации.
// Альтернатива URL-параметрам и Parcelable — достаточно для нашего случая.
object NavigationHolder {
    var pendingFile: ScheduleFile? = null
}
