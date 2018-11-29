package ml.adamsprogs.bimba.models

import android.annotation.SuppressLint
import android.content.Context
import android.database.*
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.SparseArray
import android.util.SparseBooleanArray
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
            } catch (e: SQLiteException) {
                null
            }
            this.timetable = timetable
        }

        fun delete(context: Context) {
            val filesDir = context.getSecondaryExternalFilesDir()
            val dbFile = File(filesDir, "timetable.db")
            try {
                dbFile.delete()
            } catch (e: Exception) {
            }
        }
    }

    private var db: SQLiteDatabase? = null
    private var _stops: List<StopSuggestion>? = null

    fun refresh() {
    }

    fun getStopSuggestions(/*context: Context, */force: Boolean = false): List<StopSuggestion> {
        if (_stops != null && !force)
            return _stops!!

        val zones = HashMap<String, String>()

        val cursor = db!!.rawQuery("select stop_name, zone_id from stops", null)

        while (cursor.moveToNext()) {
            val name = cursor.getString(0)
            val zone = cursor.getString(1)
            zones[name] = zone
        }

        cursor.close()

        _stops = zones.map {
            /*todo
            val colour = when (zones[it.key]) {
                "A" -> "#${getColour(R.color.zoneA, context).toString(16)}"
                "B" -> "#${getColour(R.color.zoneB, context).toString(16)}"
                "C" -> "#${getColour(R.color.zoneC, context).toString(16)}"
                else -> "#000000"
            }
            */
            StopSuggestion(it.key, it.value, "#000000")
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

    fun getHeadlinesForStop(stop: String): Map<String, Set<String>> {
        val headsigns = HashMap<String, HashSet<String>>()

        var cursor = db!!.rawQuery("select stop_id, stop_code from stops where stop_name = ?",
                arrayOf(stop))
        val stopIds = ArrayList<String>()
        val stopCodes = SparseArray<String>()
        while (cursor.moveToNext()) {
            cursor.getInt(0).let {
                stopIds.add(it.toString())
                stopCodes.put(it, cursor.getString(1))
            }
        }

        cursor.close()

        val where = stopIds.joinToString(" or ", "where ") { "stop_id = ?" }

        cursor = db!!.rawQuery("select stop_id, route_id, trip_headsign " +
                "from stop_times natural join trips " +
                where, stopIds.toTypedArray())

        while (cursor.moveToNext()) {
            val stopCode = stopCodes[cursor.getInt(0)]
            val route = cursor.getString(1)
            val headsign = cursor.getString(2)
            if (stopCode !in headsigns)
                headsigns[stopCode] = HashSet()
            headsigns[stopCode]!!.add("$route → $headsign")
        }

        cursor.close()

        return headsigns

        /*
        AWF03 -> {232 → Os. Rusa}
        AWF04 -> {232 → Rondo Kaponiera}
        AWF02 -> {76 → Pl. Bernardyński, 74 → Os. Sobieskiego, 603 → Pl. Bernardyński}
        AWF01 ->{76 → Os. Dębina, 603 → Łęczyca/Dworcowa}
        AWF42 -> {29 → Pl. Wiosny Ludów}
        AWF41 -> {10 → Połabska, 29 → Dębiec, 15 → Budziszyńska, 10 → Dębiec, 15 → Os. Sobieskiego, 12 → Os. Sobieskiego, 6 → Junikowo, 18 → Ogrody, 2 → Ogrody}
        AWF73 -> {10 → Franowo, 29 → Franowo, 6 → Miłostowo, 5 → Stomil, 18 → Franowo, 15 → Franowo, 12 → Starołęka, 74 → Os. Orła Białego}
        */
    }

    fun getHeadlinesForStopCode(stop: String): StopSegment {
        var cursor = db!!.rawQuery("select stop_id from stops where stop_code = ?",
                arrayOf(stop))
        cursor.moveToFirst()
        val stopId = cursor.getInt(0)
        cursor.close()


        cursor = db!!.rawQuery("select route_id, trip_headsign " +
                "from stop_times natural join trips where stop_id = ? ",
                arrayOf(stopId.toString()))

        val plates = HashSet<Plate.ID>()

        while (cursor.moveToNext()) {
            val route = cursor.getString(0)
            val headsign = cursor.getString(1)
            plates.add(Plate.ID(route, stop, headsign))
        }
        cursor.close()
        return StopSegment(stop, plates)
    }

    fun getStopName(stopCode: String): String {
        val cursor = db!!.rawQuery("select stop_name from stops where stop_code = ?",
                arrayOf(stopCode))
        cursor.moveToNext()
        val name = cursor.getString(0)
        cursor.close()

        return name
    }

    fun getStopId(stopCode: String): String {
        val cursor = db!!.rawQuery("select stop_id from stops where stop_code = ?",
                arrayOf(stopCode))
        cursor.moveToNext()
        val id = cursor.getString(0)
        cursor.close()

        return id
    }

    fun getStopCode(stopId: String): String {
        val cursor = db!!.rawQuery("select stop_code from stops where stop_id = ?",
                arrayOf(stopId))
        cursor.moveToNext()
        val code = cursor.getString(0)
        cursor.close()

        return code
    }

    fun getStopDepartures(stopCode: String): Map<String, List<Departure>> {
        val stopID = getStopId(stopCode)
        val map = HashMap<String, ArrayList<Departure>>()
        val cursor = db!!.rawQuery("select route_id, service_id, departure_time, " +
                "wheelchair_accessible, stop_sequence, trip_id, trip_headsign, route_desc " +
                "from stop_times natural join trips natural join routes where stop_id = ?",
                arrayOf(stopID))

        while (cursor.moveToNext()) {
            val line = cursor.getString(0)
            val service = cursor.getInt(1).toString()
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

    fun getStopDeparturesBySegments(segments: Set<StopSegment>): Map<String, List<Departure>> {
        val stopCodes = HashMap<String, Int>()
        var cursor = db!!.rawQuery("select stop_id, stop_code from stops", emptyArray())
        while (cursor.moveToNext()) {
            stopCodes[cursor.getString(1)] = cursor.getInt(0)
        }
        cursor.close()

        val wheres = segments.flatMap {
            it.plates?.map { plate ->
                "(stop_id = ${stopCodes[plate.stop]} and route_id = '${plate.line}' and trip_headsign = '${plate.headsign}')"
            } ?: listOf("stop_id = ${stopCodes[it.stop]}")
        }.joinToString(" or ")

        cursor = db!!.rawQuery("select route_id, service_id, departure_time, " +
                "wheelchair_accessible, stop_sequence, trip_id, trip_headsign, route_desc " +
                "from stop_times natural join trips natural join routes where $wheres", null)

        val map = parseDeparturesCursor(cursor)
        cursor.close()
        return map
    }

    private fun parseDeparturesCursor(cursor: Cursor): Map<String, List<Departure>> {
        val map = HashMap<String, ArrayList<Departure>>()

        while (cursor.moveToNext()) {
            val line = cursor.getString(0)
            val service = cursor.getInt(1).toString()
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

    private fun calendarToMode(serviceId: String): List<Int> {
        val days = ArrayList<Int>()
        val cursor = db!!.rawQuery("select * from calendar where service_id = ?",
                arrayOf(serviceId))

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
                    explanations.add(routeModifications[it.id]!!)
            } else {
                explanations.add(routeModifications[it.id]!!)
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
                        modifications.add(Trip.ID.Modification(id, IntRange(start.toInt(), end.toInt())))
                    } catch (e: Exception) {
                        modifications.add(Trip.ID.Modification(it, null))
                    }
                }
            }
            return Trip.ID(rawId, rawId.split("^")[0], modifications, isMain)
        } else
            return Trip.ID(rawId, rawId, HashSet(), false)
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

    fun getServiceForToday(): String? {
        val today = JCalendar.getInstance()
        return getServiceFor(today)
    }

    fun getServiceForTomorrow(): String? {
        val tomorrow = JCalendar.getInstance()
        tomorrow.add(JCalendar.DAY_OF_MONTH, 1)
        return getServiceFor(tomorrow)
    }

    private fun getServiceFor(day: JCalendar): String? {
        val dayColumn = arrayOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")[((day.get(JCalendar.DAY_OF_WEEK) + 5) % 7)]
        val cursor = db!!.rawQuery("select service_id from calendar where $dayColumn = 1 and start_date < ? and ? < end_date", arrayOf(day.toIsoDate(), day.toIsoDate()))

        cursor.moveToFirst()
        return try {
            cursor.getInt(0).let {
                cursor.close()
                it.toString()
            }
        } catch (e: CursorIndexOutOfBoundsException) {
            cursor.close()
            null
        }
    }

    private fun getPlatesForStop(stop: String): Set<Plate.ID> {

        val plates = HashSet<Plate.ID>()
        val cursor = db!!.rawQuery("select route_id, trip_headsign " +
                "from stop_times natural join trips natural join stops where stop_code = ? " +
                "group by route_id, trip_headsign", arrayOf(stop))

        while (cursor.moveToNext()) {
            val routeId = cursor.getString(0)
            val headsign = cursor.getString(1)
            plates.add(Plate.ID(routeId, stop, headsign))
        }

        cursor.close()
        return plates
    }

    fun getTripGraphs(id: String): Array<TripGraph> {
        val graphs = arrayOf(TripGraph(), TripGraph())

        val cursor = db!!.rawQuery("select trip_id, trip_headsign, direction_id, stop_id, " +
                "stop_sequence, pickup_type, stop_name, zone_id " +
                "from stop_times natural join trips natural join stops" +
                "where route_id = ?", arrayOf(id))

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

    fun getServiceFirstDay(service: String): Int {
        val cursor = db!!.rawQuery("select * from calendar where service_id = ?", arrayOf(service))
        cursor.moveToFirst()
        var i = 1
        while ((cursor.getString(i) == "0") and (i < 8)) i++
        cursor.close()
        return i
    }

    fun getServiceDescription(service: String, context: Context): String {
        val dayNames = SparseArray<String>()
        dayNames.put(1, context.getString(R.string.Mon))
        dayNames.put(2, context.getString(R.string.Tue))
        dayNames.put(3, context.getString(R.string.Wed))
        dayNames.put(4, context.getString(R.string.Thu))
        dayNames.put(5, context.getString(R.string.Fri))
        dayNames.put(6, context.getString(R.string.Sat))
        dayNames.put(7, context.getString(R.string.Sun))

        val cursor = db!!.rawQuery("select * from calendar where service_id = ?", arrayOf(service))
        cursor.moveToFirst()
        val days = SparseBooleanArray()
        for (i in 1..7) {
            days.append(i, cursor.getString(i) == "1")
        }
        days.append(8, false)
        val description = ArrayList<String>()
        var start = 0

        for (i in 1..8) {
            if (!days[i] and (start > 0)) {
                when {
                    i - start == 1 -> description.add(dayNames[start])
                    i - start == 2 -> description.add("${dayNames[start]}, ${dayNames[start + 1]}")
                    i - start > 2 -> description.add("${dayNames[start]}–${dayNames[i - 1]}")
                }
                start = 0
            }
            if (days[i] and (start == 0))
                start = i
        }

        val startDate = calendarFromIsoD(cursor.getString(8)).toNiceString(context)
        val endDate = calendarFromIsoD(cursor.getString(9)).toNiceString(context)

        cursor.close()

        return "${description.joinToString { it }} ($startDate–$endDate)"
    }

    fun getTripFrom(stopCode: String, datetime: JCalendar): Set<StopTimeSequence> {
        val stopID = getStopId(stopCode)
        val departureTime = "${datetime.get(JCalendar.HOUR_OF_DAY)}:${datetime.get(JCalendar.MINUTE)}:${datetime.get(JCalendar.SECOND)}"
        val serviceID = getServiceFor(datetime) ?: throw DateOutsideTimetable()
        val cursor = db!!.rawQuery("select route_id, departure_time, trip_id, stop_sequence" +
                "from stop_times natural join trips" +
                "where stop_id = ? and departure_time > ? and service_id = ?;", arrayOf(stopID.toString(), departureTime, serviceID))
        val res = HashSet<StopTimeSequence>()
        while (cursor.moveToNext()) {
            val routeID = cursor.getString(0)
            val tripID = cursor.getString(2)
            val stopSequence = cursor.getInt(3).toString()
            val sequence = ArrayList<Pair<JCalendar, Stop>>()

            val tripCursor = db!!.rawQuery("select pickup_type, stop_id, departure_time, stop_code, stop_name, stop_lat, stop_lon, zone_id" +
                    "from stop_times natural join stops" +
                    "where trip_id = ? and stop_sequence >= ?;", arrayOf(tripID, stopSequence))
            while (tripCursor.moveToNext()) {
                val pickupType = cursor.getInt(0)
                val tripStopID = cursor.getInt(1)
                val time = tripCursor.getString(2)
                val tripStopCode = cursor.getString(3)
                val stopName = cursor.getString(4)
                val latitude = cursor.getFloat(5)
                val longitude = cursor.getFloat(6)
                val zone = cursor.getString(7)

                sequence.add(Pair(
                        timeToCalendar(time, datetime),
                        Stop(tripStopID, tripStopCode, stopName, latitude, longitude, zone[0], pickupType == 3)
                ))
            }
            tripCursor.close()
            res.add(StopTimeSequence(routeID, tripID, sequence))
        }
        cursor.close()
        return res
    }

    private fun timeToCalendar(time: String, calendar: JCalendar = JCalendar.getInstance()): JCalendar {
        val t = time.split(":").map { it.toInt() }
        calendar.set(JCalendar.HOUR_OF_DAY, t[0])
        calendar.set(JCalendar.MINUTE, t[1])
        calendar.set(JCalendar.SECOND, t[2])
        return calendar
    }

    class TripGraph {
        var headsign = ""
        val mainTrip = HashMap<Int, Int>()
        val otherTrips = HashMap<String, HashMap<Int, Stop>>()
        val tripsMetadata = HashMap<String, String>()
    }

    class DateOutsideTimetable : Exception()
}

