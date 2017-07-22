package ml.adamsprogs.bimba

import android.app.IntentService
import android.content.Intent
import ml.adamsprogs.bimba.models.Departure

import ml.adamsprogs.bimba.models.createDepartures

class VmClient : IntentService("VmClient") {

    override fun onHandleIntent(intent: Intent?) {
        if (intent != null) {
            val stopId = intent.getStringExtra("stopId")
            if (!isNetworkAvailable(this)) {
                sendResult(createDepartures(this, stopId))
            } else {
                //todo download vm
            }
        }
    }

    private fun sendResult(departures: HashMap<String, ArrayList<Departure>>) {
        val broadcastIntent = Intent()
        broadcastIntent.action = "ml.adamsprogs.bimba.departuresCreated"
        broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT)
        broadcastIntent.putStringArrayListExtra("workdays", departures["workdays"]?.map{it.toString()} as java.util.ArrayList<String>)
        broadcastIntent.putStringArrayListExtra("saturdays", departures["saturdays"]?.map{it.toString()} as java.util.ArrayList<String>)
        broadcastIntent.putStringArrayListExtra("sundays", departures["sundays"]?.map{it.toString()} as java.util.ArrayList<String>)
        sendBroadcast(broadcastIntent)
    }
}
