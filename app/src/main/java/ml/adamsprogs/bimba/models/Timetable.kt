package ml.adamsprogs.bimba.models

import android.content.Context
import android.database.CursorIndexOutOfBoundsException
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
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
                    } catch(e: NoSuchFileException) {
                        throw SQLiteCantOpenDatabaseException("no such file")
                    } catch(e: SQLiteCantOpenDatabaseException) {
                        throw SQLiteCantOpenDatabaseException("cannot open db")
                    } catch(e: SQLiteDatabaseCorruptException) {
                        throw SQLiteCantOpenDatabaseException("db corrupt")
                    }
                    timetable = Timetable()
                    timetable!!.db = db
                    return timetable as Timetable
                } else
                    throw IllegalArgumentException("new timetable requested and no context given")
            else
                return timetable as Timetable
        }
    }

    lateinit var db: SQLiteDatabase
    private var _stops: ArrayList<StopSuggestion>? = null
    private val _stopDepartures = HashMap<String, HashMap<String, ArrayList<Departure>>>()
    private val _stopDeparturesCount = HashMap<String, Int>()

    fun refresh(context: Context) {
        val db: SQLiteDatabase?
        try {
            db = SQLiteDatabase.openDatabase(File(context.filesDir, "timetable.db").path,
                    null, SQLiteDatabase.OPEN_READONLY)
        } catch(e: NoSuchFileException) {
            throw SQLiteCantOpenDatabaseException("no such file")
        } catch(e: SQLiteCantOpenDatabaseException) {
            throw SQLiteCantOpenDatabaseException("cannot open db")
        } catch(e: SQLiteDatabaseCorruptException) {
            throw SQLiteCantOpenDatabaseException("db corrupt")
        }
        this.db = db

        for ((k, _) in _stopDepartures)
            _stopDepartures.remove(k)
        //todo recreate cache
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

    fun getStopDepartures(stopId: String, lineId: String? = null, tomorrow: Boolean = false): HashMap<String, ArrayList<Departure>> {
        val andLine: String = if (lineId == null)
            ""
        else
            "and line_id = '$lineId'"

        if (lineId == null && _stopDepartures.contains(stopId)) {
            _stopDeparturesCount[stopId] = _stopDeparturesCount[stopId]!! + 1
            return _stopDepartures[stopId]!!
        }
        _stopDeparturesCount[stopId] = _stopDeparturesCount[stopId]?:0 + 1
        val cursor = db.rawQuery("select lines.number, mode, substr('0'||hour, -2) || ':' || " +
                "substr('0'||minute, -2) as time, lowFloor, modification, headsign from departures join " +
                "timetables on(timetable_id = timetables.id) join lines on(line_id = lines.id) where " +
                "stop_id = ? $andLine order by mode, time;", listOf(stopId).toTypedArray())
        val departures = HashMap<String, ArrayList<Departure>>()
        departures.put(MODE_WORKDAYS, ArrayList())
        departures.put(MODE_SATURDAYS, ArrayList())
        departures.put(MODE_SUNDAYS, ArrayList())
        while (cursor.moveToNext()) {
            departures[cursor.getString(1)]?.add(Departure(cursor.getString(0),
                    cursor.getString(1), cursor.getString(2), cursor.getInt(3) == 1,
                    cursor.getString(4), cursor.getString(5), tomorrow = tomorrow))
        }
        cursor.close()
        if (lineId == null) {
            if (_stopDepartures.size < 10)
                _stopDepartures[stopId] = departures
            else {
                for ((key, value) in _stopDeparturesCount) {
                    if (value < _stopDeparturesCount[stopId]!!) {
                        _stopDepartures.remove(key)
                        _stopDepartures[stopId] = departures
                        break
                    }
                }
            }
        }
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

    fun getFavouriteElement(stop: String, line: String): String {
        val cursor = db.rawQuery("select name || ' (' || stops.symbol || stops.number || '): \n' " +
                "|| lines.number || ' → ' || headsign from timetables join stops on (stops.id = stop_id) " +
                "join lines on(lines.id = line_id) join nodes on(nodes.symbol = stops.symbol) where " +
                "stop_id = ? and line_id = ?",
                listOf(stop, line).toTypedArray())
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
        } catch(e: CursorIndexOutOfBoundsException) {
            return true
        }
        return false
    }
}
