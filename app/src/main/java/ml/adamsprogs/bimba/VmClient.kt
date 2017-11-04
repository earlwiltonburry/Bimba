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
        val EXTRA_SIZE = "size"
        val EXTRA_ID = "id"
    }

    override fun onHandleIntent(intent: Intent?) {
        if (intent != null) {
            val requester = intent.getStringExtra(EXTRA_REQUESTER)
            Log.i("VM", "Request for $requester received")
            val id = intent.getStringExtra(EXTRA_ID)
            val size = intent.getIntExtra(EXTRA_SIZE, -1)

            if (!NetworkStateReceiver.isNetworkAvailable(this)) {
                sendNullResult(requester, id, size)
                return
            }

            val stopSymbol = intent.getStringExtra(EXTRA_STOP_SYMBOL)
            if (stopSymbol == null) {
                sendNullResult(requester, id, size)
                return
            }
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

            Log.i("VM", "created http request")

            val responseBody: String?
            try {
                responseBody = client.newCall(request).execute().body()?.string()
            } catch (e: IOException) {
                sendNullResult(requester, id, size)
                return
            }

            Log.i("VM", "received http response")

            if (responseBody?.get(0) == '<') {
                sendNullResult(requester, id, size)
                return
            }

            val javaRootMapObject = Gson().fromJson(responseBody, HashMap::class.java)
            val times = (javaRootMapObject["success"] as Map<*, *>)["times"] as List<*>
            val date = Calendar.getInstance()
            val todayDay = "${date.get(Calendar.DATE)}".padStart(2, '0')
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

            Log.i("VM", "parsed http response")
            if (departuresToday.isEmpty())
                sendNullResult(requester, id, size)
            else
                sendResult(departuresToday, requester, id, size)
        }
    }

    private fun sendNullResult(requester: String, id: String, size: Int) {
        val broadcastIntent = Intent()
        broadcastIntent.action = ACTION_NO_DEPARTURES
        broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT)
        broadcastIntent.putExtra(EXTRA_REQUESTER, requester)
        broadcastIntent.putExtra(EXTRA_ID, id)
        broadcastIntent.putExtra(EXTRA_SIZE, size)
        sendBroadcast(broadcastIntent)
    }

    private fun sendResult(departures: ArrayList<Departure>, requester: String, id: String, size: Int) {
        val broadcastIntent = Intent()
        broadcastIntent.action = ACTION_DEPARTURES_CREATED
        broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT)
        broadcastIntent.putStringArrayListExtra(EXTRA_DEPARTURES, departures.map { it.toString() } as java.util.ArrayList<String>)
        broadcastIntent.putExtra(EXTRA_REQUESTER, requester)
        broadcastIntent.putExtra(EXTRA_ID, id)
        broadcastIntent.putExtra(EXTRA_SIZE, size)
        sendBroadcast(broadcastIntent)
        Log.i("VM", "sent response")
    }
}
