package ml.adamsprogs.bimba.models

import ml.adamsprogs.bimba.models.gtfs.AgencyAndId
import java.io.Serializable

data class Plate(val id: ID, val departures: HashMap<AgencyAndId, HashSet<Departure>>?) {
    override fun toString(): String {
        var result = "${id.line}=${id.stop}=${id.headsign}={"
        if (departures != null) {
            for ((service, column) in departures) {
                result += service.toString() + ":"
                for (departure in column) {
                    result += departure.toString() + ";"
                }
            }
        }
        result += "}"
        return result
    }

    companion object {
        fun fromString(string: String): Plate {
            val (lineStr, stopStr, headsign, departuresString) = string.split("=")
            val line = AgencyAndId.convertFromString(lineStr)
            val stop = AgencyAndId.convertFromString(stopStr)
            val departures = HashMap<AgencyAndId, HashSet<Departure>>()
            departuresString.replace("{", "").replace("}", "").split(";")
                    .filter { it != "" }
                    .forEach {
                        try {
                            val (serviceStr, depStr) = it.split(":")
                            val dep = Departure.fromString(depStr)
                            val service = AgencyAndId.convertFromString(serviceStr)
                            if (departures[service] == null)
                                departures[service] = HashSet()
                            departures[service]!!.add(dep)
                        } catch (e: IllegalArgumentException) {
                        }
                    }
            return Plate(ID(line, stop, headsign), departures)
        }

        fun join(set: Set<Plate>): HashMap<AgencyAndId, ArrayList<Departure>> {
            val departures = HashMap<AgencyAndId, ArrayList<Departure>>()
            for (plate in set) {
                for ((mode, d) in plate.departures!!) {
                    if (departures[mode] == null)
                        departures[mode] = ArrayList()
                    departures[mode]!!.addAll(d)
                }
            }
            for ((mode, _) in departures) {
                departures[mode]?.sortBy { it.time }
            }
            return departures
        }
    }

    data class ID(val line: AgencyAndId, val stop: AgencyAndId, val headsign: String) : Serializable {
        companion object {
            fun fromString(string: String): ID {
                val (line, stop, headsign) = string.split("|")
                return ID(AgencyAndId.convertFromString(line),
                        AgencyAndId.convertFromString(stop), headsign)
            }
        }

        override fun equals(other: Any?): Boolean {
            if (other !is ID)
                return false
            return line == other.line && stop == other.stop && headsign.toLowerCase() == other.headsign.toLowerCase()
        }

        override fun toString(): String {
            return "$line|$stop|$headsign"
        }

        override fun hashCode(): Int {
            var result = line.hashCode()
            result = 31 * result + stop.hashCode()
            result = 31 * result + headsign.hashCode()
            return result
        }

        constructor(other: Plate.ID) : this(other.line, other.stop, other.headsign)
    }
}