package ml.adamsprogs.bimba.models.suggestions

import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion

abstract class GtfsSuggestion(val name: String) : SearchSuggestion, Comparable<GtfsSuggestion> {
    abstract fun getIcon(): Int
}