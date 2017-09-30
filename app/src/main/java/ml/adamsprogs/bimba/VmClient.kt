package ml.adamsprogs.bimba

import android.app.IntentService
import android.content.Intent
import android.util.Log
import ml.adamsprogs.bimba.models.*
import okhttp3.*
import com.google.gson.Gson
import java.io.IOException
import java.util.*


class VmClient : IntentService("VmClient") {
    companion object {
        val ACTION_DEPARTURES_CREATED = "ml.adamsprogs.bimba.departuresCreated"
        val ACTION_NO_DEPARTURES = "ml.adamsprogs.bimba.noVM"
        val EXTRA_STOP_SYMBOL = "stopSymbol"
        val EXTRA_LINE_NUMBER = "lineNumber"
        val EXTRA_REQUESTER = "requester"
        val EXTRA_DEPARTURES = "departures"
    }

    override fun onHandleIntent(intent: Intent?) {
        if (intent != null) {
            val requester = intent.getStringExtra(EXTRA_REQUESTER)

            if (!NetworkStateReceiver.isNetworkAvailable(this)) {
                sendNullResult(requester)
                return
            }

            val stopSymbol = intent.getStringExtra(EXTRA_STOP_SYMBOL)
            val lineNumber = intent.getStringExtra(EXTRA_LINE_NUMBER)

            val client = OkHttpClient()
            val url = "http://www.peka.poznan.pl/vm/method.vm?ts=${Calendar.getInstance().timeInMillis}"
            val formBody = FormBody.Builder()
                    .add("method", "getTimes")
                    .add("p0", "{\"symbol\": \"$stopSymbol\"}")
                    .build()
            val request = Request.Builder()
                    .url(url)
                    .post(formBody)
                    .build()
            val responseBody: String?
            try {
                responseBody = client.newCall(request).execute().body()?.string()
            } catch(e: IOException) {
                sendNullResult(requester)
                return
            }

            if (responseBody?.get(0) == '<') {
                sendNullResult(requester)
                return
            }

            val javaRootMapObject = Gson().fromJson(responseBody, HashMap::class.java)
            val times = (javaRootMapObject["success"] as Map<*, *>)["times"] as List<*>
            val date = Calendar.getInstance()
            val todayDay = "${date.get(Calendar.DATE)}"
            val todayMode = date.getMode()
            val departuresToday = ArrayList<Departure>()
            for (time in times) {
                val t = time as Map<*, *>
                if (lineNumber == null || t["line"] == lineNumber) {
                    val departureDay = (t["departure"] as String).split("T")[0].split("-")[2]

                    val departureTimeRaw = (t["departure"] as String).split("T")[1].split(":")
                    val departureTime = "${departureTimeRaw[0]}:${departureTimeRaw[1]}"
                    val departure = Departure(t["line"] as String, todayMode, departureTime, false,
                            null, t["direction"] as String, t["realTime"] as Boolean,
                            departureDay != todayDay, t["onStopPoint"] as Boolean)
                    departuresToday.add(departure)
                }
            }
            if (departuresToday.isEmpty())
                sendNullResult(requester)
            else
                sendResult(departuresToday, requester)
        }
    }

    private fun sendNullResult(requester: String) {
        val broadcastIntent = Intent()
        broadcastIntent.action = ACTION_NO_DEPARTURES
        broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT)
        broadcastIntent.putExtra(EXTRA_REQUESTER, requester)
        sendBroadcast(broadcastIntent)
    }

    private fun sendResult(departures: ArrayList<Departure>, requester: String) {
        val broadcastIntent = Intent()
        broadcastIntent.action = ACTION_DEPARTURES_CREATED
        broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT)
        broadcastIntent.putStringArrayListExtra(EXTRA_DEPARTURES, departures.map { it.toString() } as java.util.ArrayList<String>)
        broadcastIntent.putExtra(EXTRA_REQUESTER, requester)
        sendBroadcast(broadcastIntent)
    }
}
