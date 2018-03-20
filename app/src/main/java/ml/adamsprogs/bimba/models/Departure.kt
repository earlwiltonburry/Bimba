package ml.adamsprogs.bimba.models

import ml.adamsprogs.bimba.rollTime
import ml.adamsprogs.bimba.models.gtfs.AgencyAndId
import ml.adamsprogs.bimba.safeSplit
import java.io.Serializable
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

//todo may show just departed as HH:MM
data class Departure(val line: AgencyAndId, val mode: List<Int>, val time: Int, val lowFloor: Boolean, //time in seconds since midnight
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
        private fun filterDepartures(departures: List<Departure>, relative: Boolean = true): Array<Serializable> {
            val filtered = ArrayList<Departure>()
            val lines = HashMap<AgencyAndId, Int>()
            val sortedDepartures = departures.sortedBy { it.timeTill(relative) }
            for (departure in sortedDepartures) {
                var lineExistedTimes = lines[departure.line]
                if (departure.timeTill(relative) >= 0 && lineExistedTimes ?: 0 < 3) {
                    lineExistedTimes = (lineExistedTimes ?: 0) + 1
                    lines[departure.line] = lineExistedTimes
                    filtered.add(departure)
                }
            }
            return arrayOf(filtered, lines.all { it.value >= 3 })
        }

        fun createDepartures(stopId: AgencyAndId): Map<AgencyAndId, List<Departure>> {
            val timetable = Timetable.getTimetable()
            val departures = timetable.getStopDepartures(stopId)

            return rollDepartures(departures)
        }

        fun rollDepartures(departures: Map<AgencyAndId, List<Departure>>): Map<AgencyAndId, List<Departure>> { //todo<p:2> it'd be nice to roll from tomorrow's real mode (Fri->Sat, Sat->Sun, Sun->Mon)
            val rolledDepartures = HashMap<AgencyAndId, List<Departure>>()
            departures.keys.forEach {
                val (filtered, isFull) = filterDepartures(departures[it]!!)
                if (isFull as Boolean) {
                    @Suppress("UNCHECKED_CAST")
                    rolledDepartures[it] = filtered as List<Departure>
                } else {
                    val (filteredTomorrow, _) = filterDepartures(departures[it]!!, false)
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
                    @Suppress("UNCHECKED_CAST")
                    rolledDepartures[it] = (result as List<Departure>).sortedBy { it.timeTill(true) }
                }
            }
            return rolledDepartures
        }

        fun fromString(string: String): Departure {
            val array = string.split("|")
            if (array.size != 9)
                throw IllegalArgumentException()
            val modification = array[4].safeSplit(";")
            return Departure(AgencyAndId.convertFromString(array[0]),
                    array[1].safeSplit(";").map { Integer.parseInt(it) },
                    Integer.parseInt(array[2]), array[3] == "true",
                    modification, array[5], array[6] == "true",
                    array[7] == "true", array[8] == "true")
        }
    }

    fun timeTill(relative: Boolean = true): Long {
        val time = Calendar.getInstance().rollTime(this.time)
        var now = Calendar.getInstance()
        if (!relative)
            now = now.rollTime(0)
        if (this.tomorrow)
            time.add(Calendar.DAY_OF_MONTH, 1)
        return (time.timeInMillis - now.timeInMillis) / (1000 * 60)
    }

    val lineText: String = line.id
}