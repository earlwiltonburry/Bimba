package ml.adamsprogs.bimba.gtfs

data class Trip(val routeId: AgencyAndId, val serviceId: AgencyAndId, val id: ID,
                val headsign: String, val direction: Int, val shapeId: AgencyAndId,
                val wheelchairAccessible: Boolean) {
    data class ID(val rawId:String, val id: AgencyAndId, val modification: Set<Modification>, val isMain: Boolean) {
        data class Modification(val id: AgencyAndId, val stopRange: IntRange?)
    }
}