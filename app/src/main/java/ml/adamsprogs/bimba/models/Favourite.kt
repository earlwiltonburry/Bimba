package ml.adamsprogs.bimba.models

import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import ml.adamsprogs.bimba.MessageReceiver
import ml.adamsprogs.bimba.getMode
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class Favourite : Parcelable, MessageReceiver.OnVmListener {
    private var isRegisteredOnVmListener: Boolean = false
    var name: String
        private set
    var timetables: HashSet<Plate>
        private set
    private val vmDeparturesMap = HashMap<String, ArrayList<Departure>>()
    private var vmDepartures = ArrayList<Departure>()
    val timetable = Timetable.getTimetable()
    val size: Int
        get() = timetables.size

    private val requestValidityNumber = HashMap<String, Int>()
    private val requestValidity = HashMap<String, Boolean>()

    private val onVmPreparedListeners = HashSet<OnVmPreparedListener>()

    fun addOnVmPreparedListener(listener: OnVmPreparedListener) {
        onVmPreparedListeners.add(listener)
    }

    fun removeOnVmPreparedListener(listener: OnVmPreparedListener) {
        onVmPreparedListeners.remove(listener)
    }

    constructor(parcel: Parcel) {
        val array = ArrayList<String>()
        parcel.readStringList(array)
        val timetables = HashSet<Plate>()
        array.mapTo(timetables) { Plate.fromString(it) }
        this.name = parcel.readString()
        this.timetables = timetables
    }

    constructor(name: String, timetables: HashSet<Plate>) {
        this.name = name
        this.timetables = timetables

    }

    override fun describeContents(): Int {
        return Parcelable.CONTENTS_FILE_DESCRIPTOR
    }

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        val parcel = timetables.map { it.toString() }
        dest?.writeStringList(parcel)
        dest?.writeString(name)
    }

    private fun filterVmDepartures() {
        this.vmDepartures
                .filter { it.timeTill() < 0 }
                .forEach { this.vmDepartures.remove(it) }
    }

    fun delete(plate: Plate) {
        timetables.remove(timetables.find { it.stop == plate.stop && it.line == plate.line })
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
    }

    fun nextDeparture(): Departure? {
        filterVmDepartures()
        if (timetables.isEmpty() && vmDepartures.isEmpty())
            return null

        if (vmDepartures.isNotEmpty()) {
            return vmDepartures.minBy { it.timeTill() }
        }

        val twoDayDepartures = nowDepartures()

        if (twoDayDepartures.isEmpty())
            return null

        return twoDayDepartures
                .filter { it.timeTill() >= 0 }
                .minBy { it.timeTill() }
    }

    private fun nowDepartures(): ArrayList<Departure> {
        val today = Calendar.getInstance().getMode()
        val tomorrowCal = Calendar.getInstance()
        tomorrowCal.add(Calendar.DAY_OF_MONTH, 1)
        val tomorrow = tomorrowCal.getMode()

        val departures = timetable.getStopDepartures(timetables)
        val todayDepartures = departures[today]!!
        val tomorrowDepartures = ArrayList<Departure>()
        val twoDayDepartures = ArrayList<Departure>()
        departures[tomorrow]!!.mapTo(tomorrowDepartures) { it.copy() }
        tomorrowDepartures.forEach { it.tomorrow = true }

        todayDepartures.forEach { twoDayDepartures.add(it) }
        tomorrowDepartures.forEach { twoDayDepartures.add(it) }
        return twoDayDepartures
    }

    fun allDepartures(): Map<String, List<Departure>> {
        val departures = timetable.getStopDepartures(timetables) as HashMap<String, ArrayList<Departure>>

        if (vmDepartures.isNotEmpty()) {
            val today = Calendar.getInstance().getMode()
            departures[today] = vmDepartures
        }

        return Departure.createDepartures(departures)
    }

    fun fullTimetable(): Map<String, List<Departure>>? {
        return timetable.getStopDepartures(timetables)
    }

    override fun onVm(vmDepartures: ArrayList<Departure>?, requester: String, id: String, size: Int) {
        Log.i("VM", "onVM fired")
        val requesterName = requester.split(";")[0]
        val requesterTimetable: String = try {
            requester.split(";")[1]
        } catch (e: IndexOutOfBoundsException) {
            ""
        }

        if (!requestValidity.containsKey(id)) {
            requestValidity[id] = false
            requestValidityNumber[id] = 0
        }
        if (vmDepartures != null && requesterName == name) {
            vmDeparturesMap[requesterTimetable] = vmDepartures
            this.vmDepartures = vmDeparturesMap.flatMap { it.value } as ArrayList<Departure>
            requestValidity[id] = true
            requestValidityNumber[id] = requestValidityNumber[id]!! + 1
        } else if (requesterName == name) {
            requestValidityNumber[id] = requestValidityNumber[id]!! + 1
        }
        if (requestValidityNumber[id] == size) {
            Log.i("VM", "All onVm’s collected")
            for (listener in onVmPreparedListeners) {
                listener.onVmPrepared()
            }
            if (!requestValidity[id]!!) {
                this.vmDepartures = ArrayList()
            }
            requestValidity.remove(id)
            requestValidityNumber.remove(id)
        }
        filterVmDepartures()
    }

    interface OnVmPreparedListener {
        fun onVmPrepared()
    }
}
