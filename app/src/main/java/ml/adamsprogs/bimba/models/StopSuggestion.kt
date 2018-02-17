package ml.adamsprogs.bimba.models

import android.os.Parcel
import android.os.Parcelable
import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion
import ml.adamsprogs.bimba.gtfs.AgencyAndId

class StopSuggestion(private val directions: HashSet<String>, val id: AgencyAndId) : SearchSuggestion {

    @Suppress("UNCHECKED_CAST")
    constructor(parcel: Parcel) : this(parcel.readSerializable() as HashSet<String>, parcel.readSerializable() as AgencyAndId)

    override fun describeContents(): Int {
        return Parcelable.CONTENTS_FILE_DESCRIPTOR
    }

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.writeSerializable(directions)
        dest?.writeSerializable(id)
    }

    override fun getBody(): String {
        return "${Timetable.getTimetable().getStopName(id)}\n${directions.sortedBy{it}.joinToString()}"
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