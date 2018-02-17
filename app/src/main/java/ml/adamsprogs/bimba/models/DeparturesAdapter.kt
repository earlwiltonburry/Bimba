package ml.adamsprogs.bimba.models

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.support.v4.content.res.ResourcesCompat
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import ml.adamsprogs.bimba.R
import android.view.LayoutInflater
import ml.adamsprogs.bimba.Declinator
import ml.adamsprogs.bimba.rollTime
import java.util.*

class DeparturesAdapter(val context: Context, private val departures: List<Departure>, private val relativeTime: Boolean) :
        RecyclerView.Adapter<DeparturesAdapter.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_LOADING: Int = 0
        const val VIEW_TYPE_CONTENT: Int = 1
    }

    override fun getItemCount(): Int {
        if (departures.isEmpty())
            return 1
        return departures.size
    }

    override fun getItemViewType(position: Int): Int {
        return if (departures.isEmpty())
            VIEW_TYPE_LOADING
        else
            VIEW_TYPE_CONTENT
    }

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
        if (departures.isEmpty()) {
            return
        }
        val departure = departures[position]
        val now = Calendar.getInstance()
        val departureTime = Calendar.getInstance().rollTime(departure.time)
        if (departure.tomorrow)
            departureTime.add(Calendar.DAY_OF_MONTH, 1)

        val departureIn = (departureTime.timeInMillis - now.timeInMillis) / (1000 * 60)
        val timeString: String

        timeString = if (departureIn > 60 || departureIn < 0 || !relativeTime)
            context.getString(R.string.departure_at, "${departureTime.get(Calendar.HOUR_OF_DAY)}:${departureTime.get(Calendar.MINUTE)}")
        else if (departureIn > 0 && !departure.onStop)
            context.getString(Declinator.decline(departureIn), departureIn.toString())
        else
            context.getString(R.string.now)

        val line = holder?.lineTextView
        line?.text = departure.lineText
        val time = holder?.timeTextView
        time?.text = timeString
        val direction = holder?.directionTextView
        direction?.text = context.getString(R.string.departure_to, departure.direction)
        val icon = holder?.typeIcon
        if (departure.vm)
            icon?.setImageDrawable(ResourcesCompat.getDrawable(context.resources, R.drawable.ic_departure_vm, context.theme))
        else
            icon?.setImageDrawable(ResourcesCompat.getDrawable(context.resources, R.drawable.ic_departure_timetable, context.theme))

        if (departure.lowFloor)
            holder?.floorIcon?.visibility = View.VISIBLE
        if (departure.isModified) {
            holder?.infoIcon?.visibility = View.VISIBLE
            holder?.root?.setOnClickListener {
                AlertDialog.Builder(context)
                        .setPositiveButton(context.getText(android.R.string.ok),
                                { dialog: DialogInterface, _: Int -> dialog.cancel() })
                        .setCancelable(true)
                        .setMessage(departure.modification.joinToString("; "))
                        .create().show()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
        val context = parent?.context
        val inflater = LayoutInflater.from(context)

        val rowView = inflater.inflate(R.layout.row_departure, parent, false)
        return ViewHolder(rowView)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val root = itemView.findViewById<View>(R.id.departureRow)!!
        val lineTextView: TextView = itemView.findViewById(R.id.lineNumber)
        val timeTextView: TextView = itemView.findViewById(R.id.departureTime)
        val directionTextView: TextView = itemView.findViewById(R.id.departureDirection)
        val typeIcon: ImageView = itemView.findViewById(R.id.departureTypeIcon)
        val infoIcon: ImageView = itemView.findViewById(R.id.departureInfoIcon)
        val floorIcon: ImageView = itemView.findViewById(R.id.departureFloorIcon)
    }
}