package ml.adamsprogs.bimba.gtfs


data class Route(val id:AgencyAndId, val agency: AgencyAndId, val shortName: String,
                 val longName: String, val description: String, val type: Int, val colour: Int,
                 val textColour: Int, val modifications: HashMap<String, String>)