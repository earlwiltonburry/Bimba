package ml.adamsprogs.bimba.models

data class Plate(val line: String, val stop: String, val departures: HashMap<String, HashSet<Departure>>?) {
    override fun toString(): String {
        var result = "$line=$stop={"
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
            val s = string.split("=")
            val departures = HashMap<String, HashSet<Departure>>()
            s[2].replace("{", "").replace("}", "").split(";")
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
            return Plate(s[0], s[1], departures)
        }

        fun join(set: HashSet<Plate>): HashMap<String, ArrayList<Departure>> {
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