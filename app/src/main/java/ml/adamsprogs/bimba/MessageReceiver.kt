package ml.adamsprogs.bimba

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MessageReceiver: BroadcastReceiver() {
    val onTimetableDownloadListeners: HashSet<OnTimetableDownloadListener> = HashSet()

    override fun onReceive(context: Context?, intent: Intent?) {
        for (listener in onTimetableDownloadListeners) {
            listener.onTimetableDownload()
        }
    }

    fun addOnTimetableDownloadListener(listener: OnTimetableDownloadListener) {
        onTimetableDownloadListeners.add(listener)
    }

    fun removeOnTimetableDownloadListener(listener: OnTimetableDownloadListener) {
        onTimetableDownloadListeners.remove(listener)
    }

    interface OnTimetableDownloadListener {
        fun onTimetableDownload()
    }
}