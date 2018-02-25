package ml.adamsprogs.bimba

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import ml.adamsprogs.bimba.activities.StopActivity
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

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

internal fun Calendar.secondsAfterMidnight(): Int {
    val hour = this.get(Calendar.HOUR_OF_DAY)
    val minute = this.get(Calendar.MINUTE)
    val second = this.get(Calendar.SECOND)
    return hour * 3600 + minute * 60 + second
}

const val ISO_8601_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

fun calendarFromIso(iso: String): Calendar { // check
    val calendar = Calendar.getInstance()
    val dateFormat = SimpleDateFormat(ISO_8601_DATE_FORMAT)
    val date = dateFormat.parse(iso)
    //date.hours = date.getHours() - 1 //fixme why?
    calendar.time = date
    return calendar
}

fun getColour(id: Int, context: Context): Int {
    @Suppress("DEPRECATION")
    (return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        context.resources.getColor(id, null)
    else
        context.resources.getColor(id))
}

fun getDrawable(id: Int, context: Context): Drawable {
    @Suppress("DEPRECATION")
    (return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        context.resources.getDrawable(id, null)
    else
        context.resources.getDrawable(id))
}

internal fun Calendar.getMode(): Int {
    return when (this.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SUNDAY -> StopActivity.MODE_SUNDAYS
        Calendar.SATURDAY -> StopActivity.MODE_SATURDAYS
        else -> StopActivity.MODE_WORKDAYS
    }
}

internal fun CharSequence.safeSplit(vararg delimiters: String, ignoreCase: Boolean = false, limit: Int = 0): List<String> {
    if (this == "")
        return ArrayList()
    return this.split(*delimiters, ignoreCase = ignoreCase, limit = limit)
}