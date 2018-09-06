package ml.adamsprogs.bimba.datasources

import android.app.Service
import android.content.Intent
import android.os.*
import android.os.Process.THREAD_PRIORITY_BACKGROUND
import com.google.gson.JsonObject
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.*
import ml.adamsprogs.bimba.NetworkStateReceiver
import ml.adamsprogs.bimba.calendarFromIso
import ml.adamsprogs.bimba.models.*
import ml.adamsprogs.bimba.secondsAfterMidnight
import java.util.*
import kotlin.collections.*

class VmService : Service() {
    companion object {
        const val ACTION_READY = "ml.adamsprogs.bimba.action.vm.ready"
        const val EXTRA_DEPARTURES = "ml.adamsprogs.bimba.extra.vm.departures"
        const val EXTRA_PLATE_ID = "ml.adamsprogs.bimba.extra.vm.plate"
        const val EXTRA_STOP_CODE = "ml.adamsprogs.bimba.extra.vm.stop"
        const val TICK_6_ZINA_TIM = 12500L
        const val TICK_6_ZINA_TIM_WITH_MARGIN = TICK_6_ZINA_TIM * 3 / 4
    }

    private var handler: Handler? = null
    private val tick6ZinaTim: Runnable = object : Runnable {
        override fun run() {
            handler!!.postDelayed(this, TICK_6_ZINA_TIM)
            try {
                for (plateId in requests.keys)
                    launch(UI) {
                        withContext(DefaultDispatcher) {
                            downloadVM()
                        }
                    }
            } catch (e: IllegalArgumentException) {
            }
        }
    }
    private val requests = HashMap<String, Int>()
    private val vms = HashMap<String, Set<Plate>>()

    override fun onCreate() {
        val thread = HandlerThread("ServiceStartArguments", THREAD_PRIORITY_BACKGROUND)
        thread.start()
        handler = Handler(thread.looper)
        handler!!.postDelayed(tick6ZinaTim, TICK_6_ZINA_TIM)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null)
            return START_STICKY
        val stopCode = intent.getStringExtra("stop")!!
        val action = intent.action
        val once = intent.getBooleanExtra("once", false)
        if (action == "request") {
            if (isAlreadyRequested(stopCode)) {
                incrementRequest(stopCode)
                sendResult(stopCode)
            } else {
                if (!once)
                    addRequest(stopCode)
                launch(UI) {
                    withContext(DefaultDispatcher) {
                        downloadVM(stopCode)
                    }
                }
            }
        } else if (action == "remove") {
            decrementRequest(stopCode)
            cleanRequests()
        }
        return START_STICKY
    }

    private fun cleanRequests() {
        requests.forEach {
            if (it.value <= 0)
                requests.remove(it.key)
        }
    }

    private fun addRequest(stopCode: String) {
        if (requests[stopCode] == null)
            requests[stopCode] = 0
        requests[stopCode] = requests[stopCode]!! + 1
    }

    private fun incrementRequest(stopCode: String) {
        requests[stopCode] = requests[stopCode]!! + 1
    }

    private fun decrementRequest(stopCode: String) {
        requests[stopCode] = requests[stopCode]!! - 1
    }

    private fun isAlreadyRequested(stopCode: String): Boolean {
        return stopCode in requests
    }


    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
    }

    private suspend fun downloadVM() {
        vms.forEach {
            downloadVM(it.key)
        }
    }

    private suspend fun downloadVM(stopCode: String) {
        if (!NetworkStateReceiver.isNetworkAvailable()) {
            vms[stopCode] = emptySet()
            sendResult(stopCode, null, null)
            return
        }

        val javaRootMapObject = VmClient.getVmClient().makeRequest("getTimes", """{"symbol": "$stopCode"}""")

        if (!javaRootMapObject.has("success")) {
            sendResult(stopCode, null, null)
            return
        }

        val times = (javaRootMapObject["success"].asJsonObject)["times"].asJsonArray.map { it.asJsonObject }
        parseTimes(stopCode, times)
    }

    private fun parseTimes(stopCode: String, times: List<JsonObject>) {
        val date = Calendar.getInstance()
        val todayDay = "${date.get(Calendar.DATE)}".padStart(2, '0')

        val departures = HashMap<Plate.ID, HashSet<Departure>>()

        times.forEach {
            val thisLine = it["line"].asString
            val thisHeadsign = it["direction"].asString
            val thisPlateId = Plate.ID(thisLine, stopCode, thisHeadsign)
            if (departures[thisPlateId] == null)
                departures[thisPlateId] = HashSet()
            val departureDay = (it["departure"].asString).split("T")[0].split("-")[2]
            val departureTime = calendarFromIso(it["departure"].asString).secondsAfterMidnight()
            val departure = Departure(thisLine, listOf(-1), departureTime, false,
                    ArrayList(), it["direction"].asString, it["realTime"].asBoolean,
                    departureDay != todayDay, it["onStopPoint"].asBoolean)
            departures[thisPlateId]!!.add(departure)
        }

        departures.forEach {
            val departuresForPlate = HashMap<Int, HashSet<Departure>>()
            departuresForPlate[-1] = it.value
            val vm = HashSet<Plate>()
            vm.add(Plate(it.key, departuresForPlate))
            vms[stopCode] = vm
            if (departures.isEmpty())
                sendResult(stopCode, it.key, null)
            else
                sendResult(stopCode, it.key, it.value)
        }

    }

    private fun sendResult(stopCode: String) {
        vms[stopCode]?.forEach {
            sendResult(it.id.stop, it.id, it.departures?.get(-1))
        }

    }

    private fun sendResult(stopCode: String, plateId: Plate.ID?, departures: HashSet<Departure>?) {
        val broadcastIntent = Intent()
        broadcastIntent.action = ACTION_READY
        broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT)
        if (departures != null)
            broadcastIntent.putStringArrayListExtra(EXTRA_DEPARTURES, departures.map { it.toString() } as ArrayList)
        broadcastIntent.putExtra(EXTRA_PLATE_ID, plateId)
        broadcastIntent.putExtra(EXTRA_STOP_CODE, stopCode)
        sendBroadcast(broadcastIntent)
    }
}

//note application stops the service on exit

