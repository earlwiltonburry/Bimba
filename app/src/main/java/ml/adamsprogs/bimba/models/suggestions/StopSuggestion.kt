package ml.adamsprogs.bimba.models.suggestions

import android.os.Parcel
import android.os.Parcelable
import ml.adamsprogs.bimba.R
import ml.adamsprogs.bimba.models.gtfs.AgencyAndId

class StopSuggestion(name: String, val ids: Set<AgencyAndId>, private val zone: String, private val zoneColour: String) : GtfsSuggestion(name), Comparable<StopSuggestion> {
    @Suppress("UNCHECKED_CAST")
    constructor(parcel: Parcel) : this(parcel.readString(), parcel.readString().split(",").map { AgencyAndId(it) }.toSet(), parcel.readString(), parcel.readString())

    override fun describeContents(): Int {
        return Parcelable.CONTENTS_FILE_DESCRIPTOR
    }

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.writeString(name)
        dest?.writeString(ids.joinToString(",") { it.toString() })
        dest?.writeString(zone)
        dest?.writeString(zoneColour)
    }

    override fun getBody(): String {
        return "$name <small><font color=\"$zoneColour\">$zone</font></small>"
    }

    override fun getIcon(): Int {
        return R.drawable.ic_stop
    }

    override fun compareTo(other: StopSuggestion): Int {
        return name.compareTo(other.name)
    }

    companion object CREATOR : Parcelable.Creator<StopSuggestion> {
        override fun createFromParcel(parcel: Parcel): StopSuggestion {
            return StopSuggestion(parcel)
        }

        override fun newArray(size: Int): Array<StopSuggestion?> {
            return arrayOfNulls(size)
        }
    }
}