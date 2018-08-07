package ml.adamsprogs.bimba.models

import java.io.Serializable

data class Plate(val id: ID, val departures: HashMap<Int, HashSet<Departure>>?) {
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
        /*fun fromString(string: String): Plate {
            val (lineStr, stopStr, headsign, departuresString) = string.split("=")
            val departures = HashMap<Int, HashSet<Departure>>()
            departuresString.replace("{", "").replace("}", "").split(";")
                    .filter { it != "" }
                    .forEach {
                        try {
                            val (serviceStr, depStr) = it.split(":")
                            val dep = Departure.fromString(depStr)
                            if (departures[serviceStr] == null)
                                departures[serviceStr] = HashSet()
                            departures[serviceStr]!!.add(dep)
                        } catch (e: IllegalArgumentException) {
                        }
                    }
            return Plate(ID(lineStr, stopStr, headsign), departures)
        }

        fun join(set: Set<Plate>): HashMap<String, ArrayList<Departure>> {
            val departures = HashMap<String, ArrayList<Departure>>()
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
        }*/
    }

    data class ID(val line: String, val stop: String, val headsign: String) : Serializable {
        companion object {
            fun fromString(string: String): ID {
                val (line, stop, headsign) = string.split("|")
                return ID(line,
                        stop, headsign)
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