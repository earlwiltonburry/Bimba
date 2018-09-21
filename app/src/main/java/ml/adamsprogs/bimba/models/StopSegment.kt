package ml.adamsprogs.bimba.models

import android.os.Parcel
import android.os.Parcelable
import ml.adamsprogs.bimba.safeSplit

data class StopSegment(val stop: String, var plates: Set<Plate.ID>?) : Parcelable {
    constructor(parcel: Parcel) : this(
            parcel.readSerializable() as String,
            parcel.readString().safeSplit(";")?.map { Plate.ID.fromString(it) }?.toSet()
    )

    companion object CREATOR : Parcelable.Creator<StopSegment> {
        override fun createFromParcel(parcel: Parcel): StopSegment {
            return StopSegment(parcel)
        }

        override fun newArray(size: Int): Array<StopSegment?> {
            return arrayOfNulls(size)
        }
    }

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.writeSerializable(stop)
        if (plates != null)
            dest?.writeString(plates!!.joinToString(";") { it.toString() })
        else
            dest?.writeString("null")
    }

    override fun describeContents(): Int {
        return Parcelable.CONTENTS_FILE_DESCRIPTOR
    }

    override fun equals(other: Any?): Boolean {
        if (other !is StopSegment)
            return false
        if (this.stop != other.stop)
            return false
        if (this.plates != null && other.plates == null)
            return false
        if (this.plates == null && other.plates != null)
            return false
        if (this.plates == null && other.plates == null)
            return true
        if (this.plates!!.size != other.plates!!.size)
            return false
        if (this.plates!!.containsAll(other.plates!!))
            return true
        return false
    }

    override fun hashCode(): Int {
        var hashCode = stop.hashCode()
        plates?.forEach { hashCode = 31 * hashCode + it.hashCode() }
        return hashCode
    }

    operator fun contains(plateId: Plate.ID): Boolean {
        if (plates == null)
            return plateId.stop == stop
        return plates!!.contains(plateId)
    }

    fun remove(plateId: Plate.ID): Boolean {
        if (plates == null)
            return false

        plates = plates!!.asSequence().filter { it != plateId }.toSet()
        return true
    }

    override fun toString(): String {
        var s = "$stop: "
        if (plates == null)
            s += "NULL"
        else {
            s += "{"
            s += plates!!.joinToString { it.toString() }
            s += "}"
        }
        return s
    }

    val size: Int
        get() = plates?.size ?: 0
}