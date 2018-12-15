package ml.adamsprogs.bimba.models.adapters

import android.view.*
import android.widget.*
import kotlinx.coroutines.*
import kotlinx.coroutines.android.Main
import ml.adamsprogs.bimba.*
import ml.adamsprogs.bimba.collections.FavouriteStorage
import ml.adamsprogs.bimba.models.*


class FavouriteEditRowAdapter(private var favourite: Favourite, private val loadingView: View, private val listView: View) :
        androidx.recyclerview.widget.RecyclerView.Adapter<FavouriteEditRowAdapter.ViewHolder>() {

    private val segments = HashMap<String, StopSegment>()
    private val providerProxy = ProviderProxy()
    private val favourites = FavouriteStorage.getFavouriteStorage()
    private val platesList = ArrayList<Plate.ID>()
    private val namesList = HashMap<Plate.ID, String>()

    init {
        GlobalScope.launch {
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


    override fun getItemCount(): Int = platesList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        GlobalScope.launch {
            val id = platesList[position]
            val favouriteElement = namesList[id]

            holder.rowTextView.text = favouriteElement
            holder.deleteButton.setOnClickListener {
                favourites.delete(favourite.name, id)
                favourite = favourites.favourites[favourite.name]!!
                notifyItemRemoved(platesList.indexOf(id))
                platesList.remove(id)
                namesList.remove(id)
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