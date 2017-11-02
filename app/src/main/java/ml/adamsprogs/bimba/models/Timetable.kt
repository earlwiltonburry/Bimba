package ml.adamsprogs.bimba.models

import android.content.Context
import android.database.CursorIndexOutOfBoundsException
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.util.Log
import ml.adamsprogs.bimba.CacheManager
import java.io.File


class Timetable private constructor() {
    companion object {
        val version = 1
        val MODE_WORKDAYS = "workdays"
        val MODE_SATURDAYS = "saturdays"
        val MODE_SUNDAYS = "sundays"
        private var timetable: Timetable? = null

        fun getTimetable(context: Context? = null, force: Boolean = false): Timetable {
            if (timetable == null || force)
                if (context != null) {
                    val db: SQLiteDatabase?
                    try {
                        db = SQLiteDatabase.openDatabase(File(context.filesDir, "timetable.db").path,
                                null, SQLiteDatabase.OPEN_READONLY)
                    } catch (e: NoSuchFileException) {
                        throw SQLiteCantOpenDatabaseException("no such file")
                    } catch (e: SQLiteCantOpenDatabaseException) {
                        throw SQLiteCantOpenDatabaseException("cannot open db")
                    } catch (e: SQLiteDatabaseCorruptException) {
                        throw SQLiteCantOpenDatabaseException("db corrupt")
                    }
                    timetable = Timetable()
                    timetable!!.db = db
                    timetable!!.cacheManager = CacheManager.getCacheManager(context)
                    return timetable as Timetable
                } else
                    throw IllegalArgumentException("new timetable requested and no context given")
            else
                return timetable as Timetable
        }
    }

    lateinit var db: SQLiteDatabase
    private lateinit var cacheManager: CacheManager
    private var _stops: ArrayList<StopSuggestion>? = null

    fun refresh(context: Context) {
        val db: SQLiteDatabase?
        try {
            db = SQLiteDatabase.openDatabase(File(context.filesDir, "timetable.db").path,
                    null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: NoSuchFileException) {
            throw SQLiteCantOpenDatabaseException("no such file")
        } catch (e: SQLiteCantOpenDatabaseException) {
            throw SQLiteCantOpenDatabaseException("cannot open db")
        } catch (e: SQLiteDatabaseCorruptException) {
            throw SQLiteCantOpenDatabaseException("db corrupt")
        }
        this.db = db

        Log.i("SQL", "from refresh")
        cacheManager.recreate(getStopDeparturesByPlates(cacheManager.keys().toSet()))

        //todo recreate stops
    }

    fun getStops(): List<StopSuggestion> {
        if (_stops != null)
            return _stops!!

        val stops = ArrayList<StopSuggestion>()
        val cursor = db.rawQuery("select name ||char(10)|| headsigns as suggestion, id, stops.symbol || number as stopSymbol from stops" +
                " join nodes on(stops.symbol = nodes.symbol) order by name, id;", null)
        while (cursor.moveToNext())
            stops.add(StopSuggestion(cursor.getString(0), cursor.getString(1), cursor.getString(2)))
        cursor?.close()
        _stops = stops
        return stops
    }

    fun getStopName(stopId: String): String {
        val cursor = db.rawQuery("select name from nodes join stops on(stops.symbol = nodes.symbol) where id = ?;",
                listOf(stopId).toTypedArray())
        val name: String
        cursor.moveToNext()
        name = cursor.getString(0)
        cursor.close()
        return name
    }

    fun getStopSymbol(stopId: String): String {
        val cursor = db.rawQuery("select symbol||number from stops where id = ?", listOf(stopId).toTypedArray())
        val symbol: String
        cursor.moveToNext()
        symbol = cursor.getString(0)
        cursor.close()
        return symbol
    }

    fun getLineNumber(lineId: String): String {
        val cursor = db.rawQuery("select number from lines where id = ?", listOf(lineId).toTypedArray())
        val number: String
        cursor.moveToNext()
        number = cursor.getString(0)
        cursor.close()
        return number
    }

    fun getStopDepartures(stopId: String): Map<String, List<Departure>> {
        val plates = HashSet<Plate>()
        val toGet = HashSet<Plate>()

        getLinesForStop(stopId)
                .map { Plate(it, stopId, null) }
                .forEach {
                    if (cacheManager.has(it))
                        plates.add(cacheManager.get(it)!!)
                    else {
                        toGet.add(it)
                    }
                }

        Log.i("SQL", "from (stop)")
        getStopDeparturesByPlates(toGet).forEach { cacheManager.push(it); plates.add(it) }

        return Plate.join(plates)
    }

    fun getStopDepartures(stopId: String, lineId: String): Map<String, List<Departure>> {
        val plates = HashSet<Plate>()
        val toGet = HashSet<Plate>()

        val plate = Plate(lineId, stopId, null)
        if (cacheManager.has(plate))
            plates.add(cacheManager.get(plate)!!)
        else {
            toGet.add(plate)
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
        Log.i("SQL", "from (plates)")

        getStopDeparturesByPlates(toGet).forEach { cacheManager.push(it); result.add(it) }

        return Plate.join(result)
    }

    private fun getStopDeparturesByPlates(plates: Set<Plate>): Set<Plate> {
        if (plates.isEmpty())
            return emptySet()
        val result = HashMap<String, Plate>()

        val condition = plates.joinToString(" or ") { "(stop_id = '${it.stop}' and line_id = '${it.line}')" }

        val sql = "select " +
                "lines.number, mode, substr('0'||hour, -2) || ':' || " +
                "substr('0'||minute, -2) as time, lowFloor, modification, headsign, stop_id, line_id " +
                "from " +
                "departures join timetables on(timetable_id = timetables.id) join lines on(line_id = lines.id) " +
                "where " +
                condition +
                "order by " +
                "mode, time;"
        Log.i("SQL", sql)
        val cursor = db.rawQuery(sql, null)



        while (cursor.moveToNext()) {
            val lineId = cursor.getString(7)
            val stopId = cursor.getString(6)

            if (!result.containsKey("$lineId@$stopId")) {
                result["$lineId@$stopId"] = Plate(lineId, stopId, HashMap())
                result["$lineId@$stopId"]?.departures?.put(MODE_WORKDAYS, HashSet())
                result["$lineId@$stopId"]?.departures?.put(MODE_SATURDAYS, HashSet())
                result["$lineId@$stopId"]?.departures?.put(MODE_SUNDAYS, HashSet())
            }

            result["$lineId@$stopId"]?.departures?.get(cursor.getString(1))?.add(
                    Departure(cursor.getString(0), cursor.getString(1),
                            cursor.getString(2), cursor.getInt(3) == 1,
                            cursor.getString(4), cursor.getString(5)))
        }
        cursor.close()
        return result.values.toSet()
    }

    private fun getLinesForStop(stopId: String): Set<String> {
        val cursor = db.rawQuery("select line_id from timetables where stop_id=?;", listOf(stopId).toTypedArray())
        val lines = HashSet<String>()
        while (cursor.moveToNext())
            lines.add(cursor.getString(0))
        cursor.close()
        return lines
    }

    fun getLines(stopId: String): List<String> {
        val cursor = db.rawQuery(" select distinct line_id from timetables join " +
                "stops on(stop_id = stops.id) where stops.id = ?;",
                listOf(stopId).toTypedArray())
        val lines = ArrayList<String>()
        while (cursor.moveToNext()) {
            lines.add(cursor.getString(0))
        }
        cursor.close()
        return lines
    }

    fun getFavouriteElement(plate: Plate): String {
        val cursor = db.rawQuery("select name || ' (' || stops.symbol || stops.number || '): \n' " +
                "|| lines.number || ' → ' || headsign from timetables join stops on (stops.id = stop_id) " +
                "join lines on(lines.id = line_id) join nodes on(nodes.symbol = stops.symbol) where " +
                "stop_id = ? and line_id = ?",
                listOf(plate.stop, plate.line).toTypedArray())
        val element: String
        cursor.moveToNext()
        element = cursor.getString(0)
        cursor.close()
        return element
    }

    fun isEmpty(): Boolean {
        val cursor = db.rawQuery("select * from metadata;", null)
        try {
            cursor.moveToNext()
            cursor.getString(0)
            cursor.close()
        } catch (e: CursorIndexOutOfBoundsException) {
            return true
        }
        return false
    }

    fun getValidity(): String {
        val cursor = db.rawQuery("select value from metadata where key = 'validFrom'", null)
        cursor.moveToNext()
        val validity = cursor.getString(0)
        cursor.close()
        return "%s-%s-%s".format(validity.substring(0..3), validity.substring(4..5), validity.substring(6..7))
    }
}

