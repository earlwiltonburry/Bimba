package ml.adamsprogs.bimba.models.gtfs

import java.io.Serializable

data class AgencyAndId(val id: String) : Serializable, Comparable<AgencyAndId> {
    override fun compareTo(other: AgencyAndId): Int {
        return this.toString().compareTo(other.toString())
    }

    companion object {
        fun convertFromString(str: String): AgencyAndId {
            return AgencyAndId(str)
        }
    }

    override fun toString(): String {
        return id
    }
}