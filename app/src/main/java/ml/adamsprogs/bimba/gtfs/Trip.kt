package ml.adamsprogs.bimba.gtfs

data class Trip(val routeId: AgencyAndId, val serviceId: AgencyAndId, val id: ID,
                val headsign: String, val direction: Int, val shapeId: AgencyAndId,
                val wheelchairAccessible: Boolean) {
    data class ID(val id: AgencyAndId, val modification: Set<Modification>, val isMain: Boolean) {
        data class Modification(val id: AgencyAndId, val stopRange: IntRange?)
    }

    val rawId: String
        get() {
            val builder = StringBuilder(id.id.toString())
            id.modification.forEach {
                builder.append("^")
                builder.append(it.id)
                if (it.stopRange != null) {
                    builder.append(":")
                    builder.append(it.stopRange.start)
                    builder.append(it.stopRange.endInclusive)
                }
            }
            if (id.isMain)
                builder.append("+")
            return builder.toString()
        }
}