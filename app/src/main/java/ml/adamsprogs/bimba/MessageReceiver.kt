package ml.adamsprogs.bimba

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ml.adamsprogs.bimba.models.Departure
import ml.adamsprogs.bimba.models.fromString

class MessageReceiver: BroadcastReceiver() {
    val onTimetableDownloadListeners: HashSet<OnTimetableDownloadListener> = HashSet()
    val onVmListeners: HashSet<OnVmListener> = HashSet()

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "ml.adamsprogs.bimba.timetableDownloaded") {
            val result = intent.getStringExtra("result")
            for (listener in onTimetableDownloadListeners) {
                listener.onTimetableDownload(result)
            }
        }
        if (intent?.action == "ml.adamsprogs.bimba.departuresCreated") {
            val workdays = intent.getStringArrayListExtra("workdays").map { fromString(it)} as ArrayList<Departure>
            val saturdays = intent.getStringArrayListExtra("saturdays").map { fromString(it)} as ArrayList<Departure>
            val sundays = intent.getStringArrayListExtra("sundays").map { fromString(it)} as ArrayList<Departure>
            val departures = HashMap<String, ArrayList<Departure>>()
            departures["workdays"] = workdays
            departures["saturdays"] = saturdays
            departures["sundays"] = sundays
            for (listener in onVmListeners) {
                listener.onVm(departures)
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
        fun onVm(departures: HashMap<String, ArrayList<Departure>>)
    }
}