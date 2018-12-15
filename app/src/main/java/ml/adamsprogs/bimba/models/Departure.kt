package ml.adamsprogs.bimba.models

import ml.adamsprogs.bimba.safeSplit

data class Departure(val line: String, val mode: List<Int>, val time: Int, val lowFloor: Boolean, //time in seconds since midnight
                     val modification: List<String>, val headsign: String, val vm: Boolean = false,
                     var tomorrow: Boolean = false, val onStop: Boolean = false) {

    val isModified: Boolean
        get() {
            return modification.isNotEmpty()
        }

    override fun toString(): String {
        return "$line|${mode.joinToString(";")}|$time|$lowFloor|${modification.joinToString(";")}|$headsign|$vm|$tomorrow|$onStop"
    }

    fun copy(): Departure {
        return Departure.fromString(this.toString())
    }

    companion object {
        fun fromString(string: String): Departure {
            val array = string.split("|")
            if (array.size != 9)
                throw IllegalArgumentException()
            val modification = array[4].safeSplit(";")!!
            return Departure(array[0],
                    array[1].safeSplit(";")!!.map { Integer.parseInt(it) },
                    Integer.parseInt(array[2]), array[3] == "true",
                    modification, array[5], array[6] == "true",
                    array[7] == "true", array[8] == "true")
        }
    }

    fun timeTill(relativeTo: Int): Int {
        var time = this.time
        if (this.tomorrow)
            time += 24 * 60 * 60
        return (time - relativeTo) / 60
    }

    val lineText: String = line
}