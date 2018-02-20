package ml.adamsprogs.bimba.datasources

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Process.THREAD_PRIORITY_BACKGROUND
import android.util.Log
import com.google.gson.Gson
import ml.adamsprogs.bimba.NetworkStateReceiver
import ml.adamsprogs.bimba.calendarFromIso
import ml.adamsprogs.bimba.models.*
import okhttp3.FormBody
import okhttp3.OkHttpClient
import ml.adamsprogs.bimba.gtfs.AgencyAndId
import ml.adamsprogs.bimba.secondsAfterMidnight
import java.io.IOException
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.concurrent.thread

class VmClient : Service() {
    companion object {
        const val ACTION_READY = "ml.adamsprogs.bimba.action.vm.ready"
        const val EXTRA_DEPARTURES = "ml.adamsprogs.bimba.extra.vm.departures"
        const val EXTRA_PLATE_ID = "ml.adamsprogs.bimba.extra.vm.plate"
    }
    private var handler: Handler? = null
    private val tick6ZinaTim: Runnable = object : Runnable {
        override fun run() {
            handler!!.postDelayed(this, (12.5 * 1000).toLong())
            for (plateId in requests.keys)
                downloadVM()
        }
    }
    private val requests = HashMap<AgencyAndId, Set<Request>>()
    private val vms = HashMap<AgencyAndId, HashSet<Plate>>() //HashSet<Departure>?
    private val timetable = Timetable.getTimetable()


    override fun onCreate() {
        val thread = HandlerThread("ServiceStartArguments", THREAD_PRIORITY_BACKGROUND)
        thread.start()
        handler = Handler(thread.looper)
        handler!!.postDelayed(tick6ZinaTim, (12.5 * 1000).toLong())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val stopSegment = intent?.getParcelableExtra<StopSegment>("stop")!!
        if (stopSegment.plates == null)
            throw EmptyStopSegmentException()
        val action = intent.action
        val once = intent.getBooleanExtra("once", false)
        if (action == "request") {
            if (isAlreadyRequested(stopSegment)) {
                incrementRequest(stopSegment)
                sendResult(stopSegment)
            } else {
                if (!once)
                    addRequest(stopSegment)
                thread {
                    downloadVM(stopSegment)
                }
            }
        } else if (action == "remove") {
            decrementRequest(stopSegment)
            cleanRequests()
        }
        return START_STICKY
    }

    private fun cleanRequests() {
        val newMap = HashMap<AgencyAndId, Set<Request>>()
        requests.forEach {
            newMap[it.key] = it.value.minus(it.value.filter { it.times == 0 })
        }
        newMap.forEach { requests[it.key] = it.value }
    }

    private fun addRequest(stopSegment: StopSegment) {
        if (requests[stopSegment.stop] == null) {
            requests[stopSegment.stop] = stopSegment.plates!!
                    .map { Request(it, 1) }
                    .toSet()
        } else {
            var req = requests[stopSegment.stop]!!
            stopSegment.plates!!.forEach {
                val plate = it
                if (req.any { it.plate == plate }) {
                    req.filter { it.plate == plate }[0].times++
                } else {
                    req = req.plus(Request(it, 1))
                }
                requests[stopSegment.stop] = req
            }
        }
    }

    private fun sendResult(stop: StopSegment) {
        vms[stop.stop]?.filter {
            val plate = it
            stop.plates!!.any { it == plate.id }
        }?.forEach { sendResult(it.id, it.departures?.get(today())) }
    }

    private fun today(): AgencyAndId {
        return timetable.getServiceForToday()
    }

    private fun incrementRequest(stopSegment: StopSegment) {
        stopSegment.plates!!.forEach {
            val plateId = it
            requests[it.stop]!!.filter { it.plate == plateId }.forEach { it.times++ }
        }
    }

    private fun decrementRequest(stopSegment: StopSegment) {
        stopSegment.plates!!.forEach {
            val plateId = it
            requests[it.stop]!!.filter { it.plate == plateId }.forEach { it.times-- }
        }
    }

    private fun isAlreadyRequested(stopSegment: StopSegment): Boolean {
        val platesIn = requests[stopSegment.stop]?.map { it.plate }?.toSet()
        val platesOut = stopSegment.plates
        if (platesIn == null || platesIn.isEmpty())
            return false
        return (platesOut == platesIn || platesIn.containsAll(platesOut!!))
    }


    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
    }

    private fun downloadVM() {
        vms.forEach {
            downloadVM(StopSegment(it.key, it.value.map { it.id }.toSet()))
        }
    }

    private fun downloadVM(stopSegment: StopSegment) {
        if (!NetworkStateReceiver.isNetworkAvailable(this)) {
            stopSegment.plates!!.forEach {
                sendResult(it, null)
            }
            return
        }

        val stopSymbol = timetable.getStopCode(stopSegment.stop)
        val client = OkHttpClient()
        val url = "http://www.peka.poznan.pl/vm/method.vm?ts=${Calendar.getInstance().timeInMillis}"
        val formBody = FormBody.Builder()
                .add("method", "getTimes")
                .add("p0", "{\"symbol\": \"$stopSymbol\"}")
                .build()
        val request = okhttp3.Request.Builder()
                .url(url)
                .post(formBody)
                .build()

        Log.i("VM", "created http request")

        val responseBody: String?
        try {
            responseBody = client.newCall(request).execute().body()?.string()
        } catch (e: IOException) {
            stopSegment.plates!!.forEach {
                sendResult(it, null)
            }
            return
        }

        Log.i("VM", "received http response")

        if (responseBody?.get(0) == '<') {
            stopSegment.plates!!.forEach {
                sendResult(it, null)
            }
            return
        }

        val javaRootMapObject = Gson().fromJson(responseBody, HashMap::class.java)
        val times = (javaRootMapObject["success"] as Map<*, *>)["times"] as List<*>
        stopSegment.plates!!.forEach { downloadVM(it, times) }

    }

    private fun downloadVM(plateId: Plate.ID, times: List<*>) {
        val date = Calendar.getInstance()
        val todayDay = "${date.get(Calendar.DATE)}".padStart(2, '0')
        val todayMode = timetable.calendarToMode(AgencyAndId(timetable.getServiceForToday().id))

        val departures = HashSet<Departure>()

        times.forEach {
            val thisLine = timetable.getLineForNumber((it as Map<*, *>)["line"] as String)
            val thisHeadsign = it["direction"] as String
            val thisPlateId = Plate.ID(thisLine, plateId.stop, thisHeadsign)
            if (plateId == thisPlateId) {
                val departureDay = (it["departure"] as String).split("T")[0].split("-")[2]
                val departureTime = calendarFromIso(it["departure"] as String).secondsAfterMidnight()
                val departure = Departure(plateId.line, todayMode, departureTime, false,
                        ArrayList(), it["direction"] as String, it["realTime"] as Boolean,
                        departureDay != todayDay, it["onStopPoint"] as Boolean)
                departures.add(departure)
            }

        }


        val departuresForPlate = HashMap<AgencyAndId, HashSet<Departure>>()
        departuresForPlate[timetable.getServiceForToday()] = departures
        val vm = vms[plateId.stop]!!
        vm.remove(vm.filter { it.id == plateId }[0])
        vm.add(Plate(plateId, departuresForPlate))
        vms[plateId.stop] = vm
        if (departures.isEmpty())
            sendResult(plateId, null)
        else
            sendResult(plateId, departures)
    }

    private fun sendResult(plateId: Plate.ID, departures: HashSet<Departure>?) {
        val broadcastIntent = Intent()
        broadcastIntent.action = ACTION_READY
        broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT)
        if (departures != null)
            broadcastIntent.putStringArrayListExtra(EXTRA_DEPARTURES, departures.map { it.toString() } as ArrayList)
        broadcastIntent.putExtra(EXTRA_PLATE_ID, plateId)
        sendBroadcast(broadcastIntent)
    }

    data class Request(val plate: Plate.ID, var times: Int)

    class EmptyStopSegmentException : Exception()
}

//note application stops the service on exit

