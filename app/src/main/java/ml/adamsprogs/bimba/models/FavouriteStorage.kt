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
        refresh()
    }

    fun refresh() {
        val favouritesString = preferences.getString("favourites", "{}")
        val favouritesMap = Gson().fromJson(favouritesString, JsonObject::class.java)
        for ((name, jsonTimetables) in favouritesMap.entrySet()) {
            val timetables = ArrayList<HashMap<String, String>>()
            for (jsonTimetable in jsonTimetables.asJsonArray) {
                val timetable = HashMap<String, String>()
                timetable["stop"] = jsonTimetable.asJsonObject["stop"].asString
                timetable["line"] = jsonTimetable.asJsonObject["line"].asString
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
        Log.i("ROW", "Deleting $name")
        Log.i("ROW", "$name is in favourites?: ${favourites.contains(name)}")
        val b = favourites.remove(name)
        Log.i("ROW", "deleted: $b")
        serialize()
    }

    fun delete(name: String, stop: String, line: String) {
        Log.i("ROW", "delete $name, $stop, $line")
        favourites[name]?.delete(stop, line)
        //todo check empty
        serialize()
    }

    fun serialize() {
        val rootObject = JsonObject()
        for ((name, favourite) in favourites) {
            val timetables = JsonArray()
            for (timetable in favourite.timetables) {
                val element = JsonObject()
                element.addProperty("stop", timetable["stop"])
                element.addProperty("line", timetable["line"])
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
        element["stop"] = stop
        element["line"] = line
        val array = ArrayList<HashMap<String, String>>()
        array.add(element)
        favourites[newName] = Favourite(newName, array)
        serialize()

        delete(name, stop, line)
    }
}