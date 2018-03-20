package ml.adamsprogs.bimba.models

import android.content.Context
import android.database.Cursor
import android.database.CursorIndexOutOfBoundsException
import android.database.sqlite.SQLiteDatabase
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
import kotlin.system.measureTimeMillis
import java.util.Calendar as JCalendar

class Timetable private constructor() {
    companion object {
        private var timetable: Timetable? = null

        fun getTimetable(context: Context? = null, force: Boolean = false): Timetable {
            return if (timetable == null || force)
                if (context != null) {
                    timetable = Timetable()
                    timetable!!.filesDir = context.getSecondaryExternalFilesDir()
                    val dbFile = File(timetable!!.filesDir, "timetable.db")
                    timetable!!.db = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
                    timetable!!
                } else
                    throw IllegalArgumentException("new timetable requested and no context given")
            else
                timetable!!
        }
    }

    private lateinit var db: SQLiteDatabase
    private var _stops: List<StopSuggestion>? = null
    private lateinit var filesDir: File
    private val tripsCache = HashMap<String, Array<String>>()

    fun refresh() {
    }

    fun getStopSuggestions(context: Context, force: Boolean = false): List<StopSuggestion> {
        if (_stops != null && !force)
            return _stops!!

        val ids = HashMap<String, HashSet<AgencyAndId>>()
        val zones = HashMap<String, String>()

        val cursor = db.rawQuery("select stop_name, stop_id, zone_id from stops", null)

        while (cursor.moveToNext()) {
            val name = cursor.getString(0)
            val id = cursor.getInt(1)
            val zone = cursor.getString(2)
            if (name !in ids)
                ids[name] = HashSet()
            ids[name]!!.add(AgencyAndId(id.toString()))
            zones[name] = zone
        }

        cursor.close()

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
        val cursor = db.rawQuery("select * from routes", null)

        while (cursor.moveToNext()) {
            val routeId = cursor.getString(0)

            routes.add(LineSuggestion(routeId,
                    createRouteFromCursorRow(cursor)))
        }

        return routes.sortedBy { it.name }
    }

    fun getHeadlinesForStop(stops: Set<AgencyAndId>): Map<AgencyAndId, Pair<String, Set<String>>> {
        val headsigns = HashMap<AgencyAndId, Pair<String, HashSet<String>>>()

        val stopsIndex = HashMap<Int, String>()
        val where = stops.joinToString(" or ", "where ") { "stop_id = ?" }
        var cursor = db.rawQuery("select stop_id, stop_code from stops $where", stops.map { it.toString() }.toTypedArray())

        while (cursor.moveToNext()) {
            stopsIndex[cursor.getInt(0)] = cursor.getString(1)
        }

        cursor.close()

        cursor = db.rawQuery("select stop_id, route_id, trip_headsign " +
                "from stop_times natural join trips " +
                where, stops.map { it.toString() }.toTypedArray())

        while (cursor.moveToNext()) {
            val stop = cursor.getInt(0)
            val stopId = AgencyAndId(stop.toString())
            val route = cursor.getString(1)
            val headsign = cursor.getString(2)
            if (stopId !in headsigns)
                headsigns[stopId] = Pair(stopsIndex[stop]!!, HashSet())
            headsigns[stopId]!!.second.add("$route → $headsign")
        }

        cursor.close()

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
        val cursor = db.rawQuery("select stop_name from stops where stop_id = ?",
                arrayOf(stopId.id))
        cursor.moveToNext()
        val name = cursor.getString(0)
        cursor.close()

        return name
    }

    fun getStopCode(stopId: AgencyAndId): String {
        val cursor = db.rawQuery("select stop_code from stops where stop_id = ?",
                arrayOf(stopId.id))
        cursor.moveToNext()
        val code = cursor.getString(0)
        cursor.close()

        return code
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
        val map = HashMap<AgencyAndId, ArrayList<Departure>>()
        val measure = measureTimeMillis {

            val cursor = db.rawQuery("select route_id, service_id, departure_time, " +
                    "wheelchair_accessible, stop_sequence, trip_id, trip_headsign, route_desc " +
                    "from stop_times natural join trips natural join routes where stop_id = ?",
                    arrayOf(stopId.id))

            while (cursor.moveToNext()) {
                val line = AgencyAndId(cursor.getString(0))
                val service = AgencyAndId(cursor.getInt(1).toString())
                val mode = calendarToMode(service)
                val time = parseTime(cursor.getString(2))
                val lowFloor = cursor.getInt(3) == 1
                val stopSequence = cursor.getInt(4)
                val tripId = createTripId(cursor.getString(5))
                val headsign = cursor.getString(6)
                val desc = cursor.getString(7)

                val modifications = Route.createModifications(desc)

                val modification = explainModification(tripId, stopSequence, modifications)
                val departure = Departure(line, mode, time, lowFloor, modification, headsign)
                if (map[service] == null)
                    map[service] = ArrayList()
                map[service]!!.add(departure)
            }

            cursor.close()
            map.forEach { it.value.sortBy { it.time } }
        }

        //println(measure)

        return map
    }

    fun getTrip(id: String): Trip {
        val cursor = db.rawQuery("select * from trips where trip_id = ?", arrayOf(id))

        val trip = Trip(
                AgencyAndId(cursor.getString(0)),
                AgencyAndId(cursor.getInt(1).toString()),
                createTripId(cursor.getString(2)),
                cursor.getString(3),
                cursor.getInt(4),
                AgencyAndId(cursor.getInt(5).toString()),
                cursor.getInt(6) == 1
        )

        cursor.close()
        return trip
    }

    fun getStopDeparturesBySegment(segment: StopSegment) = getStopDeparturesBySegment(segment, getTripsForStop(segment.stop))

    private fun getStopDeparturesBySegment(segment: StopSegment, trips: Map<String, Trip>): HashMap<AgencyAndId, List<Departure>> {
        println("getStopDeparturesBySegment: ${JCalendar.getInstance().timeInMillis}")
        /*val departures = HashMap<AgencyAndId, ArrayList<Departure>>()

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
        return sortedDepartures*/
        TODO("FIXME")
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
        val days = ArrayList<Int>()
        val cursor = db.rawQuery("select * from calendar where service_id = ?",
                arrayOf(serviceId.id))

        cursor.moveToNext()
        (1 until 7).forEach {
            if (cursor.getInt(it) == 1) days.add(it - 1)
        }

        cursor.close()
        return days
    }

    private fun explainModification(tripId: Trip.ID, stopSequence: Int, routeModifications: Map<String, String>): List<String> { //todo<p:1> "kurs obsługiwany taborem niskopodłogowym" -> ignore
        val explanations = ArrayList<String>()
        tripId.modification.forEach {
            if (it.stopRange != null) {
                if (stopSequence in it.stopRange)
                    explanations.add(routeModifications[it.id.id]!!)
            } else {
                explanations.add(routeModifications[it.id.id]!!)
            }
        }

        return explanations
    }

    private fun getRouteForTrip(trip: Trip): Route {
        val cursor = db.rawQuery("select * from routes natural join trips where trip_id = ?",
                arrayOf(trip.id.rawId))

        cursor.moveToNext()
        val route = createRouteFromCursorRow(cursor)
        cursor.close()
        return route
    }

    private fun createRouteFromCursorRow(cursor: Cursor): Route {
        val routeId = cursor.getString(0)
        val agencyId = cursor.getInt(1).toString()
        val shortName = cursor.getString(2)
        val longName = cursor.getString(3)
        val desc = cursor.getString(4)
        val type = cursor.getInt(5)
        val colour = cursor.getString(6).toInt(16)
        val textColour = cursor.getString(7).toInt(16)

        return Route.create(routeId, agencyId, shortName, longName, desc, type, colour, textColour)
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
        return try {
            File(filesDir, "timetable.db")
            //todo check if not empty
            false
        } catch (e: Exception) {
            true
        }
    }

    fun getValidSince(): String {
        val cursor = db.rawQuery("select feed_start_date from feed_info", null)

        cursor.moveToNext()
        val validTill = cursor.getString(0)

        cursor.close()
        return validTill
    }

    fun getValidTill(): String {
        val cursor = db.rawQuery("select feed_end_date from feed_info", null)

        cursor.moveToNext()
        val validTill = cursor.getString(0)

        cursor.close()
        return validTill
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
        val dayColumn = arrayOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")[((day + 5) % 7)]
        val cursor = db.rawQuery("select service_id from calendar where $dayColumn = 1", null)

        val service: Int
        cursor.moveToNext()
        try {
            service = cursor.getInt(0)
            cursor.close()
            return AgencyAndId(service.toString())
        } catch (e: CursorIndexOutOfBoundsException) {
            throw IllegalArgumentException()
        }
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
        val cursor = db.rawQuery("select route_id, trip_headsign " +
                "from stop_times natural join trips where stop_id = ? " +
                "group by route_id, trip_headsign", arrayOf(stop.id))

        while (cursor.moveToNext()) {
            val routeId = AgencyAndId(cursor.getString(0))
            val headsign = cursor.getString(1)
            plates.add(Plate.ID(routeId, stop, headsign))
        }

        cursor.close()
        return plates
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

