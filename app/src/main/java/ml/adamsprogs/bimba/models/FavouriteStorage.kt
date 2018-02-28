package ml.adamsprogs.bimba.models

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import ml.adamsprogs.bimba.MessageReceiver
import ml.adamsprogs.bimba.models.gtfs.AgencyAndId


class FavouriteStorage private constructor(context: Context) : Iterable<Favourite> {
    companion object {
        private var favouriteStorage: FavouriteStorage? = null
        fun getFavouriteStorage(context: Context? = null): FavouriteStorage {
            return if (favouriteStorage == null) {
                if (context == null)
                    throw IllegalArgumentException("requested new storage context not given")
                else {
                    favouriteStorage = FavouriteStorage(context)
                    favouriteStorage as FavouriteStorage
                }
            } else
                favouriteStorage as FavouriteStorage
        }
    }

    val favourites = HashMap<String, Favourite>()
    private val preferences: SharedPreferences = context.getSharedPreferences("ml.adamsprogs.bimba.prefs", Context.MODE_PRIVATE)

    init {
        val favouritesString = preferences.getString("favourites", "{}")
        val favouritesMap = Gson().fromJson(favouritesString, JsonObject::class.java)
        for ((name, jsonTimetables) in favouritesMap.entrySet()) {
            val timetables = HashSet<StopSegment>()
            jsonTimetables.asJsonArray.mapTo(timetables) {
                val stopSegment = StopSegment(AgencyAndId(it.asJsonObject["stop"].asString), null)
                val plates = HashSet<Plate.ID>()
                it.asJsonObject["plates"].asJsonArray.mapTo(plates) {
                    Plate.ID(AgencyAndId(it.asJsonObject["line"].asString),
                            AgencyAndId(it.asJsonObject["stop"].asString),
                            it.asJsonObject["headsign"].asString)
                }
                stopSegment.plates = plates
                stopSegment
            }
            favourites[name] = Favourite(name, timetables)
        }
    }

    override fun iterator(): Iterator<Favourite> = favourites.values.iterator()

    fun has(name: String): Boolean = favourites.contains(name)

    fun add(name: String, timetables: HashSet<StopSegment>) {
        if (favourites[name] == null) {
            favourites[name] = Favourite(name, timetables)
            serialize()
        }
    }

    fun add(name: String, favourite: Favourite) {
        if (favourites[name] == null) {
            favourites[name] = favourite
            serialize()
        }
    }

    fun delete(name: String) {
        favourites.remove(name)
        serialize()
    }

    fun delete(name: String, plate: Plate.ID) {
        favourites[name]?.delete(plate)
        serialize()
    }

    private fun serialize() {
        val rootObject = JsonObject()
        for ((name, favourite) in favourites) {
            val timetables = JsonArray()
            for (timetable in favourite.timetables) {
                val segment = JsonObject()
                segment.addProperty("stop", timetable.stop.id)
                val plates = JsonArray()
                for (plate in timetable.plates ?: HashSet()) {
                    val element = JsonObject()
                    element.addProperty("stop", plate.stop.id)
                    element.addProperty("line", plate.line.id)
                    element.addProperty("headsign", plate.headsign)
                    plates.add(element)
                }
                segment.add("plates", plates)
                timetables.add(segment)
            }
            rootObject.add(name, timetables)
        }
        val favouritesString = Gson().toJson(rootObject)
        val editor = preferences.edit()
        editor.putString("favourites", favouritesString)
        editor.apply()

    }

    fun detach(name: String, plate: Plate.ID, newName: String) {
        val plates = HashSet<Plate.ID>()
        plates.add(plate)
        val segments = HashSet<StopSegment>()
        segments.add(StopSegment(plate.stop, plates))
        favourites[newName] = Favourite(newName, segments)
        serialize()

        delete(name, plate)
    }

    fun merge(names: List<String>) {
        if (names.size < 2)
            return
        val newFavourite = Favourite(names[0], HashSet())
        for (name in names) {
            newFavourite.timetables.addAll(favourites[name]!!.timetables)
            favourites.remove(name)
        }
        favourites[names[0]] = newFavourite

        serialize()
    }

    fun rename(oldName: String, newName: String) {
        val favourite = favourites[oldName] ?: return
        favourite.rename(newName)
        favourites.remove(oldName)
        favourites[newName] = favourite
        serialize()
    }

    fun registerOnVm(receiver: MessageReceiver, context: Context) {
        favourites.values.forEach {
            it.registerOnVm(receiver, context)
        }
    }

    fun deregisterOnVm(receiver: MessageReceiver, context: Context) {
        favourites.values.forEach {
            it.deregisterOnVm(receiver, context)
        }
    }

    operator fun get(name: String): Favourite? {
        return favourites[name]
    }

    operator fun get(position: Int): Favourite? {
        return favourites.entries.sortedBy { it.key }[position].value
    }

    fun indexOf(name: String): Int {
        val favourite = favourites[name]
        return favourites.values.sortedBy { it.name }.indexOf(favourite)
    }

    val size
        get() = favourites.size
}