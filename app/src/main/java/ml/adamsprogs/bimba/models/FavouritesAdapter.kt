package ml.adamsprogs.bimba.models

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


class FavouritesAdapter(val context: Context, val favourites: List<Favourite>) :
        RecyclerView.Adapter<FavouritesAdapter.ViewHolder>() {
    override fun getItemCount(): Int {
        return favourites.size
    }

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {

        val favourite = favourites[position]
        holder?.nameTextView?.text = favourite.name
        val nextDeparture = favourite.nextDeparture ?: return
        val now = Calendar.getInstance()
        val departureTime = Calendar.getInstance()
        departureTime.set(Calendar.HOUR_OF_DAY, Integer.parseInt(nextDeparture.time.split(":")[0]))
        departureTime.set(Calendar.MINUTE, Integer.parseInt(nextDeparture.time.split(":")[1]))
        if (nextDeparture.tomorrow)
            departureTime.add(Calendar.DAY_OF_MONTH, 1)
        val interval = (departureTime.timeInMillis - now.timeInMillis) / (1000 * 60)
        holder?.timeTextView?.text = context.getString(R.string.departure_in, interval.toString())
        holder?.lineTextView?.text = context.getString(R.string.departure_to_line, nextDeparture.line, nextDeparture.direction)
        holder?.moreButton?.setOnClickListener {
            val popup = PopupMenu(context, it)
            val inflater = popup.menuInflater
            popup.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.favourite_edit -> editFavourite(favourite.name)
                    R.id.favourite_delete -> deleteFavourite(favourite.name)
                    else -> false
                }
            }
            inflater.inflate(R.menu.favourite_actions, popup.menu)
            popup.show()
        }
    }

    private fun editFavourite(name: String): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        return true
    }

    private fun deleteFavourite(name: String): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        return true
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
}