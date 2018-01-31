package ml.adamsprogs.bimba.models

import org.onebusaway.gtfs.model.AgencyAndId

data class Plate(val line: AgencyAndId, val stop: AgencyAndId, val headsign: String, val departures: HashMap<AgencyAndId, HashSet<Departure>>?) {
    override fun toString(): String {
        var result = "$line=$stop=$headsign={"
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
            return Plate(line, stop, headsign, departures)
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
}