package ml.adamsprogs.bimba

import android.app.IntentService
import android.content.Intent
import ml.adamsprogs.bimba.models.*
import okhttp3.*
import com.google.gson.Gson
import java.io.IOException
import java.util.*


class VmClient : IntentService("VmClient") {

    override fun onHandleIntent(intent: Intent?) {
        if (intent != null) {
            val stopId = intent.getStringExtra("stopId")
            if (!isNetworkAvailable(this)) {
                sendResult(createDepartures(stopId))
            } else {
                val stopSymbol = intent.getStringExtra("stopSymbol")
                val departures = createDepartures(stopId)

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
                val responseBody : String?
                try {
                    responseBody = client.newCall(request).execute().body()?.string()
                } catch(e: IOException) {
                    sendResult(departures)
                    return
                }
                val javaRootMapObject = Gson().fromJson(responseBody, HashMap::class.java)
                val times = (javaRootMapObject["success"] as Map<*, *>)["times"] as List<*>
                val date = Calendar.getInstance()
                val today = date.get(Calendar.DAY_OF_WEEK)
                val todayDay = "${date.get(Calendar.DATE)}"
                val todayMode: String
                when (today) {
                    Calendar.SATURDAY -> todayMode = "saturdays"
                    Calendar.SUNDAY -> todayMode = "sundays"
                    else -> todayMode = "workdays"
                }
                val departuresToday = ArrayList<Departure>()
                for (time in times) {
                    val t = time as Map<*, *>
                    val departureDay = (t["departure"] as String).split("T")[0].split("-")[2]

                    val departureTimeRaw = (t["departure"] as String).split("T")[1].split(":")
                    val departureTime = "${departureTimeRaw[0]}:${departureTimeRaw[1]}"
                    val departure = Departure(t["line"] as String, todayMode, departureTime, false,
                            null, t["direction"] as String, t["realTime"] as Boolean,
                            departureDay != todayDay, t["onStopPoint"] as Boolean)
                    departuresToday.add(departure)
                }
                departures[todayMode] = departuresToday
                sendResult(departures)
            }
        }
    }

    private fun sendResult(departures: HashMap<String, ArrayList<Departure>>) {
        val broadcastIntent = Intent()
        broadcastIntent.action = "ml.adamsprogs.bimba.departuresCreated"
        broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT)
        broadcastIntent.putStringArrayListExtra("workdays", departures["workdays"]?.map { it.toString() } as java.util.ArrayList<String>)
        broadcastIntent.putStringArrayListExtra("saturdays", departures["saturdays"]?.map { it.toString() } as java.util.ArrayList<String>)
        broadcastIntent.putStringArrayListExtra("sundays", departures["sundays"]?.map { it.toString() } as java.util.ArrayList<String>)
        sendBroadcast(broadcastIntent)
    }
}
