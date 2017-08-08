package ml.adamsprogs.bimba.models

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject


class FavouriteStorage private constructor(context: Context) {
    companion object {
        private var favouriteStorage: FavouriteStorage? = null
        fun getFavouriteStorage(context: Context? = null): FavouriteStorage {
            if (favouriteStorage == null) {
                if (context == null)
                    throw IllegalArgumentException("requested new storage context not given")
                else {
                    favouriteStorage = FavouriteStorage(context)
                    return favouriteStorage as FavouriteStorage
                }
            } else
                return favouriteStorage as FavouriteStorage
        }
    }
    val favourites = HashMap<String, Favourite>()
    val preferences: SharedPreferences = context.getSharedPreferences("ml.adamsprogs.bimba.prefs", Context.MODE_PRIVATE)
    val favouritesList: List<Favourite>
        get() {
            return favourites.values.toList()
        }

    init {
        val favouritesString = preferences.getString("favourites", "{}")
        val favouritesMap = Gson().fromJson(favouritesString, JsonObject::class.java)
        for ((name, jsonTimetables) in favouritesMap.entrySet()) {
            val timetables = ArrayList<HashMap<String, String>>()
            for (jsonTimetable in jsonTimetables.asJsonArray) {
                val timetable = HashMap<String, String>()
                timetable[Favourite.TAG_STOP] = jsonTimetable.asJsonObject[Favourite.TAG_STOP].asString
                timetable[Favourite.TAG_LINE] = jsonTimetable.asJsonObject[Favourite.TAG_LINE].asString
                timetables.add(timetable)
            }
            favourites[name] = Favourite(name, timetables)
        }
    }

    fun has(name: String): Boolean = favourites.contains(name)

    fun add(name: String, timetables: ArrayList<HashMap<String, String>>) {
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

    fun delete(name: String, stop: String, line: String) {
        favourites[name]?.delete(stop, line)
        serialize()
    }

    fun serialize() {
        val rootObject = JsonObject()
        for ((name, favourite) in favourites) {
            val timetables = JsonArray()
            for (timetable in favourite.timetables) {
                val element = JsonObject()
                element.addProperty(Favourite.TAG_STOP, timetable[Favourite.TAG_STOP])
                element.addProperty(Favourite.TAG_LINE, timetable[Favourite.TAG_LINE])
                timetables.add(element)
            }
            rootObject.add(name, timetables)
        }
        val favouritesString = Gson().toJson(rootObject)
        Log.i("FAB", favouritesString)
        val editor = preferences.edit()
        editor.putString("favourites", favouritesString)
        editor.apply()
    }

    fun detach(name: String, stop: String, line: String, newName: String) {
        val element = HashMap<String, String>()
        element[Favourite.TAG_STOP] = stop
        element[Favourite.TAG_LINE] = line
        val array = ArrayList<HashMap<String, String>>()
        array.add(element)
        favourites[newName] = Favourite(newName, array)
        serialize()

        delete(name, stop, line)
    }

    fun merge(names: ArrayList<String>) {
        if (names.size < 2 )
            return
        val newFavourite = Favourite(names[0], ArrayList<HashMap<String, String>>())
        for (name in names) {
            newFavourite.timetables.addAll(favourites[name]!!.timetables)
            favourites.remove(name)
        }
        favourites[names[0]] = newFavourite

        serialize()
    }

    fun rename(oldName: String, newName: String) {
        val favourite = favourites[oldName] ?: return
        favourite.name = newName
        favourites.remove(oldName)
        favourites[newName] = favourite
        serialize()
    }
}