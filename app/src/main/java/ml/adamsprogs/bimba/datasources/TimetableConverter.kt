package ml.adamsprogs.bimba.datasources

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import de.siegmar.fastcsv.reader.CsvRow
import de.siegmar.fastcsv.reader.CsvReader
import ir.mahdi.mzip.zip.ZipArchive
import java.nio.charset.StandardCharsets


class TimetableConverter(from: File, to: File, context: Context) {
    private val db: SQLiteDatabase = SQLiteDatabase.openOrCreateDatabase(to, null)

    init {
        val target = File(context.filesDir, "gtfs_files")
        target.mkdir()
        ZipArchive.unzip(from.path, target.path, "")
        createTables()
        insertAgency(context)
        insertCalendar(context)
        insertCalendarDates(context)
        insertFeedInfo(context)
        insertRoutes(context)
        insertShapes(context)
        insertStops(context)
        insertStopTimes(context)
        insertTrips(context)
        target.deleteRecursively()
    }

    private fun createTables() {
        db.rawQuery("create table agency(" +
                "agency_id TEXT PRIMARY KEY," +
                "agency_name TEXT," +
                "agency_url TEXT," +
                "agency_timezone TEXT," +
                "agency_phone TEXT," +
                "agency_lang TEXT" +
                ")", null)
        db.rawQuery("create table calendar(" +
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
                ")", null)
        db.rawQuery("create table calendar_dates(" +
                "service_id TEXT," +
                "date TEXT," +
                "exception_type INT," +
                "FOREIGN KEY(service_id) REFERENCES calendar(service_id)" +
                ")", null)
        db.rawQuery("create table feed_info(" +
                "feed_publisher_name TEXT PRIMARY KEY," +
                "feed_publisher_url TEXT," +
                "feed_lang TEXT," +
                "feed_start_date TEXT," +
                "feed_end_date TEXT" +
                ")", null)
        db.rawQuery("create table routes(" +
                "route_id TEXT PRIMARY KEY," +
                "agency_id TEXT," +
                "route_short_name TEXT," +
                "route_long_name TEXT," +
                "route_desc TEXT," +
                "route_type INT," +
                "route_color TEXT," +
                "route_text_color TEXT," +
                "FOREIGN KEY(agency_id) REFERENCES agency(agency_id)" +
                ")", null)
        db.rawQuery("create table shapes(" +
                "shape_id TEXT PRIMARY KEY," +
                "shape_pt_lat DOUBLE," +
                "shape_pt_lon DOUBLE," +
                "shape_pt_sequence INT" +
                ")", null)
        db.rawQuery("create table stops" +
                "stop_id TEXT PRIMARY KEY," +
                "stop_code TEXT," +
                "stop_name TEXT," +
                "stop_lat DOUBLE," +
                "stop_lon DOUBLE," +
                "zone_id TEXT" +
                ")", null)
        db.rawQuery("create table stop_times(" +
                "trip_id TEXT PRIMARY KEY," +
                "arrival_time TEXT," +
                "departure_time TEXT," +
                "stop_id TEXT," +
                "stop_sequence INT," +
                "stop_headsign TEXT," +
                "pickup_type INT," +
                "drop_off_type INT," +
                "FOREIGN KEY(stop_id) REFERENCES stops(stop_id)" +
                ")", null)
        db.rawQuery("create table trips(" +
                "route_id TEXT," +
                "service_id TEXT," +
                "trip_id TEXT PRIMARY KEY," +
                "trip_headsign TEXT," +
                "direction_id INT," +
                "shape_id TEXT," +
                "wheelchair_accessible INT," +
                "FOREIGN KEY(route_id) REFERENCES routes(route_id)," +
                "FOREIGN KEY(service_id) REFERENCES calendar(service_id)," +
                "FOREIGN KEY(shape_id) REFERENCE shapes(shape_id)" +
                ")", null)
    }

    private fun insertAgency(context: Context) {
        val file = File(context.filesDir, "gtfs_files/agency.txt")
        val csvReader = CsvReader()
        csvReader.setContainsHeader(true)

        csvReader.parse(file, StandardCharsets.UTF_8).use {
            var row: CsvRow? = null
            while ({ row = it.nextRow(); row }() != null) {
                val id = row!!.getField("agency_id")
                val name = row!!.getField("agency_name")
                val url = row!!.getField("agency_url")
                val timezone = row!!.getField("agency_timezone")
                val phone = row!!.getField("agency_phone")
                val lang = row!!.getField("agency_lang")
                db.rawQuery("insert into agency values(?, ?, ?, ?, ?, ?)",
                        arrayOf(id, name, url, timezone, phone, lang))
            }
        }
    }

    private fun insertCalendar(context: Context) {
        val file = File(context.filesDir, "gtfs_files/calendar.txt")
        val csvReader = CsvReader()
        csvReader.setContainsHeader(true)

        csvReader.parse(file, StandardCharsets.UTF_8).use {
            var row: CsvRow? = null
            while ({ row = it.nextRow(); row }() != null) {
                val serviceId = row!!.getField("service_id")
                val monday = row!!.getField("monday")
                val tuesday = row!!.getField("tuesday")
                val wednesday = row!!.getField("wednesday")
                val thursday = row!!.getField("thursday")
                val friday = row!!.getField("friday")
                val saturday = row!!.getField("saturday")
                val sunday = row!!.getField("sunday")
                val startDate = row!!.getField("start_date")
                val endDate = row!!.getField("end_date")
                db.rawQuery("insert into calendar values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        arrayOf(serviceId, monday, tuesday, wednesday, thursday, friday, saturday,
                                sunday, startDate, endDate))
            }
        }
    }

    private fun insertCalendarDates(context: Context) {
        val file = File(context.filesDir, "gtfs_files/calendar_dates.txt")
        val csvReader = CsvReader()
        csvReader.setContainsHeader(true)

        csvReader.parse(file, StandardCharsets.UTF_8).use {
            var row: CsvRow? = null
            while ({ row = it.nextRow(); row }() != null) {
                val serviceId = row!!.getField("service_id")
                val date = row!!.getField("date")
                val exceptionType = row!!.getField("exceptionType")
                db.rawQuery("insert into calendar_dates values(?, ?, ?)",
                        arrayOf(serviceId, date, exceptionType))
            }
        }
    }

    private fun insertFeedInfo(context: Context) {
        val file = File(context.filesDir, "gtfs_files/feed_info.txt")
        val csvReader = CsvReader()
        csvReader.setContainsHeader(true)

        csvReader.parse(file, StandardCharsets.UTF_8).use {
            var row: CsvRow? = null
            while ({ row = it.nextRow(); row }() != null) {
                val name = row!!.getField("feed_publisher_name")
                val url = row!!.getField("feed_publisher_url")
                val lang = row!!.getField("feed_lang")
                val startDate = row!!.getField("feed_start_date")
                val endDate = row!!.getField("feed_end_date")
                db.rawQuery("insert into feed_info values(?, ?, ?, ?, ?)",
                        arrayOf(name, url, lang, startDate, endDate))
            }
        }
    }

    private fun insertRoutes(context: Context) {
        val file = File(context.filesDir, "gtfs_files/routes.txt")
        val csvReader = CsvReader()
        csvReader.setContainsHeader(true)

        csvReader.parse(file, StandardCharsets.UTF_8).use {
            var row: CsvRow? = null
            while ({ row = it.nextRow(); row }() != null) {
                val id = row!!.getField("route_id")
                val agencyId = row!!.getField("agency_id")
                val shortName = row!!.getField("route_short_name")
                val longName = row!!.getField("route_long_name")
                val description = row!!.getField("route_desc")
                val type = row!!.getField("route_type")
                val colour = row!!.getField("route_color")
                val textColour = row!!.getField("route_text_color")
                db.rawQuery("insert into routes values(?, ?, ?, ?, ?, ?, ?, ?)",
                        arrayOf(id, agencyId, shortName, longName, description, type, colour,
                                textColour))
            }
        }
    }

    private fun insertShapes(context: Context) {
        val file = File(context.filesDir, "gtfs_files/shapes.txt")
        val csvReader = CsvReader()
        csvReader.setContainsHeader(true)

        csvReader.parse(file, StandardCharsets.UTF_8).use {
            var row: CsvRow? = null
            while ({ row = it.nextRow(); row }() != null) {
                val id = row!!.getField("shape_id")
                val latitude = row!!.getField("shape_pt_lat")
                val longitude = row!!.getField("shape_pt_lon")
                val sequence = row!!.getField("shape_pt_sequence")
                db.rawQuery("insert into shapes values(?, ?, ?, ?, ?)",
                        arrayOf(id, latitude, longitude, sequence))
            }
        }
    }

    private fun insertStops(context: Context) {
        val file = File(context.filesDir, "gtfs_files/stops.txt")
        val csvReader = CsvReader()
        csvReader.setContainsHeader(true)

        csvReader.parse(file, StandardCharsets.UTF_8).use {
            var row: CsvRow? = null
            while ({ row = it.nextRow(); row }() != null) {
                val id = row!!.getField("stop_id")
                val code = row!!.getField("stop_code")
                val name = row!!.getField("stop_name")
                val latitude = row!!.getField("stop_lat")
                val longitude = row!!.getField("stop_lon")
                val zone = row!!.getField("zone_id")
                db.rawQuery("insert into stops values(?, ?, ?, ?, ?, ?)",
                        arrayOf(id, code, name, latitude, longitude, zone))
            }
        }
    }

    private fun insertStopTimes(context: Context) {
        val file = File(context.filesDir, "gtfs_files/stop_times.txt")
        val csvReader = CsvReader()
        csvReader.setContainsHeader(true)

        csvReader.parse(file, StandardCharsets.UTF_8).use {
            var row: CsvRow? = null
            while ({ row = it.nextRow(); row }() != null) {
                val id = row!!.getField("trip_id")
                val arrival = row!!.getField("arrival_time")
                val departure = row!!.getField("departure_time")
                val stop = row!!.getField("stop_id")
                val sequence = row!!.getField("stop_sequence")
                val headsign = row!!.getField("stop_headsign")
                val pickup = row!!.getField("pickup_type")
                val dropOff = row!!.getField("drop_off_type")
                db.rawQuery("insert into stop_times values(?, ?, ?, ?, ?, ?, ?, ?)",
                        arrayOf(id, arrival, departure, stop, sequence, headsign, pickup, dropOff))
            }
        }
    }

    private fun insertTrips(context: Context) {
        val file = File(context.filesDir, "gtfs_files/trips.txt")
        val csvReader = CsvReader()
        csvReader.setContainsHeader(true)

        csvReader.parse(file, StandardCharsets.UTF_8).use {
            var row: CsvRow? = null
            while ({ row = it.nextRow(); row }() != null) {
                val route = row!!.getField("route_id")
                val service = row!!.getField("service_id")
                val id = row!!.getField("trip_id")
                val headsign = row!!.getField("headsign")
                val direction = row!!.getField("direction")
                val shape = row!!.getField("shape")
                db.rawQuery("insert into trpis values(?, ?, ?, ?, ?, ?)",
                        arrayOf(route, service, id, headsign, direction, shape))
            }
        }
    }
}