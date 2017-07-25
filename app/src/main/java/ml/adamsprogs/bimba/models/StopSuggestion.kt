package ml.adamsprogs.bimba.models

import android.os.Parcel
import android.os.Parcelable
import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion

class StopSuggestion(text: String, val id: String, val symbol: String) : SearchSuggestion {
    private val body: String = text
    val CONTENTS_SUGGESTION = 0x0105

    constructor(parcel: Parcel) : this(parcel.readString(), parcel.readString(), parcel.readString())

    override fun describeContents(): Int {
        return CONTENTS_SUGGESTION
    }

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.writeString(body)
        dest?.writeString(id)
        dest?.writeString(symbol)
    }

    override fun getBody(): String {
        return body
    }

    companion object CREATOR : Parcelable.Creator<StopSuggestion> {
        override fun createFromParcel(parcel: Parcel): StopSuggestion {
            return StopSuggestion(parcel)
        }

        override fun newArray(size: Int): Array<StopSuggestion?> {
            return arrayOfNulls(size)
        }
    }
}