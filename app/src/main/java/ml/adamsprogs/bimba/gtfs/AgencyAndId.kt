package ml.adamsprogs.bimba.gtfs

import java.io.Serializable

data class AgencyAndId(val id: String):Serializable {
    companion object {
        fun convertFromString(str: String): AgencyAndId {
            return AgencyAndId(str)
        }
    }

    override fun toString(): String {
        return id
    }
}