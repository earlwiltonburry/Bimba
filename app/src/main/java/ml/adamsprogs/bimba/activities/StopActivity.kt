package ml.adamsprogs.bimba.activities

import android.content.*
import android.support.design.widget.*
import android.os.Bundle
import android.view.*
import android.support.v4.app.*
import android.support.v4.view.PagerAdapter
import android.support.v4.content.res.ResourcesCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.*

import java.util.Calendar
import kotlinx.android.synthetic.main.activity_stop.*
import ml.adamsprogs.bimba.*
import ml.adamsprogs.bimba.collections.FavouriteStorage
import ml.adamsprogs.bimba.datasources.*
import ml.adamsprogs.bimba.models.*
import ml.adamsprogs.bimba.models.adapters.DeparturesAdapter

class StopActivity : AppCompatActivity(), MessageReceiver.OnTimetableDownloadListener, ProviderProxy.OnDeparturesReadyListener {

    private var sectionsPagerAdapter: SectionsPagerAdapter? = null

    companion object {
        const val EXTRA_STOP_CODE = "stopCode"
        const val EXTRA_STOP_NAME = "stopName"
        const val EXTRA_FAVOURITE = "favourite"
        const val SOURCE_TYPE = "sourceType"
        const val SOURCE_TYPE_STOP = "stop"
        const val SOURCE_TYPE_FAV = "favourite"

        const val MODE_WORKDAYS = 0
        const val MODE_SATURDAYS = 1
        const val MODE_SUNDAYS = 2

        const val TIMETABLE_TYPE_DEPARTURE = "timetable_type_departure"
        const val TIMETABLE_TYPE_FULL = "timetable_type_full"
    }

    private var stopCode = ""
    private var favourite: Favourite? = null
    private var timetableType = "departure"
    private val context = this
    private val receiver = MessageReceiver.getMessageReceiver()
    private lateinit var providerProxy: ProviderProxy
    private val departures = HashMap<Plate.ID, Set<Departure>>()
    private val fullDepartures = HashMap<Int, List<Departure>>()
    private lateinit var subscriptionId: String


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

        sectionsPagerAdapter = SectionsPagerAdapter(supportFragmentManager, null)

        container.adapter = sectionsPagerAdapter

        container.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(tabs))
        tabs.addOnTabSelectedListener(TabLayout.ViewPagerOnTabSelectedListener(container))

        selectTodayPage()

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
            /* todo
            if (!favourites.has(stopSymbol)) {
                val items = HashSet<StopSegment>()
                items.add(stopSegment!!)
                favourites.add(stopSymbol, items, this@StopActivity)
                fab.setImageDrawable(ResourcesCompat.getDrawable(context.resources, R.drawable.ic_favourite, this.theme))
            } else {
                Snackbar.make(it, getString(R.string.stop_already_fav), Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show()
            }
            */
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

    override fun onDeparturesReady(departures: Set<Departure>, plateId: Plate.ID) {
        this.departures[plateId] = HashSet()
        (this.departures[plateId]as HashSet).addAll(departures)
        if (timetableType == TIMETABLE_TYPE_FULL)
            return
        refreshAdapter()
    }

    private fun refreshAdapter() {
        if (timetableType == TIMETABLE_TYPE_FULL)
            sectionsPagerAdapter!!.departures = fullDepartures
        else {
            val departures = HashMap<Int, List<Departure>>()
            val now = Calendar.getInstance()
            val tab = now.getMode()
            val seconds = now.secondsAfterMidnight()
            departures[tab] = this.departures.flatMap { it.value }.sortedBy { it.timeTill(seconds) }
            sectionsPagerAdapter!!.departures = departures
        }
        sectionsPagerAdapter!!.notifyDataSetChanged()
    }

    override fun onTimetableDownload(result: String?) {
        val message: String = when (result) {
            TimetableDownloader.RESULT_NO_CONNECTIVITY -> getString(R.string.no_connectivity)
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

    private fun selectTodayPage() {
        tabs.getTabAt(sectionsPagerAdapter!!.todayTab())!!.select()
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
                sectionsPagerAdapter?.relativeTime = false
                if (fullDepartures.isEmpty())
                    if (sourceType == SOURCE_TYPE_STOP)
                        fullDepartures.putAll(providerProxy.getFullTimetable(stopCode))
                    else
                        fullDepartures.putAll(favourite!!.fullTimetable())
                refreshAdapter()
            } else {
                timetableType = TIMETABLE_TYPE_DEPARTURE
                item.icon = (ResourcesCompat.getDrawable(resources, R.drawable.ic_timetable_full, this.theme))
                sectionsPagerAdapter?.relativeTime = true
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
            favourite!!.unsubscribeFromDepartures(subscriptionId, this)
        unregisterReceiver(receiver)
    }

    inner class SectionsPagerAdapter(fm: FragmentManager, var departures: Map<Int, List<Departure>>?) : FragmentStatePagerAdapter(fm) {
        var relativeTime = true

        override fun getItem(position: Int): Fragment {
            if (departures == null)
                return PlaceholderFragment.newInstance(null, relativeTime) { updateFabVisibility(it) }
            if (departures!!.isEmpty())
                return PlaceholderFragment.newInstance(ArrayList(), relativeTime) { updateFabVisibility(it) }
            val list: List<Departure> = departures!![position] ?: ArrayList()
            return PlaceholderFragment.newInstance(list, relativeTime) { updateFabVisibility(it) }
        }

        override fun getCount() = 3

        override fun getItemPosition(obj: Any): Int {
            return PagerAdapter.POSITION_NONE
        }

        fun todayTab(): Int {
            return Calendar.getInstance().getMode()
        }
    }

    class PlaceholderFragment : Fragment() {
        lateinit var updater: (Int) -> Unit
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            val rootView = inflater.inflate(R.layout.fragment_stop, container, false)

            val layoutManager = LinearLayoutManager(activity)
            val departuresList: RecyclerView = rootView.findViewById(R.id.departuresList)
            val departures = arguments?.getStringArrayList("departures")?.map { Departure.fromString(it) }
            if (departures != null && departures.isNotEmpty())
                departuresList.addItemDecoration(DividerItemDecoration(departuresList.context, layoutManager.orientation))


            departuresList.adapter = DeparturesAdapter(activity as Context, departures,
                    arguments?.get("relativeTime") as Boolean)
            departuresList.layoutManager = layoutManager

            departuresList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {}
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    updater(dy)
                    super.onScrolled(recyclerView, dx, dy)
                }
            })
            return rootView
        }

        companion object {
            fun newInstance(departures: List<Departure>?, relativeTime: Boolean, updater: (Int) -> Unit): PlaceholderFragment {
                val fragment = PlaceholderFragment()
                fragment.updater = updater
                val args = Bundle()
                if (departures != null) {
                    if (departures.isNotEmpty()) {
                        val d = ArrayList<String>()
                        departures.mapTo(d) { it.toString() }
                        args.putStringArrayList("departures", d)
                    } else
                        args.putStringArrayList("departures", ArrayList<String>())
                }
                args.putBoolean("relativeTime", relativeTime)
                fragment.arguments = args
                return fragment
            }
        }
    }
}
