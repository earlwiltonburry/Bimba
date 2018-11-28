package ml.adamsprogs.bimba.models.adapters

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ml.adamsprogs.bimba.ProviderProxy
import ml.adamsprogs.bimba.R
import ml.adamsprogs.bimba.collections.FavouriteStorage
import ml.adamsprogs.bimba.models.Favourite
import ml.adamsprogs.bimba.models.Plate
import ml.adamsprogs.bimba.models.StopSegment


class FavouriteEditRowAdapter(private var favourite: Favourite, private val loadingView: View, private val listView: View) :
        androidx.recyclerview.widget.RecyclerView.Adapter<FavouriteEditRowAdapter.ViewHolder>() {

    private val segments = HashMap<String, StopSegment>()
    private val providerProxy = ProviderProxy()
    private val favourites = FavouriteStorage.getFavouriteStorage()
    private val platesList = ArrayList<Plate.ID>()
    private val namesList = HashMap<Plate.ID, String>()

    init {
        launch(Dispatchers.Main) {
            withContext(Dispatchers.Default) {
                favourite.segments.forEach {
                    if (it.plates == null) {
                        (providerProxy.fillStopSegment(it) ?: it).let { segment ->
                            segments[segment.stop] = segment
                            it.plates = segment.plates
                        }
                    } else {
                        segments[it.stop] = it
                    }
                }
                favourites[favourite.name] = favourite

                segments.flatMap {
                    it.value.plates ?: emptyList<Plate.ID>()
                }.sortedBy { "${it.line}${it.stop}" }.forEach {
                    platesList.add(it)
                    namesList[it] = providerProxy.getStopName(it.stop).let { name ->
                        "${name ?: ""} (${it.stop}):\n${it.line} → ${it.headsign}"
                    }
                }
                launch(Dispatchers.Main) {
                    loadingView.visibility = View.GONE
                    listView.visibility = View.VISIBLE
                    this@FavouriteEditRowAdapter.notifyDataSetChanged()
                }
            }
        }
    }


    override fun getItemCount(): Int = platesList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        launch(Dispatchers.Main) {
            val id = platesList[position]
            val favouriteElement = namesList[id]

            holder.rowTextView.text = favouriteElement
            holder.deleteButton.setOnClickListener {
                launch(Dispatchers.Main) {
                    favourites.delete(favourite.name, id)
                    favourite = favourites.favourites[favourite.name]!!
                    notifyItemRemoved(platesList.indexOf(id))
                    platesList.remove(id)
                    namesList.remove(id)
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

    inner class ViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        val rowTextView: TextView = itemView.findViewById(R.id.favourite_edit_row)
        val deleteButton: ImageView = itemView.findViewById(R.id.favourite_edit_delete)
    }
}