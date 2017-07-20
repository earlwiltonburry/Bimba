package ml.adamsprogs.bimba

import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.support.v7.app.AppCompatDelegate
import android.text.Html
import android.view.View
import com.arlib.floatingsearchview.FloatingSearchView
import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion
import ml.adamsprogs.bimba.models.StopSuggestion
import ml.adamsprogs.bimba.models.Timetable
import kotlin.concurrent.thread
import android.app.Activity
import android.view.inputmethod.InputMethodManager


class MainActivity : AppCompatActivity(), MessageReceiver.OnTimetableDownloadListener {
    val listener = this
    val receiver  = MessageReceiver()
    lateinit var layout: View
    lateinit var timetable: Timetable
    var stops: ArrayList<StopSuggestion>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO)

        layout = findViewById(R.id.main_layout)
        val context = this

        val filter = IntentFilter("ml.adamsprogs.bimba.timetableDownloaded")
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        registerReceiver(receiver, filter)
        receiver.addOnTimetableDownloadListener(listener)
        startService(Intent(context, TimetableDownloader::class.java))

        timetable = Timetable(this)
        stops = timetable.getStops()
        val searchView = findViewById(R.id.search_view) as FloatingSearchView

        searchView.setOnQueryChangeListener({ _, newQuery ->
            thread {
                val newStops = stops!!.filter { deAccent(it.body.split("\n")[0]).contains(newQuery, true) }
                runOnUiThread { searchView.swapSuggestions (newStops)}
            }
        })

        searchView.setOnSearchListener(object : FloatingSearchView.OnSearchListener {
            override fun onSuggestionClicked(searchSuggestion: SearchSuggestion) {
                val imm = context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                var view = context.currentFocus
                if (view == null) {
                    view = View(context)
                }
                imm.hideSoftInputFromWindow(view.windowToken, 0)
                intent = Intent(context, StopActivity::class.java)
                intent.putExtra("stop", (searchSuggestion as StopSuggestion).id)
                startActivity(intent)
            }
            override fun onSearchAction(query: String) {
            }
        })

        searchView.setOnBindSuggestionCallback { _, _, textView, item, _ ->
            val suggestion = item as StopSuggestion
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
