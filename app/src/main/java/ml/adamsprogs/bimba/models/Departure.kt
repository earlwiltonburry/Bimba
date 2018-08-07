package ml.adamsprogs.bimba.models

import ml.adamsprogs.bimba.safeSplit
import ml.adamsprogs.bimba.secondsAfterMidnight
import java.io.Serializable
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

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
        private fun filterDepartures(departures: List<Departure>, relativeTo: Int = Calendar.getInstance().secondsAfterMidnight()): Array<Serializable> {
            val filtered = ArrayList<Departure>()
            val lines = HashMap<String, Int>()
            val sortedDepartures = departures.sortedBy { it.timeTill(relativeTo) }
            for (departure in sortedDepartures) {
                val timeTill = departure.timeTill(relativeTo)
                var lineExistedTimes = lines[departure.line]
                if (timeTill >= 0 && lineExistedTimes ?: 0 < 3) {
                    lineExistedTimes = (lineExistedTimes ?: 0) + 1
                    lines[departure.line] = lineExistedTimes
                    filtered.add(departure)
                }
            }
            return arrayOf(filtered, lines.all { it.value >= 3 })
        }

        /*fun createDepartures(stopCode: String): Map<String, List<Departure>> {
            val timetable = Timetable.getTimetable()
            val departures = timetable.getStopDepartures(stopCode)

            return rollDepartures(departures)
        }*/

        fun rollDepartures(departures: Map<Int, List<Departure>>): Map<Int, List<Departure>> { //todo<p:2> it'd be nice to roll from tomorrow's real mode (Fri->Sat, Sat->Sun, Sun->Mon)
            val rolledDepartures = HashMap<Int, List<Departure>>()
            departures.keys.forEach {
                val (filtered, isFull) = filterDepartures(departures[it]!!)
                if (isFull as Boolean) {
                    @Suppress("UNCHECKED_CAST")
                    rolledDepartures[it] = filtered as List<Departure>
                } else {
                    val (filteredTomorrow, _) = filterDepartures(departures[it]!!, 0)
                    val departuresTomorrow = ArrayList<Departure>()
                    @Suppress("UNCHECKED_CAST")
                    (filteredTomorrow as List<Departure>).forEach {
                        val departure = it.copy()
                        departure.tomorrow = true
                        departuresTomorrow.add(departure)
                    }
                    val (result, _) =
                            @Suppress("UNCHECKED_CAST")
                            filterDepartures((filtered as List<Departure>) + departuresTomorrow)
                    val now = Calendar.getInstance().secondsAfterMidnight()
                    @Suppress("UNCHECKED_CAST")
                    rolledDepartures[it] = (result as List<Departure>).sortedBy { it.timeTill(now) }
                }
            }
            return rolledDepartures
        }

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
}