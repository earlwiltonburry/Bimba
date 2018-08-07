package ml.adamsprogs.bimba

import android.annotation.SuppressLint
import android.content.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.*
import ml.adamsprogs.bimba.activities.StopActivity
import ml.adamsprogs.bimba.datasources.*
import ml.adamsprogs.bimba.models.*
import ml.adamsprogs.bimba.models.suggestions.*
import java.util.*

//todo make singleton
class ProviderProxy(context: Context? = null) {
    private val vmStopsClient = VmClient.getVmStopClient()
    private var timetable: Timetable = Timetable.getTimetable(context)
    private var suggestions = emptyList<GtfsSuggestion>()
    private val requests = HashMap<String, Request>()
    var mode = if (timetable.isEmpty()) MODE_VM else MODE_FULL

    companion object {
        const val MODE_FULL = "mode_full"
        const val MODE_VM = "mode_vm"
    }

    fun getSuggestions(query: String = "", callback: (List<GtfsSuggestion>) -> Unit) {
        launch(UI) {
            suggestions = withContext(DefaultDispatcher) {
                getStopSuggestions(query) //+ getLineSuggestions(query) //todo<p:v+1> + bike stations, train stations, &c
            }
            callback(filterSuggestions(query))
        }
    }

    private suspend fun getStopSuggestions(query: String): List<StopSuggestion> {
        val vmSuggestions = withContext(DefaultDispatcher) {
            vmStopsClient.getStops(query)
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
        launch(UI) {
            val vmSheds = withContext(DefaultDispatcher) {
                vmStopsClient.getSheds(name)
            }

            val sheds = if (vmSheds.isEmpty() and !timetable.isEmpty()) {
                timetable.getHeadlinesForStop(name)
            } else {
                vmSheds
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

    fun subscribeForDepartures(stopCode: String, listener: StopActivity, context: StopActivity): String {
        val intent = Intent(context, VmService::class.java)
        intent.putExtra("stop", stopCode)
        intent.action = "request"
        context.startService(intent)

        val uuid = UUID.randomUUID().toString()
        requests[uuid] = Request(listener, setOf(StopSegment(stopCode, null)))
        return uuid
    }

    private fun constructSegmentDepartures(stopSegments: Set<StopSegment>): Set<Departure> {
        if (timetable.isEmpty())
            return emptySet()
        else
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun unsubscribeFromDepartures(uuid: String, context: Context) {
        requests[uuid]?.unsubscribe(context)
        requests.remove(uuid)
    }

    fun refreshTimetable(context: Context) {
        timetable = Timetable.getTimetable(context, true)
        mode = MODE_FULL
    }

    fun getFullTimetable(stopCode: String): Map<Int, List<Departure>> {
        val departures = if (timetable.isEmpty())
            emptyMap()
        else
            timetable.getStopDepartures(stopCode)

        return convertCalendarModes(departures)
    }

    fun getFullTimetable(stopSegments: Set<StopSegment>): Map<Int, List<Departure>> {
        val departures = if (timetable.isEmpty())
            emptyMap()
        else
            timetable.getStopDeparturesBySegments(stopSegments)

        return convertCalendarModes(departures)
    }

    @SuppressLint("UseSparseArrays")
    private fun convertCalendarModes(raw: Map<String, List<Departure>>): Map<Int, List<Departure>> {
        val sunday = timetable.getServiceFor(Calendar.SUNDAY)
        val saturday = timetable.getServiceFor(Calendar.SATURDAY)

        val departures = HashMap<Int, List<Departure>>()
        departures[StopActivity.MODE_WORKDAYS] =
                try {
                    raw.filter { it.key != saturday && it.key != sunday }.toList()[0].second
                } catch (e: IndexOutOfBoundsException) {
                    ArrayList<Departure>()
                }

        departures[StopActivity.MODE_SATURDAYS] = raw[saturday] ?: ArrayList()
        departures[StopActivity.MODE_SUNDAYS] = raw[sunday] ?: ArrayList()

        return departures
    }

    interface OnDeparturesReadyListener {
        fun onDeparturesReady(departures: Set<Departure>, plateId: Plate.ID)
    }

    inner class Request(private val listener: OnDeparturesReadyListener, private val segments: Set<StopSegment>) : MessageReceiver.OnVmListener {
        private val receiver = MessageReceiver.getMessageReceiver()
        private val receivedPlates = HashSet<Plate.ID>()

        init {
            receiver.addOnVmListener(this)
        }

        override fun onVm(vmDepartures: Set<Departure>?, plateId: Plate.ID, stopCode: String) {
            if (segments.any { plateId in it }) {
                if (vmDepartures != null) {
                    listener.onDeparturesReady(vmDepartures, plateId)
                    if (plateId !in receivedPlates)
                        receivedPlates.add(plateId)
                } else {
                    receivedPlates.remove(plateId)
                    if (receivedPlates.isEmpty())
                        listener.onDeparturesReady(constructSegmentDepartures(segments), plateId)
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