package ml.adamsprogs.bimba.models.suggestions

import android.os.Parcel
import android.os.Parcelable
import ml.adamsprogs.bimba.R
import ml.adamsprogs.bimba.models.gtfs.Route

class LineSuggestion(name: String, private val route: Route) : GtfsSuggestion(name) {
    constructor(parcel: Parcel) : this(
            parcel.readString(),
            parcel.readParcelable(Route::class.java.classLoader))

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeParcelable(route, flags)
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun getIcon(): Int {
        return when (route.type) {
            Route.TYPE_BUS -> R.drawable.ic_bus
            Route.TYPE_TRAM -> R.drawable.ic_tram
            else -> R.drawable.ic_vehicle
        }
    }

    override fun getBody(): String {
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

    companion object CREATOR : Parcelable.Creator<LineSuggestion> {
        override fun createFromParcel(parcel: Parcel): LineSuggestion {
            return LineSuggestion(parcel)
        }

        override fun newArray(size: Int): Array<LineSuggestion?> {
            return arrayOfNulls(size)
        }
    }
}