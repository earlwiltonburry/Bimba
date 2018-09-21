package ml.adamsprogs.bimba.models.gtfs

data class Trip(val routeId: String, val serviceId: String, val id: ID,
                val headsign: String, val direction: Int, val shapeId: String,
                val wheelchairAccessible: Boolean) {
    data class ID(val rawId:String, val id: String, val modification: Set<Modification>, val isMain: Boolean) {
        data class Modification(val id: String, val stopRange: IntRange?)
    }
}