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