package ml.adamsprogs.bimba

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.support.v7.app.AppCompatDelegate
import android.text.Html
import android.view.View
import android.widget.Toast
import com.arlib.floatingsearchview.FloatingSearchView
import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity(), MessageReceiver.OnTimetableDownloadListener {
    lateinit var listener: MessageReceiver.OnTimetableDownloadListener
    lateinit var receiver: MessageReceiver
    lateinit var layout: View
    lateinit var timetable: Timetable
    var stops: ArrayList<Timetable.Suggestion>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO)

        layout = findViewById(R.id.main_layout)
        val context = this as Context
        listener = this

        val filter = IntentFilter("ml.adamsprogs.bimba.timetableDownloaded")
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        receiver = MessageReceiver()
        registerReceiver(receiver, filter)
        receiver.addOnTimetableDownloadListener(listener)
        startService(Intent(context, TimetableDownloader::class.java))

        timetable = Timetable(this)
        stops = timetable.getStops()
        val searchView = findViewById(R.id.search_view) as FloatingSearchView

        if (stops == null) {
            //todo something more direct and create pull-to-refresh
            Snackbar.make(layout, getString(R.string.no_timetable), Snackbar.LENGTH_LONG).show()
            return
        }

        searchView.setOnQueryChangeListener({ _, newQuery ->
            thread {
                val newStops = stops!!.filter { deAccent(it.body.split("\n")[0]).contains(newQuery, true) }
                runOnUiThread { searchView.swapSuggestions (newStops)}
            }
        })

        searchView.setOnSearchListener(object : FloatingSearchView.OnSearchListener {
            override fun onSuggestionClicked(searchSuggestion: SearchSuggestion) {
                Toast.makeText(context, "clicked "+ (searchSuggestion as Timetable.Suggestion).id, Toast.LENGTH_SHORT).show()
                //todo to next screen
            }
            override fun onSearchAction(query: String) {
            }
        })

        searchView.setOnBindSuggestionCallback { _, _, textView, item, _ ->
            val suggestion = item as Timetable.Suggestion
            val text = suggestion.body.split("\n")
            val t = "<small><font color=\"#a0a0a0\">" + text[1] + "</font></small>"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                textView.text = Html.fromHtml(text[0]+"<br/>"+t, Html.FROM_HTML_MODE_LEGACY)
            } else {
                @Suppress("DEPRECATION")
                textView.text = Html.fromHtml(text[0]+"<br/>"+t)
            }
        }

        //todo searchView.attachNavigationDrawerToMenuButton(mDrawerLayout)
    }

    override fun onDestroy() {
        super.onDestroy()
        receiver.removeOnTimetableDownloadListener(listener)
        unregisterReceiver(receiver)
    }

    fun deAccent(str: String): String {
        var result = str.replace('ę', 'e')
        result = result.replace('ó', 'o')
        result = result.replace('ą', 'a')
        result = result.replace('ś', 's')
        result = result.replace('ł', 'l')
        result = result.replace('ż', 'ż')
        result = result.replace('ź', 'ź')
        result = result.replace('ć', 'ć')
        result = result.replace('ń', 'n')
        return result
    }

    override fun onTimetableDownload() {
        timetable.refresh()
        stops = timetable.getStops()
        Snackbar.make(layout, getString(R.string.timetable_downloaded), Snackbar.LENGTH_LONG).show()
    }
}
