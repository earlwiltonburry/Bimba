package ml.adamsprogs.bimba.models

import org.onebusaway.gtfs.model.AgencyAndId

data class Plate(val line: AgencyAndId, val stop: AgencyAndId, val headsign: String, val departures: HashMap<AgencyAndId, HashSet<Departure>>?) {
    override fun toString(): String {
        var result = "$line=$stop=$headsign={"
        if (departures != null) {
            for ((_, column) in departures)
                for (departure in column) {
                    result += departure.toString() + ";"
                }
        }
        result += "}"
        return result
    }

    companion object {
        fun fromString(string: String): Plate {
            val (line, stop, headsign, departuresString) = string.split("=")
            val departures = HashMap<String, HashSet<Departure>>()
            departuresString.replace("{", "").replace("}", "").split(";")
                    .filter { it != "" }
                    .forEach {
                        try {
                            val dep = Departure.fromString(it)
                            if (departures[dep.mode] == null)
                                departures[dep.mode] = HashSet()
                            departures[dep.mode]!!.add(dep)
                        } catch (e: IllegalArgumentException) {
                        }
                    }
            return Plate(line, stop, headsign, departures)
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
        }
    }
}