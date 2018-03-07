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
import ml.adamsprogs.bimba.models.suggestions.GtfsSuggestion
import ml.adamsprogs.bimba.models.suggestions.LineSuggestion
import ml.adamsprogs.bimba.models.suggestions.StopSuggestion
import android.support.v7.widget.DefaultItemAnimator
import android.content.Intent
import java.util.*
import kotlin.collections.ArrayList

//todo cards https://enoent.fr/blog/2015/01/18/recyclerview-basics/
//todo searchView integration
class DashActivity : AppCompatActivity(), MessageReceiver.OnTimetableDownloadListener,
        FavouritesAdapter.OnMenuItemClickListener, Favourite.OnVmPreparedListener,
        FavouritesAdapter.ViewHolder.OnClickListener {
    val context: Context = this
    private val receiver = MessageReceiver.getMessageReceiver()
    lateinit var timetable: Timetable
    var suggestions: List<GtfsSuggestion>? = null
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerView: NavigationView
    lateinit var favouritesList: RecyclerView
    lateinit var searchView: FloatingSearchView
    private lateinit var favourites: FavouriteStorage
    private lateinit var adapter: FavouritesAdapter
    private val actionModeCallback = ActionModeCallback()
    private var actionMode: ActionMode? = null

    companion object {
        const val REQUEST_EDIT_FAVOURITE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dash)

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO)
        setSupportActionBar(toolbar)

        timetable = Timetable.getTimetable(this)

        getSuggestions()

        warnTimetableValidity()

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
                    val newStops = suggestions!!.filter { deAccent(it.name).contains(deAccent(searchView.query), true) } //todo sorted by similarity
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
                val newStops = suggestions!!.filter { deAccent(it.name).contains(deAccent(newQuery), true) } //todo sorted by similarity
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
                if (searchSuggestion is StopSuggestion) {
                    val intent = Intent(context, StopSpecifyActivity::class.java)
                    intent.putExtra(StopSpecifyActivity.EXTRA_STOP_IDS, searchSuggestion.ids.joinToString(",") { it.id })
                    intent.putExtra(StopSpecifyActivity.EXTRA_STOP_NAME, searchSuggestion.name)
                    startActivity(intent)
                } else if (searchSuggestion is LineSuggestion) {
                    val intent = Intent(context, LineSpecifyActivity::class.java)
                    intent.putExtra(LineSpecifyActivity.EXTRA_LINE_ID, searchSuggestion.name)
                    startActivity(intent)
                }
            }

            override fun onSearchAction(query: String) {
            }
        })

        searchView.setOnBindSuggestionCallback { _, iconView, textView, item, _ ->
            val suggestion = item as GtfsSuggestion
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                textView.text = Html.fromHtml(item.body, Html.FROM_HTML_MODE_LEGACY)
            } else {
                @Suppress("DEPRECATION")
                textView.text = Html.fromHtml(item.body)
            }
            iconView.setImageDrawable(getDrawable(suggestion.getIcon(), context))
        }

        searchView.attachNavigationDrawerToMenuButton(drawer_layout as DrawerLayout)
    }

    private fun warnTimetableValidity() {
        val validTill = timetable.getValidTill()
        val today = Calendar.getInstance().toIsoDate()
        val tomorrow = Calendar.getInstance().apply {
            this.add(Calendar.DAY_OF_MONTH, 1)
        }.toIsoDate()

        try {
            timetable.getServiceForToday()
            if (today >= validTill) {
                notifyTimetableValidity()
                suggestions = ArrayList()
                return
            }
        } catch (e: IllegalArgumentException) {
            notifyTimetableValidity()
            suggestions = ArrayList()
            return
        }

        try {
            timetable.getServiceForTomorrow()
            if (tomorrow == validTill) {
                notifyTimetableValidity(true)
                return
            }
        } catch (e: IllegalArgumentException) {
            notifyTimetableValidity(true)
            return
        }
    }

    private fun notifyTimetableValidity(warning: Boolean = false) {
        val message = if (warning)
            getString(R.string.timetable_validity_warning)
        else
            getString(R.string.timetable_validity_finished)
        AlertDialog.Builder(context)
                .setPositiveButton(context.getText(android.R.string.ok),
                        { dialog: DialogInterface, _: Int -> dialog.cancel() })
                .setCancelable(true)
                .setMessage(message)
                .create().show()
    }

    private fun prepareFavourites() {
        favourites = FavouriteStorage.getFavouriteStorage(context)
        val layoutManager = LinearLayoutManager(context)
        favouritesList = favourites_list
        adapter = FavouritesAdapter(context, favourites, this, this)
        favouritesList.adapter = adapter
        favouritesList.itemAnimator = DefaultItemAnimator()
        favouritesList.layoutManager = layoutManager
    }

    override fun onVmPrepared() {
        Log.i("VM", "DataSetChange")
        favouritesList.adapter.notifyDataSetChanged()
    }

    private fun getSuggestions() {
        suggestions = (timetable.getStopSuggestions(context) + timetable.getLineSuggestions()).sorted() //todo<p:v+1> + bike stations, &c
    }

    private fun prepareListeners() {
        val filter = IntentFilter(TimetableDownloader.ACTION_DOWNLOADED)
        filter.addAction(VmClient.ACTION_READY)
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        registerReceiver(receiver, filter)
        receiver.addOnTimetableDownloadListener(context as MessageReceiver.OnTimetableDownloadListener)
        favourites.registerOnVm(receiver, context)
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
        adapter.favourites = favourites
        favouritesList.adapter.notifyDataSetChanged()
    }

    override fun onDestroy() {
        super.onDestroy()
        receiver.removeOnTimetableDownloadListener(context as MessageReceiver.OnTimetableDownloadListener)
        favourites.deregisterOnVm(receiver, context)
        unregisterReceiver(receiver)
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
            getSuggestions()

            drawerView.menu.findItem(R.id.drawer_validity_since).title = getString(R.string.valid_since, timetable.getValidSince())
            drawerView.menu.findItem(R.id.drawer_validity_till).title = getString(R.string.valid_since, timetable.getValidTill())
        }
    }

    override fun edit(name: String): Boolean {
        val positionBefore = favourites.indexOf(name)
        val intent = Intent(this, EditFavouriteActivity::class.java)
        intent.putExtra(EditFavouriteActivity.EXTRA_FAVOURITE, favourites[name])
        intent.putExtra(EditFavouriteActivity.EXTRA_POSITION_BEFORE, positionBefore)
        startActivityForResult(intent, REQUEST_EDIT_FAVOURITE)
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (requestCode == REQUEST_EDIT_FAVOURITE) {
            if (resultCode == Activity.RESULT_OK) {
                val name = data.getStringExtra(EditFavouriteActivity.EXTRA_NEW_NAME)
                val positionBefore = data.getIntExtra(EditFavouriteActivity.EXTRA_POSITION_BEFORE, -1)
                //adapter.favourites = favourites.favouritesList
                if (positionBefore == -1)
                    favouritesList.adapter.notifyDataSetChanged()
                else {
                    val positionAfter = favourites.indexOf(name)
                    favouritesList.adapter.notifyItemChanged(positionBefore)
                    favouritesList.adapter.notifyItemMoved(positionBefore, positionAfter)
                }
            }
        }
    }

    override fun delete(name: String): Boolean {
        favourites.delete(name)
        //adapter.favourites = favourites.favouritesList
        favouritesList.adapter.notifyItemRemoved(favourites.indexOf(name))
        return true
    }

    @SuppressLint("MissingSuperCall")
    override fun onSaveInstanceState(outState: Bundle) {
        //hack below line to be commented to prevent crash on nougat.
        //super.onSaveInstanceState(outState);
    }

    override fun onItemClicked(position: Int) {
        if (actionMode != null) {
            toggleSelection(position)
        }

        //todo else -> StopActivity
    }

    override fun onItemLongClicked(position: Int): Boolean {
        if (actionMode == null) {
            actionMode = startActionMode(actionModeCallback)
        }

        toggleSelection(position)

        return true
    }

    private fun toggleSelection(position: Int) {
        adapter.toggleSelection(position)
        val count = adapter.getSelectedItemCount()

        if (count == 0) {
            actionMode?.finish()
        } else {
            actionMode?.title = getString(R.string.merge_favourites)
            actionMode?.invalidate()
        }
    }

    private fun clearSelection() {
        adapter.clearSelection()
        actionMode?.finish()
    }

    private inner class ActionModeCallback : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menuInflater.inflate(R.menu.menu_favourite_merge, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            return false
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.action_merge -> {
                    val selectedPositions = adapter.getSelectedItems()
                    val selectedNames = selectedPositions.map { favourites[it]?.name }.filter { it != null }.map { it!! }
                    favourites.merge(selectedNames)

                    adapter.notifyItemChanged(selectedPositions.min()!!)
                    (1 until selectedPositions.size).forEach {
                        adapter.notifyItemRemoved(it)
                    }

                    clearSelection()
                    true
                }

                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            (favouritesList.adapter as FavouritesAdapter).clearSelection()
            actionMode = null
        }
    }
}
