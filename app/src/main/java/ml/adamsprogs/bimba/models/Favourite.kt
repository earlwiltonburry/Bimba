package ml.adamsprogs.bimba.models

import android.os.Parcel
import android.os.Parcelable
import ml.adamsprogs.bimba.MessageReceiver
import ml.adamsprogs.bimba.getMode
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class Favourite : Parcelable, MessageReceiver.OnVmListener {
    override fun onVm(vmDepartures: ArrayList<Departure>?, requester: String) {
        val requesterName = requester.split(";")[0]
        val requesterTimetable: String = try {
            requester.split(";")[1]
        } catch (e: IndexOutOfBoundsException) {
            ""
        }
        if (vmDepartures != null && requesterName == name) {
            vmDeparturesMap[requesterTimetable] = vmDepartures
            this.vmDepartures = vmDeparturesMap.flatMap { it.value } as ArrayList<Departure>
        }
        filterVmDepartures()
    }

    private var isRegisteredOnVmListener: Boolean = false
    var name: String
        private set
    var timetables: ArrayList<HashMap<String, String>>
        private set
    private var oneDayDepartures: ArrayList<HashMap<String, ArrayList<Departure>>>? = null
    private val vmDeparturesMap = HashMap<String, ArrayList<Departure>>()
    private var vmDepartures = ArrayList<Departure>()

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
        return Parcelable.CONTENTS_FILE_DESCRIPTOR
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
            filterVmDepartures()
            if (timetables.isEmpty() && vmDepartures.isEmpty())
                return null

            if (vmDepartures.isNotEmpty()) {
                return vmDepartures.minBy { it.timeTill() }
            }

            val twoDayDepartures = ArrayList<Departure>()
            val today = Calendar.getInstance().getMode()
            val tomorrowCal = Calendar.getInstance()
            tomorrowCal.add(Calendar.DAY_OF_MONTH, 1)
            val tomorrow = tomorrowCal.getMode()

            if (oneDayDepartures == null) {
                oneDayDepartures = ArrayList()
                timetables.mapTo(oneDayDepartures!!) { timetable.getStopDepartures(it[TAG_STOP] as String, it[TAG_LINE]) }
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

            return twoDayDepartures
                    .filter { it.timeTill() >= 0 }
                    .minBy { it.timeTill() }
        }
        private set

    private fun filterVmDepartures() {
        this.vmDepartures
                .filter { it.timeTill() < 0 }
                .forEach { this.vmDepartures.remove(it) }
    }

    fun delete(stop: String, line: String) {
        timetables.remove(timetables.find { it[TAG_STOP] == stop && it[TAG_LINE] == line })
    }

    fun registerOnVm(receiver: MessageReceiver) {
        if (!isRegisteredOnVmListener) {
            receiver.addOnVmListener(this)
            isRegisteredOnVmListener = true
        }
    }

    fun rename(newName: String) {
        name = newName
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

    fun allDepartures(): HashMap<String, ArrayList<Departure>>? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun fullTimetable(): HashMap<String, ArrayList<Departure>>? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
