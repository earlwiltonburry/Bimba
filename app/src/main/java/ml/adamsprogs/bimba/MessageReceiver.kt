package ml.adamsprogs.bimba

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ml.adamsprogs.bimba.models.Departure

class MessageReceiver : BroadcastReceiver() {
    private val onTimetableDownloadListeners: HashSet<OnTimetableDownloadListener> = HashSet()
    private val onVmListeners: HashSet<OnVmListener> = HashSet()

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == TimetableDownloader.ACTION_DOWNLOADED) {
            val result = intent.getStringExtra(TimetableDownloader.EXTRA_RESULT)
            for (listener in onTimetableDownloadListeners) {
                listener.onTimetableDownload(result)
            }
        }
        if (intent?.action == VmClient.ACTION_DEPARTURES_CREATED) {
            val departures = intent.getStringArrayListExtra(VmClient.EXTRA_DEPARTURES).map { Departure.fromString(it) } as ArrayList<Departure>
            val requester = intent.getStringExtra(VmClient.EXTRA_REQUESTER)
            for (listener in onVmListeners) {
                listener.onVm(departures, requester)
            }
        }
        if (intent?.action == VmClient.ACTION_NO_DEPARTURES) {
            val requester = intent.getStringExtra(VmClient.EXTRA_REQUESTER)
            for (listener in onVmListeners) {
                listener.onVm(null, requester)
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
        fun onVm(vmDepartures: ArrayList<Departure>?, requester: String)
    }
}