package ml.adamsprogs.bimba.models

import java.util.*

data class Departure(val line: String, val mode: String, val time: String, val lowFloor: Boolean,
                     val modification: String?, val direction: String, val vm: Boolean = false,
                     var tomorrow: Boolean = false, val onStop: Boolean = false) {

    override fun toString(): String {
        return "$line|$mode|$time|$lowFloor|$modification|$direction|$vm|$tomorrow|$onStop"
    }

    fun copy(): Departure {
        return Departure.fromString(this.toString())
    }

    companion object {
        private fun filterDepartures(departures: List<Departure>?): ArrayList<Departure> {
            val filtered = ArrayList<Departure>()
            val lines = HashMap<String, Int>()
            val now = Calendar.getInstance()
            for (departure in departures!!) {
                val time = Calendar.getInstance()
                time.set(Calendar.HOUR_OF_DAY, Integer.parseInt(departure.time.split(":")[0]))
                time.set(Calendar.MINUTE, Integer.parseInt(departure.time.split(":")[1]))
                time.set(Calendar.SECOND, 0)
                time.set(Calendar.MILLISECOND, 0)
                if (departure.tomorrow)
                    time.add(Calendar.DAY_OF_MONTH, 1)
                var lineExistedTimes = lines[departure.line]
                if ((now.before(time) || now == time) && lineExistedTimes ?: 0 < 3) {
                    lineExistedTimes = (lineExistedTimes ?: 0) + 1
                    lines[departure.line] = lineExistedTimes
                    filtered.add(departure)
                }
            }
            return filtered
        }

        fun createDepartures(stopId: String): HashMap<String, ArrayList<Departure>> {
            val timetable = Timetable.getTimetable()
            val departures = timetable.getStopDepartures(stopId)
            val moreDepartures = timetable.getStopDepartures(stopId)
            val rolledDepartures = HashMap<String, ArrayList<Departure>>()

            for ((_, tomorrowDepartures) in moreDepartures!!) {
                tomorrowDepartures.forEach { it.tomorrow = true }
            }

            for ((mode, _) in departures!!) {
                rolledDepartures[mode] = (departures[mode] as ArrayList<Departure> +
                        moreDepartures[mode] as ArrayList<Departure>) as ArrayList<Departure>
                rolledDepartures[mode] = filterDepartures(rolledDepartures[mode])
            }

            return rolledDepartures
        }

        fun fromString(string: String): Departure {
            val array = string.split("|")
            return Departure(array[0], array[1], array[2], array[3] == "1", array[4], array[5],
                    array[6] == "true", array[7] == "true", array[8] == "true")
        }
    }
}