package ml.adamsprogs.bimba.models.gtfs

import android.os.Parcel
import android.os.Parcelable


data class Route(val id: AgencyAndId, val agency: AgencyAndId, val shortName: String,
                 val longName: String, val description: String, val type: Int, val colour: Int,
                 val textColour: Int, val modifications: HashMap<String, String>) : Parcelable {
    companion object CREATOR : Parcelable.Creator<Route> {
        const val TYPE_BUS = 3
        const val TYPE_TRAM = 0

        override fun createFromParcel(parcel: Parcel): Route {
            return Route(parcel)
        }

        override fun newArray(size: Int): Array<Route?> {
            return arrayOfNulls(size)
        }
    }

    @Suppress("UNCHECKED_CAST")
    constructor(parcel: Parcel) : this(
            AgencyAndId(parcel.readString()),
            AgencyAndId(parcel.readString()),
            parcel.readString(),
            parcel.readString(),
            parcel.readString(),
            parcel.readInt(),
            parcel.readInt(),
            parcel.readInt(),
            parcel.readSerializable() as HashMap<String, String>)

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id.id)
        parcel.writeString(agency.id)
        parcel.writeString(shortName)
        parcel.writeString(longName)
        parcel.writeString(description)
        parcel.writeInt(type)
        parcel.writeInt(colour)
        parcel.writeInt(textColour)
        parcel.writeSerializable(modifications)
    }

    override fun describeContents(): Int {
        return Parcelable.CONTENTS_FILE_DESCRIPTOR
    }
}