package ml.adamsprogs.bimba.models

import android.content.Context
import android.database.CursorIndexOutOfBoundsException
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
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

        cacheManager.recreate(getStopDeparturesByPlates(cacheManager.keys().toSet() as HashSet<Plate>)) //todo optimise
    }

    fun getStops(): ArrayList<StopSuggestion> {
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

    fun getStopDepartures(stopId: String, lineId: String? = null): HashMap<String, ArrayList<Departure>> {
        val plates = HashSet<Plate>()
        val toGet = HashSet<Plate>()

        if (lineId == null) {
            getLinesForStop(stopId)
                    .map { Plate(it, stopId, null) }
                    .forEach {
                        if (cacheManager.has(it))
                            plates.add(cacheManager.get(it)!!)
                        else {
                            toGet.add(it)
                        }
                    }
        } else {
            val plate = Plate(lineId, stopId, null)
            if (cacheManager.has(plate))
                plates.add(cacheManager.get(plate)!!)
            else {
                toGet.add(plate)
            }
        }

        getStopDeparturesByPlates(toGet).forEach {cacheManager.push(it); plates.add(it)}

        return Plate.join(plates)
    }

    fun getStopDepartures(plates: HashSet<Plate>): HashMap<String, ArrayList<Departure>> {
        val result = HashSet<Plate>()
        val toGet = HashSet<Plate>()

        for (plate in plates) {
            if (cacheManager.has(plate))
                result.add(cacheManager.get(plate)!!)
            else
                toGet.add(plate)
        }

        getStopDeparturesByPlates(toGet).forEach {cacheManager.push(it); result.add(it)}

        return Plate.join(result)
    }

    private fun getStopDeparturesByPlates(plates: HashSet<Plate>): HashSet<Plate> {
        val result = HashSet<Plate>()
        plates.mapTo(result) { Plate(it.line, it.stop, getStopDeparturesByLine(it.line, it.stop)) }  //fixme to one query
        return result
    }

    private fun getLinesForStop(stopId: String): HashSet<String> {
        val cursor = db.rawQuery("select line_id from timetables where stop_id=?;", listOf(stopId).toTypedArray())
        val lines = HashSet<String>()
        while (cursor.moveToNext())
            lines.add(cursor.getString(0))
        cursor.close()
        return lines
    }

    private fun getStopDeparturesByLine(lineId: String, stopId: String): HashMap<String, HashSet<Departure>>? {
        val cursor = db.rawQuery("select lines.number, mode, substr('0'||hour, -2) || ':' || " +
                "substr('0'||minute, -2) as time, lowFloor, modification, headsign from departures join " +
                "timetables on(timetable_id = timetables.id) join lines on(line_id = lines.id) where " +
                "stop_id = ? and line_id = ? order by mode, time;", listOf(stopId, lineId).toTypedArray())
        val departures = HashMap<String, HashSet<Departure>>()
        departures.put(MODE_WORKDAYS, HashSet())
        departures.put(MODE_SATURDAYS, HashSet())
        departures.put(MODE_SUNDAYS, HashSet())
        while (cursor.moveToNext()) { //fixme first moveToNext takes 2s, subsequent ones are instant
            departures[cursor.getString(1)]?.add(Departure(cursor.getString(0),
                    cursor.getString(1), cursor.getString(2), cursor.getInt(3) == 1,
                    cursor.getString(4), cursor.getString(5)))
        }
        cursor.close()
        return departures
    }

    fun getLines(stopId: String): ArrayList<String> {
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

