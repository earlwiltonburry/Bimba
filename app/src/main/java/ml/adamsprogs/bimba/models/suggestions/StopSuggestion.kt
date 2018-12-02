package ml.adamsprogs.bimba.models.suggestions

import ml.adamsprogs.bimba.R

class StopSuggestion(name: String, val zone: String, private val zoneColour: String) : GtfsSuggestion(name) {

    override fun getBody(): String {
        return name
    }

    override fun getIcon(): Int {
        return R.drawable.ic_stop
    }

    override fun getColour(): Int {
        return 0xffffff
    }

    override fun getBgColour(): Int {
        return "ffffff".toInt(16)
    }

    override fun compareTo(other: GtfsSuggestion): Int {
        return name.compareTo(other.name)
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is GtfsSuggestion)
            return false
        return name == other.name
    }

    override fun hashCode(): Int {
        var result = zone.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}