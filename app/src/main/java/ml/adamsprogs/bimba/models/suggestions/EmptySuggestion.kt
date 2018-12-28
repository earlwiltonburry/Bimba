package ml.adamsprogs.bimba.models.suggestions

import android.content.Context
import ml.adamsprogs.bimba.R

class EmptySuggestion : GtfsSuggestion("Empty") {
    override fun equals(other: Any?): Boolean {
        return other != null && other is EmptySuggestion
    }

    override fun getIcon(): Int {
        return R.drawable.ic_error_outline
    }

    override fun getColour(): Int {
        return 0xffffff
    }

    override fun getBgColour(): Int {
        return 0x000000
    }

    override fun getBody(context: Context): String {
        return context.getString(R.string.nothing_found)
    }

    override fun compareTo(other: GtfsSuggestion): Int {
        return if (other is EmptySuggestion)
            0
        else
            -1
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}