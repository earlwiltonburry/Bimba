package ml.adamsprogs.bimba.models

import android.content.Context
import android.support.v4.content.res.ResourcesCompat
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import ml.adamsprogs.bimba.R
import android.view.LayoutInflater
import java.util.*

fun filterDepartures(departures: List<Departure>?): ArrayList<Departure> {
    val filtered = ArrayList<Departure>()
    val lines = HashMap<String, Int>()
    val now = Calendar.getInstance()
    for (departure in departures!!) {
        val time = Calendar.getInstance()
        time.set(Calendar.HOUR_OF_DAY, Integer.parseInt(departure.time.split(":")[0]))
        time.set(Calendar.MINUTE, Integer.parseInt(departure.time.split(":")[1]))
        time.set(Calendar.SECOND, 0)
        time.set(Calendar.MILLISECOND, 0)
        if (departure.tomorrow)
            time.add(Calendar.DAY_OF_MONTH, 1)
        var lineExistedTimes = lines[departure.line]
        if ((now.before(time) || now == time) && lineExistedTimes ?: 0 < 3) {
            lineExistedTimes = (lineExistedTimes ?: 0) + 1
            lines[departure.line] = lineExistedTimes
            filtered.add(departure)
        }
    }
    return filtered
}

fun createDepartures(stopId: String): HashMap<String, ArrayList<Departure>> {
    val timetable = Timetable.getTimetable()
    val departures = timetable.getStopDepartures(stopId)
    val moreDepartures = timetable.getStopDepartures(stopId)
    val rolledDepartures = HashMap<String, ArrayList<Departure>>()

    for ((_, tomorrowDepartures) in moreDepartures!!) {
        tomorrowDepartures.forEach { it.tomorrow = true }
    }

    for ((mode, _) in departures!!) {
        rolledDepartures[mode] = (departures[mode] as ArrayList<Departure> +
                moreDepartures[mode] as ArrayList<Departure>) as ArrayList<Departure>
        rolledDepartures[mode] = filterDepartures(rolledDepartures[mode])
    }

    return rolledDepartures
}

class DeparturesAdapter(val context: Context, val departures: List<Departure>, val relativeTime: Boolean) :
        RecyclerView.Adapter<DeparturesAdapter.ViewHolder>() {
    override fun getItemCount(): Int {
        return departures.size
    }

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
        val departure = departures[position]
        val now = Calendar.getInstance()
        val departureTime = Calendar.getInstance()
        departureTime.set(Calendar.HOUR_OF_DAY, Integer.parseInt(departure.time.split(":")[0]))
        departureTime.set(Calendar.MINUTE, Integer.parseInt(departure.time.split(":")[1]))
        if (departure.tomorrow)
            departureTime.add(Calendar.DAY_OF_MONTH, 1)

        val departureIn = (departureTime.timeInMillis - now.timeInMillis) / (1000 * 60)
        val timeString: String

        if (departureIn > 60 || departureIn < 0 || !relativeTime)
            timeString = context.getString(R.string.departure_at, departure.time)
        else if (departureIn > 0 && !departure.onStop)
            timeString = context.getString(R.string.departure_in, departureIn.toString())
        else
            timeString = context.getString(R.string.now)

        val line = holder?.lineTextView
        line?.text = departure.line
        val time = holder?.timeTextView
        time?.text = timeString
        val direction = holder?.directionTextView
        direction?.text = context.getString(R.string.departure_to, departure.direction)
        val icon = holder?.typeIcon
        if (departure.vm)
            icon?.setImageDrawable(ResourcesCompat.getDrawable(context.resources, R.drawable.ic_departure_vm, context.theme))
        else
            icon?.setImageDrawable(ResourcesCompat.getDrawable(context.resources, R.drawable.ic_departure_timetable, context.theme))
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
        val context = parent?.context
        val inflater = LayoutInflater.from(context)

        val rowView = inflater.inflate(R.layout.row_departure, parent, false)
        val viewHolder = ViewHolder(rowView)
        return viewHolder
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val lineTextView: TextView = itemView.findViewById(R.id.lineNumber) as TextView
        val timeTextView: TextView = itemView.findViewById(R.id.departureTime) as TextView
        val directionTextView: TextView = itemView.findViewById(R.id.departureDirection) as TextView
        val typeIcon: ImageView = itemView.findViewById(R.id.departureTypeIcon) as ImageView
    }
}