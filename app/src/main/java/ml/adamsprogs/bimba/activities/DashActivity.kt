package ml.adamsprogs.bimba.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.os.*
import android.preference.PreferenceManager.getDefaultSharedPreferences
import android.support.design.widget.*
import android.support.v4.widget.*
import android.support.v7.widget.*
import android.support.v7.app.*
import android.text.Html
import android.view.*
import android.view.inputmethod.InputMethodManager
import kotlin.collections.ArrayList
import kotlinx.android.synthetic.main.activity_dash.*
import java.util.*
import java.text.*

import ml.adamsprogs.bimba.models.*
import ml.adamsprogs.bimba.*
import ml.adamsprogs.bimba.datasources.*
import ml.adamsprogs.bimba.models.suggestions.*
import ml.adamsprogs.bimba.models.adapters.*
import ml.adamsprogs.bimba.collections.*

import com.arlib.floatingsearchview.FloatingSearchView
import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion

//todo<p:1> searchView integration
class DashActivity : AppCompatActivity(), MessageReceiver.OnTimetableDownloadListener,
        FavouritesAdapter.OnMenuItemClickListener, Favourite.OnVmPreparedListener,
        FavouritesAdapter.ViewHolder.OnClickListener {
    val context: Context = this
    private val receiver = MessageReceiver.getMessageReceiver()
    private lateinit var timetable: Timetable
    private var suggestions: List<GtfsSuggestion>? = null
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerView: NavigationView
    lateinit var favouritesList: RecyclerView
    lateinit var searchView: FloatingSearchView
    private lateinit var favourites: FavouriteStorage
    private lateinit var adapter: FavouritesAdapter
    private val actionModeCallback = ActionModeCallback()
    private var actionMode: ActionMode? = null
    private var isWarned = false
    private lateinit var providerProxy: ProviderProxy

    companion object {
        const val REQUEST_EDIT_FAVOURITE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dash)

        setSupportActionBar(toolbar)

        providerProxy = ProviderProxy(this)
        timetable = Timetable.getTimetable()

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
                R.id.drawer_settings -> {
                    startActivity(Intent(context, SettingsActivity::class.java))
                }
                else -> {
                }
            }
            drawerLayout.closeDrawer(drawerView)
            super.onOptionsItemSelected(item)
        }

        showValidityInDrawer()

        searchView = search_view

        searchView.setOnFocusChangeListener(object : FloatingSearchView.OnFocusChangeListener {
            override fun onFocus() {
                favouritesList.visibility = View.GONE
                providerProxy.getSuggestions(searchView.query) {
                    searchView.swapSuggestions(it)
                }
            }

            override fun onFocusCleared() {
                favouritesList.visibility = View.VISIBLE
            }
        })

        searchView.setOnQueryChangeListener { oldQuery, newQuery ->
            if (oldQuery != "" && newQuery == "")
                searchView.clearSuggestions()
            providerProxy.getSuggestions(newQuery) {
                searchView.swapSuggestions(it)
            }
        }

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

    private fun showValidityInDrawer() {
        if (timetable.isEmpty()) {
            drawerView.menu.findItem(R.id.drawer_validity_since).title = getString(R.string.validity_offline_unavailable)
        } else {
            val formatter = DateFormat.getDateInstance(DateFormat.SHORT)
            var calendar = calendarFromIsoD(timetable.getValidSince())
            formatter.timeZone = calendar.timeZone
            drawerView.menu.findItem(R.id.drawer_validity_since).title = getString(R.string.valid_since, formatter.format(calendar.time))
            calendar = calendarFromIsoD(timetable.getValidTill())
            formatter.timeZone = calendar.timeZone
            drawerView.menu.findItem(R.id.drawer_validity_till).title = getString(R.string.valid_till, formatter.format(calendar.time))
        }
    }

    private fun warnTimetableValidity() {
        if (isWarned)
            return
        isWarned = true
        if (timetable.isEmpty())
            return
        val validTill = timetable.getValidTill()
        val today = Calendar.getInstance().toIsoDate()
        val tomorrow = Calendar.getInstance().apply {
            this.add(Calendar.DAY_OF_MONTH, 1)
        }.toIsoDate()

        try {
            timetable.getServiceForToday()
            if (today > validTill) {
                notifyTimetableValidity(-1)
                suggestions = ArrayList()
                return
            }
            if (today == validTill) {
                notifyTimetableValidity(0)
                return
            }
        } catch (e: IllegalArgumentException) {
            notifyTimetableValidity(-1)
            suggestions = ArrayList()
            return
        }

        try {
            timetable.getServiceForTomorrow()
            if (tomorrow == validTill) {
                notifyTimetableValidity(1)
                return
            }
        } catch (e: IllegalArgumentException) {
            notifyTimetableValidity(1)
            return
        }
    }

    private fun notifyTimetableValidity(daysTillInvalid: Int) {
        val message = when (daysTillInvalid) {
            -1 -> getString(R.string.timetable_validity_finished)
            0 -> getString(R.string.timetable_validity_today)
            1 -> getString(R.string.timetable_validity_tomorrow)
            else -> return
        }
        AlertDialog.Builder(context)
                .setPositiveButton(context.getText(android.R.string.ok)
                ) { dialog: DialogInterface, _: Int -> dialog.cancel() }
                .setCancelable(true)
                .setMessage(message)
                .create().show()
    }

    private fun prepareFavourites() {
        favourites = FavouriteStorage.getFavouriteStorage(context)
        favourites.forEach {
            it.addOnVmPreparedListener(this)
        }
        val layoutManager = LinearLayoutManager(context)
        favouritesList = favourites_list
        adapter = FavouritesAdapter(context, favourites, this, this)
        favouritesList.adapter = adapter
        favouritesList.itemAnimator = DefaultItemAnimator()
        favouritesList.layoutManager = layoutManager
    }

    override fun onVmPrepared() {
        favouritesList.adapter.notifyDataSetChanged()
    }

    private fun getSuggestions() {
        providerProxy.getSuggestions {
            searchView.swapSuggestions(it)
        }
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
        if (getDefaultSharedPreferences(this).getBoolean("automatic timetable updates", false))
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

    override fun onTimetableDownload(result: String?) {
        val message: String = when (result) {
            TimetableDownloader.RESULT_NO_CONNECTIVITY -> getString(R.string.no_connectivity)
            TimetableDownloader.RESULT_UP_TO_DATE -> getString(R.string.timetable_up_to_date)
            TimetableDownloader.RESULT_FINISHED -> getString(R.string.timetable_downloaded)
            else -> getString(R.string.error_try_later)
        }
        Snackbar.make(findViewById(R.id.drawer_layout), message, Snackbar.LENGTH_LONG).show()
        if (result == TimetableDownloader.RESULT_FINISHED) {
            timetable = Timetable.getTimetable(this, true)
            getSuggestions()
            showValidityInDrawer()
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
        } else {
            val intent = Intent(context, StopActivity::class.java)
            intent.putExtra(StopActivity.SOURCE_TYPE, StopActivity.SOURCE_TYPE_FAV)
            intent.putExtra(StopActivity.EXTRA_FAVOURITE, favourites[position])
            startActivity(intent)
        }
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
                    favourites.merge(selectedNames, this@DashActivity)

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
