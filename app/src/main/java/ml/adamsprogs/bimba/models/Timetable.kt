package ml.adamsprogs.bimba.models

import android.annotation.SuppressLint
import android.content.Context
import android.database.*
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import ml.adamsprogs.bimba.*
import ml.adamsprogs.bimba.models.gtfs.*
import ml.adamsprogs.bimba.models.suggestions.*
import java.io.*
import kotlin.collections.*
import java.util.Calendar as JCalendar

class Timetable private constructor() {
    companion object {
        private var timetable: Timetable? = null

        fun getTimetable(context: Context? = null, force: Boolean = false): Timetable {
            return if (timetable == null || force)
                if (context != null) {
                    constructTimetable(context)
                    timetable!!
                } else
                    throw IllegalArgumentException("new timetable requested and no `context` given")
            else if (context != null) {
                try {
                    constructTimetable(context)
                    timetable!!
                } catch (e: Exception) {
                    timetable!!
                }
            } else
                timetable!!
        }

        private fun constructTimetable(context: Context) {
            val timetable = Timetable()
            val filesDir = context.getSecondaryExternalFilesDir()
            val dbFile = File(filesDir, "timetable.db")
            timetable.db = try {
                SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
            } catch(e: SQLiteException) {
                null
            }
            this.timetable = timetable
        }
    }

    private var db: SQLiteDatabase? = null
    private var _stops: List<StopSuggestion>? = null

    fun refresh() {
    }

    fun getStopSuggestions(/*context: Context, */force: Boolean = false): List<StopSuggestion> {
        if (_stops != null && !force)
            return _stops!!

        val ids = HashMap<String, HashSet<AgencyAndId>>()
        val zones = HashMap<String, String>()

        val cursor = db!!.rawQuery("select stop_name, stop_id, zone_id from stops", null)

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
            /*todo
            val colour = when (zones[it.key]) {
                "A" -> "#${getColour(R.color.zoneA, context).toString(16)}"
                "B" -> "#${getColour(R.color.zoneB, context).toString(16)}"
                "C" -> "#${getColour(R.color.zoneC, context).toString(16)}"
                else -> "#000000"
            }
            */
            StopSuggestion(it.key, it.value, zones[it.key]!!, "#000000")
        }.sorted()
        return _stops!!
    }

    fun getLineSuggestions(): List<LineSuggestion> {
        val routes = ArrayList<LineSuggestion>()
        val cursor = db!!.rawQuery("select * from routes", null)

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
        var cursor = db!!.rawQuery("select stop_id, stop_code from stops $where", stops.map { it.toString() }.toTypedArray())

        while (cursor.moveToNext()) {
            stopsIndex[cursor.getInt(0)] = cursor.getString(1)
        }

        cursor.close()

        cursor = db!!.rawQuery("select stop_id, route_id, trip_headsign " +
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

    fun getStopName(stopId: AgencyAndId): String {
        val cursor = db!!.rawQuery("select stop_name from stops where stop_id = ?",
                arrayOf(stopId.id))
        cursor.moveToNext()
        val name = cursor.getString(0)
        cursor.close()

        return name
    }

    fun getStopCode(stopId: AgencyAndId): String {
        val cursor = db!!.rawQuery("select stop_code from stops where stop_id = ?",
                arrayOf(stopId.id))
        cursor.moveToNext()
        val code = cursor.getString(0)
        cursor.close()

        return code
    }

    fun getStopDepartures(stopId: AgencyAndId): Map<AgencyAndId, List<Departure>> {
        val map = HashMap<AgencyAndId, ArrayList<Departure>>()
        val cursor = db!!.rawQuery("select route_id, service_id, departure_time, " +
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

        return map
    }

    fun getStopDeparturesBySegments(segments: HashSet<StopSegment>): Map<AgencyAndId, List<Departure>> {
        val wheres = segments.flatMap {
            it.plates?.map {
                "(stop_id = ${it.stop} and route_id = '${it.line}' and trip_headsign = '${it.headsign}')"
            } ?: listOf()
        }.joinToString(" or ")

        val cursor = db!!.rawQuery("select route_id, service_id, departure_time, " +
                "wheelchair_accessible, stop_sequence, trip_id, trip_headsign, route_desc " +
                "from stop_times natural join trips natural join routes where $wheres", null)

        val map = parseDeparturesCursor(cursor)
        cursor.close()
        return map
    }

    private fun parseDeparturesCursor(cursor: Cursor): Map<AgencyAndId, List<Departure>> {
        val map = HashMap<AgencyAndId, ArrayList<Departure>>()

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

        map.forEach { it.value.sortBy { it.time } }
        return map
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
        val cursor = db!!.rawQuery("select * from calendar where service_id = ?",
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

    @SuppressLint("Recycle")
    fun isEmpty(): Boolean {
        if (db == null)
            return true
        var result: Boolean
        var cursor: Cursor? = null
        try {
            cursor = db!!.rawQuery("select * from feed_info", null)
            result = !cursor.moveToNext()
        } catch (e: Exception) {
            result = true
        } finally {
            cursor?.close()
        }
        return result
    }

    fun getValidSince(): String {
        val cursor = db!!.rawQuery("select feed_start_date from feed_info", null)

        cursor.moveToNext()
        val validTill = cursor.getString(0)

        cursor.close()
        return validTill
    }

    fun getValidTill(): String {
        val cursor = db!!.rawQuery("select feed_end_date from feed_info", null)

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
        val cursor = db!!.rawQuery("select service_id from calendar where $dayColumn = 1", null)

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

    fun getPlatesForStop(stop: AgencyAndId): Set<Plate.ID> {
        val plates = HashSet<Plate.ID>()
        val cursor = db!!.rawQuery("select route_id, trip_headsign " +
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

    fun getTripGraphs(id: AgencyAndId): Array<TripGraph> {
        val graphs = arrayOf(TripGraph(), TripGraph())

        val cursor = db!!.rawQuery("select trip_id, trip_headsign, direction_id, stop_id, " +
                "stop_sequence, pickup_type, stop_name, zone_id " +
                "from stop_times natural join trips natural join stops" +
                "where route_id = ?", arrayOf(id.id))

        while (cursor.moveToNext()) {
            val trip = cursor.getString(0)
            val headsign = cursor.getString(1)
            val direction = cursor.getInt(2)
            val stopId = cursor.getInt(3)
            val sequence = cursor.getInt(4)
            val pickupType = cursor.getInt(5)
            val stopName = cursor.getString(6)
            val zone = cursor.getString(7)

            if (trip.contains('+')) {
                graphs[direction].mainTrip[stopId] = sequence
                graphs[direction].headsign = headsign
            }

            if (graphs[direction].otherTrips[trip] == null)
                graphs[direction].otherTrips[trip] = HashMap()
            graphs[direction].otherTrips[trip]?.put(sequence, Stop(stopId, null, stopName, null, null, zone[0], pickupType == 3))
        }

        cursor.close()

        graphs.forEach {
            val thisTripGraph = it
            it.otherTrips.forEach {
                val tripId = it.key
                val trip = it.value
                it.value.keys.sortedBy { it }.forEach {
                    if (thisTripGraph.tripsMetadata[tripId] == "" || thisTripGraph.tripsMetadata[tripId] == "o")
                        if (thisTripGraph.mainTrip[trip[it]!!.id] != null) {
                            val mainLayer = thisTripGraph.mainTrip[trip[it]!!.id]!!
                            if (it == 0 || thisTripGraph.tripsMetadata[tripId]!![0] == 'o') {
                                thisTripGraph.tripsMetadata[tripId] = "o|$mainLayer|$it"
                            } else {
                                val startingLayer = mainLayer - it + 1
                                thisTripGraph.tripsMetadata[tripId] = "i|$startingLayer|$it"
                            }
                        }
                }
            }
        }

        return graphs
    }

    class TripGraph {
        var headsign = ""
        val mainTrip = HashMap<Int, Int>()
        val otherTrips = HashMap<String, HashMap<Int, Stop>>()
        val tripsMetadata = HashMap<String, String>()
    }
}

