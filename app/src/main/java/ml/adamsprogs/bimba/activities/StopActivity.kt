package ml.adamsprogs.bimba.activities

import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_stop.*
import ml.adamsprogs.bimba.*
import ml.adamsprogs.bimba.collections.FavouriteStorage
import ml.adamsprogs.bimba.datasources.TimetableDownloader
import ml.adamsprogs.bimba.datasources.VmService
import ml.adamsprogs.bimba.models.Departure
import ml.adamsprogs.bimba.models.Favourite
import ml.adamsprogs.bimba.models.Plate
import ml.adamsprogs.bimba.models.StopSegment
import ml.adamsprogs.bimba.models.adapters.DeparturesAdapter
import ml.adamsprogs.bimba.models.adapters.ServiceAdapter
import java.util.Calendar
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.set

class StopActivity : AppCompatActivity(), MessageReceiver.OnTimetableDownloadListener, ProviderProxy.OnDeparturesReadyListener {
    companion object {
        const val EXTRA_STOP_CODE = "stopCode"
        const val EXTRA_STOP_NAME = "stopName"
        const val EXTRA_FAVOURITE = "favourite"
        const val SOURCE_TYPE = "sourceType"
        const val SOURCE_TYPE_STOP = "stop"
        const val SOURCE_TYPE_FAV = "favourite"

        const val TIMETABLE_TYPE_DEPARTURE = "timetable_type_departure"
        const val TIMETABLE_TYPE_FULL = "timetable_type_full"
    }

    private var stopCode = ""
    private var favourite: Favourite? = null
    private var timetableType = TIMETABLE_TYPE_DEPARTURE
    private val context = this
    private val receiver = MessageReceiver.getMessageReceiver()
    private lateinit var providerProxy: ProviderProxy
    private val departures = HashMap<Plate.ID, List<Departure>>()
    private val fullDepartures = HashMap<String, List<Departure>>()
    private lateinit var subscriptionId: String
    private lateinit var adapter: DeparturesAdapter


    private lateinit var sourceType: String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stop)

        providerProxy = ProviderProxy(this)

        sourceType = intent.getStringExtra(SOURCE_TYPE)

        setSupportActionBar(toolbar)

        when (sourceType) {
            SOURCE_TYPE_STOP -> {
                stopCode = intent.getSerializableExtra(EXTRA_STOP_CODE) as String
                supportActionBar?.title = intent.getSerializableExtra(EXTRA_STOP_NAME) as String
            }
            SOURCE_TYPE_FAV -> {
                favourite = intent.getParcelableExtra(EXTRA_FAVOURITE)
                supportActionBar?.title = favourite!!.name
            }
        }

        showFab()

        val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        departuresList.addItemDecoration(androidx.recyclerview.widget.DividerItemDecoration(departuresList.context, layoutManager.orientation))
        departuresList.adapter = DeparturesAdapter(this, null, true)
        adapter = departuresList.adapter as DeparturesAdapter
        departuresList.layoutManager = layoutManager

        departuresList.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: androidx.recyclerview.widget.RecyclerView, newState: Int) {}
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                updateFabVisibility(dy)
                super.onScrolled(recyclerView, dx, dy)
            }
        })

        prepareOnDownloadListener()
        subscribeForDepartures()
    }

    private fun showFab() {
        if (sourceType == SOURCE_TYPE_FAV)
            return

        val favourites = FavouriteStorage.getFavouriteStorage(context)
        if (!favourites.has(stopCode)) {
            fab.setImageDrawable(ResourcesCompat.getDrawable(context.resources, R.drawable.ic_favourite_empty, this.theme))
        }

        fab.setOnClickListener {
            if (!favourites.has(stopCode)) {
                val items = HashSet<StopSegment>()
                items.add(StopSegment(stopCode, null))
                favourites.add(stopCode, items, this@StopActivity)
                fab.setImageDrawable(ResourcesCompat.getDrawable(context.resources, R.drawable.ic_favourite, this.theme))
            } else {
                Snackbar.make(it, getString(R.string.stop_already_fav), Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show()
            }
        }
    }

    //fixme<p:5> maybe better effects
    private fun updateFabVisibility(dy: Int) {
        if (fab == null)
            return
        if (dy > 0) {
            fab.hide()
        } else {
            fab.show()
        }
    }

    private fun prepareOnDownloadListener() {
        val filter = IntentFilter(TimetableDownloader.ACTION_DOWNLOADED)
        filter.addAction(VmService.ACTION_READY)
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        registerReceiver(receiver, filter)
        receiver.addOnTimetableDownloadListener(context)
    }

    private fun subscribeForDepartures() {
        subscriptionId = if (sourceType == SOURCE_TYPE_STOP) {
            providerProxy.subscribeForDepartures(stopCode, this, this)
        } else
            favourite!!.subscribeForDepartures(this, context)
    }

    override fun onDeparturesReady(departures: List<Departure>, plateId: Plate.ID?, code: Int) {
        progressBar.visibility = View.GONE
        showError(stop_layout, code, this)
        if (plateId == null) {
            this.departures.clear()
            this.departures[Plate.ID.dummy] = departures
        } else {
            this.departures.remove(Plate.ID.dummy)
            this.departures[plateId] = departures
        }
        if (timetableType == TIMETABLE_TYPE_FULL)
            return
        refreshAdapter()
        if (adapter.departures?.isEmpty() != false) {
            emptyStateIcon.visibility = View.VISIBLE
            emptyStateText.visibility = View.VISIBLE
            departuresList.visibility = View.GONE
        } else {
            emptyStateIcon.visibility = View.GONE
            emptyStateText.visibility = View.GONE
            departuresList.visibility = View.VISIBLE
        }
    }

    private fun refreshAdapter() {
        if (timetableType == TIMETABLE_TYPE_FULL) {
            @Suppress("UNCHECKED_CAST")
            adapter.departures = fullDepartures[(dateSpinner.selectedItem as ServiceAdapter.RowItem).service]
        } else {
            val now = Calendar.getInstance()
            val seconds = now.secondsAfterMidnight()
            adapter.departures = this.departures.flatMap { it.value }.sortedBy { it.timeTill(seconds) }
        }
        adapter.notifyDataSetChanged()
    }

    override fun onTimetableDownload(result: String?) {
        val message: String = when (result) {
            TimetableDownloader.RESULT_NO_CONNECTIVITY -> getString(R.string.no_connectivity_cant_update)
            TimetableDownloader.RESULT_UP_TO_DATE -> getString(R.string.timetable_up_to_date)
            TimetableDownloader.RESULT_FINISHED -> getString(R.string.timetable_downloaded)
            else -> getString(R.string.error_try_later)
        }
        try {
            Snackbar.make(findViewById(R.id.stop_layout), message, Snackbar.LENGTH_LONG).show()
        } catch (e: IllegalArgumentException) {
        }
        providerProxy.refreshTimetable(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (providerProxy.mode == ProviderProxy.MODE_FULL)
            menuInflater.inflate(R.menu.menu_stop, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId

        if (id == R.id.action_change_type) {
            if (timetableType == TIMETABLE_TYPE_DEPARTURE) {
                timetableType = TIMETABLE_TYPE_FULL

                item.icon = (ResourcesCompat.getDrawable(resources, R.drawable.ic_timetable_departure, this.theme))
                adapter.relativeTime = false
                if (fullDepartures.isEmpty())
                    if (sourceType == SOURCE_TYPE_STOP)
                        fullDepartures.putAll(providerProxy.getFullTimetable(stopCode))
                    else
                        fullDepartures.putAll(favourite!!.fullTimetable())

                dateSpinner.let { spinner ->
                    spinner.adapter = ServiceAdapter(this, R.layout.toolbar_spinner_item, fullDepartures.keys.map {
                        ServiceAdapter.RowItem(it, providerProxy.describeService(it, this)!!)
                    }.sorted()).apply {
                        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    }
                    spinner.visibility = View.VISIBLE
                    spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            refreshAdapter()
                        }

                        override fun onNothingSelected(parent: AdapterView<*>?) {
                        }

                    }
                }

                refreshAdapter()
            } else {
                dateSpinner.visibility = View.GONE
                timetableType = TIMETABLE_TYPE_DEPARTURE
                item.icon = (ResourcesCompat.getDrawable(resources, R.drawable.ic_timetable_full, this.theme))
                adapter.relativeTime = true
                refreshAdapter()
            }
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        receiver.removeOnTimetableDownloadListener(context)
        if (sourceType == SOURCE_TYPE_STOP)
            providerProxy.unsubscribeFromDepartures(subscriptionId, this)
        else
            favourite!!.unsubscribeFromDepartures(this)
        unregisterReceiver(receiver)
    }
}
