package ml.adamsprogs.bimba.models

import android.os.Parcel
import android.os.Parcelable
import ml.adamsprogs.bimba.models.gtfs.AgencyAndId

data class StopSegment(val stop: AgencyAndId, var plates: Set<Plate.ID>?) : Parcelable {
    constructor(parcel: Parcel) : this(
            parcel.readSerializable() as AgencyAndId,
            parcel.readString().split(";").map { Plate.ID.fromString(it) }.toSet()
    )

    companion object CREATOR : Parcelable.Creator<StopSegment> {
        override fun createFromParcel(parcel: Parcel): StopSegment {
            return StopSegment(parcel)
        }

        override fun newArray(size: Int): Array<StopSegment?> {
            return arrayOfNulls(size)
        }
    }

    fun fillPlates() {
        plates = Timetable.getTimetable().getPlatesForStop(stop)
    }

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.writeSerializable(stop)
        if (plates != null)
            dest?.writeString(plates!!.joinToString(";") { it.toString() })
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
        return super.hashCode()
    }

    fun contains(plateId: Plate.ID): Boolean {
        if (plates == null)
            return false
        return plates!!.contains(plateId)
    }

    fun remove(plateId: Plate.ID) {
        (plates as HashSet).remove(plateId)
    }

    val size: Int
        get() = plates?.size ?: 0
}