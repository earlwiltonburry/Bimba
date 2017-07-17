package ml.adamsprogs.bimba.models

data class Departure(val line: String, val mode: String, val time: String, val lowFloor: Boolean,
                val modification: String?, val direction: String) {
    override fun toString():String {
        return "$line|$mode|$time|$lowFloor|$modification|$direction"
    }
}