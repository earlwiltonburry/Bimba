package ml.adamsprogs.bimba.models.adapters

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import ml.adamsprogs.bimba.R
import ml.adamsprogs.bimba.collections.FavouriteStorage
import ml.adamsprogs.bimba.models.Favourite
import ml.adamsprogs.bimba.models.Plate
import ml.adamsprogs.bimba.models.Timetable


//todo when plates null -> get all plates from proxy
class FavouriteEditRowAdapter(private var favourite: Favourite) :
        RecyclerView.Adapter<FavouriteEditRowAdapter.ViewHolder>() {
    override fun getItemCount(): Int {
        return favourite.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val timetable = Timetable.getTimetable()
        val favourites = FavouriteStorage.getFavouriteStorage()
        val id = favourite.segments.flatMap { it.plates!! }.sortedBy { "${it.line}${it.stop}"}[position]
        val plate = Plate(id, null)
        val favouriteElement = "${timetable.getStopName(plate.id.stop)} ( ${timetable.getStopCode(plate.id.stop)}):\n${plate.id.line} → ${plate.id.headsign}"
        holder.rowTextView.text = favouriteElement
        holder.deleteButton.setOnClickListener {
            favourites.delete(favourite.name, id)
            favourite = favourites.favourites[favourite.name]!!
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)

        val rowView = inflater.inflate(R.layout.row_favourite_edit, parent, false)
        return ViewHolder(rowView)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val rowTextView:TextView = itemView.findViewById(R.id.favourite_edit_row)
        val deleteButton:ImageView = itemView.findViewById(R.id.favourite_edit_delete)
    }
}