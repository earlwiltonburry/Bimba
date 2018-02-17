package ml.adamsprogs.bimba.models

import android.content.Context
import ml.adamsprogs.bimba.datasources.CacheManager
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

//todo faster csv: http://simpleflatmapper.org/0101-getting-started-csv.html
//todo faster csv: https://github.com/uniVocity/univocity-parsers
//todo prolly need to write own simple and fast parser
class Timetable private constructor() {
    companion object {
        private var timetable: Timetable? = null

        fun getTimetable(context: Context? = null, force: Boolean = false): Timetable {
            return if (timetable == null || force)
                if (context != null) {
                    timetable = Timetable()
                    //timetable!!.store = read(context)
                    timetable!!.filesDir = context.filesDir
                    timetable!!.cacheManager = CacheManager.getCacheManager(context)
                    timetable as Timetable
                } else
                    throw IllegalArgumentException("new timetable requested and no context given")
            else
                timetable as Timetable
        }

        /*private fun read(context: Context): SQLiteDatabase {
            return SQLiteDatabase.openDatabase(File(context.filesDir, "timetable.db").path,
                    null, SQLiteDatabase.OPEN_READONLY)
        }*/
    }

    //lateinit var store: SQLiteDatabase
    private lateinit var cacheManager: CacheManager
    private var _stops: ArrayList<StopSuggestion>? = null
    private lateinit var filesDir: File

    fun refresh() {
        //this.store = read(context)

        cacheManager.recreate(getStopDeparturesByPlates(cacheManager.keys().toSet()))

        getStops(true)
    }

    fun getStops(force: Boolean = false): List<StopSuggestion> {
        println("STOPS!")
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

        //trip_id, stop_id from stop_times if pickup_type in {0,3}
        //route_id as line, trip_id, headsign from trips

        println(JCalendar.getInstance())
        val stopTripMap = HashMap<String, Set<String>>()
        val stopTimesFile = File(filesDir, "gtfs_files/stop_times.txt")
        var mapReader = CsvMapReader(FileReader(stopTimesFile), CsvPreference.STANDARD_PREFERENCE)
        var header = mapReader.getHeader(true)

        var stopTimesRow: Map<String, Any>? = null
        var processors = Array<CellProcessor?>(header.size, { null })
        while ({ stopTimesRow = mapReader.read(header, processors); stopTimesRow }() != null) {
            if ((stopTimesRow!!["pickup_type"] as String) in arrayOf("0", "3")) {
                val stopId = stopTimesRow!!["stop_id"] as String
                val tripId = stopTimesRow!!["trip_id"] as String
                if (stopId !in stopTripMap)
                    stopTripMap[stopId] = HashSet()
                (stopTripMap[stopId]!! as HashSet).add(tripId)
            }
        }
        mapReader.close()
        println(JCalendar.getInstance())

        val tripIds = stopTripMap.flatMap { it.value }

        val trips = HashMap<String, String>()
        val tripsFile = File(filesDir, "gtfs_files/trips.txt")
        mapReader = CsvMapReader(FileReader(tripsFile), CsvPreference.STANDARD_PREFERENCE)
        header = mapReader.getHeader(true)

        var tripsRow: Map<String, Any>? = null
        processors = Array(header.size, { null })
        while ({ tripsRow = mapReader.read(header, processors); tripsRow }() != null) { //fixme takes 16 min, 21 times more than a file 28 times bigger
            val tripId = tripsRow!!["trip_id"] as String
            if (tripId in tripIds) {
                trips[tripId] = tripsRow!!["trip_headsign"] as String //todo save route_id
            }
        }
        mapReader.close()
        println(JCalendar.getInstance())

        val routes = HashMap<String, String>()
        val routesFile = File(filesDir, "gtfs_files/routes.txt")
        mapReader = CsvMapReader(FileReader(routesFile), CsvPreference.STANDARD_PREFERENCE)
        header = mapReader.getHeader(true)

        var routesRow: Map<String, Any>? = null
        processors = Array(header.size, { null })
        while ({ routesRow = mapReader.read(header, processors); routesRow }() != null) {
            val tripId = routesRow!!["route_id"] as String
            if (tripId in tripIds) {//fixme
                routes[tripId] = routesRow!!["route_short_name"] as String
            }
        }
        mapReader.close()
        println(JCalendar.getInstance())

        val map = HashMap<AgencyAndId, Set<String>>()

        stopTripMap.forEach {
            val directions = HashSet<String>()
            it.value.forEach {
                val route = routes[it]
                val headsign = trips[it]
                directions.add("$route → $headsign")
            }
            map[AgencyAndId(it.key)] = directions
        }

        _stops = map.map { StopSuggestion(it.value, it.key) }.sortedBy { getStopName(it.id) } as ArrayList<StopSuggestion>

        return _stops!!
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

        getTripsForStop(stopId)
                .map {
                    Plate(Plate.ID(it.routeId, stopId, it.headsign), null)
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

    fun getStopDepartures(plates: Set<Plate>): Map<AgencyAndId, ArrayList<Departure>> {
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
        val resultPlate = Plate(Plate.ID(plate.id), HashMap())
        val trips = HashMap<String, Map<String, Any>>()
        val stopTimesFile = File(filesDir, "gtfs_files/stop_times.txt")
        var mapReader = CsvMapReader(FileReader(stopTimesFile), CsvPreference.STANDARD_PREFERENCE)
        var header = mapReader.getHeader(true)

        var stopTimesRow: Map<String, Any>? = null
        var processors = Array<CellProcessor?>(header.size, { null })
        while ({ stopTimesRow = mapReader.read(header, processors); stopTimesRow }() != null) {
            if ((stopTimesRow!!["stop_id"] as String) == plate.id.stop.id) {
                val tripId = stopTimesRow!!["trip_id"] as String
                trips[tripId] = stopTimesRow!!
            }
        }
        mapReader.close()

        val tripsFile = File(filesDir, "gtfs_files/trips.txt")
        mapReader = CsvMapReader(FileReader(tripsFile), CsvPreference.STANDARD_PREFERENCE)
        header = mapReader.getHeader(true)

        var tripsRow: Map<String, Any>? = null
        processors = Array(header.size, { null })
        while ({ tripsRow = mapReader.read(header, processors); tripsRow }() != null) {
            val tripId = tripsRow!!["trip_id"] as String
            if (tripId in trips && tripsRow!!["route_id"] as String == plate.id.line.id
                    && tripsRow!!["trip_headsign"] as String == plate.id.headsign) { //check if toLower is needed
                val cal = JCalendar.getInstance()
                val (h, m, s) = (trips[tripId]!!["departure_time"] as String).split(":")
                cal.set(JCalendar.HOUR_OF_DAY, h.toInt())
                cal.set(JCalendar.MINUTE, m.toInt())
                cal.set(JCalendar.SECOND, s.toInt())
                val time = cal.secondsAfterMidnight()
                val serviceId = AgencyAndId(tripsRow!!["service_id"] as String)
                val mode = calendarToMode(serviceId)
                val lowFloor = trips[tripId]!!["wheelchair_accessible"] as String == "1"
                val mod = explainModification(Trip(AgencyAndId(tripsRow!!["route_id"] as String),
                        serviceId, createTripId(tripsRow!!["trip_id"] as String),
                        tripsRow!!["trip_headsign"] as String, Integer.parseInt(tripsRow!!["direction_id"] as String),
                        AgencyAndId(tripsRow!!["shape_id"] as String)), Integer.parseInt(trips[tripId]!!["stop_sequence"] as String))

                val dep = Departure(plate.id.line, mode, time, lowFloor, mod, plate.id.headsign)
                if (resultPlate.departures!![serviceId] == null)
                    resultPlate.departures[serviceId] = HashSet()
                resultPlate.departures[serviceId]!!.add(dep)
            }
        }
        mapReader.close()
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

    private fun explainModification(trip: Trip, stopSequence: Int): List<String> {
        val definitions = getRouteForTrip(trip).modifications
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
        val routesFile = File(filesDir, "gtfs_files/routes.txt")
        var mapReader = CsvMapReader(FileReader(routesFile), CsvPreference.STANDARD_PREFERENCE)
        var header = mapReader.getHeader(true)

        var routeId = ""
        var row: Map<String, Any>? = null
        var processors = Array<CellProcessor?>(header.size, { null })
        while ({ row = mapReader.read(header, processors); row }() != null) {
            if ((row!!["trip_id"] as String) == trip.rawId) {
                mapReader.close()
                routeId = row!!["route_id"] as String
                break
            }
        }
        if (routeId == "") {
            mapReader.close()
            throw IllegalArgumentException("Trip ${trip.rawId} not in store")
        }

        val tripsFile = File(filesDir, "gtfs_files/trips.txt")
        mapReader = CsvMapReader(FileReader(tripsFile), CsvPreference.STANDARD_PREFERENCE)
        header = mapReader.getHeader(true)

        var routeRow: Map<String, Any>? = null
        processors = Array(header.size, { null })
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
        throw IllegalArgumentException("Trip ${trip.rawId} not in store")
    }

//    fun getLinesForStop(stopId: AgencyAndId): Set<AgencyAndId> {
//        val lines = HashSet<AgencyAndId>()
//        store.allStopTimes.filter { it.stop.id == stopId }.forEach { lines.add(it.trip.route.id) }
//        return lines
//    }

    fun getTripsForStop(stopId: AgencyAndId): Set<Trip> {
        val tripIds = HashSet<String>()
        val stopTimesFile = File(filesDir, "gtfs_files/stop_times.txt")
        var mapReader = CsvMapReader(FileReader(stopTimesFile), CsvPreference.STANDARD_PREFERENCE)
        var header = mapReader.getHeader(true)

        var stopTimesRow: Map<String, Any>? = null
        var processors = Array<CellProcessor?>(header.size, { null })
        while ({ stopTimesRow = mapReader.read(header, processors); stopTimesRow }() != null) {
            if ((stopTimesRow!!["stop_id"] as String) == stopId.id) {
                val tripId = stopTimesRow!!["trip_id"] as String
                tripIds.add(tripId)
            }
        }
        mapReader.close()


        val trips = HashSet<Trip>()
        val tripsFile = File(filesDir, "gtfs_files/trips.txt")
        mapReader = CsvMapReader(FileReader(tripsFile), CsvPreference.STANDARD_PREFERENCE)
        header = mapReader.getHeader(true)

        var tripsRow: Map<String, Any>? = null
        processors = Array(header.size, { null })
        while ({ tripsRow = mapReader.read(header, processors); tripsRow }() != null) {
            val tripId = tripsRow!!["trip_id"] as String
            if (tripId in tripIds) {
                trips.add(Trip(AgencyAndId(tripsRow!!["route_id"] as String),
                        AgencyAndId(tripsRow!!["service_id"] as String),
                        createTripId(tripId),
                        tripsRow!!["trip_headsign"] as String,
                        Integer.parseInt(tripsRow!!["direction_id"] as String),
                        AgencyAndId(tripsRow!!["shape_id"] as String)))
            }
        }
        mapReader.close()
        return trips
    }

    private fun createTripId(rawId: String): Trip.ID {
        if (rawId.contains('^')) {
            var modification = rawId.split("^")[1]
            val isMain = modification[modification.length - 1] == '+'
            if (isMain)
                modification = modification.subSequence(0, modification.length - 1) as String
            val modifications = HashSet<Trip.ID.Modification>()
            modification.split(",").forEach {
                try {
                    val (id, start, end) = it.split(":")
                    modifications.add(Trip.ID.Modification(AgencyAndId(id), IntRange(start.toInt(), end.toInt())))
                } catch (e: Exception) {
                    modifications.add(Trip.ID.Modification(AgencyAndId(it), null))
                }
            }
            return Trip.ID(AgencyAndId(rawId.split("^")[0]), modifications, isMain)
        } else
            return Trip.ID(AgencyAndId(rawId), HashSet(), false)
    }

    fun isEmpty(): Boolean {
        try {
            val file = File(filesDir, "gtfs_files/feed_info.txt")
            val mapReader = CsvMapReader(FileReader(file), CsvPreference.STANDARD_PREFERENCE)
            val header = mapReader.getHeader(true)

            val processors = Array<CellProcessor?>(header.size, { null })
            if (mapReader.read(header, processors) == null) {
                mapReader.close()
                return true
            }
            mapReader.close()
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
        val file = File(filesDir, "gtfs_files/calendar.txt")
        val mapReader = CsvMapReader(FileReader(file), CsvPreference.STANDARD_PREFERENCE)
        val header = mapReader.getHeader(true)

        var row: Map<String, Any>? = null
        val processors = Array<CellProcessor?>(header.size, { null })
        while ({ row = mapReader.read(header, processors); row }() != null) {
            if ((row!![dow[day - 1]] as String) == "1") {
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
        val plates = HashSet<Plate.ID>()
        val tripIds = HashSet<String>()
        val stopTimesFile = File(filesDir, "gtfs_files/stop_times.txt")
        var mapReader = CsvMapReader(FileReader(stopTimesFile), CsvPreference.STANDARD_PREFERENCE)
        var header = mapReader.getHeader(true)

        var stopTimesRow: Map<String, Any>? = null
        var processors = Array<CellProcessor?>(header.size, { null })
        while ({ stopTimesRow = mapReader.read(header, processors); stopTimesRow }() != null) {
            if ((stopTimesRow!!["stop_id"] as String) == stop.id) {
                val tripId = stopTimesRow!!["trip_id"] as String
                tripIds.add(tripId)
            }
        }
        mapReader.close()


        val tripsFile = File(filesDir, "gtfs_files/trips.txt")
        mapReader = CsvMapReader(FileReader(tripsFile), CsvPreference.STANDARD_PREFERENCE)
        header = mapReader.getHeader(true)

        var tripsRow: Map<String, Any>? = null
        processors = Array(header.size, { null })
        while ({ tripsRow = mapReader.read(header, processors); tripsRow }() != null) {
            if (tripsRow!!["trip_id"] as String in tripIds) {
                plates.add(Plate.ID(AgencyAndId(tripsRow!!["route_id"] as String), stop, tripsRow!!["trip_headsign"] as String))
            }
        }
        mapReader.close()

        return plates
    }
}

