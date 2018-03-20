package ml.adamsprogs.bimba.models.gtfs

import android.os.Parcel
import android.os.Parcelable


data class Route(val id: AgencyAndId, val agency: AgencyAndId, val shortName: String,
                 val longName: String, val description: String, val type: Int, val colour: Int,
                 val textColour: Int, val modifications: Map<String, String>) : Parcelable {
    companion object CREATOR : Parcelable.Creator<Route> {
        const val TYPE_BUS = 3
        const val TYPE_TRAM = 0

        override fun createFromParcel(parcel: Parcel): Route {
            return Route(parcel)
        }

        override fun newArray(size: Int): Array<Route?> {
            return arrayOfNulls(size)
        }

        fun create(id: String, agency: String, shortName: String, longName: String,
                                desc: String, type: Int, colour: Int, textColour: Int): Route {
            return if (desc.contains("|")) {
                val (to, from) = desc.split("|")
                val fromSplit = from.split("^")
                val toSplit = to.split("^")
                val description = "${toSplit[0]}|${fromSplit[0]}"
                val modifications = createModifications(desc)
                Route(AgencyAndId(id), AgencyAndId(agency), shortName, longName, description,
                        type, colour, textColour, modifications)
            } else {
                val toSplit = desc.split("^")
                val description = toSplit[0]
                val modifications = createModifications(desc)
                Route(AgencyAndId(id), AgencyAndId(agency), shortName, longName, description,
                        type, colour, textColour, modifications)
            }
        }

        fun createModifications(desc: String): Map<String, String> {
            val (to, from) = if(desc.contains('|')) desc.split("|") else listOf(desc, null)
            val toSplit = to!!.split("^")
            val fromSplit = from?.split("^")
            val modifications = HashMap<String, String>()
            toSplit.slice(1 until toSplit.size).forEach {
                val (k, v) = it.split(" - ")
                modifications[k] = v
            }
            fromSplit?.slice(1 until fromSplit.size)?.forEach {
                val (k,v) = it.split(" - ")
                modifications[k] = v
            }
            return modifications
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
        parcel.writeSerializable(modifications as HashMap)
    }

    override fun describeContents(): Int {
        return Parcelable.CONTENTS_FILE_DESCRIPTOR
    }
}