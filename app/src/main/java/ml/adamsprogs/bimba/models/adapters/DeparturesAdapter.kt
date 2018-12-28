package ml.adamsprogs.bimba.models.adapters

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import ml.adamsprogs.bimba.R
import ml.adamsprogs.bimba.models.Departure

class DeparturesAdapter(val context: Context, var departures: List<Departure>?, var relativeTime: Boolean) :
        androidx.recyclerview.widget.RecyclerView.Adapter<DeparturesAdapter.ViewHolder>() {

    override fun getItemCount(): Int {
        if (departures == null || departures!!.isEmpty())
            return 1
        return departures!!.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.floorIcon.visibility = View.GONE
        holder.infoIcon.visibility = View.GONE

        if (departures == null) {
            return
        }

        val line = holder.lineTextView
        val time = holder.timeTextView
        val direction = holder.directionTextView
        if (departures!!.isEmpty()) {
            time.text = context.getString(R.string.no_departures)
            return
        }
        val departure = departures!![position]

        val timeString = departure.timeTillText(context, relativeTime)

        line.text = departure.lineText
        time.text = timeString
        direction.text = context.getString(R.string.departure_to, departure.headsign)
        val icon = holder.typeIcon
        if (departure.vm)
            icon.setImageDrawable(ResourcesCompat.getDrawable(context.resources, R.drawable.ic_departure_vm, context.theme))
        else
            icon.setImageDrawable(ResourcesCompat.getDrawable(context.resources, R.drawable.ic_departure_timetable, context.theme))

        if (departure.lowFloor)
            holder.floorIcon.visibility = View.VISIBLE
        if (departure.isModified)
            holder.infoIcon.visibility = View.VISIBLE
        holder.root.setOnClickListener {
            AlertDialog.Builder(context)
                    .setPositiveButton(context.getText(android.R.string.ok)
                    ) { dialog: DialogInterface, _: Int -> dialog.cancel() }
                    .setCancelable(true)
                    .setMessage(departure.timeAtMessage(context))
                    .create().show()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)

        val rowView = inflater.inflate(R.layout.row_departure, parent, false)
        return ViewHolder(rowView)
    }

    inner class ViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        val root = itemView.findViewById<View>(R.id.departureRow)!!
        val lineTextView: TextView = itemView.findViewById(R.id.lineNumber)
        val timeTextView: TextView = itemView.findViewById(R.id.departureTime)
        val directionTextView: TextView = itemView.findViewById(R.id.departureDirection)
        val typeIcon: ImageView = itemView.findViewById(R.id.departureTypeIcon)
        val infoIcon: ImageView = itemView.findViewById(R.id.departureInfoIcon)
        val floorIcon: ImageView = itemView.findViewById(R.id.departureFloorIcon)
    }
}