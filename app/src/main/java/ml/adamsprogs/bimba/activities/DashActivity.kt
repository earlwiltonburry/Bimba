package ml.adamsprogs.bimba.activities

import android.annotation.SuppressLint
import android.content.*
import android.os.*
import android.support.design.widget.Snackbar
import android.support.v7.app.*
import android.text.Html
import com.arlib.floatingsearchview.FloatingSearchView
import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion
import ml.adamsprogs.bimba.models.*
import kotlin.concurrent.thread
import android.app.Activity
import android.support.design.widget.NavigationView
import android.support.v4.widget.*
import android.support.v7.widget.*
import android.view.*
import android.view.inputmethod.InputMethodManager
import ml.adamsprogs.bimba.*
import android.os.Bundle
import android.util.Log
import kotlinx.android.synthetic.main.activity_dash.*
import ml.adamsprogs.bimba.datasources.TimetableDownloader
import ml.adamsprogs.bimba.datasources.VmClient

//todo cards
class DashActivity : AppCompatActivity(), MessageReceiver.OnTimetableDownloadListener,
        FavouritesAdapter.OnMenuItemClickListener, Favourite.OnVmPreparedListener {
    val context: Context = this
    private val receiver = MessageReceiver.getMessageReceiver()
    lateinit var timetable: Timetable
    var stops: List<StopSuggestion>? = null
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerView: NavigationView
    lateinit var favouritesList: RecyclerView
    lateinit var searchView: FloatingSearchView
    private lateinit var favourites: FavouriteStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dash)

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.merge_favourites)

        getStops()

        prepareFavourites()

        prepareListeners()
        startDownloaderService()

        drawerLayout = drawer_layout
        drawerView = drawer
        //drawer.setCheckedItem(R.id.drawer_home)
        drawerView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.drawer_refresh -> {
                    startDownloaderService()
                }
                R.id.drawer_help -> {
                    startActivity(Intent(context, HelpActivity::class.java))
                }
                else -> {
                }
            }
            drawerLayout.closeDrawer(drawerView)
            super.onOptionsItemSelected(item)
        }

        val validSince = timetable.getValidSince()
        val validTill = timetable.getValidTill()
        drawerView.menu.findItem(R.id.drawer_validity_since).title = getString(R.string.valid_since, validSince)
        drawerView.menu.findItem(R.id.drawer_validity_till).title = getString(R.string.valid_till, validTill)

        searchView = search_view

        searchView.setOnFocusChangeListener(object : FloatingSearchView.OnFocusChangeListener {
            override fun onFocus() {
                favouritesList.visibility = View.GONE
                thread {
                    val newStops = stops!!.filter { deAccent(it.body.split("\n")[0]).contains(deAccent(searchView.query), true) }
                    runOnUiThread { searchView.swapSuggestions(newStops) }
                }
            }

            override fun onFocusCleared() {
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
                intent = Intent(context, StopSpecifyActivity::class.java)
                searchSuggestion as StopSuggestion
                intent.putExtra(StopSpecifyActivity.EXTRA_STOP_IDS, searchSuggestion.ids.joinToString(",") { it.id })
                intent.putExtra(StopSpecifyActivity.EXTRA_STOP_NAME, searchSuggestion.name)
                startActivity(intent)
            }

            override fun onSearchAction(query: String) {
            }
        })

        searchView.setOnBindSuggestionCallback { _, _, textView, item, _ ->
            val suggestion = item as StopSuggestion
            val text = suggestion.body.split("\n")
            val colour = when (text[1]) {
                "A" -> context.getString(R.string.zone_a_colour)
                "B" -> context.getString(R.string.zone_b_colour)
                "C" -> context.getString(R.string.zone_c_colour)
                else -> "#000000"
            }
            val t = "<small><font color=\"$colour\">" + text[1] + "</font></small>"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                textView.text = Html.fromHtml("${text[0]} $t", Html.FROM_HTML_MODE_LEGACY)
            } else {
                @Suppress("DEPRECATION")
                textView.text = Html.fromHtml(text[0] + "<br/>" + t)
            }
        }

        searchView.attachNavigationDrawerToMenuButton(drawer_layout as DrawerLayout)
    }

    private fun prepareFavourites() {
        favourites = FavouriteStorage.getFavouriteStorage(context)
        val layoutManager = LinearLayoutManager(context)
        favouritesList = favourites_list
        favouritesList.adapter = FavouritesAdapter(context, favourites.favouritesList, this)
        favouritesList.layoutManager = layoutManager
    }

    override fun onVmPrepared() {
        Log.i("VM", "DataSetChange")
        favouritesList.adapter.notifyDataSetChanged()
    }

    private fun getStops() {
        timetable = Timetable.getTimetable(this)
        stops = timetable.getStops()
    }

    private fun prepareListeners() {
        val filter = IntentFilter(TimetableDownloader.ACTION_DOWNLOADED)
        filter.addAction(VmClient.ACTION_READY)
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        registerReceiver(receiver, filter)
        receiver.addOnTimetableDownloadListener(context as MessageReceiver.OnTimetableDownloadListener)
        favourites.favouritesList.forEach {
            it.registerOnVm(receiver, context)
        }
    }

    private fun startDownloaderService() {
        startService(Intent(context, TimetableDownloader::class.java))
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(drawerView)) {
            drawerLayout.closeDrawer(drawerView)
            return
        }
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
        favourites.favouritesList.forEach {
            it.deregisterOnVm(receiver, context)
        }
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
        result = result.replace('ż', 'z')
        result = result.replace('ź', 'z')
        result = result.replace('ć', 'c')
        result = result.replace('ń', 'n')
        return result
    }

    override fun onTimetableDownload(result: String?) {
        val message: String = when (result) {
            TimetableDownloader.RESULT_DOWNLOADED -> getString(R.string.refreshing_cache)
            TimetableDownloader.RESULT_NO_CONNECTIVITY -> getString(R.string.no_connectivity)
            TimetableDownloader.RESULT_UP_TO_DATE -> getString(R.string.timetable_up_to_date)
            TimetableDownloader.RESULT_FINISHED -> getString(R.string.timetable_downloaded)
            else -> getString(R.string.error_try_later)
        }
        Snackbar.make(findViewById(R.id.drawer_layout), message, Snackbar.LENGTH_LONG).show()
        if (result == TimetableDownloader.RESULT_FINISHED) {
            stops = timetable.getStops()

            drawerView.menu.findItem(R.id.drawer_validity_since).title = getString(R.string.valid_since, timetable.getValidSince())
            drawerView.menu.findItem(R.id.drawer_validity_till).title = getString(R.string.valid_since, timetable.getValidTill())
        }
    }

    override fun edit(name: String): Boolean {
        val intent = Intent(this, EditFavouriteActivity::class.java)
        intent.putExtra(EditFavouriteActivity.EXTRA_FAVOURITE, favourites.favourites[name])
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

    @SuppressLint("MissingSuperCall")
    override fun onSaveInstanceState(outState: Bundle) {
        //hack below line to be commented to prevent crash on nougat.
        //super.onSaveInstanceState(outState);
    }
}
