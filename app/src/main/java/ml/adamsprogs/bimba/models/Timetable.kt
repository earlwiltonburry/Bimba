package ml.adamsprogs.bimba.models

import android.content.Context
import com.univocity.parsers.csv.CsvParser
import com.univocity.parsers.csv.CsvParserSettings
import ml.adamsprogs.bimba.gtfs.AgencyAndId
import ml.adamsprogs.bimba.gtfs.Route
import ml.adamsprogs.bimba.gtfs.Trip
import ml.adamsprogs.bimba.gtfs.Calendar
import ml.adamsprogs.bimba.secondsAfterMidnight
import org.supercsv.cellprocessor.ift.CellProcessor
import org.supercsv.io.CsvMapReader
import org.supercsv.prefs.CsvPreference
import java.io.File
import java.io.FileReader
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import java.util.Calendar as JCalendar

class Timetable private constructor() {
    companion object {
        private var timetable: Timetable? = null

        fun getTimetable(context: Context? = null, force: Boolean = false): Timetable {
            return if (timetable == null || force)
                if (context != null) {
                    timetable = Timetable()
                    timetable!!.filesDir = context.filesDir
                    //timetable!!.cacheManager = CacheManager.getCacheManager(context)
                    timetable!!
                } else
                    throw IllegalArgumentException("new timetable requested and no context given")
            else
                timetable!!
        }
    }

    //private lateinit var cacheManager: CacheManager
    private var _stops: List<StopSuggestion>? = null
    private lateinit var filesDir: File
    private val tripsCache = HashMap<String, Array<String>>()

    fun refresh() {
        //cacheManager.recreate(getStopDeparturesByPlates(cacheManager.keys().toSet()))

        //getStops(true)
    }

    fun getStops(force: Boolean = false): List<StopSuggestion> {
        if (_stops != null && !force)
            return _stops!!


        val settings = CsvParserSettings()
        settings.format.setLineSeparator("\r\n")
        val parser = CsvParser(settings)

        val ids = HashMap<String, HashSet<AgencyAndId>>()
        val zones = HashMap<String, String>()

        val stopsFile = File(filesDir, "gtfs_files/stops.txt")
        parser.parseAll(stopsFile).forEach {
            if (it[2] !in ids)
                ids[it[2]] = HashSet()
            ids[it[2]]!!.add(AgencyAndId(it[0]))
            zones[it[2]] = it[5]
        }

        _stops = ids.map { StopSuggestion(it.key, it.value, zones[it.key]!!) }.sorted()
        return _stops!!
    }

    fun getHeadlinesForStop(stops: Set<AgencyAndId>): Map<AgencyAndId, Pair<String, Set<String>>> {
        val trips = HashMap<String, HashSet<String>>()
        val routes = HashMap<String, Pair<String, String>>()
        val headsigns = HashMap<AgencyAndId, Pair<String, HashSet<String>>>()
        val settings = CsvParserSettings()
        settings.format.setLineSeparator("\r\n")
        settings.format.quote = '"'
        val parser = CsvParser(settings)
        stops.forEach {
            trips[it.id] = HashSet()
            val stop = it.id
            val stopsFile = File(filesDir, "gtfs_files/stop_times_${it.id}.txt")
            parser.parseAll(stopsFile).forEach {
                if (it[6] in arrayOf("0", "3")) {
                    trips[stop]!!.add(it[0])
                }
            }
        }

        val stopsFile = File(filesDir, "gtfs_files/trips.txt")
        parser.parseAll(stopsFile).forEach {
            tripsCache[it[2]] = it
            routes[it[2]] = Pair(it[0], it[3])
        }

        trips.forEach {
            val headsign = HashSet<String>()
            it.value.forEach {
                headsign.add("${routes[it]!!.first} → ${routes[it]!!.second}")
            }
            headsigns[AgencyAndId(it.key)] = Pair(getStopCode(AgencyAndId(it.key)), headsign)
        }

        return headsigns
        /*
        1435 -> (AWF03, {232 → Os. Rusa})
        1436 -> (AWF04, {232 → Rondo Kaponiera})
        1437 -> (AWF02, {76 → Pl. Bernardyński, 74 → Os. Sobieskiego, 603 → Pl. Bernardyński})
        1634 -> (AWF01, {76 → Os. Dębina, 603 → Łęczyca/Dworcowa})
        171 -> (AWF42, {29 → Pl. Wiosny Ludów})
        172 -> (AWF41, {10 → Połabska, 29 → Dębiec, 15 → Budziszyńska, 10 → Dębiec, 15 → Os. Sobieskiego, 12 → Os. Sobieskiego, 6 → Junikowo, 18 → Ogrody, 2 → Ogrody})
        4586 -> (AWF73, {10 → Franowo, 29 → Franowo, 6 → Miłostowo, 5 → Stomil, 18 → Franowo, 15 → Franowo, 12 → Starołęka, 74 → Os. Orła Białego})
        */
    }

    fun getStopName(stopId: AgencyAndId): String {
        val file = File(filesDir, "gtfs_files/stops.txt")
        val mapReader = CsvMapReader(FileReader(file), CsvPreference.STANDARD_PREFERENCE)
        val header = mapReader.getHeader(true)

        var row: Map<String, Any>? = null
        val processors = Array<CellProcessor?>(header.size, { null })
        while ({ row = mapReader.read(header, processors); row }() != null) {
            if ((row!!["stop_id"] as String) == stopId.id) {
                mapReader.close()
                return row!!["stop_name"] as String
            }
        }
        mapReader.close()
        throw IllegalArgumentException("Stop $stopId not in store")
    }

    fun getStopCode(stopId: AgencyAndId): String {
        val file = File(filesDir, "gtfs_files/stops.txt")
        val mapReader = CsvMapReader(FileReader(file), CsvPreference.STANDARD_PREFERENCE)
        val header = mapReader.getHeader(true)

        var row: Map<String, Any>? = null
        val processors = Array<CellProcessor?>(header.size, { null })
        while ({ row = mapReader.read(header, processors); row }() != null) {
            if ((row!!["stop_id"] as String) == stopId.id) {
                mapReader.close()
                return row!!["stop_code"] as String
            }
        }
        mapReader.close()
        throw IllegalArgumentException("Stop $stopId not in store")
    }

    fun getLineNumber(lineId: AgencyAndId): String {
        val file = File(filesDir, "gtfs_files/routes.txt")
        val mapReader = CsvMapReader(FileReader(file), CsvPreference.STANDARD_PREFERENCE)
        val header = mapReader.getHeader(true)

        var row: Map<String, Any>? = null
        val processors = Array<CellProcessor?>(header.size, { null })
        while ({ row = mapReader.read(header, processors); row }() != null) {
            if ((row!!["route_id"] as String) == lineId.id) {
                mapReader.close()
                return row!!["route_short_name"] as String
            }
        }
        mapReader.close()
        throw IllegalArgumentException("Route $lineId not in store")
    }

    fun getStopDepartures(stopId: AgencyAndId): Map<AgencyAndId, List<Departure>> {
        val plates = HashSet<Plate>()
        val toGet = HashSet<Plate>()

        val trips = getTripsForStop(stopId)
        trips.values.map {
            Plate(Plate.ID(it.routeId, stopId, it.headsign), null)
        }.forEach {
                    toGet.add(it)
                }

        getStopDeparturesByPlates(toGet, trips).forEach { plates.add(it) }
        return Plate.join(plates)
    }

    fun getStopDepartures(plates: Set<Plate>): Map<AgencyAndId, ArrayList<Departure>> {
        val trips = HashMap<String, Trip>()
        //todo get trips

        return Plate.join(getStopDeparturesByPlates(plates, trips))
    }

    private fun getStopDeparturesByPlates(plates: Set<Plate>, trips: Map<String, Trip>): Set<Plate> {
        if (plates.isEmpty())
            return emptySet()

        return plates.map { getStopDeparturesByPlate(it, trips) }.toSet()
    }

    private fun getStopDeparturesByPlate(plate: Plate, trips: Map<String, Trip>): Plate { //fixme takes too long
        println("getStopDeparturesByPlate: ${JCalendar.getInstance().timeInMillis}")
        val resultPlate = Plate(Plate.ID(plate.id), HashMap())
        val stopTimes = HashMap<String, Map<String, Any>>()
        val stopTimesFile = File(filesDir, "gtfs_files/stop_times_${plate.id.stop.id}.txt")
        val mapReader = CsvMapReader(FileReader(stopTimesFile), CsvPreference.STANDARD_PREFERENCE)
        val header = mapReader.getHeader(true)

        var stopTimesRow: Map<String, Any>? = null
        val processors = Array<CellProcessor?>(header.size, { null })
        while ({ stopTimesRow = mapReader.read(header, processors); stopTimesRow }() != null) {
            val tripId = stopTimesRow!!["trip_id"] as String
            stopTimes[tripId] = stopTimesRow!!
        }
        mapReader.close()

        trips.forEach {
            if (it.value.routeId == plate.id.line &&
                    it.value.headsign == plate.id.headsign) {
                val cal = JCalendar.getInstance()
                val (h, m, s) = (stopTimes[it.key]!!["departure_time"] as String).split(":")
                cal.set(JCalendar.HOUR_OF_DAY, h.toInt())
                cal.set(JCalendar.MINUTE, m.toInt())
                cal.set(JCalendar.SECOND, s.toInt())
                val time = cal.secondsAfterMidnight()
                val serviceId = it.value.serviceId
                val mode = calendarToMode(serviceId)
                val lowFloor = it.value.wheelchairAccessible
                val mod = explainModification(it.value,
                        Integer.parseInt(stopTimes[it.key]!!["stop_sequence"] as String))

                val dep = Departure(plate.id.line, mode, time, lowFloor, mod, plate.id.headsign)
                if (resultPlate.departures!![serviceId] == null)
                    resultPlate.departures[serviceId] = HashSet()
                resultPlate.departures[serviceId]!!.add(dep)
            }
        }
        println("</>: ${JCalendar.getInstance().timeInMillis}")
        return resultPlate
    }

    fun calendarToMode(serviceId: AgencyAndId): List<Int> {
        val file = File(filesDir, "gtfs_files/calendar.txt")
        val mapReader = CsvMapReader(FileReader(file), CsvPreference.STANDARD_PREFERENCE)
        val header = mapReader.getHeader(true)

        var row: Map<String, Any>? = null
        val processors = Array<CellProcessor?>(header.size, { null })
        while ({ row = mapReader.read(header, processors); row }() != null) {
            if ((row!!["service_id"] as String) == serviceId.id) {
                mapReader.close()
                val calendar = Calendar(row!!["monday"] as String == "1", row!!["tuesday"] as String == "1",
                        row!!["wednesday"] as String == "1", row!!["thursday"] as String == "1", row!!["friday"] as String == "1",
                        row!!["saturday"] as String == "1", row!!["sunday"] as String == "1")
                val days = ArrayList<Int>()
                if (calendar.monday) days.add(0)
                if (calendar.tuesday) days.add(1)
                if (calendar.wednesday) days.add(2)
                if (calendar.thursday) days.add(3)
                if (calendar.friday) days.add(4)
                if (calendar.saturday) days.add(5)
                if (calendar.sunday) days.add(6)

                return days
            }
        }
        mapReader.close()
        throw IllegalArgumentException("Service $serviceId not in store")
    }

    private fun explainModification(trip: Trip, stopSequence: Int): List<String> { //todo "kurs obsługiwany taborem niskopodłogowym" -> ignore
        val route = getRouteForTrip(trip)
        val definitions = route.modifications

        val explanations = ArrayList<String>()
        trip.id.modification.forEach {
            if (it.stopRange != null) {
                if (stopSequence in it.stopRange)
                    explanations.add(definitions[it.id.id]!!)
            } else {
                explanations.add(definitions[it.id.id]!!)
            }
        }

        return explanations
    }

    private fun getRouteForTrip(trip: Trip): Route {
        val routeId = tripsCache[trip.id.rawId]!![0]

        val tripsFile = File(filesDir, "gtfs_files/routes.txt")
        val mapReader = CsvMapReader(FileReader(tripsFile), CsvPreference.STANDARD_PREFERENCE)
        val header = mapReader.getHeader(true)

        var routeRow: Map<String, Any>? = null
        val processors = Array<CellProcessor?>(header.size, { null })
        while ({ routeRow = mapReader.read(header, processors); routeRow }() != null) {
            if ((routeRow!!["route_id"] as String) == routeId) {
                mapReader.close()
                val id = routeRow!!["route_id"] as String
                val agency = routeRow!!["agency_id"] as String
                val shortName = routeRow!!["route_short_name"] as String
                val longName = routeRow!!["route_long_name"] as String
                val desc = routeRow!!["route_desc"] as String
                val type = Integer.parseInt(routeRow!!["route_type"] as String)
                val colour = Integer.parseInt(routeRow!!["route_color"] as String, 16)
                val textColour = Integer.parseInt(routeRow!!["route_text_color"] as String, 16)
                val (to, from) = desc.split("|")
                val toSplit = to.split("^")
                val fromSplit = from.split("^")
                val description = "${toSplit[0]}|${fromSplit[0]}"
                val modifications = HashMap<String, String>()
                toSplit.slice(1 until toSplit.size).forEach {
                    val (k, v) = it.split(" - ")
                    modifications[k] = v
                }
                return Route(AgencyAndId(id), AgencyAndId(agency), shortName, longName, description,
                        type, colour, textColour, modifications)
            }
        }
        mapReader.close()
        throw IllegalArgumentException("Trip ${trip.id.rawId} not in store")
    }

//    fun getLinesForStop(stopId: AgencyAndId): Set<AgencyAndId> {
//        val lines = HashSet<AgencyAndId>()
//        store.allStopTimes.filter { it.stop.id == stopId }.forEach { lines.add(it.trip.route.id) }
//        return lines
//    }

    fun getTripsForStop(stopId: AgencyAndId): HashMap<String, Trip> {
        val tripIds = HashSet<String>()
        val stopTimesFile = File(filesDir, "gtfs_files/stop_times_${stopId.id}.txt")
        val mapReader = CsvMapReader(FileReader(stopTimesFile), CsvPreference.STANDARD_PREFERENCE)
        val header = mapReader.getHeader(true)

        var stopTimesRow: Map<String, Any>? = null
        val processors = Array<CellProcessor?>(header.size, { null })
        while ({ stopTimesRow = mapReader.read(header, processors); stopTimesRow }() != null) {
            val tripId = stopTimesRow!!["trip_id"] as String
            tripIds.add(tripId)
        }
        mapReader.close()

        val filteredTrips = HashMap<String, Trip>()

        tripIds.forEach {
            filteredTrips[it] = Trip(AgencyAndId(tripsCache[it]!![0]),
                    AgencyAndId(tripsCache[it]!![1]), createTripId(tripsCache[it]!![2]),
                    tripsCache[it]!![3], Integer.parseInt(tripsCache[it]!![4]),
                    AgencyAndId(tripsCache[it]!![5]), tripsCache[it]!![6] == "1")
        }
        return filteredTrips
    }

    private fun createTripId(rawId: String): Trip.ID {
        if (rawId.contains('^')) {
            var modification = rawId.split("^")[1]
            val isMain = modification[modification.length - 1] == '+'
            if (isMain)
                modification = modification.subSequence(0, modification.length - 1) as String
            val modifications = HashSet<Trip.ID.Modification>()
            if (modification != "") {
                modification.split(",").forEach {
                    try {
                        val (id, start, end) = it.split(":")
                        modifications.add(Trip.ID.Modification(AgencyAndId(id), IntRange(start.toInt(), end.toInt())))
                    } catch (e: Exception) {
                        modifications.add(Trip.ID.Modification(AgencyAndId(it), null))
                    }
                }
            }
            return Trip.ID(rawId, AgencyAndId(rawId.split("^")[0]), modifications, isMain)
        } else
            return Trip.ID(rawId, AgencyAndId(rawId), HashSet(), false)
    }

    fun isEmpty(): Boolean {
        try {
            val file = File(filesDir, "gtfs_files/feed_info.txt").readText()
            if (file == "")
                return true
            return false
        } catch (e: Exception) {
            return true
        }
    }

    fun getValidSince(): String {
        val file = File(filesDir, "gtfs_files/feed_info.txt")
        val mapReader = CsvMapReader(FileReader(file), CsvPreference.STANDARD_PREFERENCE)
        val header = mapReader.getHeader(true)

        val processors = Array<CellProcessor?>(header.size, { null })
        val row = mapReader.read(header, processors)
        mapReader.close()
        return row["feed_start_date"] as String
    }

    fun getValidTill(): String {
        val file = File(filesDir, "gtfs_files/feed_info.txt")
        val mapReader = CsvMapReader(FileReader(file), CsvPreference.STANDARD_PREFERENCE)
        val header = mapReader.getHeader(true)

        val processors = Array<CellProcessor?>(header.size, { null })
        val row = mapReader.read(header, processors)
        mapReader.close()
        return row["feed_end_date"] as String
    }

    fun getServiceForToday(): AgencyAndId {
        val today = JCalendar.getInstance().get(JCalendar.DAY_OF_WEEK)
        return getServiceFor(today)
    }

    fun getServiceForTomorrow(): AgencyAndId {
        val tomorrow = JCalendar.getInstance()
        tomorrow.add(JCalendar.DAY_OF_MONTH, 1)
        val tomorrowDoW = tomorrow.get(JCalendar.DAY_OF_WEEK)
        return getServiceFor(tomorrowDoW)
    }

    private fun getServiceFor(day: Int): AgencyAndId {
        val dow = arrayOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")

        return getServiceFor(dow[day - 1])
    }

    fun getServiceFor(day: String): AgencyAndId {
        val file = File(filesDir, "gtfs_files/calendar.txt")
        val mapReader = CsvMapReader(FileReader(file), CsvPreference.STANDARD_PREFERENCE)
        val header = mapReader.getHeader(true)

        var row: Map<String, Any>? = null
        val processors = Array<CellProcessor?>(header.size, { null })
        while ({ row = mapReader.read(header, processors); row }() != null) {
            if ((row!![day] as String) == "1") {
                mapReader.close()
                return AgencyAndId(row!!["service_id"] as String)
            }
        }
        mapReader.close()
        throw IllegalArgumentException("Day $day not in calendar")

    }

    fun getLineForNumber(number: String): AgencyAndId {
        val file = File(filesDir, "gtfs_files/routes.txt")
        val mapReader = CsvMapReader(FileReader(file), CsvPreference.STANDARD_PREFERENCE)
        val header = mapReader.getHeader(true)

        var row: Map<String, Any>? = null
        val processors = Array<CellProcessor?>(header.size, { null })
        while ({ row = mapReader.read(header, processors); row }() != null) {
            if ((row!!["route_short_name"] as String) == number) {
                mapReader.close()
                return AgencyAndId(row!!["route_id"] as String)
            }
        }
        mapReader.close()
        throw IllegalArgumentException("Route $number not in store")
    }

    fun getPlatesForStop(stop: AgencyAndId): Set<Plate.ID> {
        val tripIds = HashSet<String>()
        val stopTimesFile = File(filesDir, "gtfs_files/stop_times_${stop.id}.txt")
        val mapReader = CsvMapReader(FileReader(stopTimesFile), CsvPreference.STANDARD_PREFERENCE)
        val header = mapReader.getHeader(true)

        var stopTimesRow: Map<String, Any>? = null
        val processors = Array<CellProcessor?>(header.size, { null })
        while ({ stopTimesRow = mapReader.read(header, processors); stopTimesRow }() != null) {
            val tripId = stopTimesRow!!["trip_id"] as String
            tripIds.add(tripId)
        }
        mapReader.close()

        val filteredPlates = HashSet<Plate.ID>()
        tripIds.forEach {
            filteredPlates.add(Plate.ID(AgencyAndId(tripsCache[it]!![0]), stop, tripsCache[it]!![3]))
        }

        return filteredPlates
    }
}

