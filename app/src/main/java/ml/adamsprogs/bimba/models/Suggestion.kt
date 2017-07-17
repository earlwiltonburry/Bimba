package ml.adamsprogs.bimba.models

import android.os.Parcel
import android.os.Parcelable
import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion

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