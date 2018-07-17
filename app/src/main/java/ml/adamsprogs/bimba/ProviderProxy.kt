package ml.adamsprogs.bimba

import android.content.Context
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.*
import ml.adamsprogs.bimba.datasources.VmStopsClient
import ml.adamsprogs.bimba.models.Timetable
import ml.adamsprogs.bimba.models.suggestions.*

class ProviderProxy(context: Context) {
    private val vmStopsClient = VmStopsClient.getVmStopClient()
    private val timetable: Timetable = Timetable.getTimetable(context)
    private var suggestions = emptyList<GtfsSuggestion>()

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

        return if (vmSuggestions.isEmpty()) {
            if (timetable.isEmpty())
                emptyList()
            else
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
}