package ml.adamsprogs.bimba.models

import android.content.Context
import ml.adamsprogs.bimba.Declinator
import ml.adamsprogs.bimba.R
import ml.adamsprogs.bimba.rollTime
import ml.adamsprogs.bimba.safeSplit
import java.util.*

data class Departure(val line: String, val mode: List<Int>, val time: Int, val lowFloor: Boolean, //time in seconds since midnight
                     val modification: List<String>, val headsign: String, val vm: Boolean = false,
                     var tomorrow: Boolean = false, val onStop: Boolean = false) {

    val isModified: Boolean
        get() {
            return modification.isNotEmpty()
        }

    override fun toString(): String {
        return "$line|${mode.joinToString(";")}|$time|$lowFloor|${modification.joinToString(";")}|$headsign|$vm|$tomorrow|$onStop"
    }

    fun copy(): Departure {
        return Departure.fromString(this.toString())
    }

    companion object {
        fun fromString(string: String): Departure {
            val array = string.split("|")
            if (array.size != 9)
                throw IllegalArgumentException()
            val modification = array[4].safeSplit(";")!!
            return Departure(array[0],
                    array[1].safeSplit(";")!!.map { Integer.parseInt(it) },
                    Integer.parseInt(array[2]), array[3] == "true",
                    modification, array[5], array[6] == "true",
                    array[7] == "true", array[8] == "true")
        }
    }

    fun timeTill(relativeTo: Int): Int {
        var time = this.time
        if (this.tomorrow)
            time += 24 * 60 * 60
        return (time - relativeTo) / 60
    }

    val lineText: String = line

    fun timeTillText(context: Context, relativeTime: Boolean = true): String {
        val now = Calendar.getInstance()
        val departureTime = Calendar.getInstance().rollTime(time)
        if (tomorrow)
            departureTime.add(Calendar.DAY_OF_MONTH, 1)

        val departureIn = ((departureTime.timeInMillis - now.timeInMillis) / (1000 * 60)).toInt()

        return if (departureIn > 60 || departureIn < 0 || !relativeTime)
            context.getString(R.string.departure_at, "${String.format("%02d", departureTime.get(Calendar.HOUR_OF_DAY))}:${String.format("%02d", departureTime.get(Calendar.MINUTE))}")
        else if (departureIn > 0 && !onStop)
            context.getString(Declinator.decline(departureIn), departureIn.toString())
        else if (departureIn == 0 && !onStop)
            context.getString(R.string.in_a_moment)
        else if (departureIn == 0)
            context.getString(R.string.now)
        else
            context.getString(R.string.just_departed)
    }

    fun timeAtMessage(context: Context): String {
        val departureTime = Calendar.getInstance().rollTime(time)
        if (tomorrow)
            departureTime.add(Calendar.DAY_OF_MONTH, 1)

        return context.getString(R.string.departure_at,
                "${String.format("%02d",
                        departureTime.get(Calendar.HOUR_OF_DAY))}:${String.format("%02d",
                        departureTime.get(Calendar.MINUTE))}") +
                if (isModified)
                    " " + modification.joinToString("; ", "(", ")")
                else ""
    }
}