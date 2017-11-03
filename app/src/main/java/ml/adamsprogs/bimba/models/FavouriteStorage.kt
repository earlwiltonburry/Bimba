package ml.adamsprogs.bimba.models

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject


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
    val favouritesList: List<Favourite>
        get() {
            return favourites.values.toList()
        }

    init {
        val favouritesString = preferences.getString("favourites", "{}")
        val favouritesMap = Gson().fromJson(favouritesString, JsonObject::class.java)
        for ((name, jsonTimetables) in favouritesMap.entrySet()) {
            val timetables = HashSet<Plate>()
            jsonTimetables.asJsonArray.mapTo(timetables) { Plate(it.asJsonObject["line"].asString, it.asJsonObject["stop"].asString, null) }
            favourites[name] = Favourite(name, timetables)
        }
    }

    override fun iterator(): Iterator<Favourite> = favourites.values.iterator()

    fun has(name: String): Boolean = favourites.contains(name)

    fun add(name: String, timetables: HashSet<Plate>) {
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

    fun delete(name: String, plate: Plate) {
        favourites[name]?.delete(plate)
        serialize()
    }

    private fun serialize() {
        val rootObject = JsonObject()
        for ((name, favourite) in favourites) {
            val timetables = JsonArray()
            for (timetable in favourite.timetables) {
                val element = JsonObject()
                element.addProperty("stop", timetable.stop)
                element.addProperty("line", timetable.line)
                timetables.add(element)
            }
            rootObject.add(name, timetables)
        }
        val favouritesString = Gson().toJson(rootObject)
        val editor = preferences.edit()
        editor.putString("favourites", favouritesString)
        editor.apply()

    }

    fun detach(name: String, plate: Plate, newName: String) {
        val array = HashSet<Plate>()
        array.add(plate)
        favourites[newName] = Favourite(newName, array)
        serialize()

        delete(name, plate)
    }

    fun merge(names: ArrayList<String>) {
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
}