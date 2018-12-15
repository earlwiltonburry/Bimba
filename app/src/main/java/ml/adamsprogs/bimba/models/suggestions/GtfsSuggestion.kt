package ml.adamsprogs.bimba.models.suggestions

abstract class GtfsSuggestion(val name: String) : Comparable<GtfsSuggestion> {
    abstract fun getIcon(): Int

    abstract fun getColour(): Int

    abstract fun getBgColour(): Int

    abstract fun getBody(): String
}