package ml.adamsprogs.bimba.models

import android.os.Parcel
import android.os.Parcelable
import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion
import ml.adamsprogs.bimba.gtfs.AgencyAndId

class StopSuggestion(val name: String, val ids: Set<AgencyAndId>, val zone: String) : SearchSuggestion, Comparable<StopSuggestion> {

    @Suppress("UNCHECKED_CAST")
    constructor(parcel: Parcel) : this(parcel.readString(), parcel.readString().split(",").map { AgencyAndId(it) }.toSet(), parcel.readString())

    override fun describeContents(): Int {
        return Parcelable.CONTENTS_FILE_DESCRIPTOR
    }

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.writeString(name)
        dest?.writeString(ids.joinToString(",") { it.toString() })
        dest?.writeString(zone)
    }

    override fun getBody(): String {
        return "$name\n$zone"
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