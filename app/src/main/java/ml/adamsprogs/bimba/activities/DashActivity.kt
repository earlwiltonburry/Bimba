package ml.adamsprogs.bimba.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.preference.PreferenceManager.getDefaultSharedPreferences
import android.text.Editable
import android.text.TextWatcher
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.mancj.materialsearchbar.MaterialSearchBar
import com.mancj.materialsearchbar.MaterialSearchBar.BUTTON_BACK
import com.mancj.materialsearchbar.MaterialSearchBar.BUTTON_NAVIGATION
import kotlinx.android.synthetic.main.activity_dash.*
import ml.adamsprogs.bimba.*
import ml.adamsprogs.bimba.collections.FavouriteStorage
import ml.adamsprogs.bimba.datasources.TimetableDownloader
import ml.adamsprogs.bimba.datasources.VmService
import ml.adamsprogs.bimba.models.Departure
import ml.adamsprogs.bimba.models.Plate
import ml.adamsprogs.bimba.models.Timetable
import ml.adamsprogs.bimba.models.adapters.FavouritesAdapter
import ml.adamsprogs.bimba.models.adapters.SuggestionsAdapter
import ml.adamsprogs.bimba.models.suggestions.EmptySuggestion
import ml.adamsprogs.bimba.models.suggestions.GtfsSuggestion
import ml.adamsprogs.bimba.models.suggestions.LineSuggestion
import ml.adamsprogs.bimba.models.suggestions.StopSuggestion
import java.text.DateFormat
import java.util.*
import kotlin.collections.ArrayList

class DashActivity : AppCompatActivity(), MessageReceiver.OnTimetableDownloadListener,
        FavouritesAdapter.OnMenuItemClickListener, FavouritesAdapter.ViewHolder.OnClickListener, ProviderProxy.OnDeparturesReadyListener, SuggestionsAdapter.OnSuggestionClickListener {

    val context: Context = this
    private val receiver = MessageReceiver.getMessageReceiver()
    private lateinit var timetable: Timetable
    private var suggestions: List<GtfsSuggestion>? = null
    private lateinit var drawerLayout: androidx.drawerlayout.widget.DrawerLayout
    private lateinit var drawerView: NavigationView
    lateinit var favouritesList: androidx.recyclerview.widget.RecyclerView
    lateinit var searchView: MaterialSearchBar
    private lateinit var favourites: FavouriteStorage
    private lateinit var adapter: FavouritesAdapter
    private val actionModeCallback = ActionModeCallback()
    private var actionMode: ActionMode? = null
    private var isWarned = false
    private lateinit var providerProxy: ProviderProxy
    private lateinit var suggestionsAdapter: SuggestionsAdapter

    companion object {
        const val REQUEST_EDIT_FAVOURITE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dash)

        setSupportActionBar(toolbar)

        providerProxy = ProviderProxy(this)
        timetable = Timetable.getTimetable()
        NetworkStateReceiver.init(this)

        prepareFavourites()

        prepareListeners()
        startDownloaderService()

        drawerLayout = drawer_layout
        drawerView = drawer
        //drawer.setCheckedItem(R.id.drawer_home)
        drawerView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.drawer_refresh -> {
                    startDownloaderService(true)
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

        warnTimetableValidity()

        showValidityInDrawer()

        searchView = search_view
        suggestionsAdapter = SuggestionsAdapter(layoutInflater, this, this)
        searchView.setCustomSuggestionAdapter(suggestionsAdapter)

        searchView.addTextChangeListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (searchView.isSearchEnabled) {
                    getSuggestions(s.toString())
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

        })

        searchView.setOnSearchActionListener(object : MaterialSearchBar.OnSearchActionListener {
            override fun onButtonClicked(buttonCode: Int) {
                when (buttonCode) {
                    BUTTON_NAVIGATION -> {
                        if (drawerLayout.isDrawerOpen(drawerView))
                            drawerLayout.closeDrawer(drawerView)
                        else
                            drawerLayout.openDrawer(drawerView)
                    }
                    BUTTON_BACK -> {
                        searchView.disableSearch()
                    }
                }
            }

            override fun onSearchStateChanged(enabled: Boolean) {
            }

            override fun onSearchConfirmed(text: CharSequence?) {
                getSuggestions(text as String)
            }

        })
    }

    private fun getSuggestions(query: String = "") {
        providerProxy.getSuggestions(query) { suggestions ->
            if (!suggestionsAdapter.equals(suggestions)) {
                if (suggestions.isEmpty()) {
                    suggestionsAdapter.clearSuggestions()
                    suggestionsAdapter.addSuggestion(EmptySuggestion())
                } else {
                    suggestionsAdapter.updateSuggestions(suggestions, query)
                }
                searchView.showSuggestionsList()
            }
        }
    }

    override fun onSuggestionClickListener(suggestion: GtfsSuggestion) {
        val imm = context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        var view = (context as DashActivity).currentFocus
        if (view == null) {
            view = View(context)
        }
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        if (suggestion is StopSuggestion) {
            val intent = Intent(context, StopSpecifyActivity::class.java)
            intent.putExtra(StopSpecifyActivity.EXTRA_STOP_NAME, suggestion.name)
            startActivity(intent)
        } else if (suggestion is LineSuggestion) {
            val intent = Intent(context, LineSpecifyActivity::class.java)
            intent.putExtra(LineSpecifyActivity.EXTRA_LINE_ID, suggestion.name)
            startActivity(intent)
        }
    }

    override fun onRestart() {
        super.onRestart()
        favourites = FavouriteStorage.getFavouriteStorage(context)
        favourites.forEach {
            it.subscribeForDepartures(this, this)
        }
    }

    override fun onStop() {
        super.onStop()
        favourites.forEach {
            it.unsubscribeFromDepartures(this)
        }
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

        if (daysTillInvalid == -1) {
            Timetable.delete(this)
        }
    }

    private fun prepareFavourites() {
        favourites = FavouriteStorage.getFavouriteStorage(context)
        favourites.forEach {
            it.subscribeForDepartures(this, this)
        }
        val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        favouritesList = favourites_list
        adapter = FavouritesAdapter(context, favourites, this, this)
        favouritesList.adapter = adapter
        favouritesList.itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator()
        favouritesList.layoutManager = layoutManager
    }

    override fun onDeparturesReady(departures: List<Departure>, plateId: Plate.ID?, code: Int) {
        favouritesList.adapter!!.notifyDataSetChanged()
        showError(drawer_layout, code, this)
    }

    private fun prepareListeners() {
        val filter = IntentFilter(TimetableDownloader.ACTION_DOWNLOADED)
        filter.addAction(VmService.ACTION_READY)
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        registerReceiver(receiver, filter)
        receiver.addOnTimetableDownloadListener(context as MessageReceiver.OnTimetableDownloadListener)
    }

    private fun startDownloaderService(force: Boolean = false) {
        if (getDefaultSharedPreferences(this).getBoolean(getString(R.string.key_timetable_automatic_update), false) or force)
            startService(Intent(context, TimetableDownloader::class.java))
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(drawerView)) {
            drawerLayout.closeDrawer(drawerView)
            return
        }
        if (searchView.isSearchEnabled) {
            searchView.disableSearch()
        } else {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.favourites = favourites
        favouritesList.adapter!!.notifyDataSetChanged()
    }

    override fun onDestroy() {
        super.onDestroy()
        receiver.removeOnTimetableDownloadListener(context as MessageReceiver.OnTimetableDownloadListener)
        unregisterReceiver(receiver)
    }

    override fun onTimetableDownload(result: String?) {
        val message: String = when (result) {
            TimetableDownloader.RESULT_NO_CONNECTIVITY -> getString(R.string.no_connectivity_cant_update)
            TimetableDownloader.RESULT_UP_TO_DATE -> getString(R.string.timetable_up_to_date)
            TimetableDownloader.RESULT_FINISHED -> getString(R.string.timetable_downloaded)
            else -> getString(R.string.error_try_later)
        }
        Snackbar.make(findViewById(R.id.drawer_layout), message, Snackbar.LENGTH_LONG).show()
        if (result == TimetableDownloader.RESULT_FINISHED) {
            timetable = Timetable.getTimetable(this, true)
            getSuggestions(searchView.text)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_EDIT_FAVOURITE) {
            if (resultCode == Activity.RESULT_OK) {
                val name = data!!.getStringExtra(EditFavouriteActivity.EXTRA_NEW_NAME)
                val positionBefore = data.getIntExtra(EditFavouriteActivity.EXTRA_POSITION_BEFORE, -1)
                //adapter.favourites = favourites.favouritesList
                if (positionBefore == -1)
                    favouritesList.adapter!!.notifyDataSetChanged()
                else {
                    val positionAfter = favourites.indexOf(name)
                    favouritesList.adapter!!.notifyItemChanged(positionBefore)
                    favouritesList.adapter!!.notifyItemMoved(positionBefore, positionAfter)
                }
                adapter[name]?.let {
                    it.unsubscribeFromDepartures(context)
                    it.subscribeForDepartures(this, context)
                }
            }
        }
    }

    override fun delete(name: String): Boolean {
        favourites.delete(name)
        //adapter.favourites = favourites.favouritesList
        favouritesList.adapter!!.notifyItemRemoved(favourites.indexOf(name))
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

                    (1 until selectedNames.size).forEach {
                        selectedNames[it].let { name ->
                            adapter.notifyItemRemoved(adapter.indexOf(name))
                            adapter[name]?.unsubscribeFromDepartures(context)
                        }
                    }
                    favourites.merge(selectedNames, context)
                    adapter[selectedNames[0]]?.let {
                        it.unsubscribeFromDepartures(context)
                        it.subscribeForDepartures(this@DashActivity, context)
                    }
                    adapter.notifyItemChanged(adapter.indexOf(selectedNames[0]))

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
