package ml.adamsprogs.bimba.collections

import android.content.*
import com.google.gson.*
import ml.adamsprogs.bimba.*
import ml.adamsprogs.bimba.models.*
import java.util.Calendar


class FavouriteStorage private constructor(context: Context) : Iterable<Favourite> {
    companion object {
        private var favouriteStorage: FavouriteStorage? = null
        fun getFavouriteStorage(context: Context? = null): FavouriteStorage {
            return if (favouriteStorage == null) {
                if (context == null)
                    throw IllegalArgumentException("requested new storage appContext not given")
                else {
                    favouriteStorage = FavouriteStorage(context)
                    favouriteStorage as FavouriteStorage
                }
            } else
                favouriteStorage as FavouriteStorage
        }
    }

    val favourites = HashMap<String, Favourite>()
    private val positionIndex = IndexableTreeSet<String>()
    private val preferences: SharedPreferences = context.getSharedPreferences("ml.adamsprogs.bimba.prefs", Context.MODE_PRIVATE)

    init {
        val favouritesString = preferences.getString("favourites", "{}")
        val favouritesMap = Gson().fromJson(favouritesString, JsonObject::class.java)
        for ((name, jsonTimetables) in favouritesMap.entrySet()) {
            val timetables = HashSet<StopSegment>()
            jsonTimetables.asJsonArray.mapTo(timetables) {
                val stopSegment = StopSegment(it.asJsonObject["stop"].asString, null)
                val plates = HashSet<Plate.ID>()
                it.asJsonObject["plates"].asJsonArray.mapTo(plates) {
                    Plate.ID(it.asJsonObject["line"].asString,
                            it.asJsonObject["stop"].asString,
                            it.asJsonObject["headsign"].asString)
                }
                stopSegment.plates = plates
                stopSegment
            }
            favourites[name] = Favourite(name, timetables, context)
            positionIndex.add(name)
        }
    }

    override fun iterator(): Iterator<Favourite> = favourites.values.iterator()

    fun has(name: String): Boolean = favourites.contains(name)

    fun add(name: String, timetables: HashSet<StopSegment>, context: Context) {
        if (favourites[name] == null) {
            favourites[name] = Favourite(name, timetables, context)
            addIndex(name)
            serialize()
        }
    }

    fun add(name: String, favourite: Favourite) {
        if (favourites[name] == null) {
            favourites[name] = favourite
            addIndex(name)
            serialize()
        }
    }

    private fun addIndex(name: String) {
        positionIndex.add(name)
    }

    fun delete(name: String) {
        favourites.remove(name)
        positionIndex.remove(name)
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
            for (timetable in favourite.segments) {
                val segment = JsonObject()
                segment.addProperty("stop", timetable.stop)
                val plates = JsonArray()
                for (plate in timetable.plates ?: HashSet()) {
                    val element = JsonObject()
                    element.addProperty("stop", plate.stop)
                    element.addProperty("line", plate.line)
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

    fun merge(names: List<String>, context: Context) {
        if (names.size < 2)
            return

        val newCache = HashMap<Int, ArrayList<Departure>>()
        names.forEach {
            favourites[it]!!.fullTimetable().forEach {
                if (newCache[it.key] == null)
                    newCache[it.key] = ArrayList()
                newCache[it.key]!!.addAll(it.value)
            }
        }
        val now = Calendar.getInstance().secondsAfterMidnight()
        newCache.forEach {
            it.value.sortBy { it.timeTill(now) }
        }
        val newFavourite = Favourite(names[0], HashSet(), newCache, context)
        for (name in names) {
            newFavourite.segments.addAll(favourites[name]!!.segments)
            favourites.remove(name)
            positionIndex.remove(name)
        }
        favourites[names[0]] = newFavourite
        addIndex(names[0])

        serialize()
    }

    fun rename(oldName: String, newName: String) {
        val favourite = favourites[oldName] ?: return
        favourite.rename(newName)
        favourites.remove(oldName)
        positionIndex.remove(oldName)
        favourites[newName] = favourite
        addIndex(newName)
        serialize()
    }

    operator fun get(name: String): Favourite? {
        return favourites[name]
    }

    operator fun get(position: Int): Favourite? {
        return favourites[positionIndex[position]]
    }

    fun indexOf(name: String): Int {
        return positionIndex.indexOf(name)
    }

    val size
        get() = favourites.size
}