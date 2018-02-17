package ml.adamsprogs.bimba.datasources

import android.content.ContentValues
import android.content.Context
import io.requery.android.database.sqlite.SQLiteDatabase
import java.io.File
import ir.mahdi.mzip.zip.ZipArchive
import org.supercsv.cellprocessor.ift.CellProcessor
import org.supercsv.prefs.CsvPreference
import org.supercsv.io.CsvMapReader
import org.supercsv.io.ICsvMapReader
import java.io.FileReader
import java.util.*

//todo faster csv: http://simpleflatmapper.org/0101-getting-started-csv.html
//todo faster csv: https://github.com/uniVocity/univocity-parsers
class TimetableConverter(from: File, context: Context) {
    init {
       // println(Calendar.getInstance())
        val target = File(context.filesDir, "gtfs_files")
        target.mkdir()
        ZipArchive.unzip(from.path, target.path, "")
        /*println("tables…")
        createTables()
        println(" done")
        println("agency…")
        insertAgency(context)
        println(" done")
        println("calendar…")
        insertCalendar(context)
        println(" done")
        println("dates…")
        insertCalendarDates(context)
        println(" done")
        println("feed…")
        insertFeedInfo(context)
        println(" done")
        println("routes…")
        insertRoutes(context)
        println(" done")
        println("shapes…")
        insertShapes(context)
        println(" done")
        println("stops…")
        insertStops(context)
        println(" done")
        println("times…")
        insertStopTimes(context)
        println(" done")
        println("trips…")
        insertTrips(context)
        println(" done")
        target.deleteRecursively()
        println(Calendar.getInstance())*/
    } /*

    private fun createTables() {
        db.execSQL("create table agency(" +
                "agency_id TEXT PRIMARY KEY," +
                "agency_name TEXT," +
                "agency_url TEXT," +
                "agency_timezone TEXT," +
                "agency_phone TEXT," +
                "agency_lang TEXT" +
                ")")
        db.execSQL("create table calendar(" +
                "service_id TEXT PRIMARY KEY," +
                "monday INT," +
                "tuesday INT," +
                "wednesday INT," +
                "thursday INT," +
                "friday INT," +
                "saturday INT," +
                "sunday INT," +
                "start_date TEXT," +
                "end_date TEXT" +
                ")")
        db.execSQL("create table calendar_dates(" +
                "service_id TEXT," +
                "date TEXT," +
                "exception_type INT," +
                "FOREIGN KEY(service_id) REFERENCES calendar(service_id)" +
                ")")
        db.execSQL("create table feed_info(" +
                "feed_publisher_name TEXT PRIMARY KEY," +
                "feed_publisher_url TEXT," +
                "feed_lang TEXT," +
                "feed_start_date TEXT," +
                "feed_end_date TEXT" +
                ")")
        db.execSQL("create table routes(" +
                "route_id TEXT PRIMARY KEY," +
                "agency_id TEXT," +
                "route_short_name TEXT," +
                "route_long_name TEXT," +
                "route_desc TEXT," +
                "route_type INT," +
                "route_color TEXT," +
                "route_text_color TEXT," +
                "FOREIGN KEY(agency_id) REFERENCES agency(agency_id)" +
                ")")
        db.execSQL("create table shapes(" +
                "shape_id TEXT," +
                "shape_pt_lat DOUBLE," +
                "shape_pt_lon DOUBLE," +
                "shape_pt_sequence INT" +
                ")")
        db.execSQL("create table stops(" +
                "stop_id TEXT PRIMARY KEY," +
                "stop_code TEXT," +
                "stop_name TEXT," +
                "stop_lat DOUBLE," +
                "stop_lon DOUBLE," +
                "zone_id TEXT" +
                ")")
        db.execSQL("create table stop_times(" +
                "trip_id TEXT," +
                "arrival_time TEXT," +
                "departure_time TEXT," +
                "stop_id TEXT," +
                "stop_sequence INT," +
                "stop_headsign TEXT," +
                "pickup_type INT," +
                "drop_off_type INT," +
                "FOREIGN KEY(stop_id) REFERENCES stops(stop_id)" +
                ")")
        db.execSQL("create table trips(" +
                "route_id TEXT," +
                "service_id TEXT," +
                "trip_id TEXT PRIMARY KEY," +
                "trip_headsign TEXT," +
                "direction_id INT," +
                "shape_id TEXT," +
                "wheelchair_accessible INT," +
                "FOREIGN KEY(route_id) REFERENCES routes(route_id)," +
                "FOREIGN KEY(service_id) REFERENCES calendar(service_id)," +
                "FOREIGN KEY(shape_id) REFERENCES shapes(shape_id)" +
                ")")
    }

    private fun insertAgency(context: Context) {
        val file = File(context.filesDir, "gtfs_files/agency.txt")
        var mapReader: ICsvMapReader? = null
        try {
            db.beginTransaction()
            mapReader = CsvMapReader(FileReader(file), CsvPreference.STANDARD_PREFERENCE)
            val header = mapReader.getHeader(true)

            var customerMap: Map<String, Any>? = null
            val processors = Array<CellProcessor?>(header.size, { null })
            while ({ customerMap = mapReader.read(header, processors); customerMap }() != null) {
                val values = ContentValues().apply {
                    put("agency_id", customerMap!!["agency_id"] as String)
                    put("agency_name", customerMap!!["agency_name"] as String)
                    put("agency_url", customerMap!!["agency_url"] as String)
                    put("agency_timezone", customerMap!!["agency_timezone"] as String)
                    put("agency_phone", customerMap!!["agency_phone"] as String)
                    put("agency_lang", customerMap!!["agency_lang"] as String)
                }
                db.insert("agency", null, values)
            }
            db.setTransactionSuccessful()
            db.endTransaction()
        } finally {
            if (mapReader != null) {
                mapReader.close()
            }
        }
    }

    private fun insertCalendar(context: Context) {
        val file = File(context.filesDir, "gtfs_files/calendar.txt")
        var mapReader: ICsvMapReader? = null
        try {
            db.beginTransaction()
            mapReader = CsvMapReader(FileReader(file), CsvPreference.STANDARD_PREFERENCE)
            val header = mapReader.getHeader(true)

            var customerMap: Map<String, Any>? = null
            val processors = Array<CellProcessor?>(header.size, { null })
            while ({ customerMap = mapReader.read(header, processors); customerMap }() != null) {
                val values = ContentValues().apply {
                    put("service_id", customerMap!!["service_id"] as String)
                    put("monday", customerMap!!["monday"] as String)
                    put("tuesday", customerMap!!["tuesday"] as String)
                    put("wednesday", customerMap!!["wednesday"] as String)
                    put("thursday", customerMap!!["thursday"] as String)
                    put("friday", customerMap!!["friday"] as String)
                    put("saturday", customerMap!!["saturday"] as String)
                    put("sunday", customerMap!!["sunday"] as String)
                    put("start_date", customerMap!!["start_date"] as String)
                    put("end_date", customerMap!!["end_date"] as String)
                }

                db.insert("calendar", null, values)
            }
            db.setTransactionSuccessful()
            db.endTransaction()
        } finally {
            if (mapReader != null) {
                mapReader.close()
            }
        }
    }

    private fun insertCalendarDates(context: Context) {
        val file = File(context.filesDir, "gtfs_files/calendar_dates.txt")

        var mapReader: ICsvMapReader? = null
        try {
            db.beginTransaction()
            mapReader = CsvMapReader(FileReader(file), CsvPreference.STANDARD_PREFERENCE)
            val header = mapReader.getHeader(true)

            var customerMap: Map<String, Any>? = null
            val processors = Array<CellProcessor?>(header.size, { null })
            while ({ customerMap = mapReader.read(header, processors); customerMap }() != null) {
                val values = ContentValues().apply {
                    put("service_id", customerMap!!["service_id"] as String)
                    put("date", customerMap!!["date"] as String)
                    put("exceptionType", customerMap!!["exceptionType"] as String)
                }
                db.insert("calendar_dates", null, values)
            }
            db.setTransactionSuccessful()
            db.endTransaction()
        } finally {
            if (mapReader != null) {
                mapReader.close()
            }
        }
    }

    private fun insertFeedInfo(context: Context) {
        val file = File(context.filesDir, "gtfs_files/feed_info.txt")
        var mapReader: ICsvMapReader? = null
        try {
            db.beginTransaction()
            mapReader = CsvMapReader(FileReader(file), CsvPreference.STANDARD_PREFERENCE)
            val header = mapReader.getHeader(true)

            var customerMap: Map<String, Any>? = null
            val processors = Array<CellProcessor?>(header.size, { null })
            while ({ customerMap = mapReader.read(header, processors); customerMap }() != null) {
                val values = ContentValues().apply {
                    put("feed_publisher_name", customerMap!!["feed_publisher_name"] as String)
                    put("feed_publisher_url", customerMap!!["feed_publisher_url"] as String)
                    put("feed_lang", customerMap!!["feed_lang"] as String)
                    put("feed_start_date", customerMap!!["feed_start_date"] as String)
                    put("feed_end_date", customerMap!!["feed_end_date"] as String)
                }
                db.insert("feed_info", null, values)
            }
            db.setTransactionSuccessful()
            db.endTransaction()
        } finally {
            if (mapReader != null) {
                mapReader.close()
            }
        }
    }

    private fun insertRoutes(context: Context) {
        val file = File(context.filesDir, "gtfs_files/routes.txt")
        var mapReader: ICsvMapReader? = null
        try {
            db.beginTransaction()
            mapReader = CsvMapReader(FileReader(file), CsvPreference.STANDARD_PREFERENCE)
            val header = mapReader.getHeader(true)

            var customerMap: Map<String, Any>? = null
            val processors = Array<CellProcessor?>(header.size, { null })
            while ({ customerMap = mapReader.read(header, processors); customerMap }() != null) {
                val values = ContentValues().apply {
                    put("route_id", customerMap!!["route_id"] as String)
                    put("agency_id", customerMap!!["agency_id"] as String)
                    put("route_short_name", customerMap!!["route_short_name"] as String)
                    put("route_long_name", customerMap!!["route_long_name"] as String)
                    put("route_desc", customerMap!!["route_desc"] as String)
                    put("route_type", customerMap!!["route_type"] as String)
                    put("route_color", customerMap!!["route_color"] as String)
                    put("route_text_color", customerMap!!["route_text_color"] as String)
                }
                db.insert("routes", null, values)
            }
            db.setTransactionSuccessful()
            db.endTransaction()
        } finally {
            if (mapReader != null) {
                mapReader.close()
            }
        }
    }

    private fun insertShapes(context: Context) {
        val file = File(context.filesDir, "gtfs_files/shapes.txt")
        var mapReader: ICsvMapReader? = null
        try {
            db.beginTransaction()
            mapReader = CsvMapReader(FileReader(file), CsvPreference.STANDARD_PREFERENCE)
            val header = mapReader.getHeader(true)

            var customerMap: Map<String, Any>? = null
            val processors = Array<CellProcessor?>(header.size, { null })
            while ({ customerMap = mapReader.read(header, processors); customerMap }() != null) {
                val values = ContentValues().apply {
                    put("shape_id", customerMap!!["shape_id"] as String)
                    put("shape_pt_lat", customerMap!!["shape_pt_lat"] as String)
                    put("shape_pt_lon", customerMap!!["shape_pt_lon"] as String)
                    put("shape_pt_sequence", customerMap!!["shape_pt_sequence"] as String)
                }
                db.insert("shapes", null, values)
                if (mapReader.rowNumber % 1000 == 0)
                    println(mapReader.rowNumber)
            }
            db.setTransactionSuccessful()
            db.endTransaction()
        } finally {
            if (mapReader != null) {
                mapReader.close()
            }
        }
    }

    private fun insertStops(context: Context) {
        val file = File(context.filesDir, "gtfs_files/stops.txt")
        var mapReader: ICsvMapReader? = null
        try {
            db.beginTransaction()
            mapReader = CsvMapReader(FileReader(file), CsvPreference.STANDARD_PREFERENCE)
            val header = mapReader.getHeader(true)

            var customerMap: Map<String, Any>? = null
            val processors = Array<CellProcessor?>(header.size, { null })
            while ({ customerMap = mapReader.read(header, processors); customerMap }() != null) {
                val values = ContentValues().apply {
                    put("stop_id", customerMap!!["stop_id"] as String)
                    put("stop_code", customerMap!!["stop_code"] as String)
                    put("stop_name", customerMap!!["stop_name"] as String)
                    put("stop_lat", customerMap!!["stop_lat"] as String)
                    put("stop_lon", customerMap!!["stop_lon"] as String)
                    put("zone_id", customerMap!!["zone_id"] as String)
                }
                db.insert("stops", null, values)
            }
            db.setTransactionSuccessful()
            db.endTransaction()
        } finally {
            if (mapReader != null) {
                mapReader.close()
            }
        }
    }

    private fun insertStopTimes(context: Context) {
        val file = File(context.filesDir, "gtfs_files/stop_times.txt")
        var mapReader: ICsvMapReader? = null
        try {
            db.beginTransaction()
            mapReader = CsvMapReader(FileReader(file), CsvPreference.STANDARD_PREFERENCE)
            val header = mapReader.getHeader(true)

            var customerMap: Map<String, Any>? = null
            val processors = Array<CellProcessor?>(header.size, { null })
            while ({ customerMap = mapReader.read(header, processors); customerMap }() != null) {
                val values = ContentValues().apply {
                    put("trip_id", customerMap!!["trip_id"] as String)
                    put("arrival_time", customerMap!!["arrival_time"] as String)
                    put("departure_time", customerMap!!["departure_time"] as String)
                    put("stop_id", customerMap!!["stop_id"] as String)
                    put("stop_sequence", customerMap!!["stop_sequence"] as String)
                    put("stop_headsign", customerMap!!["stop_headsign"] as String)
                    put("pickup_type", customerMap!!["pickup_type"] as String)
                    put("drop_off_type", customerMap!!["drop_off_type"] as String)
                }
                db.insert("stop_times", null, values)
                if (mapReader.rowNumber % 10000 == 0)
                    println(mapReader.rowNumber)
            }
            db.setTransactionSuccessful()
            db.endTransaction()
        } finally {
            if (mapReader != null) {
                mapReader.close()
            }
        }
    }

    private fun insertTrips(context: Context) {
        val file = File(context.filesDir, "gtfs_files/trips.txt")
        var mapReader: ICsvMapReader? = null
        try {
            db.beginTransaction()
            mapReader = CsvMapReader(FileReader(file), CsvPreference.STANDARD_PREFERENCE)
            val header = mapReader.getHeader(true)

            var customerMap: Map<String, Any>? = null
            val processors = Array<CellProcessor?>(header.size, { null })
            while ({ customerMap = mapReader.read(header, processors); customerMap }() != null) {
                val values = ContentValues().apply {
                    put("route_id", customerMap!!["route_id"] as String)
                    put("service_id", customerMap!!["service_id"] as String)
                    put("trip_id", customerMap!!["trip_id"] as String)
                    put("trip_headsign", customerMap!!["trip_headsign"] as String)
                    put("direction_id", customerMap!!["direction_id"] as String)
                    put("shape_id", customerMap!!["shape_id"] as String)
                    put("wheelchair_accessible", customerMap!!["wheelchair_accessible"] as String)
                }
                db.insert("trips", null, values)
            }
            db.setTransactionSuccessful()
            db.endTransaction()
        } finally {
            if (mapReader != null) {
                mapReader.close()
            }
        }
    }*/
}