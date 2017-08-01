package ml.adamsprogs.bimba.models

import android.content.Context
import java.util.*

class Favourite(var name: String, var timetables: ArrayList<HashMap<String, String>>, context: Context) {
    val timetable = Timetable(context)

    var nextDeparture: Departure? = null
        get() {
            val today: String
            val allDepartures = ArrayList<Departure>()
            val now = Calendar.getInstance()
            val departureTime = Calendar.getInstance()
            when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
                Calendar.SUNDAY -> today = "sundays"
                Calendar.SATURDAY -> today = "saturdays"
                else -> today = "workdays"
            }

            for (t in timetables)
                allDepartures.addAll(timetable.getStopDepartures(t["stop"] as String, t["line"])!![today]!!)
            var minDeparture: Departure = allDepartures[0]
            var minInterval = 24 * 60L
            for (departure in allDepartures) {
                departureTime.set(Calendar.HOUR_OF_DAY, Integer.parseInt(departure.time.split(":")[0]))
                departureTime.set(Calendar.MINUTE, Integer.parseInt(departure.time.split(":")[1]))
                if (departure.tomorrow)
                    departureTime.add(Calendar.DAY_OF_MONTH, 1)
                val interval = (departureTime.timeInMillis - now.timeInMillis) / (1000 * 60)
                if (interval in 0..(minInterval - 1)) {
                    minInterval = (departureTime.timeInMillis - now.timeInMillis) / (1000 * 60)
                    minDeparture = departure
                }
            }

            return minDeparture
        }
        private set
}
