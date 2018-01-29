package ml.adamsprogs.bimba.models

import android.content.Context
import ml.adamsprogs.bimba.CacheManager
import ml.adamsprogs.bimba.toPascalCase
import java.io.File
import org.onebusaway.gtfs.impl.GtfsDaoImpl
import org.onebusaway.gtfs.model.*
import org.onebusaway.gtfs.serialization.GtfsReader
import org.onebusaway.gtfs.services.GtfsDao
import java.util.*

class Timetable private constructor() {
    companion object {
        const val MODE_WORKDAYS = "workdays"
        const val MODE_SATURDAYS = "saturdays"
        const val MODE_SUNDAYS = "sundays"
        private var timetable: Timetable? = null

        fun getTimetable(context: Context? = null, force: Boolean = false): Timetable {
            return if (timetable == null || force)
                if (context != null) {
                    timetable = Timetable()
                    timetable!!.store = read(context)
                    timetable!!.cacheManager = CacheManager.getCacheManager(context)
                    timetable as Timetable
                } else
                    throw IllegalArgumentException("new timetable requested and no context given")
            else
                timetable as Timetable
        }

        private fun read(context: Context): GtfsDao {
            val reader = GtfsReader()
            reader.setInputLocation(File(context.filesDir, "timetable.zip"))
            val store = GtfsDaoImpl()
            reader.entityStore = store
            reader.run()
            return store
        }
    }

    lateinit var store: GtfsDao
    private lateinit var cacheManager: CacheManager
    private var _stops: ArrayList<StopSuggestion>? = null //todo stops to cache

    fun refresh(context: Context) {
        this.store = read(context)

        cacheManager.recreate(getStopDeparturesByPlates(cacheManager.keys().toSet()))

        getStops(true)
    }

    fun getStops(force: Boolean = false): List<StopSuggestion> {
        if (_stops != null && !force)
            return _stops!!

        /*
        AWF
        232 → Os. Rusa|8:1435|AWF03
        AWF
        232 → Rondo Kaponiera|8:1436|AWF04
        AWF
        76 → Pl. Bernardyński, 74 → Os. Sobieskiego, 603 → Pl. Bernardyński|8:1437|AWF02
        AWF
        76 → Os. Dębina, 603 → Łęczyca/Dworcowa|8:1634|AWF01
        AWF
        29 → Pl. Wiosny Ludów|8:171|AWF42
        AWF
        10 → Połabska, 29 → Dębiec, 15 → Budziszyńska, 10 → Dębiec, 15 → Os. Sobieskiego, 12 → Os. Sobieskiego, 6 → Junikowo, 18 → Ogrody, 2 → Ogrody|8:172|AWF41
        AWF
        10 → Franowo, 29 → Franowo, 6 → Miłostowo, 5 → Stomil, 18 → Franowo, 15 → Franowo, 12 → Starołęka, 74 → Os. Orła Białego|8:4586|AWF73
        */

        //trip_id, stop_id from stop_times if drop_off_type in {0,3}
        //route_id as line, trip_id, headsign from trips
        //stop_id, stop_code from stops

        val map = HashMap<AgencyAndId, HashSet<String>>()

        store.allStopTimes.filter { it.dropOffType == 0 || it.dropOffType == 3 }.forEach {
            val trip = it.trip
            val line = trip.route.shortName
            val headsign = (trip.tripHeadsign).toPascalCase()
            val stopId = it.stop.id
            if (map[stopId] == null)
                map[stopId] = HashSet()
            map[stopId]!!.add("$line → $headsign")
        }

        val stops = map.entries.map { StopSuggestion(it.value, it.key) }.toSet()


        _stops = stops.sortedBy { this.getStopSymbol(it.id) } as ArrayList<StopSuggestion>
        return _stops!!
    }

    fun getStopName(stopId: AgencyAndId) = store.getStopForId(stopId).name!!

    fun getStopSymbol(stopId: AgencyAndId) = store.getStopForId(stopId).code!!

    fun getLineNumber(lineId: AgencyAndId) = store.getRouteForId(lineId).shortName!!

    fun getStopDepartures(stopId: AgencyAndId): Map<String, List<Departure>> {
        val plates = HashSet<Plate>()
        val toGet = HashSet<Plate>()

        getTripsForStop(stopId)
                .map {
                    it.tripHeadsign
                    Plate(it.route.id, stopId, it.tripHeadsign, null)
                }
                .forEach {
                    if (cacheManager.has(it))
                        plates.add(cacheManager.get(it)!!)
                    else {
                        toGet.add(it)
                    }
                }

        getStopDeparturesByPlates(toGet).forEach { cacheManager.push(it); plates.add(it) }

        return Plate.join(plates)
    }

    fun getStopDepartures(plates: Set<Plate>): Map<String, ArrayList<Departure>> {
        val result = HashSet<Plate>()
        val toGet = HashSet<Plate>()

        for (plate in plates) {
            if (cacheManager.has(plate))
                result.add(cacheManager.get(plate)!!)
            else
                toGet.add(plate)
        }

        getStopDeparturesByPlates(toGet).forEach { cacheManager.push(it); result.add(it) }

        return Plate.join(result)
    }

    private fun getStopDeparturesByPlates(plates: Set<Plate>): Set<Plate> {
        if (plates.isEmpty())
            return emptySet()

        return plates.map { getStopDeparturesByPlate(it) }.toSet()
    }

    private fun getStopDeparturesByPlate(plate: Plate): Plate {
        val p = Plate(plate.line, plate.stop, plate.headsign, HashMap())
        store.allStopTimes
                .filter { it.stop.id == plate.stop }
                .filter { it.trip.route.id == plate.line }
                .filter { it.trip.tripHeadsign.toLowerCase() == plate.headsign.toLowerCase() }
                .forEach {
                    val time = it.departureTime
                    val serviceId = it.trip.serviceId
                    val mode = calendarToMode(serviceId.id.toInt())
                    val lowFloor = it.trip.wheelchairAccessible == 1
                    val mod = explainModification(it.trip, it.trip.id.id.split("^")[1], it.stopSequence)

                    val dep = Departure(plate.line, mode, time, lowFloor, mod, plate.headsign)
                    if (p.departures!![serviceId] == null)
                        p.departures[serviceId] = HashSet()
                    p.departures[serviceId]!!.add(dep)
                }
        return p
    }

    private fun calendarToMode(serviceId: Int): List<Int> {
        val calendar = store.getCalendarForId(serviceId)
        val days = ArrayList<Int>()
        if (calendar.monday == 1) days.add(0)
        if (calendar.tuesday == 1) days.add(1)
        if (calendar.wednesday == 1) days.add(2)
        if (calendar.thursday == 1) days.add(3)
        if (calendar.friday == 1) days.add(4)
        if (calendar.saturday == 1) days.add(5)
        if (calendar.sunday == 1) days.add(6)
        return days
    }

    private fun explainModification(trip: Trip, modifications: String, stopSequence: Int): List<String> {
        val mods = modifications.replace("+", "").split(",")
        var definitions = trip.route.desc.split("^")
        definitions = definitions.slice(2..definitions.size)

        val definitionsMap = HashMap<String, String>()

        definitions.forEach {
            val (key, definition) = it.split(" - ")
            definitionsMap[key] = definition
        }

        val explanations = ArrayList<String>()

        mods.forEach {
            if (it.contains(":")) {
                val (key, start, stop) = it.split(":")
                if (stopSequence in start.toInt()..stop.toInt())
                    explanations.add(definitionsMap[key]!!)
            } else {
                explanations.add(definitionsMap[it]!!)
            }
        }

        return explanations
    }

    fun getLinesForStop(stopId: AgencyAndId): Set<AgencyAndId> {
        val lines = HashSet<AgencyAndId>()
        store.allStopTimes.filter { it.stop.id == stopId }.forEach { lines.add(it.trip.route.id) }
        return lines
    }

    private fun getTripsForStop(stopId: AgencyAndId): Set<Trip> {
        val lines = HashSet<Trip>()
        store.allStopTimes.filter { it.stop.id == stopId }.forEach { lines.add(it.trip) }
        return lines
    }

    fun isEmpty() = store.allFeedInfos.isEmpty()

    fun getValidSince() = store.allFeedInfos.toTypedArray()[0].startDate.asString!!

    fun getValidTill() = store.allFeedInfos.toTypedArray()[0].endDate.asString!!
}

