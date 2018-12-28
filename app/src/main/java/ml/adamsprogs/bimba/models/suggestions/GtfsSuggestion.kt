package ml.adamsprogs.bimba.models.suggestions

import android.content.Context

abstract class GtfsSuggestion(val name: String) : Comparable<GtfsSuggestion> {
    abstract fun getIcon(): Int

    abstract fun getColour(): Int

    abstract fun getBgColour(): Int

    abstract fun getBody(context: Context): String

    abstract override fun equals(other: Any?): Boolean

    abstract override fun hashCode(): Int
}