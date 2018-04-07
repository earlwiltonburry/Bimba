package ml.adamsprogs.bimba.models.gtfs

data class Stop(val id: Int, val code: String?, val name: String, val latitude: Float?, val Longitude: Float?, val zone: Char, val onDemand: Boolean = false) {
    override fun toString() = "$id: $name ($zone)"
}