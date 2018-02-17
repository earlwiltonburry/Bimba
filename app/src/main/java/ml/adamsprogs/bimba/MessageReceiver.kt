package ml.adamsprogs.bimba

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ml.adamsprogs.bimba.models.Departure
import ml.adamsprogs.bimba.models.Plate

class MessageReceiver private constructor() : BroadcastReceiver() {
    companion object {
        private var receiver:MessageReceiver? = null
        fun getMessageReceiver(): MessageReceiver {
            if (receiver == null)
                receiver = MessageReceiver()
            return receiver as MessageReceiver
        }
    }

    private val onTimetableDownloadListeners: HashSet<OnTimetableDownloadListener> = HashSet()
    private val onVmListeners: HashSet<OnVmListener> = HashSet()

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == TimetableDownloader.ACTION_DOWNLOADED) {
            val result = intent.getStringExtra(TimetableDownloader.EXTRA_RESULT)
            for (listener in onTimetableDownloadListeners) {
                listener.onTimetableDownload(result)
            }
        }
        if (intent?.action == VmClient.ACTION_READY) {
            val departures = intent.getStringArrayListExtra(VmClient.EXTRA_DEPARTURES)?.map { Departure.fromString(it) }?.toSet() as HashSet<Departure>?
            val plateId = intent.getSerializableExtra(VmClient.EXTRA_PLATE_ID) as Plate.ID
            for (listener in onVmListeners) {
                listener.onVm(departures, plateId)
            }
        }
    }

    fun addOnTimetableDownloadListener(listener: OnTimetableDownloadListener) {
        onTimetableDownloadListeners.add(listener)
    }

    fun removeOnTimetableDownloadListener(listener: OnTimetableDownloadListener) {
        onTimetableDownloadListeners.remove(listener)
    }

    fun addOnVmListener(listener: OnVmListener) {
        onVmListeners.add(listener)
    }

    fun removeOnVmListener(listener: OnVmListener) {
        onVmListeners.remove(listener)
    }

    interface OnTimetableDownloadListener {
        fun onTimetableDownload(result: String?)
    }

    interface OnVmListener {
        fun onVm(vmDepartures: Set<Departure>?, plateId: Plate.ID)
    }
}