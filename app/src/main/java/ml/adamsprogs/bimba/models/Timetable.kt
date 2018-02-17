package ml.adamsprogs.bimba.models

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import ml.adamsprogs.bimba.CacheManager
import ml.adamsprogs.bimba.gtfs.AgencyAndId
import ml.adamsprogs.bimba.gtfs.Route
import ml.adamsprogs.bimba.gtfs.Trip
import ml.adamsprogs.bimba.gtfs.Calendar
import ml.adamsprogs.bimba.secondsAfterMidnight
import ml.adamsprogs.bimba.toPascalCase
import java.io.File
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import java.util.Calendar as JCalendar

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

        private fun read(context: Context): SQLiteDatabase {
            return SQLiteDatabase.openDatabase(File(context.filesDir, "timetable.db").path,
                    null, SQLiteDatabase.OPEN_READONLY)
        }
    }

    lateinit var store: SQLiteDatabase
    private lateinit var cacheManager: CacheManager
    private var _stops: ArrayList<StopSuggestion>? = null

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

        val cursor = store.rawQuery("select route_short_name, trip_headsign, stop_id " +
                "from stop_times join trips using trip_id join routes using route_id " +
                "where drop_off_type = 0 or drop_off_type = 3", null)

        while(cursor.moveToNext()) {
            val line = cursor.getString(0)
            val headsign = cursor.getString(1).toPascalCase()
            val stopId = AgencyAndId(cursor.getString(2))
            if (map[stopId] == null)
                map[stopId] = HashSet()
            map[stopId]!!.add("$line → $headsign")
        }

        cursor.close()

        val stops = map.entries.map { StopSuggestion(it.value, it.key) }.toSet()

        _stops = stops.sortedBy { this.getStopCode(it.id) } as ArrayList<StopSuggestion>
        return _stops!!
    }

    fun getStopName(stopId: AgencyAndId): String {
        val cursor = store.rawQuery("select stop_name from stops where stop_id = ?", arrayOf(stopId.id))
        cursor.moveToNext()
        val name = cursor.getString(0)
        cursor.close()
        return name
    }

    fun getStopCode(stopId: AgencyAndId): String {
        val cursor = store.rawQuery("select stop_code from stops where stop_id = ?", arrayOf(stopId.id))
        cursor.moveToNext()
        val name = cursor.getString(0)
        cursor.close()
        return name
    }

    fun getLineNumber(lineId: AgencyAndId): String {
        val cursor = store.rawQuery("select route_short_name from routes where route_id = ?", arrayOf(lineId.id))
        cursor.moveToNext()
        val name = cursor.getString(0)
        cursor.close()
        return name
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
        val cursor = store.rawQuery("select departure_time, trip_headsign, route_id, " +
                "service_id, wheelchair_accessible, stop_sequence, trip_id, direction_id, shape_id from stop_times join trips using trip_id " +
                "where stop_id = ? and route_id = ? and trip_headsign = ?",
                arrayOf(plate.id.stop.toString(), plate.id.line.toString(), plate.id.headsign)) // fixme headsign toLower // test if needed
        while (cursor.moveToNext()) {
            val cal = JCalendar.getInstance()
            val (h, m, s) = cursor.getString(0).split(":")
            cal.set(JCalendar.HOUR_OF_DAY, h.toInt())
            cal.set(JCalendar.MINUTE, m.toInt())
            cal.set(JCalendar.SECOND, s.toInt())
            val time = cal.secondsAfterMidnight()
            val serviceId = AgencyAndId(cursor.getString(3))
            val mode = calendarToMode(serviceId)
            val lowFloor = cursor.getInt(4) == 1
            val mod = explainModification(Trip(AgencyAndId(cursor.getString(2)),
                    serviceId, createTripId(cursor.getString(6)),
                    cursor.getString(1), cursor.getInt(7),
                    AgencyAndId(cursor.getString(8))), cursor.getInt(5))

            val dep = Departure(plate.id.line, mode, time, lowFloor, mod, plate.id.headsign)
            if (resultPlate.departures!![serviceId] == null)
                resultPlate.departures[serviceId] = HashSet()
            resultPlate.departures[serviceId]!!.add(dep)
        }
        cursor.close()
        return resultPlate
    }

    fun calendarToMode(serviceId: AgencyAndId): List<Int> {
        val cursor = store.rawQuery("select monday, tuesday, wednesday, thursday, friday, " +
                "saturday, sunday from calendar where service_id = ?", arrayOf(serviceId.id))
        cursor.moveToNext()
        val calendar = Calendar(cursor.getInt(0), cursor.getInt(1),
                cursor.getInt(2), cursor.getInt(3), cursor.getInt(4),
                cursor.getInt(5), cursor.getInt(6))
        val days = ArrayList<Int>()
        if (calendar.monday == 1) days.add(0)
        if (calendar.tuesday == 1) days.add(1)
        if (calendar.wednesday == 1) days.add(2)
        if (calendar.thursday == 1) days.add(3)
        if (calendar.friday == 1) days.add(4)
        if (calendar.saturday == 1) days.add(5)
        if (calendar.sunday == 1) days.add(6)
        cursor.close()
        return days
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
        val cursor = store.rawQuery("select route_id, agency_id, route_short_name, " +
                "route_long_name route_desc, route_type, route_color, route_text_color " +
                "from routes join trips using route_id where trip_id = ?", arrayOf(trip.rawId))
        cursor.moveToNext()
        val id = cursor.getString(0)
        val agency = cursor.getString(1)
        val shortName = cursor.getString(2)
        val longName = cursor.getString(3)
        val desc = cursor.getString(4)
        val type = cursor.getInt(5)
        val colour = cursor.getInt(6)
        val textColour = cursor.getInt(7)
        val (to, from) = desc.split("|")
        val toSplit = to.split("^")
        val fromSplit = from.split("^")
        val description = "${toSplit[0]}|${fromSplit[0]}"
        val modifications = HashMap<String, String>()
        toSplit.slice(1 until toSplit.size).forEach {
            val (k, v) = it.split(" - ")
            modifications[k] = v
        }
        val route = Route(AgencyAndId(id), AgencyAndId(agency), shortName, longName, description,
                type, colour, textColour, modifications)
        cursor.close()
        return route
    }

//    fun getLinesForStop(stopId: AgencyAndId): Set<AgencyAndId> {
//        val lines = HashSet<AgencyAndId>()
//        store.allStopTimes.filter { it.stop.id == stopId }.forEach { lines.add(it.trip.route.id) }
//        return lines
//    }

    fun getTripsForStop(stopId: AgencyAndId): Set<Trip> {
        val trips = HashSet<Trip>()
        val cursor = store.rawQuery("select route_id, service_id, trip_id, trip_headsign, " +
                "direction_id, shape_id from stop_times join trips using trip_id " +
                "where stop_id = ?", arrayOf(stopId.id))
        while (cursor.moveToNext()) {
            val routeId = AgencyAndId(cursor.getString(0))
            val serviceId = AgencyAndId(cursor.getString(1))
            val tripId = cursor.getString(2)
            val headsign = cursor.getString(3)
            val direction = cursor.getInt(4)
            val shape = AgencyAndId(cursor.getString(5))
            val trip = Trip(routeId, serviceId, createTripId(tripId), headsign, direction, shape)
            trips.add(trip)
        }
        cursor.close()
        return trips
    }

    private fun createTripId(rawId: String): Trip.ID {
        var modification = rawId.split("^")[1]
        modification = modification.subSequence(0, modification.length - 1) as String
        val isMain = modification[modification.length - 1] == '+'
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
    }

    fun isEmpty(): Boolean {
        val cursor = store.rawQuery("select * from feed_info", null)
        return try {
            cursor.moveToNext()
            cursor.close()
            true
        } catch (e: Exception) {
            cursor.close()
            false
        }
    }

    fun getValidSince(): String {
        val cursor = store.rawQuery("select feed_start_date from feed_info", null)
        cursor.moveToNext()
        val validSince = cursor.getString(0)
        cursor.close()
        return validSince
    }

    fun getValidTill(): String {
        val cursor = store.rawQuery("select feed_end_date from feed_info", null)
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

    private fun getServiceFor(day: Int): AgencyAndId {
        val dow = arrayOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
        val cursor = store.rawQuery("select service_id from calendar where ? = 1", arrayOf(dow[day - 1]))
        cursor.moveToNext()
        val service = AgencyAndId(cursor.getString(0))
        cursor.close()
        return service
    }

    fun getLineForNumber(number: String): AgencyAndId {
        val cursor = store.rawQuery("select route_id from routes where route_short_name = ?", arrayOf(number))
        cursor.moveToNext()
        val id = AgencyAndId(cursor.getString(0))
        cursor.close()
        return id
    }

    fun getPlatesForStop(stop: AgencyAndId): Set<Plate.ID> {
        val plates = HashSet<Plate.ID>()
        val cursor = store.rawQuery("select trip_headsign, route_id from stop_times join trips using trip_id where stop_id = ?", arrayOf(stop.id))
        while (cursor.moveToNext()) {
            plates.add(Plate.ID(AgencyAndId(cursor.getString(1)), stop, cursor.getString(0)))
        }
        cursor.close()
        return plates
    }
}

