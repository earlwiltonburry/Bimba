package ml.adamsprogs.bimba

import ml.adamsprogs.bimba.models.Timetable
import java.util.*

internal fun Calendar.getMode(): String {
    when (this.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SUNDAY -> return Timetable.MODE_SUNDAYS
        Calendar.SATURDAY -> return Timetable.MODE_SATURDAYS
        else -> return Timetable.MODE_WORKDAYS
    }
}