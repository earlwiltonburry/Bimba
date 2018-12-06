package ml.adamsprogs.bimba

import android.app.NativeActivity
import android.content.Context
import ml.adamsprogs.bimba.activities.DashActivity
import ml.adamsprogs.bimba.models.Timetable
import ml.adamsprogs.bimba.models.gtfs.StopTimeSequence
import java.util.Calendar as JCalendar


class RouteFinder {
    companion object {
        private lateinit var timetable: Timetable

        fun findRoute(start: String, end: String, time: JCalendar, context: Context) {
            timetable = Timetable.getTimetable(context)
            val options = timetable.getTripFrom(start, time)
            for (opt in options) {
                System.out.println(opt.tripID+ " " + opt.route)
            }
        }
    }
}