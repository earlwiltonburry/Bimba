package ml.adamsprogs.bimba.models.gtfs

import java.util.Calendar

data class StopTimeSequence(val route: String, val tripID: String, val sequence: MutableList<Pair<Calendar, Stop>>)