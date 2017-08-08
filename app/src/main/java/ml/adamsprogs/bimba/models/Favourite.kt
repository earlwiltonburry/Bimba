package ml.adamsprogs.bimba.models

import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import ml.adamsprogs.bimba.getMode
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class Favourite : Parcelable {
    var name: String
    var timetables: ArrayList<HashMap<String, String>>
    private var oneDayDepartures: ArrayList<HashMap<String, ArrayList<Departure>>>? = null

    constructor(parcel: Parcel) {
        val array = ArrayList<String>()
        parcel.readStringList(array)
        val timetables = ArrayList<HashMap<String, String>>()
        for (row in array) {
            val element = HashMap<String, String>()
            element[TAG_STOP] = row.split("|")[0]
            element[TAG_LINE] = row.split("|")[1]
            timetables.add(element)
        }
        this.name = parcel.readString()
        this.timetables = timetables
    }

    constructor(name: String, timetables: ArrayList<HashMap<String, String>>) {
        this.name = name
        this.timetables = timetables
    }

    override fun describeContents(): Int {
        return 105
    }

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        val parcel = timetables.map { "${it[TAG_STOP]}|${it[TAG_LINE]}" }
        dest?.writeStringList(parcel)
        dest?.writeString(name)
    }

    val timetable = Timetable.getTimetable()
    val size: Int
        get() = timetables.size

    var nextDeparture: Departure? = null
        get() {
            if (timetables.isEmpty())
                return null
            val twoDayDepartures = ArrayList<Departure>()
            val now = Calendar.getInstance()
            val departureTime = Calendar.getInstance()
            val today = now.getMode()
            val tomorrowCal = Calendar.getInstance()
            tomorrowCal.add(Calendar.DAY_OF_MONTH, 1)
            val tomorrow = tomorrowCal.getMode()

            if (oneDayDepartures == null) {
                oneDayDepartures = ArrayList<HashMap<String, ArrayList<Departure>>>()
                timetables.mapTo(oneDayDepartures!!) { timetable.getStopDepartures(it[TAG_STOP] as String, it[TAG_LINE])!! }
            }

            oneDayDepartures!!.forEach {
                it[today]!!.forEach {
                    twoDayDepartures.add(it.copy())
                }
            }
            oneDayDepartures!!.forEach {
                it[tomorrow]!!.forEach {
                    val d = it.copy()
                    d.tomorrow = true
                    twoDayDepartures.add(d)
                }
            }

            if (twoDayDepartures.isEmpty())
                return null

            var minDeparture: Departure = twoDayDepartures[0]
            var minInterval = 24 * 60L
            for (departure in twoDayDepartures) {
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

            Log.i("preInterval", "$minInterval")
            return minDeparture
        }
        private set

    fun delete(stop: String, line: String) {
        Log.i("ROW", "Favourite deleting $stop, $line")
        timetables.remove(timetables.find { it[TAG_STOP] == stop && it[TAG_LINE] == line })
        Log.i("ROW", timetables.toString())
    }

    companion object CREATOR : Parcelable.Creator<Favourite> {
        override fun createFromParcel(parcel: Parcel): Favourite {
            return Favourite(parcel)
        }

        override fun newArray(size: Int): Array<Favourite?> {
            return arrayOfNulls(size)
        }

        val TAG_STOP = "stop"
        val TAG_LINE = "line"
    }
}
