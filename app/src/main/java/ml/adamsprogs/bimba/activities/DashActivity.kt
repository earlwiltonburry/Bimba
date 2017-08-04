package ml.adamsprogs.bimba.activities

import android.content.*
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
import android.support.v4.widget.*
import android.support.v7.widget.*
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import ml.adamsprogs.bimba.*
import java.util.*

class DashActivity : AppCompatActivity(), MessageReceiver.OnTimetableDownloadListener, SwipeRefreshLayout.OnRefreshListener, FavouritesAdapter.OnMenuItemClickListener {
    val context: Context = this
    val receiver = MessageReceiver()
    lateinit var timetable: Timetable
    var stops: ArrayList<StopSuggestion>? = null
    lateinit var swipeRefreshLayout: SwipeRefreshLayout
    lateinit var favouritesList: RecyclerView
    lateinit var searchView: FloatingSearchView
    lateinit var favourites: FavouriteStorage
    var timer = Timer()
    private lateinit var timerTask: TimerTask

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dash)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO)
        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.merge_favourites)

        prepareSwipeLayout()

        createTimerTask()

        prepareOnDownloadListener()
        startDownloaderService()

        getStops()

        prepareFavourites()

        scheduleRefresh()

        searchView = findViewById(R.id.search_view) as FloatingSearchView

        searchView.setOnFocusChangeListener(object : FloatingSearchView.OnFocusChangeListener {
            override fun onFocus() {
                swipeRefreshLayout.isEnabled = false
                favouritesList.visibility = View.GONE
                thread {
                    val newStops = stops!!.filter { deAccent(it.body.split("\n")[0]).contains(deAccent(searchView.query), true) }
                    runOnUiThread { searchView.swapSuggestions(newStops) }
                }
            }

            override fun onFocusCleared() {
                swipeRefreshLayout.isEnabled = true
                favouritesList.visibility = View.VISIBLE
            }
        })

        searchView.setOnQueryChangeListener({ oldQuery, newQuery ->
            if (oldQuery != "" && newQuery == "")
                searchView.clearSuggestions()
            thread {
                val newStops = stops!!.filter { deAccent(it.body.split("\n")[0]).contains(deAccent(newQuery), true) }
                runOnUiThread { searchView.swapSuggestions(newStops) }
            }
        })

        searchView.setOnSearchListener(object : FloatingSearchView.OnSearchListener {
            override fun onSuggestionClicked(searchSuggestion: SearchSuggestion) {
                val imm = context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                var view = (context as DashActivity).currentFocus
                if (view == null) {
                    view = View(context)
                }
                imm.hideSoftInputFromWindow(view.windowToken, 0)
                intent = Intent(context, StopActivity::class.java)
                searchSuggestion as StopSuggestion
                intent.putExtra("stopId", searchSuggestion.id)
                intent.putExtra("stopSymbol", searchSuggestion.symbol)
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

    private fun prepareFavourites() {
        favourites = FavouriteStorage.getFavouriteStorage(context)
        val layoutManager = LinearLayoutManager(context)
        favouritesList = findViewById(R.id.favouritesList) as RecyclerView
        favouritesList.adapter = FavouritesAdapter(context, favourites.favouritesList, this)
        favouritesList.layoutManager = layoutManager
    }

    private fun scheduleRefresh() {
        timer.cancel()
        timer = Timer()
        createTimerTask()
        timer.scheduleAtFixedRate(timerTask, 0, 15000)
    }

    private fun createTimerTask() {
        timerTask = object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    favouritesList.adapter.notifyDataSetChanged()
                    //todo vm
                }
            }
        }
    }

    private fun getStops() {
        timetable = Timetable.getTimetable(this)
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
        startDownloaderService()
    }

    override fun onBackPressed() {
        if (!searchView.setSearchFocused(false)) {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        (favouritesList.adapter as FavouritesAdapter).favourites = favourites.favouritesList
        favouritesList.adapter.notifyDataSetChanged()
    }

    override fun onDestroy() {
        super.onDestroy()
        receiver.removeOnTimetableDownloadListener(context as MessageReceiver.OnTimetableDownloadListener)
        unregisterReceiver(receiver)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_favourite_merge, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId

        if (id == R.id.action_merge) {
            val names = (favouritesList.adapter as FavouritesAdapter).selectedNames
            favourites.merge(names)
            (favouritesList.adapter as FavouritesAdapter).favourites = favourites.favouritesList
            favouritesList.adapter.notifyDataSetChanged()
            (favouritesList.adapter as FavouritesAdapter).stopSelecting(names[0])
        }

        return super.onOptionsItemSelected(item)
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

    override fun edit(name: String): Boolean {
        val intent = Intent(this, EditFavouriteActivity::class.java)
        intent.putExtra("favourite", favourites.favourites[name])
        startActivity(intent)
        (favouritesList.adapter as FavouritesAdapter).favourites = favourites.favouritesList
        favouritesList.adapter.notifyDataSetChanged()
        return true
    }

    override fun delete(name: String): Boolean {
        favourites.delete(name)
        (favouritesList.adapter as FavouritesAdapter).favourites = favourites.favouritesList
        favouritesList.adapter.notifyDataSetChanged()
        return true
    }
}
