package ml.adamsprogs.bimba.models.suggestions

import android.os.Parcel
import android.os.Parcelable
import ml.adamsprogs.bimba.R

class StopSuggestion(name: String, private val zone: String, private val zoneColour: String) : GtfsSuggestion(name){
    @Suppress("UNCHECKED_CAST")
    constructor(parcel: Parcel) : this(parcel.readString(), parcel.readString(), parcel.readString())

    override fun describeContents(): Int {
        return Parcelable.CONTENTS_FILE_DESCRIPTOR
    }

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.writeString(name)
        dest?.writeString(zone)
        dest?.writeString(zoneColour)
    }

    override fun getBody(): String {
        return name
    }

    override fun getIcon(): Int {
        return R.drawable.ic_stop
    }

    override fun getColour(): Int {
        return zoneColour.filter { it in "0123456789abcdef" }.toInt(16)
    }

    override fun getBgColour(): Int {
        return "ffffff".toInt(16)
    }

    override fun compareTo(other: GtfsSuggestion): Int {
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