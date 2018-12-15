package ml.adamsprogs.bimba.collections

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.*
import ml.adamsprogs.bimba.models.Departure
import ml.adamsprogs.bimba.models.Favourite
import ml.adamsprogs.bimba.models.Plate
import ml.adamsprogs.bimba.models.StopSegment
import ml.adamsprogs.bimba.secondsAfterMidnight
import java.util.Calendar
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set


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
        JsonParser().parse(favouritesString).asJsonObject.entrySet().forEach { (name, timetables) ->
            timetables.asJsonArray.map {
                val plates = it.asJsonObject["plates"].let { element ->
                    if (element == null || element.isJsonNull)
                        null
                    else {
                        element.asJsonArray.map { it ->
                            it.asJsonObject.let { id ->
                                Plate.ID(id["line"].asString, id["stop"].asString, id["headsign"].asString)
                            }
                        }.toHashSet()
                    }
                }
                StopSegment(it.asJsonObject["stop"].asString, plates)
            }.toHashSet().let {
                favourites[name] = Favourite(name, it, context)
            }
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

    fun delete(name: String, plate: Plate.ID): Boolean {
        return favourites[name]?.delete(plate).let {
            serialize()
            it
        } ?: false
    }

    private fun serialize() {
        val rootObject = JsonObject()
        for ((name, favourite) in favourites) {
            val timetables = JsonArray()
            for (timetable in favourite.segments) {
                val segment = JsonObject()
                segment.addProperty("stop", timetable.stop)
                val plates =
                        if (timetable.plates == null)
                            JsonNull.INSTANCE
                        else
                            JsonArray().apply {
                                for (plate in timetable.plates ?: HashSet()) {
                                    val element = JsonObject()
                                    element.addProperty("stop", plate.stop)
                                    element.addProperty("line", plate.line)
                                    element.addProperty("headsign", plate.headsign)
                                    add(element)
                                }
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

        val newCache = HashMap<String, ArrayList<Departure>>()
        names.forEach { name ->
            favourites[name]!!.fullTimetable().forEach {
                if (newCache[it.key] == null)
                    newCache[it.key] = ArrayList()
                newCache[it.key]!!.addAll(it.value)
            }
        }
        val now = Calendar.getInstance().secondsAfterMidnight()
        newCache.forEach { entry ->
            entry.value.sortBy { it.timeTill(now) }
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

    operator fun set(name: String, value: Favourite) {
        favourites[name] = value
        serialize()
    }

    fun indexOf(name: String): Int {
        return positionIndex.indexOf(name)
    }

    val size
        get() = favourites.size
}