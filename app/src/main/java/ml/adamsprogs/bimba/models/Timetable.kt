package ml.adamsprogs.bimba.models

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.univocity.parsers.csv.CsvParser
import com.univocity.parsers.csv.CsvParserSettings
import ml.adamsprogs.bimba.R
import ml.adamsprogs.bimba.getColour
import ml.adamsprogs.bimba.getSecondaryExternalFilesDir
import ml.adamsprogs.bimba.models.gtfs.AgencyAndId
import ml.adamsprogs.bimba.models.gtfs.Route
import ml.adamsprogs.bimba.models.gtfs.Trip
import ml.adamsprogs.bimba.models.gtfs.Calendar
import ml.adamsprogs.bimba.models.suggestions.LineSuggestion
import ml.adamsprogs.bimba.models.suggestions.StopSuggestion
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

class Timetable private constructor() { //fixme uses much too much RAM
    companion object {
        private var timetable: Timetable? = null

        fun getTimetable(context: Context? = null, force: Boolean = false): Timetable {
            return if (timetable == null || force)
                if (context != null) {
                    timetable = Timetable()
                    timetable!!.filesDir = context.getSecondaryExternalFilesDir()
                    val gtfsDir = File(timetable!!.filesDir, "gtfs_dir")
                    timetable!!.agencyFile = File(gtfsDir, "agency.txt")
                    timetable!!.calendarFile = File(gtfsDir, "calendar.txt")
                    timetable!!.calendarDatesFile = File(gtfsDir, "calendar_dates.txt")
                    timetable!!.feedInfoFile = File(gtfsDir, "feed_info.txt")
                    timetable!!.routesFile = File(gtfsDir, "routes.txt")
                    timetable!!.shapesFile = File(gtfsDir, "shapes.txt")
                    timetable!!.stopsFile = File(gtfsDir, "stops.txt")
                    timetable!!.stopTimesFile = File(gtfsDir, "stop_times.txt")
                    timetable!!.tripsFile = File(gtfsDir, "trips.txt")
                    timetable!!.stopsIndexFile = File(gtfsDir, "stop_index.txt")
                    timetable!!.tripsIndexFile = File(gtfsDir, "trip_index.txt")
                    timetable!!
                } else
                    throw IllegalArgumentException("new timetable requested and no context given")
            else
                timetable!!
        }
    }

    private lateinit var agencyFile: File
    private lateinit var calendarFile: File
    private lateinit var calendarDatesFile: File
    private lateinit var feedInfoFile: File
    private lateinit var routesFile: File
    private lateinit var shapesFile: File
    private lateinit var stopsFile: File
    private lateinit var stopTimesFile: File
    private lateinit var tripsFile: File
    private lateinit var stopsIndexFile: File
    private lateinit var tripsIndexFile: File
    private var _stops: List<StopSuggestion>? = null
    private lateinit var filesDir: File
    private val tripsCache = HashMap<String, Array<String>>()

    fun refresh() {
    }

    fun getStopSuggestions(context: Context, force: Boolean = false): List<StopSuggestion> {
        if (_stops != null && !force)
            return _stops!!


        val settings = CsvParserSettings()
        settings.format.setLineSeparator("\r\n")
        settings.format.quote = '"'
        settings.isHeaderExtractionEnabled = true
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


        _stops = ids.map {
            val colour = when (zones[it.key]) {
                "A" -> "#${getColour(R.color.zoneA, context).toString(16)}"
                "B" -> "#${getColour(R.color.zoneB, context).toString(16)}"
                "C" -> "#${getColour(R.color.zoneC, context).toString(16)}"
                else -> "#000000"
            }
            StopSuggestion(it.key, it.value, zones[it.key]!!, colour)
        }.sorted()
        return _stops!!
    }

    fun getLineSuggestions(): List<LineSuggestion> {
        val routes = ArrayList<LineSuggestion>()
        val file = File(filesDir, "gtfs_files/routes.txt")
        val settings = CsvParserSettings()
        settings.format.setLineSeparator("\r\n")
        settings.format.quote = '"'
        settings.isHeaderExtractionEnabled = true
        val parser = CsvParser(settings)
        parser.parseAll(file).forEach {
            routes.add(LineSuggestion(it[2], createRoute(
                    it[0],
                    it[1],
                    it[2],
                    it[3],
                    it[4],
                    Integer.parseInt(it[5]),
                    Integer.parseInt(it[6], 16),
                    Integer.parseInt(it[7], 16)
            )))
        }
        return routes.sortedBy { it.name }
    }

    fun getHeadlinesForStop(stops: Set<AgencyAndId>): Map<AgencyAndId, Pair<String, Set<String>>> { //fixme adds one (not-)random shed
        val trips = HashMap<String, HashSet<String>>()
        val routes = HashMap<String, Pair<String, String>>()
        val headsigns = HashMap<AgencyAndId, Pair<String, HashSet<String>>>()

        val stopIds = stops.map { it.id }

        parseStopTimesWithStopIndex(stopIds) {
            val stopId = it[3]
//            println("Parsing line ${parser.context.currentLine()}; stopId: $stopId")
            if (it[6] != "1") {
                if (trips[stopId] == null)
                    trips[stopId] = HashSet()
                trips[stopId]!!.add(it[0])
            }
        }

        if (tripsCache.isEmpty())
            createTripCache()
        tripsCache.forEach {
            routes[it.key] = Pair(it.value[0], it.value[3])
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

    private fun parseStopTimesWithStopIndex(stopIds: List<String>, process: (Array<String>) -> Unit) {
        val lines = readIndex(stopIds, File(filesDir, "gtfs_files/stop_index.yml"))
        parseStopTimesWithIndex(lines, process)
    }

    private fun parseStopTimesWithTripIndex(tripIds: List<String>, process: (Array<String>) -> Unit) {
        val lines = readIndex(tripIds, File(filesDir, "gtfs_files/trip_index.yml"))
        parseStopTimesWithIndex(lines, process)
    }

    private fun readIndex(ids: List<String>, indexFile: File): List<Long> {
        val index = HashMap<String, List<Long>>()

        val reader = indexFile.bufferedReader() //fixme 5s
        val json = Gson().fromJson(reader.readText(), JsonObject::class.java)
        reader.close()

        json.entrySet().forEach {
            //fixme 3s
            index[it.key] = ArrayList()
            it.value.asJsonArray.mapTo(index[it.key] as ArrayList) { it.asLong }
        }
        return index.filter { it.key in ids }.flatMap { it.value }.sorted()
    }

    private fun parseStopTimesWithIndex(lines: List<Long>, process: (Array<String>) -> Unit) {
        val settings = CsvParserSettings()
        settings.format.setLineSeparator("\r\n")
        settings.format.quote = '"'
        settings.isHeaderExtractionEnabled = true
        val parser = CsvParser(settings)

        parser.beginParsing(File(filesDir, "gtfs_files/stop_times.txt"))
        lines.forEach {
            //fixme 3s
//            println("At line ${parser.context.currentLine()}, skipping ${lines[lineKey] - parser.context.currentLine() - 1} lines to line ${lines[lineKey]}")
            parser.context.skipLines(it - parser.context.currentLine() - 1)
            val line = parser.parseNext()
            process(line)
        }

    }

    private fun createTripCache() {
        val settings = CsvParserSettings()
        settings.format.setLineSeparator("\r\n")
        settings.format.quote = '"'
        settings.isHeaderExtractionEnabled = true
        val parser = CsvParser(settings)
        val stopsFile = File(filesDir, "gtfs_files/trips.txt")
        parser.parseAll(stopsFile).forEach {
            tripsCache[it[2]] = it
        }
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
        println("getStopDepartures: ${JCalendar.getInstance().timeInMillis}")
//        val trips = getTripsForStop(stopId)
        val trips = HashMap<String, Trip>()
        tripsCache.forEach {
            trips[it.key] = tripFromCache(it.key)
        }
        val segment = StopSegment(stopId, null)
        segment.fillPlates()
        return getStopDeparturesBySegment(segment, trips)
    }

    fun getStopDeparturesBySegment(segment: StopSegment) = getStopDeparturesBySegment(segment, getTripsForStop(segment.stop))

    private fun getStopDeparturesBySegment(segment: StopSegment, trips: Map<String, Trip>): HashMap<AgencyAndId, List<Departure>> {
        println("getStopDeparturesBySegment: ${JCalendar.getInstance().timeInMillis}")
        val departures = HashMap<AgencyAndId, ArrayList<Departure>>()

        val tripsInStop = HashMap<String, Pair<Int, Int>>()

        parseStopTimesWithStopIndex(listOf(segment.stop.id)) {
            tripsInStop[it[0]] = Pair(parseTime(it[2]), it[4].toInt())
        }

        val file = File(filesDir, "gtfs_files/calendar.txt")
        val settings = CsvParserSettings()
        settings.format.setLineSeparator("\r\n")
        settings.format.quote = '"'
        settings.isHeaderExtractionEnabled = true
        val parser = CsvParser(settings)
        parser.parseAll(file).forEach {
            departures[AgencyAndId(it[0])] = ArrayList()
        }

        tripsInStop.forEach {
            //fixme this part is long --- cache is the only option
            departures[trips[it.key]!!.serviceId]!!.add(Departure(trips[it.key]!!.routeId, // fixme null?
                    calendarToMode(trips[it.key]!!.serviceId),
                    it.value.first, trips[it.key]!!.wheelchairAccessible,
                    explainModification(trips[it.key]!!, it.value.second),
                    trips[it.key]!!.headsign))
        }
        println("getStopDeparturesBySegment: ${JCalendar.getInstance().timeInMillis}")
        val sortedDepartures = HashMap<AgencyAndId, List<Departure>>()
        departures.keys.forEach {
            sortedDepartures[it] = departures[it]!!.sortedBy { it.time }
        }
        println("</>: ${JCalendar.getInstance().timeInMillis}")
        println("</>: ${JCalendar.getInstance().timeInMillis}")
        return sortedDepartures
    }

    private fun parseTime(time: String): Int {
        val cal = JCalendar.getInstance()
        val (h, m, s) = time.split(":")
        cal.set(JCalendar.HOUR_OF_DAY, h.toInt())
        cal.set(JCalendar.MINUTE, m.toInt())
        cal.set(JCalendar.SECOND, s.toInt())
        return cal.secondsAfterMidnight()
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

    private fun explainModification(trip: Trip, stopSequence: Int): List<String> { //todo<p:1> "kurs obsługiwany taborem niskopodłogowym" -> ignore
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
        if (tripsCache.isEmpty())
            createTripCache()
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
                return createRoute(id, agency, shortName, longName, desc, type, colour, textColour)
            }
        }
        mapReader.close()
        throw IllegalArgumentException("Trip ${trip.id.rawId} not in store")
    }

    private fun createRoute(id: String, agency: String, shortName: String, longName: String,
                            desc: String, type: Int, colour: Int, textColour: Int): Route {
        if (desc.contains("|")) {
            val (to, from) = desc.split("|")
            val fromSplit = from.split("^")
            val toSplit = to.split("^")
            val description = "${toSplit[0]}|${fromSplit[0]}"
            val modifications = HashMap<String, String>()
            toSplit.slice(1 until toSplit.size).forEach {
                val (k, v) = it.split(" - ")
                modifications[k] = v
            }
            return Route(AgencyAndId(id), AgencyAndId(agency), shortName, longName, description,
                    type, colour, textColour, modifications)
        } else {
            val toSplit = desc.split("^")
            val description = toSplit[0]
            val modifications = HashMap<String, String>()
            toSplit.slice(1 until toSplit.size).forEach {
                val (k, v) = it.split(" - ")
                modifications[k] = v
            }
            return Route(AgencyAndId(id), AgencyAndId(agency), shortName, longName, description,
                    type, colour, textColour, modifications)
        }
    }

    fun getTripsForStop(stopId: AgencyAndId): HashMap<String, Trip> {
        val tripIds = HashSet<String>()

        parseStopTimesWithStopIndex(listOf(stopId.id)) {
            tripIds.add(it[0])
        }

        val filteredTrips = HashMap<String, Trip>()

        tripIds.forEach {
            filteredTrips[it] = tripFromCache(it)
        }
        return filteredTrips
    }

    private fun tripFromCache(id: String): Trip {
        if (tripsCache.isEmpty())
            createTripCache()
        return Trip(AgencyAndId(tripsCache[id]!![0]),
                AgencyAndId(tripsCache[id]!![1]), createTripId(tripsCache[id]!![2]),
                tripsCache[id]!![3], Integer.parseInt(tripsCache[id]!![4]),
                AgencyAndId(tripsCache[id]!![5]), tripsCache[id]!![6] == "1")

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

    fun getServiceFor(day: Int): AgencyAndId {
        val dayColumn = ((day + 5) % 7) + 1
        val file = File(filesDir, "gtfs_files/calendar.txt")

        val settings = CsvParserSettings()
        settings.format.quote = '"'
        settings.format.setLineSeparator("\r\n")
        settings.isHeaderExtractionEnabled = true
        val parser = CsvParser(settings)

        parser.parseAll(file).forEach {
            if ((it[dayColumn] as String) == "1") {
                return AgencyAndId(it[0] as String)
            }
        }
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

        parseStopTimesWithStopIndex(listOf(stop.id)) {
            tripIds.add(it[0])
        }

        val filteredPlates = HashSet<Plate.ID>()
        tripIds.forEach {
            filteredPlates.add(Plate.ID(AgencyAndId(tripsCache[it]!![0]), stop, tripsCache[it]!![3]))
        }

        return filteredPlates
    }

    fun getTripGraphs(id: AgencyAndId): List<Map<Int, List<Int>>> {
        val tripsToDo = HashSet<String>()
        if (tripsCache.isEmpty())
            createTripCache()
        tripsCache.forEach {
            if (it.value[0] == id.id) {
                tripsToDo.add(it.key) //todo and direction {0,1}
            }
        }
        parseStopTimesWithTripIndex(tripsToDo.toList()) {
            //todo create graph
        }
        val map = ArrayList<HashMap<Int, List<Int>>>()
        val map0 = HashMap<Int, List<Int>>()
        map0[0] = listOf(1, 2)
        map0[1] = listOf(3, 4, 5)
        map.add(map0)
        val map1 = HashMap<Int, List<Int>>()
        map1[0] = listOf(1)
        map1[1] = listOf(2, 3, 4)
        map1[2] = listOf(4, 5)
        map.add(map1)
        return map
    }
}

