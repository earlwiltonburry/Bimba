package ml.adamsprogs.bimba.models.adapters

import android.content.Context
import android.support.v4.content.res.ResourcesCompat
import android.support.v7.widget.*
import android.support.v7.widget.PopupMenu
import android.util.SparseBooleanArray
import android.view.*
import android.widget.*
import ml.adamsprogs.bimba.R
import android.view.LayoutInflater
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.*
import java.util.*
import ml.adamsprogs.bimba.Declinator
import ml.adamsprogs.bimba.collections.FavouriteStorage
import ml.adamsprogs.bimba.secondsAfterMidnight


class FavouritesAdapter(val appContext: Context, var favourites: FavouriteStorage,
                        private val onMenuItemClickListener: OnMenuItemClickListener,
                        private val onClickListener: ViewHolder.OnClickListener) :
        RecyclerView.Adapter<FavouritesAdapter.ViewHolder>() {

    private val selectedItems = SparseBooleanArray()

    private fun isSelected(position: Int) = getSelectedItems().contains(position)

    fun toggleSelection(position: Int) {
        if (selectedItems.get(position, false)) {
            selectedItems.delete(position)
        } else {
            selectedItems.put(position, true)
        }
        notifyItemChanged(position)
    }

    fun clearSelection() {
        val selection = getSelectedItems()
        selectedItems.clear()
        for (i in selection) {
            notifyItemChanged(i)
        }
    }

    fun getSelectedItemCount() = selectedItems.size()

    fun getSelectedItems(): List<Int> {
        val items = ArrayList<Int>(selectedItems.size())
        (0 until selectedItems.size()).mapTo(items) { selectedItems.keyAt(it) }
        return items
    }

    override fun getItemCount() = favourites.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        launch(UI) {
            val favourite = favourites[position]!!
            holder.nameTextView.text = favourite.name

            holder.selectedOverlay.visibility = if (isSelected(position)) View.VISIBLE else View.INVISIBLE
            holder.moreButton.setOnClickListener { it ->
                val popup = PopupMenu(appContext, it)
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

            val nextDeparture = withContext(CommonPool) {
                favourite.nextDeparture()
            }

            val nextDepartureText: String
            val nextDepartureLineText: String
            if (nextDeparture != null) {
                val interval = nextDeparture.timeTill(Calendar.getInstance().secondsAfterMidnight())
                nextDepartureLineText = appContext.getString(R.string.departure_to_line, nextDeparture.line, nextDeparture.headsign)
                nextDepartureText = if (interval < 0)
                    appContext.getString(R.string.just_departed)
                else
                    appContext.getString(Declinator.decline(interval), interval.toString())
            } else {
                nextDepartureText = appContext.getString(R.string.no_next_departure)
                nextDepartureLineText = ""
            }
            holder.timeTextView.text = nextDepartureText
            holder.lineTextView.text = nextDepartureLineText
            if (nextDeparture != null) {
                if (nextDeparture.vm)
                    holder.typeIcon.setImageDrawable(ResourcesCompat.getDrawable(appContext.resources, R.drawable.ic_departure_vm, appContext.theme))
                else
                    holder.typeIcon.setImageDrawable(ResourcesCompat.getDrawable(appContext.resources, R.drawable.ic_departure_timetable, appContext.theme))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)

        val rowView = inflater.inflate(R.layout.row_favourite, parent, false)
        return ViewHolder(rowView, onClickListener)
    }

    class ViewHolder(itemView: View, private val listener: OnClickListener) : RecyclerView.ViewHolder(itemView), View.OnClickListener, View.OnLongClickListener {
        override fun onLongClick(v: View?): Boolean {
            return listener.onItemLongClicked(adapterPosition)
        }

        override fun onClick(v: View?) {
            listener.onItemClicked(adapterPosition)
        }

        val selectedOverlay: View = itemView.findViewById(R.id.selected_overlay)
        val nameTextView: TextView = itemView.findViewById(R.id.favourite_name)
        val timeTextView: TextView = itemView.findViewById(R.id.favourite_time)
        val lineTextView: TextView = itemView.findViewById(R.id.favourite_line)
        val moreButton: ImageView = itemView.findViewById(R.id.favourite_more_button)
        val typeIcon: ImageView = itemView.findViewById(R.id.departureTypeIcon)

        init {
            itemView.setOnClickListener(this)
            itemView.setOnLongClickListener(this)
        }

        interface OnClickListener {
            fun onItemClicked(position: Int)
            fun onItemLongClicked(position: Int): Boolean
        }
    }

    interface OnMenuItemClickListener {
        fun edit(name: String): Boolean
        fun delete(name: String): Boolean
    }
}