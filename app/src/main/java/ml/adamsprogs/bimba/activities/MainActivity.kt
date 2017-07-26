package ml.adamsprogs.bimba.activities

import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.support.design.widget.Snackbar
import android.support.v7.app.*
import android.text.Html
import android.view.View
import com.arlib.floatingsearchview.FloatingSearchView
import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion
import ml.adamsprogs.bimba.models.*
import kotlin.concurrent.thread
import android.app.Activity
import android.content.Context
import android.support.v4.widget.SwipeRefreshLayout
import android.util.Log
import android.view.inputmethod.InputMethodManager
import ml.adamsprogs.bimba.MessageReceiver
import ml.adamsprogs.bimba.R
import ml.adamsprogs.bimba.TimetableDownloader


class MainActivity : AppCompatActivity(), MessageReceiver.OnTimetableDownloadListener, SwipeRefreshLayout.OnRefreshListener {
    val context: Context = this
    val receiver = MessageReceiver()
    lateinit var timetable: Timetable
    var stops: ArrayList<StopSuggestion>? = null
    lateinit var swipeRefreshLayout: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO)

        prepareSwipeLayout()

        prepareOnDownloadListener()
        startDownloaderService()

        getStops()

        val searchView = findViewById(R.id.search_view) as FloatingSearchView

        searchView.setOnQueryChangeListener({ _, newQuery ->
            thread {
                val newStops = stops!!.filter { deAccent(it.body.split("\n")[0]).contains(deAccent(newQuery), true) }
                runOnUiThread { searchView.swapSuggestions(newStops) }
            }
        })

        searchView.setOnSearchListener(object : FloatingSearchView.OnSearchListener {
            override fun onSuggestionClicked(searchSuggestion: SearchSuggestion) {
                val imm = context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                var view = (context as MainActivity).currentFocus
                if (view == null) {
                    view = View(context)
                }
                imm.hideSoftInputFromWindow(view.windowToken, 0)
                intent = Intent(context, StopActivity::class.java)
                intent.putExtra("stopId", (searchSuggestion as StopSuggestion).id)
                intent.putExtra("stopSymbol", (searchSuggestion as StopSuggestion).symbol)
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
                textView.text = Html.fromHtml(text[0] + "<br/>" + t, Html.FROM_HTML_MODE_LEGACY)
            } else {
                @Suppress("DEPRECATION")
                textView.text = Html.fromHtml(text[0] + "<br/>" + t)
            }
        }

        //todo searchView.attachNavigationDrawerToMenuButton(mDrawerLayout)
    }

    private fun getStops() {
        timetable = Timetable(this)
        stops = timetable.getStops()
    }

    private fun prepareOnDownloadListener() {
        val filter = IntentFilter("ml.adamsprogs.bimba.timetableDownloaded")
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        registerReceiver(receiver, filter)
        receiver.addOnTimetableDownloadListener(context as MessageReceiver.OnTimetableDownloadListener)
    }

    private fun startDownloaderService() {
        startService(Intent(context, TimetableDownloader::class.java))
    }

    private fun prepareSwipeLayout() {
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout) as SwipeRefreshLayout
        swipeRefreshLayout.isEnabled = true
        swipeRefreshLayout.setOnRefreshListener(this)
        swipeRefreshLayout.setColorSchemeResources(R.color.colorAccent, R.color.colorPrimary)
    }

    override fun onRefresh() {
        swipeRefreshLayout.isRefreshing = true
        Log.i("Refresh", "Downloading")
        startDownloaderService()
    }

    override fun onDestroy() {
        super.onDestroy()
        receiver.removeOnTimetableDownloadListener(context as MessageReceiver.OnTimetableDownloadListener)
        unregisterReceiver(receiver)
        timetable.close()
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

    override fun onTimetableDownload(result: String?) {
        Log.i("Refresh", "downloaded: $result")
        val message: String
        when (result) {
            "downloaded" -> message = getString(R.string.timetable_downloaded)
            "no connectivity" -> message = getString(R.string.no_connectivity)
            "up-to-date" -> message = getString(R.string.timetable_up_to_date)
            "validity failed" -> message = getString(R.string.validity_failed)
            else -> message = getString(R.string.error_try_later)
        }
        timetable.refresh()
        stops = timetable.getStops()
        Snackbar.make(swipeRefreshLayout, message, Snackbar.LENGTH_LONG).show()
        swipeRefreshLayout.isRefreshing = false
    }
}
