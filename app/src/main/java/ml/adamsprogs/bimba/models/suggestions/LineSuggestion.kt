package ml.adamsprogs.bimba.models.suggestions

import android.content.Context
import ml.adamsprogs.bimba.R
import ml.adamsprogs.bimba.models.gtfs.Route

class LineSuggestion(name: String, private val route: Route) : GtfsSuggestion(name) {
    override fun getIcon(): Int {
        return when (route.type) {
            Route.TYPE_BUS -> R.drawable.ic_bus
            Route.TYPE_TRAM -> R.drawable.ic_tram
            else -> R.drawable.ic_vehicle
        }
    }

    override fun getBody(context: Context): String {
        return name
    }

    override fun getColour(): Int {
        return route.colour
    }

    override fun getBgColour(): Int {
        return route.textColour
    }

    override fun compareTo(other: GtfsSuggestion): Int {
        return if (other is LineSuggestion)
            name.padStart(3, '0').compareTo(other.name.padStart(3, '0'))
        else
            name.compareTo(other.name)
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is GtfsSuggestion)
            return false
        return if (other is LineSuggestion)
            name.padStart(3, '0') == other.name.padStart(3, '0')
        else
            name == other.name
    }

    override fun hashCode(): Int {
        var result = route.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}