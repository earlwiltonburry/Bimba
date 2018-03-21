package ml.adamsprogs.bimba.models

import android.content.Context
import android.content.Intent
import android.os.Parcel
import android.os.Parcelable
import ml.adamsprogs.bimba.MessageReceiver
import ml.adamsprogs.bimba.datasources.VmClient
import ml.adamsprogs.bimba.models.gtfs.AgencyAndId
import ml.adamsprogs.bimba.secondsAfterMidnight
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class Favourite : Parcelable, MessageReceiver.OnVmListener {
    private var isRegisteredOnVmListener: Boolean = false
    var name: String
        private set
    var segments: HashSet<StopSegment>
        private set
    private var vmDepartures = HashMap<Plate.ID, List<Departure>>()
    val timetable = Timetable.getTimetable()

    val size
        get() = segments.sumBy {
            it.size
        }

    private val onVmPreparedListeners = HashSet<OnVmPreparedListener>()

    fun addOnVmPreparedListener(listener: OnVmPreparedListener) {
        onVmPreparedListeners.add(listener)
    }

    fun removeOnVmPreparedListener(listener: OnVmPreparedListener) {
        onVmPreparedListeners.remove(listener)
    }

    constructor(parcel: Parcel) {
        this.name = parcel.readString()
        @Suppress("UNCHECKED_CAST")
        val set = HashSet<StopSegment>()
        val array = parcel.readParcelableArray(StopSegment::class.java.classLoader)
        array.forEach {
            set.add(it as StopSegment)
        }
        this.segments = set
    }

    constructor(name: String, timetables: HashSet<StopSegment>) {
        this.name = name
        this.segments = timetables

    }

    override fun describeContents(): Int {
        return Parcelable.CONTENTS_FILE_DESCRIPTOR
    }

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.writeString(name)
        val parcelableSegments = segments.map { it }.toTypedArray()
        dest?.writeParcelableArray(parcelableSegments, flags)
    }

    private fun filterVmDepartures() {
        val now = Calendar.getInstance().secondsAfterMidnight()
        this.vmDepartures.forEach {
            val newVms = it.value
                    .filter { it.timeTill(now) >= 0 }.sortedBy { it.timeTill(now) }
            this.vmDepartures[it.key] = newVms
        }
    }

    fun delete(plateId: Plate.ID) {
        segments.forEach {
            it.remove(plateId)
        }
    }

    fun registerOnVm(receiver: MessageReceiver, context: Context) {
        if (!isRegisteredOnVmListener) {
            receiver.addOnVmListener(this)
            isRegisteredOnVmListener = true


            segments.forEach {
                val intent = Intent(context, VmClient::class.java)
                intent.putExtra("stop", it)
                intent.action = "request"
                context.startService(intent)
            }
        }
    }

    fun deregisterOnVm(receiver: MessageReceiver, context: Context) {
        if (isRegisteredOnVmListener) {
            receiver.removeOnVmListener(this)
            isRegisteredOnVmListener = false

            segments.forEach {
                val intent = Intent(context, VmClient::class.java)
                intent.putExtra("stop", it)
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
        val now = Calendar.getInstance().secondsAfterMidnight()
        filterVmDepartures()
        if (segments.isEmpty() && vmDepartures.isEmpty())
            return null

        if (vmDepartures.isNotEmpty()) {
            return vmDepartures.flatMap { it.value }
                    .minBy {
                        it.timeTill(now)
                    }
        }

        val full = fullTimetable()
        /*println("full:")
        full.forEach {
            println("${it.key}:")
            it.value.forEach {
                println("\t$it")
            }
        }*/

        val twoDayDepartures = Departure.rollDepartures(full)[timetable.getServiceForToday()]

        println("NextDep: is empty? ${twoDayDepartures?.isEmpty()}")

        if (twoDayDepartures?.isEmpty() != false)
            return null

        println("NextDep: twoDays:")
        twoDayDepartures.forEach {
            println("\t$it")
        }

        println("NextDep: ${twoDayDepartures[0]}")

        return twoDayDepartures[0]
    }

    fun allDepartures(): Map<AgencyAndId, List<Departure>> {
        if (vmDepartures.isNotEmpty()) {
            val departures = HashMap<AgencyAndId, ArrayList<Departure>>()
            val today = timetable.getServiceForToday()
            departures[today] = vmDepartures.flatMap { it.value } as ArrayList<Departure>
            return departures
        }

        val departures = fullTimetable()
        return Departure.rollDepartures(departures)
    }

    fun fullTimetable() = timetable.getStopDeparturesBySegments(segments)

    override fun onVm(vmDepartures: Set<Departure>?, plateId: Plate.ID) {
        val now = Calendar.getInstance().secondsAfterMidnight()
        if (segments.any { it.contains(plateId) }) {
            if (vmDepartures == null)
                this.vmDepartures.remove(plateId)
            else
                this.vmDepartures[plateId] = vmDepartures.sortedBy { it.timeTill(now) }
        }
        filterVmDepartures()
        //todo<p:1> think about tick
        onVmPreparedListeners.forEach {
            it.onVmPrepared()
        }
    }

    interface OnVmPreparedListener {
        fun onVmPrepared()
    }
}
