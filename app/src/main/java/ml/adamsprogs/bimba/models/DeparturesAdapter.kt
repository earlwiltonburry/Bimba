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
import java.util.*

class DeparturesAdapter(val context: Context, private val departures: List<Departure>?, private val relativeTime: Boolean) :
        RecyclerView.Adapter<DeparturesAdapter.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_LOADING: Int = 0
        const val VIEW_TYPE_CONTENT: Int = 1
    }

    override fun getItemCount(): Int {

        if (departures == null)
            return 1
        return departures.size
    }

    override fun getItemViewType(position: Int): Int {
        return if (departures == null)
            VIEW_TYPE_LOADING
        else
            VIEW_TYPE_CONTENT
    }

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
        if (departures == null) {
            return
        }
        val departure = departures[position]
        val now = Calendar.getInstance()
        val departureTime = Calendar.getInstance()
        departureTime.set(Calendar.HOUR_OF_DAY, Integer.parseInt(departure.time.split(":")[0]))
        departureTime.set(Calendar.MINUTE, Integer.parseInt(departure.time.split(":")[1]))
        if (departure.tomorrow)
            departureTime.add(Calendar.DAY_OF_MONTH, 1)

        val departureIn = (departureTime.timeInMillis - now.timeInMillis) / (1000 * 60)
        val timeString: String

        timeString = if (departureIn > 60 || departureIn < 0 || !relativeTime)
            context.getString(R.string.departure_at, departure.time)
        else if (departureIn > 0 && !departure.onStop)
            context.getString(Declinator.decline(departureIn), departureIn.toString())
        else
            context.getString(R.string.now)

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

        if (departure.lowFloor)
            holder?.floorIcon?.visibility = View.VISIBLE
        if (departure.modification != "") {
            holder?.infoIcon?.visibility = View.VISIBLE
            holder?.root?.setOnClickListener {
                AlertDialog.Builder(context)
                        .setPositiveButton(context.getText(android.R.string.ok),
                                { dialog: DialogInterface, _: Int -> dialog.cancel() })
                        .setCancelable(true)
                        .setMessage(departure.modification)
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
        val root = itemView.findViewById(R.id.departureRow)!!
        val lineTextView: TextView = itemView.findViewById(R.id.lineNumber) as TextView
        val timeTextView: TextView = itemView.findViewById(R.id.departureTime) as TextView
        val directionTextView: TextView = itemView.findViewById(R.id.departureDirection) as TextView
        val typeIcon: ImageView = itemView.findViewById(R.id.departureTypeIcon) as ImageView
        val infoIcon: ImageView = itemView.findViewById(R.id.departureInfoIcon) as ImageView
        val floorIcon: ImageView = itemView.findViewById(R.id.departureFloorIcon) as ImageView
    }
}