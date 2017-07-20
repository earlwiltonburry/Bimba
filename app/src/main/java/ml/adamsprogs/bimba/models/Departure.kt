package ml.adamsprogs.bimba.models

fun fromString(string: String): Departure {
    val array = string.split("|")
    return Departure(array[0], array[1], array[2], array[3] == "1", array[4], array[5],
            array[6] == "true", array[7] == "true")
}

data class Departure(val line: String, val mode: String, val time: String, val lowFloor: Boolean,
                     val modification: String?, val direction: String, val vm: Boolean = false,
                     var tomorrow: Boolean = false) {

    override fun toString(): String {
        return "$line|$mode|$time|$lowFloor|$modification|$direction|$vm|$tomorrow"
    }
}