package ml.adamsprogs.bimba.models.adapters

import android.content.Context
import android.graphics.PorterDuff
import android.os.Build
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat.getColor
import ml.adamsprogs.bimba.R
import ml.adamsprogs.bimba.getDrawable
import ml.adamsprogs.bimba.models.suggestions.EmptySuggestion
import ml.adamsprogs.bimba.models.suggestions.GtfsSuggestion
import ml.adamsprogs.bimba.models.suggestions.LineSuggestion
import ml.adamsprogs.bimba.models.suggestions.StopSuggestion
import com.mancj.materialsearchbar.adapter.SuggestionsAdapter as SearchBarSuggestionsAdapter

class SuggestionsAdapter(inflater: LayoutInflater, private val onSuggestionClickListener: OnSuggestionClickListener, private val context: Context) :
        SearchBarSuggestionsAdapter<GtfsSuggestion, SuggestionsAdapter.ViewHolder>(inflater) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val rowView = layoutInflater.inflate(R.layout.row_suggestion, parent, false)
        return ViewHolder(rowView)
    }

    override fun getSingleViewHeight(): Int = 48

    override fun onBindSuggestionHolder(suggestion: GtfsSuggestion, holder: ViewHolder?, pos: Int) {
        holder!!.root.setOnClickListener {
            onSuggestionClickListener.onSuggestionClickListener(suggestion)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            holder.text.text = Html.fromHtml(suggestion.getBody(context), Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            holder.text.text = Html.fromHtml(suggestion.getBody(context))
        }

        holder.text.setTextColor(getColor(context, R.color.textDark))

        val icon = getDrawable(suggestion.getIcon(), context)
        icon.mutate()
        icon.colorFilter = null
        if (suggestion is StopSuggestion)
            when (suggestion.zone) {
                "A" -> icon.setColorFilter(getColor(context, R.color.zoneA), PorterDuff.Mode.SRC_IN)
                "B" -> icon.setColorFilter(getColor(context, R.color.zoneB), PorterDuff.Mode.SRC_IN)
                "C" -> icon.setColorFilter(getColor(context, R.color.zoneC), PorterDuff.Mode.SRC_IN)
                else -> icon.setColorFilter(getColor(context, R.color.textDark), PorterDuff.Mode.SRC_IN)
            }
        else if (suggestion is LineSuggestion) {
            icon.setColorFilter(suggestion.getColour(), PorterDuff.Mode.SRC_IN)
            holder.icon.setBackgroundColor(suggestion.getBgColour())
        }
        holder.icon.setImageDrawable(icon)

    }

    fun updateSuggestions(newSuggestions: List<GtfsSuggestion>, query: String) {
        suggestions = sort(newSuggestions, query).take(6)
        suggestions_clone = suggestions
        notifyDataSetChanged()
    }

    private fun sort(suggestions: List<GtfsSuggestion>, query: String): List<GtfsSuggestion> {
        val r = Regex(query, RegexOption.IGNORE_CASE)
        return suggestions.sortedBy {
            (r.find(it.name)?.range?.start?.toString()?.padStart(128, '0') ?: "")+it.name
        }
    }

    operator fun contains(suggestion: GtfsSuggestion): Boolean {
        return suggestion in suggestions //|| suggestion in suggestions_clone
    }

    inner class ViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        val root: View = itemView.findViewById(R.id.row_suggestion)
        val icon: ImageView = itemView.findViewById(R.id.suggestion_row_image)
        val text: TextView = itemView.findViewById(R.id.suggestion_row_text)
    }

    interface OnSuggestionClickListener {
        fun onSuggestionClickListener(suggestion: GtfsSuggestion)
    }

    fun equals(other: List<GtfsSuggestion>): Boolean {
        if ((suggestions.containsAll(other) and other.containsAll(suggestions)))
            return true
        if (other.isEmpty())
            if ((suggestions.isEmpty()) or (suggestions[0] is EmptySuggestion))
                return true
        return false
    }
}
