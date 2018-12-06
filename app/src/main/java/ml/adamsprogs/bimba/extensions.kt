package ml.adamsprogs.bimba

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.format.DateFormat
import android.view.View
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

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

internal fun Calendar.toIsoDate(): String {
    val year = this.get(Calendar.YEAR)
    val month = String.format("%02d", this.get(Calendar.MONTH) + 1)
    val day = String.format("%02d", this.get(Calendar.DAY_OF_MONTH))
    return "$year$month$day"
}

const val ISO_8601_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
const val ISO_8601_DATE_ONLY_FORMAT = "yyyyMMdd"

@SuppressLint("SimpleDateFormat")
fun calendarFromIso(iso: String): Calendar {
    val calendar = Calendar.getInstance()
    val dateFormat = SimpleDateFormat(ISO_8601_DATE_FORMAT)
    val date = dateFormat.parse(iso)
    calendar.time = date
    return calendar
}

@SuppressLint("SimpleDateFormat")
fun calendarFromIsoD(iso: String): Calendar {
    val calendar = Calendar.getInstance()
    val dateFormat = SimpleDateFormat(ISO_8601_DATE_ONLY_FORMAT)
    val date = dateFormat.parse(iso)
    calendar.time = date
    return calendar
}

fun getDrawable(id: Int, context: Context): Drawable {
    @Suppress("DEPRECATION")
    (return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        context.resources.getDrawable(id, null)
    else
        context.resources.getDrawable(id))
}

internal fun CharSequence.safeSplit(vararg delimiters: String, ignoreCase: Boolean = false, limit: Int = 0): List<String>? {
    if (this == "null")
        return null
    if (this == "")
        return ArrayList()
    return this.split(*delimiters, ignoreCase = ignoreCase, limit = limit)
}

internal fun Context.getSecondaryExternalFilesDir(): File {
    val dirs = this.getExternalFilesDirs(null)
    return dirs[0]
//    return dirs[dirs.size - 1]
}

internal fun InputStream.listenableCopyTo(out: OutputStream, bufferSize: Int = DEFAULT_BUFFER_SIZE, listener: (Long) -> Unit): Long {
    var bytesCopied: Long = 0
    val buffer = ByteArray(bufferSize)
    var bytes = read(buffer)
    while (bytes >= 0) {
        out.write(buffer, 0, bytes)
        bytesCopied += bytes
        listener(bytesCopied)
        bytes = read(buffer)
    }
    return bytesCopied
}

internal fun Calendar.toNiceString(context: Context, withTime: Boolean = false): String {
    val dateFormat = DateFormat.getMediumDateFormat(context)
    val timeFormat = DateFormat.getTimeFormat(context)
    val now = Calendar.getInstance()
    val date = if (get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
        when {
            get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) -> timeFormat.format(time)
            now.apply { add(Calendar.DATE, -1) }.get(Calendar.DAY_OF_YEAR) == get(Calendar.DAY_OF_YEAR) -> "Yesterday"
            else -> DateFormat.format("d MMM" as CharSequence, this.time) as String
        }
    } else
        dateFormat.format(this.time)

    return if (withTime) {
        val time = timeFormat.format(this.time)
        "$date, $time"
    } else
        date
}

fun showError(view: View, code: Int, context: Context) {
    val message = when {
        code == 0 -> context.getString(R.string.no_connectivity)
        (code >= 500) and (code < 600) -> context.getString(R.string.server_error)
        else -> ""
    }
    if (message != "")
        Snackbar.make(view, message, Snackbar.LENGTH_LONG).show()
}