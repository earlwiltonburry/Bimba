package ml.adamsprogs.bimba.models

import android.app.Activity
import android.content.Context
import android.support.v7.widget.PopupMenu
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import ml.adamsprogs.bimba.R
import android.view.LayoutInflater
import java.util.*
import kotlin.concurrent.thread


class FavouritesAdapter(val context: Context, var favourites: List<Favourite>, val onMenuItemClickListener: FavouritesAdapter.OnMenuItemClickListener) :
        RecyclerView.Adapter<FavouritesAdapter.ViewHolder>() {
    override fun getItemCount(): Int {
        return favourites.size
    }

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {

        thread {
            val favourite = favourites[position]
            holder?.nameTextView?.text = favourite.name
            val nextDeparture = favourite.nextDeparture
            val nextDepartureText: String
            val nextDepartureLineText: String
            if (nextDeparture != null) {
                val now = Calendar.getInstance()
                val departureTime = Calendar.getInstance()
                departureTime.set(Calendar.HOUR_OF_DAY, Integer.parseInt(nextDeparture.time.split(":")[0]))
                departureTime.set(Calendar.MINUTE, Integer.parseInt(nextDeparture.time.split(":")[1]))
                if (nextDeparture.tomorrow)
                    departureTime.add(Calendar.DAY_OF_MONTH, 1)
                val interval = ((departureTime.timeInMillis - now.timeInMillis) / (1000 * 60)).toString()
                nextDepartureText = context.getString(R.string.departure_in, interval)
                nextDepartureLineText =context.getString(R.string.departure_to_line, nextDeparture.line, nextDeparture.direction)
            } else {
                nextDepartureText = context.getString(R.string.no_next_departure)
                nextDepartureLineText = ""
            }
            (context as Activity).runOnUiThread {
                holder?.timeTextView?.text = nextDepartureText
                holder?.lineTextView?.text = nextDepartureLineText
                holder?.moreButton?.setOnClickListener {
                    val popup = PopupMenu(context, it)
                    val inflater = popup.menuInflater
                    popup.setOnMenuItemClickListener {
                        when (it.itemId) {
                            R.id.favourite_edit -> onMenuItemClickListener.edit(favourite.name)
                            R.id.favourite_delete -> onMenuItemClickListener.delete(favourite.name)
                            else -> false
                        }
                    }
                    inflater.inflate(R.menu.favourite_actions, popup.menu)
                    popup.show()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
        val context = parent?.context
        val inflater = LayoutInflater.from(context)

        val rowView = inflater.inflate(R.layout.row_favourite, parent, false)
        val viewHolder = ViewHolder(rowView)
        return viewHolder
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView = itemView.findViewById(R.id.favourite_name) as TextView
        val timeTextView = itemView.findViewById(R.id.favourite_time) as TextView
        val lineTextView = itemView.findViewById(R.id.favourite_line) as TextView
        val moreButton = itemView.findViewById(R.id.favourite_more_button) as ImageView
    }

    interface OnMenuItemClickListener {
        fun edit(name: String): Boolean
        fun delete(name: String): Boolean
    }
}