package ml.adamsprogs.bimba

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import ml.adamsprogs.bimba.models.Timetable
import java.io.File
import kotlin.math.sqrt
import java.util.Calendar as JCalendar


class RouteFinder {
    companion object {
        private lateinit var timetable: Timetable
        private const val tramSpeed = 15
        private const val walkSpeed = 1
        private var db: SQLiteDatabase? = null

        private class SimpleStop {
            var latitude: Float = .0F
            var longitude: Float = .0F
            var name: String = ""
            var id: Int = 0
        }

        private class Proposition(stop: Int, t1: Long, t2: Long, hist: String) {
            var stop_id: Int = stop
            var time: Long = t1
            var finish: Long = time + t2
            var history: String = hist
        }

        private fun findKClosest(stops: HashMap<Int, SimpleStop>, stop: SimpleStop?, k: Int): Array<Pair<Long, SimpleStop>> {
            val res: Array<Pair<Long, SimpleStop>> = Array(k + 1) { Pair(Long.MAX_VALUE, SimpleStop()) }
            for (s in stops.values) {
                res[k] = Pair((distance(stop, s) / walkSpeed).toLong(), s)
                var i: Int = k - 1
                while (i >= 0) {
                    if (res[i + 1].first < res[i].first) {
                        val tmp = res[i]
                        res[i] = res[i + 1]
                        res[i + 1] = tmp
                    } else {
                        break
                    }
                    i--
                }
            }
            return res
        }

        private fun eucildeanDistance(lat1: Float?, lng1: Float?, lat2: Float?, lng2: Float?): Float {
            val earthRadius = 6371000.0 //meters
            val dLat = Math.toRadians((lat2!! - lat1!!).toDouble())
            val dLng = Math.toRadians((lng2!! - lng1!!).toDouble())
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1.toDouble())) * Math.cos(Math.toRadians(lat2.toDouble())) *
                    Math.sin(dLng / 2) * Math.sin(dLng / 2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

            return (earthRadius * c).toFloat()
        }

        private fun manhattanDistance(lat1: Float?, lng1: Float?, lat2: Float?, lng2: Float?): Float {
            return eucildeanDistance(lat1, lng1, lat2, lng1) + eucildeanDistance(lat2, lng2, lat2, lng1)
        }

        private fun distance(s0: SimpleStop?, s1: SimpleStop?): Float {
            return manhattanDistance(s0!!.latitude, s0.longitude, s1!!.latitude, s1.longitude)
        }

        private fun getStops(context: Context): HashMap<Int, SimpleStop> {
            val filesDir = context.getSecondaryExternalFilesDir()
            val dbFile = File(filesDir, "timetable.db")
            db = try {
                SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
            } catch (e: SQLiteException) {
                null
            }
            val cursor = db!!.rawQuery("select stop_id, stop_lat, stop_lon, stop_code from stops order by stop_id desc", emptyArray())
            val result: HashMap<Int, SimpleStop> = HashMap(cursor.count)
            while (cursor.moveToNext()) {
                val stop = SimpleStop()
                stop.id = cursor.getInt(0)
                stop.latitude = cursor.getFloat(1)
                stop.longitude = cursor.getFloat(2)
                stop.name = cursor.getString(3)
                result[stop.id] = stop

            }
            cursor.close()
            return result
        }

        private fun pickBest(propositions: HashSet<Proposition>): Proposition {
            var best = Proposition(0, Long.MAX_VALUE, 0, "")
            for (p in propositions) {
                if (p.finish < best.finish) {
                    best = p
                }
            }
            return best
        }

        private fun addToPropositions(propositions: HashSet<Proposition>, visited: HashSet<Proposition>, prop: Proposition) {
            for (p in visited) {
                if (p.stop_id == prop.stop_id && p.finish <= prop.finish) {
                    return
                }
            }
            propositions.add(prop)
            visited.add(prop)
        }


        fun findRoute(start: String, end: String, time: JCalendar, context: Context): String {
            val stops = getStops(context)
            System.out.println(stops[204]!!.name + " " + stops[205]!!.name + " " + distance(stops[204], stops[205]) + " " + (distance(stops[204], stops[205])* 1000 / walkSpeed * 3.6))
            val visited: HashSet<Proposition> = HashSet(10)
            timetable = Timetable.getTimetable(context)
            val startStop = stops[timetable.getStopId(start).toInt()]
            val endStop = stops[timetable.getStopId(end).toInt()]
            val propositions: HashSet<Proposition> = HashSet(10)
            System.out.println(startStop!!.id)
            propositions.add(Proposition(startStop!!.id, time.timeInMillis, (distance(startStop, endStop) * 1000 / tramSpeed * 3.6).toLong(), ""))
            while (true) {
                val pop = pickBest(propositions)
                propositions.remove(pop)
                System.out.println(pop.history)
                System.out.println(stops[pop.stop_id]!!.name)
                System.out.println(pop.time)
                if (pop.stop_id == endStop!!.id || distance(endStop, stops[pop.stop_id]) < 50) return pop.history
                val options = timetable.getTripFrom(stops[pop.stop_id]!!.name, time)
                for (opt in options) {
                    for (stop in opt.sequence) {
                        addToPropositions(propositions, visited, Proposition(stop.second.id, stop.first.timeInMillis,
                                (manhattanDistance(stop.second.latitude, stop.second.Longitude, endStop.latitude, endStop.longitude)
                                        * 1000 * 3.6 / tramSpeed).toLong(),
                                pop.history + " " + pop.stop_id + "-" + opt.route + "-" + stop.second.id))
                    }
                }
                val walk = findKClosest(stops, stops[pop.stop_id], 5)
                for (opt in walk) {
                    addToPropositions(propositions, visited, Proposition(opt.second.id, pop.time + opt.first,
                            (distance(endStop, opt.second) * 1000 / walkSpeed * 3.6).toLong(),
                            pop.history + " " + pop.stop_id + "-" + "walk" + "-" + opt.second.id))
                }
            }

        }
    }
}