package ml.adamsprogs.bimba.models.adapters

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import kotlinx.coroutines.experimental.DefaultDispatcher
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import ml.adamsprogs.bimba.ProviderProxy
import ml.adamsprogs.bimba.R
import ml.adamsprogs.bimba.collections.FavouriteStorage
import ml.adamsprogs.bimba.models.Favourite
import ml.adamsprogs.bimba.models.Plate
import ml.adamsprogs.bimba.models.StopSegment


//todo when plates null -> get all plates from proxy
class FavouriteEditRowAdapter(private var favourite: Favourite) :
        RecyclerView.Adapter<FavouriteEditRowAdapter.ViewHolder>() {

    private val segments = HashMap<String, StopSegment>()
    private val providerProxy = ProviderProxy()

    init {
        launch(UI) {
            withContext(DefaultDispatcher) {
                favourite.segments.forEach {
                    segments[it.stop] = providerProxy.fillStopSegment(it) ?: it
                }
            }
            this@FavouriteEditRowAdapter.notifyDataSetChanged()
        }
    }


    override fun getItemCount(): Int {
        return segments.flatMap { it.value.plates ?: emptyList<Plate.ID>() }.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        launch(UI) {
            val plates = segments.flatMap { it.value.plates ?: emptyList<Plate.ID>() }
            val favourites = FavouriteStorage.getFavouriteStorage()
            val id = plates.sortedBy { "${it.line}${it.stop}" }[position]
            val favouriteElement = withContext(DefaultDispatcher) {
                providerProxy.getStopName(id.stop).let {
                    "${it ?: ""} (${id.stop}):\n${id.line} → ${id.headsign}"
                }
            }
            holder.rowTextView.text = favouriteElement
            holder.deleteButton.setOnClickListener {
                launch(UI) {
                    favourite.segments.clear()
                    favourite.segments.addAll(segments.map { it.value })
                    favourites.delete(favourite.name, id)
                    favourite = favourites.favourites[favourite.name]!!
                    notifyDataSetChanged()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)

        val rowView = inflater.inflate(R.layout.row_favourite_edit, parent, false)
        return ViewHolder(rowView)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val rowTextView: TextView = itemView.findViewById(R.id.favourite_edit_row)
        val deleteButton: ImageView = itemView.findViewById(R.id.favourite_edit_delete)
    }
}