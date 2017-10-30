package ml.adamsprogs.bimba.models

import android.app.Activity
import android.content.Context
import android.os.Build
import android.support.design.widget.AppBarLayout
import android.support.v4.content.res.ResourcesCompat
import android.support.v7.widget.*
import android.support.v7.widget.PopupMenu
import android.view.*
import android.widget.*
import ml.adamsprogs.bimba.R
import android.view.LayoutInflater
import java.util.*
import kotlin.concurrent.thread
import android.util.TypedValue
import com.arlib.floatingsearchview.FloatingSearchView
import ml.adamsprogs.bimba.Declinator
import kotlin.collections.ArrayList

//todo list to storage
class FavouritesAdapter(val context: Context, var favourites: List<Favourite>, private val onMenuItemClickListener: FavouritesAdapter.OnMenuItemClickListener) :
        RecyclerView.Adapter<FavouritesAdapter.ViewHolder>() {

    private val isSelecting: Boolean
        get() {
            return selected.any { it }
        }
    private val selected = ArrayList<Boolean>()
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
            val nextDeparture: Departure?
            try {
                nextDeparture = favourite.nextDeparture()
            } catch (e: ConcurrentModificationException) {
                return@thread
            }
            val nextDepartureText: String
            val nextDepartureLineText: String
            if (nextDeparture != null) {
                val interval = nextDeparture.timeTill()
                if (interval < 0)
                    return@thread
                nextDepartureText = context.getString(Declinator.decline(interval), interval.toString())
                nextDepartureLineText = context.getString(R.string.departure_to_line, nextDeparture.line, nextDeparture.direction)
            } else {
                //fixme too early ?
                nextDepartureText = context.getString(R.string.no_next_departure)
                nextDepartureLineText = ""
            }
            (context as Activity).runOnUiThread {
                holder?.root?.setOnLongClickListener {
                    toggleSelected(it as CardView, position)
                    true
                }
                holder?.nameTextView?.text = favourite.name
                holder?.timeTextView?.text = nextDepartureText
                holder?.lineTextView?.text = nextDepartureLineText
                if(nextDeparture!=null) {
                    if (nextDeparture.vm)
                        holder?.typeIcon?.setImageDrawable(ResourcesCompat.getDrawable(context.resources, R.drawable.ic_departure_vm, context.theme))
                    else
                        holder?.typeIcon?.setImageDrawable(ResourcesCompat.getDrawable(context.resources, R.drawable.ic_departure_timetable, context.theme))
                }
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

    private fun toggleSelected(view: CardView, position: Int) {
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

    private fun select(view: CardView, position: Int) {
        growSelected(position)

        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            view.setCardBackgroundColor(context.resources.getColor(R.color.colorAccent, null))
        else
            view.setCardBackgroundColor(context.resources.getColor(R.color.colorAccent))
        selected[position] = true
        setSelecting()
    }

    private fun unSelect(view: CardView, position: Int) {
        growSelected(position)

        val colour = TypedValue()
        context.theme.resolveAttribute(R.attr.cardBackgroundColor, colour, true)
        view.setCardBackgroundColor(colour.data)
        selected[position] = false
        setSelecting()
    }

    private fun setSelecting() {
        context as Activity
        if (isSelecting) {
            context.findViewById<FloatingSearchView>(R.id.search_view).visibility = View.INVISIBLE
            context.findViewById<AppBarLayout>(R.id.appbar).visibility = View.VISIBLE
        } else {
            context.findViewById<FloatingSearchView>(R.id.search_view).visibility = View.VISIBLE
            context.findViewById<AppBarLayout>(R.id.appbar).visibility = View.INVISIBLE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
        val context = parent?.context
        val inflater = LayoutInflater.from(context)

        val rowView = inflater.inflate(R.layout.row_favourite, parent, false)
        return ViewHolder(rowView)
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
        val root:CardView = itemView.findViewById(R.id.favourite_card)
        val nameTextView:TextView = itemView.findViewById(R.id.favourite_name)
        val timeTextView:TextView = itemView.findViewById(R.id.favourite_time)
        val lineTextView:TextView = itemView.findViewById(R.id.favourite_line)
        val moreButton:ImageView = itemView.findViewById(R.id.favourite_more_button)
        val typeIcon:ImageView = itemView.findViewById(R.id.departureTypeIcon)
    }

    interface OnMenuItemClickListener {
        fun edit(name: String): Boolean
        fun delete(name: String): Boolean
    }
}