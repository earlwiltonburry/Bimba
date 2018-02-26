package ml.adamsprogs.bimba.models

import android.content.Context
import android.content.Intent
import android.os.Parcel
import android.os.Parcelable
import ml.adamsprogs.bimba.MessageReceiver
import ml.adamsprogs.bimba.datasources.VmClient
import ml.adamsprogs.bimba.models.gtfs.AgencyAndId
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
    private var vmDepartures = HashMap<Plate.ID, Set<Departure>>()
    val timetable = Timetable.getTimetable()

    val size
        get() = this.timetables.size

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
        this.vmDepartures.forEach {
            val newSet = it.value
                    .filter { it.timeTill(true) >= 0 }.toSet()
            this.vmDepartures[it.key] = newSet
        }
    }

    fun delete(plate: Plate) {
        timetables.remove(timetables.find { it.id == plate.id })
    }

    fun registerOnVm(receiver: MessageReceiver, context: Context) {
        if (!isRegisteredOnVmListener) {
            receiver.addOnVmListener(this)
            isRegisteredOnVmListener = true

            val segments = HashMap<AgencyAndId, StopSegment>()
            timetables.forEach {
                if (segments[it.id.stop] == null)
                    segments[it.id.stop] = StopSegment(it.id.stop, HashSet())
                segments[it.id.stop]!!.plates = segments[it.id.stop]!!.plates!!.plus(it.id)
            }

            segments.forEach {
                val intent = Intent(context, VmClient::class.java)
                intent.putExtra("stop", it.value)
                intent.action = "request"
                context.startService(intent)
            }
        }
    }

    fun deregisterOnVm(receiver: MessageReceiver, context: Context) {
        if (isRegisteredOnVmListener) {
            receiver.removeOnVmListener(this)
            isRegisteredOnVmListener = false

            val segments = HashMap<AgencyAndId, StopSegment>()
            timetables.forEach {
                if (segments[it.id.stop] == null)
                    segments[it.id.stop] = StopSegment(it.id.stop, HashSet())
                segments[it.id.stop]!!.plates = segments[it.id.stop]!!.plates!!.plus(it.id)
            }

            segments.forEach {
                val intent = Intent(context, VmClient::class.java)
                intent.putExtra("stop", it.value)
                intent.action = "remove"
                context.startService(intent)
            }
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
            return vmDepartures.flatMap { it.value }
                    .minBy {
                        it.timeTill(true)
                    }
        }

        val twoDayDepartures = nowDepartures()

        if (twoDayDepartures.isEmpty())
            return null

        return twoDayDepartures
                .filter { it.timeTill(true) >= 0 }
                .minBy { it.timeTill(true) }
    }

    private fun nowDepartures(): ArrayList<Departure> {
        val today = timetable.getServiceForToday()
        val tomorrowCal = Calendar.getInstance()
        tomorrowCal.add(Calendar.DAY_OF_MONTH, 1)
        val tomorrow = timetable.getServiceForTomorrow()

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

    fun allDepartures(): Map<AgencyAndId, List<Departure>> { // fixme departures through vm not the other way around
        val departures = timetable.getStopDepartures(timetables) as HashMap<AgencyAndId, ArrayList<Departure>>

        if (vmDepartures.isNotEmpty()) {
            val today = timetable.getServiceForToday()
            departures[today] = vmDepartures.flatMap { it.value } as ArrayList<Departure>
        }

        return Departure.rollDepartures(departures)
    }

    fun fullTimetable(): Map<AgencyAndId, List<Departure>> {
        return timetable.getStopDepartures(timetables)
    }

    override fun onVm(vmDepartures: Set<Departure>?, plateId: Plate.ID) {
        if (timetables.any { it.id == plateId }) {
            if (vmDepartures == null)
                this.vmDepartures.remove(plateId)
            else
                this.vmDepartures[plateId] = vmDepartures
        }
        filterVmDepartures()
    }

    interface OnVmPreparedListener {
        fun onVmPrepared()
    }
}
