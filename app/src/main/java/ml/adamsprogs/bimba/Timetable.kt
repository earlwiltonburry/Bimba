package ml.adamsprogs.bimba

import android.content.Context
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabase
import android.os.Parcel
import android.os.Parcelable
import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion
import java.io.File

class Timetable() {
    var db: SQLiteDatabase? = null
    lateinit var context: Context

    constructor(context: Context) : this() {
        this.context = context
        readDbFile()
    }

    fun refresh() {
        readDbFile()
    }

    private fun readDbFile() {
        try {
            db = SQLiteDatabase.openDatabase(File(context.filesDir, "new_timetable.db").path,
                    null, SQLiteDatabase.OPEN_READONLY)
        } catch(e: SQLiteCantOpenDatabaseException) {
            db = null
        }
    }

    fun getStops(): ArrayList<Suggestion>? {
        if (db == null)
            return null
        val cursor = db!!.rawQuery("select name ||char(10)|| headsigns as suggestion, id from stops" +
                " join nodes on(stops.symbol = nodes.symbol);", null)
        val stops = ArrayList<Suggestion>()
        while (cursor.moveToNext())
            stops.add(Suggestion(cursor.getString(0), cursor.getString(1)))
        cursor.close()
        return stops
    }

    class Suggestion(text: String, val id: String) : SearchSuggestion {
        private val body: String = text

        constructor(parcel: Parcel) : this(parcel.readString(), parcel.readString())

        override fun describeContents(): Int {
            TODO("not implemented")
        }

        override fun writeToParcel(dest: Parcel?, flags: Int) {
            dest?.writeString(body)
            dest?.writeString(id)
        }

        override fun getBody(): String {
            return body
        }

        companion object CREATOR : Parcelable.Creator<Suggestion> {
            override fun createFromParcel(parcel: Parcel): Suggestion {
                return Suggestion(parcel)
            }

            override fun newArray(size: Int): Array<Suggestion?> {
                return arrayOfNulls(size)
            }
        }
    }
}