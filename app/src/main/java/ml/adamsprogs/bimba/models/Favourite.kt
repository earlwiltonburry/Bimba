package ml.adamsprogs.bimba.models

import android.content.*
import android.os.*
import ml.adamsprogs.bimba.*
import java.io.File
import java.math.BigInteger
import java.security.SecureRandom
import kotlin.collections.*

class Favourite : Parcelable, ProviderProxy.OnDeparturesReadyListener {
    private val cacheDir: File
    private lateinit var listener: ProviderProxy.OnDeparturesReadyListener
    var name: String
        private set
    var segments: HashSet<StopSegment>
        private set
    private var fullDepartures: Map<String, List<Departure>> = HashMap()
    private val cache = HashMap<Plate.ID, List<Departure>>()
    private var listenerId = ""

    val size
        get() = segments.sumBy {
            it.size
        }

    private val providerProxy: ProviderProxy

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

        val map = HashMap<String, List<Departure>>()
        mapString.safeSplit("%")!!.forEach { it ->
            val (k, v) = it.split("#")
            map[k] = v.split("&").map { Departure.fromString(it) }
        }
        this.fullDepartures = map
        mapDir.delete()
        providerProxy = ProviderProxy()
    }

    constructor(name: String, segments: HashSet<StopSegment>, cache: Map<String, List<Departure>>, context: Context) {
        this.fullDepartures = cache
        this.name = name
        this.segments = segments
        this.cacheDir = context.cacheDir
        providerProxy = ProviderProxy(context)
    }

    constructor(name: String, timetables: HashSet<StopSegment>, context: Context) {
        this.name = name
        this.segments = timetables
        this.cacheDir = context.cacheDir
        providerProxy = ProviderProxy(context)

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
        fullDepartures.forEach { it ->
            if (isFirst)
                isFirst = false
            else
                map += '%'

            map += "${it.key}#${it.value.joinToString("&") { it.toString() }}"
        }
        mapFile.writeText(map)
    }

    fun delete(plateId: Plate.ID): Boolean {
        segments.forEach {
            if (!it.remove(plateId))
                return false
        }
        removeFromCache(plateId)
        return true
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

    fun nextDeparture() =
            if (cache.isEmpty())
                null
            else
                cache.flatMap { it.value }.let {
                    if (it.isEmpty())
                        null
                    else
                        it.sortedBy { it.time }[0]
                }


    fun fullTimetable(): Map<String, List<Departure>> {
        if (fullDepartures.isEmpty())
            fullDepartures = providerProxy.getFullTimetable(segments)
        return fullDepartures
    }

    private fun removeFromCache(plate: Plate.ID) {
        val map = HashMap<String, List<Departure>>()
        fullDepartures
        fullDepartures.forEach { it ->
            map[it.key] = it.value.filter { plate.line != it.line || plate.headsign != it.headsign }
        }
        fullDepartures = map
    }

    fun subscribeForDepartures(listener: ProviderProxy.OnDeparturesReadyListener, context: Context): String {
        this.listener = listener
        listenerId = providerProxy.subscribeForDepartures(segments, this, context)
        return listenerId
    }

    override fun onDeparturesReady(departures: List<Departure>, plateId: Plate.ID?, code: Int) {
        if (plateId == null) {
            cache.clear()
            cache[Plate.ID.dummy] = departures
        } else {
            cache.remove(Plate.ID.dummy)
            cache[plateId] = departures
        }
        listener.onDeparturesReady(departures, plateId, code)
    }

    fun unsubscribeFromDepartures(context: Context) {
        providerProxy.unsubscribeFromDepartures(listenerId, context)
    }
}
