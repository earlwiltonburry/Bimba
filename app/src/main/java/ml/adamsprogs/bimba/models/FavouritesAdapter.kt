package ml.adamsprogs.bimba.models

import android.app.Activity
import android.content.Context
import android.os.Build
import android.support.v7.widget.CardView
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
import android.util.TypedValue
import kotlin.collections.ArrayList

//todo list to storage
class FavouritesAdapter(val context: Context, var favourites: List<Favourite>, val onMenuItemClickListener: FavouritesAdapter.OnMenuItemClickListener) :
        RecyclerView.Adapter<FavouritesAdapter.ViewHolder>() {

    val isSelecting: Boolean
        get() {
            return selected.any { it }
        }
    val selected = ArrayList<Boolean>()
    val selectedNames: ArrayList<String>
        get() {
            val l = ArrayList<String>()
            for ((i, it) in selected.withIndex()) {
                if (it)
                    l.add(favourites[i].name)
            }
            return l
        }

    init {
        favourites.forEach {
            selected.add(false)
        }
    }

    override fun getItemCount(): Int {
        return favourites.size
    }

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {

        thread {
            val favourite = favourites[position]
            holder?.nameTextView?.text = favourite.name
            val nextDeparture: Departure?
            try {
                nextDeparture = favourite.nextDeparture
            } catch (e: ConcurrentModificationException) {
                return@thread
            }
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
                nextDepartureLineText = context.getString(R.string.departure_to_line, nextDeparture.line, nextDeparture.direction)
            } else {
                nextDepartureText = context.getString(R.string.no_next_departure)
                nextDepartureLineText = ""
            }
            (context as Activity).runOnUiThread {
                holder?.root?.setOnLongClickListener {
                    toggleSelected(it as CardView, position)
                    true
                }
                holder?.timeTextView?.text = nextDepartureText
                holder?.lineTextView?.text = nextDepartureLineText
                holder?.moreButton?.setOnClickListener {
                    unSelect(holder.root, position)
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

    fun toggleSelected(view: CardView, position: Int) {
        growSelected(position)

        if (selected[position])
            unSelect(view, position)
        else
            select(view, position)
    }

    private fun growSelected(position: Int) {
        while (position >= selected.size)
            selected.add(false)
    }

    fun select(view: CardView, position: Int) {
        growSelected(position)

        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            view.setCardBackgroundColor(context.resources.getColor(R.color.colorAccent, null))
        else
            view.setCardBackgroundColor(context.resources.getColor(R.color.colorAccent))
        selected[position] = true
        setSelecting()
    }

    fun unSelect(view: CardView, position: Int) {
        growSelected(position)

        val colour = TypedValue()
        context.theme.resolveAttribute(R.attr.cardBackgroundColor, colour, true)
        view.setCardBackgroundColor(colour.data)
        selected[position] = false
        setSelecting()
    }

    fun setSelecting() {
        context as Activity
        if (isSelecting) {
            context.findViewById(R.id.search_view).visibility = View.INVISIBLE
            context.findViewById(R.id.appbar).visibility = View.VISIBLE
        } else {
            context.findViewById(R.id.search_view).visibility = View.VISIBLE
            context.findViewById(R.id.appbar).visibility = View.INVISIBLE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
        val context = parent?.context
        val inflater = LayoutInflater.from(context)

        val rowView = inflater.inflate(R.layout.row_favourite, parent, false)
        val viewHolder = ViewHolder(rowView)
        return viewHolder
    }

    fun stopSelecting(name: String) {
        selected.clear()
        favourites.forEach {
            if (it.name == name)
                selected.add(true)
            else
                selected.add(false)
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val root = itemView.findViewById(R.id.favourite_card) as CardView
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