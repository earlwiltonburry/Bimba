package ml.adamsprogs.bimba.models

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class Favourite : Parcelable {
    lateinit var name:String
    lateinit var timetables: ArrayList<HashMap<String, String>>
    lateinit var context: Context

    private constructor()

    constructor(parcel: Parcel) {
        val array = ArrayList<String>()
        parcel.readStringList(array)
        val timetables = ArrayList<HashMap<String, String>>()
        for (row in array) {
            val element = HashMap<String, String>()
            element["stop"] = row.split("|")[0]
            element["line"] = row.split("|")[1]
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
        val parcel = timetables.map { "${it["stop"]}|${it["line"]}" }
        dest?.writeStringList(parcel)
        dest?.writeString(name)
    }

    val timetable = Timetable.getTimetable()
    val size: Int
        get() = timetables.size

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

    fun delete(stop: String, line: String) {
        Log.i("ROW", "Favourite deleting $stop, $line")
        val element = HashMap<String, String>()
        element["stop"] = stop
        element["line"] = line
        val b = timetables.remove(element)
        Log.i("ROW", "$b")
        Log.i("ROW", timetables.toString())
    }

    companion object CREATOR : Parcelable.Creator<Favourite> {
        override fun createFromParcel(parcel: Parcel): Favourite {
            return Favourite(parcel)
        }

        override fun newArray(size: Int): Array<Favourite?> {
            return arrayOfNulls(size)
        }
    }
}
