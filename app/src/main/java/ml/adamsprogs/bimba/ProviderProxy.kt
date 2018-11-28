package ml.adamsprogs.bimba

import android.content.*
import kotlinx.coroutines.*
import kotlinx.coroutines.android.Main
import ml.adamsprogs.bimba.datasources.*
import ml.adamsprogs.bimba.models.*
import ml.adamsprogs.bimba.models.suggestions.*
import java.util.*
import kotlin.collections.HashMap

//todo make singleton
class ProviderProxy(context: Context? = null) {
    private val vmClient = VmClient.getVmClient()
    private var timetable: Timetable = Timetable.getTimetable(context)
    private var suggestions = emptyList<GtfsSuggestion>()
    private val requests = HashMap<String, Request>()

    var mode = if (timetable.isEmpty()) MODE_VM else MODE_FULL

    companion object {
        const val MODE_FULL = "mode_full"
        const val MODE_VM = "mode_vm"
    }

    fun getSuggestions(query: String = "", callback: (List<GtfsSuggestion>) -> Unit) {
        launch(Dispatchers.Main, CoroutineStart.DEFAULT, null, {
            val filtered = withContext(Dispatchers.Default) {
                suggestions = getStopSuggestions(query) //+ getLineSuggestions(query) //todo<p:v+1> + bike stations, train stations, &c
                filterSuggestions(query)
            }
            callback(filtered)
        })
    }

    private suspend fun getStopSuggestions(query: String): List<StopSuggestion> {
        val vmSuggestions = withContext(Dispatchers.Default) {
            vmClient.getStops(query)
        }

        return if (vmSuggestions.isEmpty() and !timetable.isEmpty()) {
            timetable.getStopSuggestions()
        } else {
            vmSuggestions
        }
    }

    private fun filterSuggestions(query: String): List<GtfsSuggestion> {
        return suggestions.filter {
            deAccent(it.name).contains(deAccent(query), true)
        }
    }

    private fun deAccent(str: String): String {
        var result = str.replace('ę', 'e', true)
        result = result.replace('ó', 'o', true)
        result = result.replace('ą', 'a', true)
        result = result.replace('ś', 's', true)
        result = result.replace('ł', 'l', true)
        result = result.replace('ż', 'z', true)
        result = result.replace('ź', 'z', true)
        result = result.replace('ć', 'c', true)
        result = result.replace('ń', 'n', true)
        return result
    }

    fun getSheds(name: String, callback: (Map<String, Set<String>>) -> Unit) {
        launch(Dispatchers.Main) {
            val sheds = withContext(Dispatchers.Default) {
                val vmSheds = vmClient.getSheds(name)

                if (vmSheds.isEmpty() and !timetable.isEmpty()) {
                    timetable.getHeadlinesForStop(name)
                } else {
                    vmSheds
                }
            }

            callback(sheds)
        }
    }

    fun subscribeForDepartures(stopSegments: Set<StopSegment>, listener: OnDeparturesReadyListener, context: Context): String {
        stopSegments.forEach {
            val intent = Intent(context, VmService::class.java)
            intent.putExtra("stop", it.stop)
            intent.action = "request"
            context.startService(intent)
        }
        val uuid = UUID.randomUUID().toString()
        requests[uuid] = Request(listener, stopSegments)
        return uuid
    }

    fun subscribeForDepartures(stopCode: String, listener: OnDeparturesReadyListener, context: Context): String {
        val intent = Intent(context, VmService::class.java)
        intent.putExtra("stop", stopCode)
        intent.action = "request"
        context.startService(intent)

        val uuid = UUID.randomUUID().toString()
        requests[uuid] = Request(listener, setOf(StopSegment(stopCode, null)))
        return uuid
    }

    private fun constructSegmentDepartures(stopSegments: Set<StopSegment>): Deferred<Map<String, List<Departure>>> {
        return GlobalScope.async(Dispatchers.Default, CoroutineStart.DEFAULT, null, {
            if (timetable.isEmpty())
                emptyMap()
            else {
                timetable.getStopDeparturesBySegments(stopSegments)
            }
        })
    }

    private fun filterDepartures(departures: Map<String, List<Departure>>): List<Departure> {
        val now = Calendar.getInstance().secondsAfterMidnight()
        val lines = HashMap<String, Int>()
        val twoDayDepartures = (timetable.getServiceForToday()?.let {
            departures[it]
        } ?: emptyList()) +
                (timetable.getServiceForTomorrow()?.let { service ->
                    departures[service]!!.map { it.copy().apply { tomorrow = true } }
                } ?: emptyList())

        return twoDayDepartures
                .filter { it.timeTill(now) >= 0 }
                .filter {
                    val existed = lines[it.line] ?: 0
                    if (existed < 3) {
                        lines[it.line] = existed + 1
                        true
                    } else false
                }
    }

    fun unsubscribeFromDepartures(uuid: String, context: Context) {
        requests[uuid]?.unsubscribe(context)
        requests.remove(uuid)
    }

    fun refreshTimetable(context: Context) {
        timetable = Timetable.getTimetable(context, true)
        mode = MODE_FULL
    }

    fun getFullTimetable(stopCode: String): Map<String, List<Departure>> {
        return if (timetable.isEmpty())
            emptyMap()
        else
            timetable.getStopDepartures(stopCode)

    }

    fun getFullTimetable(stopSegments: Set<StopSegment>): Map<String, List<Departure>> {
        return if (timetable.isEmpty())
            emptyMap()
        else
            timetable.getStopDeparturesBySegments(stopSegments)

    }

    fun fillStopSegment(stopSegment: StopSegment, callback: (StopSegment?) -> Unit) {
        launch(Dispatchers.Main) {
            withContext(Dispatchers.Default) {
                callback(fillStopSegment(stopSegment))
            }
        }
    }

    suspend fun fillStopSegment(stopSegment: StopSegment): StopSegment? {
        if (stopSegment.plates != null)
            return stopSegment

        return if (timetable.isEmpty())
            vmClient.getDirections(stopSegment.stop)
        else
            timetable.getHeadlinesForStopCode(stopSegment.stop)
    }

    fun getStopName(stopCode: String, callback: (String?) -> Unit) {
        launch(Dispatchers.Main) {
            withContext(Dispatchers.Default) {
                callback(getStopName(stopCode))
            }
        }
    }

    suspend fun getStopName(stopCode: String): String? {
        return if (timetable.isEmpty())
            vmClient.getName(stopCode)
        else
            timetable.getStopName(stopCode)
    }

    fun describeService(service: String, context: Context): String? {
        return if (timetable.isEmpty())
            null
        else
            timetable.getServiceDescription(service, context)
    }

    fun getServiceFirstDay(service: String): Int {
        return timetable.getServiceFirstDay(service)
    }

    interface OnDeparturesReadyListener {
        fun onDeparturesReady(departures: List<Departure>, plateId: Plate.ID?, code: Int)
    }

    inner class Request(private val listener: OnDeparturesReadyListener, private val segments: Set<StopSegment>) : MessageReceiver.OnVmListener {
        private val receiver = MessageReceiver.getMessageReceiver()
        private val receivedPlates = HashSet<Plate.ID>()

        private var cache: Deferred<Map<String, List<Departure>>>? = null

        init {
            receiver.addOnVmListener(this@Request)
            launch(Dispatchers.Main) {
                cache = constructSegmentDepartures(segments)
            }
        }

        override fun onVm(vmDepartures: Set<Departure>?, plateId: Plate.ID?, stopCode: String, code: Int) {
            launch(Dispatchers.Main) {
                if ((plateId == null || vmDepartures == null) and (timetable.isEmpty())) {
                    listener.onDeparturesReady(emptyList(), null, code)
                    return@launch
                }
                if (plateId == null) {
                    listener.onDeparturesReady(filterDepartures(cache!!.await()), null, code)
                } else {
                    if (segments.any { plateId in it }) {
                        if (vmDepartures != null) {
                            listener.onDeparturesReady(vmDepartures.toList(), plateId, code)
                            if (plateId !in receivedPlates)
                                receivedPlates.add(plateId)
                        } else {
                            receivedPlates.remove(plateId)
                            if (receivedPlates.isEmpty()) {
                                listener.onDeparturesReady(filterDepartures(cache!!.await()), null, code)
                            }
                        }
                    }
                }
            }
        }

        fun unsubscribe(context: Context) {
            segments.forEach {
                val intent = Intent(context, VmService::class.java)
                intent.putExtra("stop", it.stop)
                intent.action = "remove"
                context.startService(intent)
            }
            receiver.removeOnVmListener(this)
        }
    }
}