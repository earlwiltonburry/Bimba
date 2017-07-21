package ml.adamsprogs.bimba

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MessageReceiver: BroadcastReceiver() {
    val onTimetableDownloadListeners: HashSet<OnTimetableDownloadListener> = HashSet()

    override fun onReceive(context: Context?, intent: Intent?) {
        val result = intent?.getStringExtra("result")
        for (listener in onTimetableDownloadListeners) {
            listener.onTimetableDownload(result)
        }
    }

    fun addOnTimetableDownloadListener(listener: OnTimetableDownloadListener) {
        onTimetableDownloadListeners.add(listener)
    }

    fun removeOnTimetableDownloadListener(listener: OnTimetableDownloadListener) {
        onTimetableDownloadListeners.remove(listener)
    }

    interface OnTimetableDownloadListener {
        fun onTimetableDownload(result: String?)
    }
}