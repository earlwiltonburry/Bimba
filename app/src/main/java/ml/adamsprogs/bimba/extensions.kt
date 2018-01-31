package ml.adamsprogs.bimba

import ml.adamsprogs.bimba.models.Timetable
import java.util.*

internal fun Calendar.getMode(): String {
    return when (this.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SUNDAY -> Timetable.MODE_SUNDAYS
        Calendar.SATURDAY -> Timetable.MODE_SATURDAYS
        else -> Timetable.MODE_WORKDAYS
    }
}

internal fun String.toPascalCase(): String { //check
    val builder = StringBuilder(this)
    var isLastCharSeparator = true
    builder.forEach {
        isLastCharSeparator = if ((it in 'a'..'z' || it in 'A'..'Z') && isLastCharSeparator) {
            it.toUpperCase()
            false
        } else
            true
    }
    return builder.toString()
}

internal fun Calendar.rollTime(seconds: Int): Calendar {
    val hour = seconds / 3600
    val minute = (seconds % 3600) / 60
    val second = (seconds % 60)
    this.set(Calendar.HOUR_OF_DAY, hour)
    this.set(Calendar.MINUTE, minute)
    this.set(Calendar.SECOND, second)
    this.set(Calendar.MILLISECOND, 0)
    return this
}