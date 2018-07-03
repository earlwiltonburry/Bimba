package ml.adamsprogs.bimba.models

import android.content.*
import android.os.*
import ml.adamsprogs.bimba.*
import ml.adamsprogs.bimba.datasources.VmClient
import ml.adamsprogs.bimba.models.gtfs.AgencyAndId
import java.io.File
import java.math.BigInteger
import java.security.SecureRandom
import java.util.Calendar
import kotlin.collections.*

class Favourite : Parcelable, MessageReceiver.OnVmListener {
    private var isRegisteredOnVmListener: Boolean = false
    private val cacheDir: File
    var name: String
        private set
    var segments: HashSet<StopSegment>
        private set
    private var vmDepartures = HashMap<Plate.ID, List<Departure>>()
    var fullDepartures: Map<AgencyAndId, List<Departure>> = HashMap()
        private set
    val timetable = Timetable.getTimetable()

    val size
        get() = segments.sumBy {
            it.size
        }
    val isBackedByVm
        get() = vmDepartures.isNotEmpty()

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
        this.cacheDir = File(parcel.readString())
        val mapDir = File(parcel.readString())

        val mapString = mapDir.readText()

        val map = HashMap<AgencyAndId, List<Departure>>()
        mapString.safeSplit("%").forEach {
            val (k, v) = it.split("#")
            map[AgencyAndId(k)] = v.split("&").map { Departure.fromString(it) }
        }
        this.fullDepartures = map
        mapDir.delete()
    }

    constructor(name: String, segments: HashSet<StopSegment>, cache: Map<AgencyAndId, List<Departure>>, context: Context) {
        this.fullDepartures = cache
        this.name = name
        this.segments = segments
        this.cacheDir = context.cacheDir
    }

    constructor(name: String, timetables: HashSet<StopSegment>, context: Context) {
        this.name = name
        this.segments = timetables
        this.cacheDir = context.cacheDir

    }

    override fun describeContents(): Int {
        return Parcelable.CONTENTS_FILE_DESCRIPTOR
    }

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.writeString(name)
        val parcelableSegments = segments.map { it }.toTypedArray()
        dest?.writeParcelableArray(parcelableSegments, flags)
        dest?.writeString(cacheDir.absolutePath)

        val bytes = ByteArray(4) { 0 }
        SecureRandom().nextBytes(bytes)
        val mapFile = File(cacheDir, BigInteger(1, bytes).toString(16))
        dest?.writeString(mapFile.absolutePath)

        var isFirst = true
        var map = ""
        fullDepartures.forEach {
            if (isFirst)
                isFirst = false
            else
                map += '%'

            map += "${it.key}#${it.value.joinToString("&") { it.toString() }}"
        }
        mapFile.writeText(map)
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
        removeFromCache(plateId)
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

        val twoDayDepartures = try {
            Departure.rollDepartures(full)[timetable.getServiceForToday()]
        } catch (e: IllegalArgumentException) {
            listOf<Departure>()
        }

        if (twoDayDepartures?.isEmpty() != false)
            return null

        return twoDayDepartures[0]
    }

    fun allDepartures(): Map<AgencyAndId, List<Departure>> {
        if (vmDepartures.isNotEmpty()) {
            val now = Calendar.getInstance().secondsAfterMidnight()
            val departures = HashMap<AgencyAndId, List<Departure>>()
            val today = timetable.getServiceForToday()
            departures[today] = vmDepartures.flatMap { it.value }.sortedBy { it.timeTill(now) }
            return departures
        }

        val departures = fullTimetable()
        return Departure.rollDepartures(departures)
    }

    fun fullTimetable() =
            if (fullDepartures.isNotEmpty())
                fullDepartures
            else {
                fullDepartures = timetable.getStopDeparturesBySegments(segments)
                fullDepartures

            }


    override fun onVm(vmDepartures: Set<Departure>?, plateId: Plate.ID) {
        val now = Calendar.getInstance().secondsAfterMidnight()
        if (segments.any { it.contains(plateId) }) {
            if (vmDepartures == null)
                this.vmDepartures.remove(plateId)
            else
                this.vmDepartures[plateId] = vmDepartures.sortedBy { it.timeTill(now) }
        }
        filterVmDepartures()
        onVmPreparedListeners.forEach {
            it.onVmPrepared()
        }
    }

    private fun removeFromCache(plate: Plate.ID) {
        val map = HashMap<AgencyAndId, List<Departure>>()
        fullDepartures
        fullDepartures.forEach {
            map[it.key] = it.value.filter { plate.line != it.line || plate.headsign != it.headsign }
        }
        fullDepartures = map
    }

    interface OnVmPreparedListener {
        fun onVmPrepared()
    }
}
