package ml.adamsprogs.bimba.activities

import java.util.*
import kotlin.collections.*

import android.content.*
import android.os.Bundle
import android.view.*
import android.support.design.widget.*
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.*
import android.support.v4.app.*
import android.support.v4.view.*
import android.support.v4.content.res.ResourcesCompat

import ml.adamsprogs.bimba.models.*
import ml.adamsprogs.bimba.*
import kotlin.concurrent.thread
import kotlinx.android.synthetic.main.activity_stop.*
import ml.adamsprogs.bimba.datasources.TimetableDownloader
import ml.adamsprogs.bimba.datasources.VmClient
import ml.adamsprogs.bimba.gtfs.AgencyAndId
import android.support.v4.view.ViewPager

class StopActivity : AppCompatActivity(), MessageReceiver.OnTimetableDownloadListener, MessageReceiver.OnVmListener, Favourite.OnVmPreparedListener {
    companion object {
        const val EXTRA_STOP_ID = "stopId"
        const val EXTRA_FAVOURITE = "favourite"
        const val SOURCE_TYPE = "sourceType"
        const val SOURCE_TYPE_STOP = "stop"
        const val SOURCE_TYPE_FAV = "favourite"
    }


    private var stopSegment: StopSegment? = null
    private var favourite: Favourite? = null
    private var timetableType = "departure"
    private var sectionsPagerAdapter: SectionsPagerAdapter? = null
    private var viewPager: ViewPager? = null
    private lateinit var timetable: Timetable
    private lateinit var tabLayout: TabLayout
    private val context = this
    private val receiver = MessageReceiver.getMessageReceiver()
    private val vmDepartures = HashMap<Plate.ID, Set<Departure>>()
    private var hasDepartures = false

    private lateinit var sourceType: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stop)

        timetable = Timetable.getTimetable()

        sourceType = intent.getStringExtra(SOURCE_TYPE)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        when (sourceType) {
            SOURCE_TYPE_STOP -> {
                stopSegment = StopSegment(intent.getSerializableExtra(EXTRA_STOP_ID) as AgencyAndId, null)
                stopSegment!!.fillPlates()
                supportActionBar?.title = timetable.getStopName(stopSegment!!.stop)
            }
            SOURCE_TYPE_FAV -> {
                favourite = intent.getParcelableExtra(EXTRA_FAVOURITE)
                supportActionBar?.title = favourite!!.name
                favourite!!.addOnVmPreparedListener(this)
            }
        }

        prepareOnDownloadListener()

        viewPager = container
        tabLayout = tabs
        sectionsPagerAdapter = SectionsPagerAdapter(supportFragmentManager, HashMap<AgencyAndId, ArrayList<Departure>>())

        /*thread {
            if (sourceType == SOURCE_TYPE_STOP) {
                sectionsPagerAdapter!!.departures = Departure.createDepartures(stopSegment!!.stop)
            } else {
                sectionsPagerAdapter!!.departures = favourite!!.allDepartures()
            }
            runOnUiThread {
                sectionsPagerAdapter?.notifyDataSetChanged()
            }
        }*/

        viewPager!!.adapter = sectionsPagerAdapter
        viewPager!!.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(tabLayout))
        tabLayout.addOnTabSelectedListener(TabLayout.ViewPagerOnTabSelectedListener(viewPager))

        selectTodayPage()

        showFab()
    }

    private fun getFavouriteDepartures() {
        thread {
            refreshAdapter(favourite!!.allDepartures())
        }
    }

    private fun refreshAdapter(departures: Map<AgencyAndId, List<Departure>>) {
        tabLayout.removeAllTabs()
        sectionsPagerAdapter?.notifyDataSetChanged()
        departures.keys.sortedBy {
            timetable.calendarToMode(AgencyAndId(it.id)).sorted()[0]
        }.forEach {
                    val tab = tabLayout.newTab()
                    tab.text = timetable.calendarToMode(it)
                            .joinToString { resources.getStringArray(R.array.daysOfWeekShort)[it] }
                    tabLayout.addTab(tab)
                    sectionsPagerAdapter?.notifyDataSetChanged()
                }
        println("refreshing:")
        departures[AgencyAndId("4")]?.forEach {
            println("${it.lineText} -> ${it.direction} @ ${it.time}")
        }
        sectionsPagerAdapter?.departures = departures
        try {
            sectionsPagerAdapter?.notifyDataSetChanged()
        } catch (e: Exception) {
            runOnUiThread {
                sectionsPagerAdapter?.notifyDataSetChanged()
            }
        }

        selectTodayPage()
    }

    override fun onVmPrepared() {
        getFavouriteDepartures()
    }

    private fun showFab() {
        if (sourceType == SOURCE_TYPE_FAV)
            return

        val stopSymbol = timetable.getStopCode(stopSegment!!.stop)

        val favourites = FavouriteStorage.getFavouriteStorage(context)
        if (!favourites.has(stopSymbol)) {
            fab.setImageDrawable(ResourcesCompat.getDrawable(context.resources, R.drawable.ic_favourite_empty, this.theme))
        }

        fab.setOnClickListener {
            if (!favourites.has(stopSymbol)) {
                val items = HashSet<Plate>()
                timetable.getTripsForStop(stopSegment!!.stop).values.forEach {
                    val o = Plate(Plate.ID(it.routeId, stopSegment!!.stop, it.headsign), null)
                    items.add(o)
                }
                favourites.add(stopSymbol, items)
                fab.setImageDrawable(ResourcesCompat.getDrawable(context.resources, R.drawable.ic_favourite, this.theme))
            } else {
                Snackbar.make(it, getString(R.string.stop_already_fav), Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show()
            }
        }
    }

    private fun prepareOnDownloadListener() {
        val filter = IntentFilter(TimetableDownloader.ACTION_DOWNLOADED)
        filter.addAction(VmClient.ACTION_READY)
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        registerReceiver(receiver, filter)
        receiver.addOnTimetableDownloadListener(context)
        if (sourceType == SOURCE_TYPE_STOP) {
            receiver.addOnVmListener(context)
            val intent = Intent(this, VmClient::class.java)
            intent.putExtra("stop", stopSegment)
            intent.action = "request"
            startService(intent)
        } else
            favourite!!.registerOnVm(receiver, context)
    }

    override fun onVm(vmDepartures: Set<Departure>?, plateId: Plate.ID) {
        if (vmDepartures == null && this.vmDepartures.isEmpty() && hasDepartures) {
            return
        }
        if (timetableType == "departure" && stopSegment!!.contains(plateId)) {
            if (vmDepartures != null)
                this.vmDepartures[plateId] = vmDepartures
            else
                this.vmDepartures.remove(plateId)
            val departures = HashMap<AgencyAndId, List<Departure>>()
            if (this.vmDepartures.isNotEmpty()) {
                departures[timetable.getServiceForToday()] = this.vmDepartures.flatMap { it.value }.sortedBy { it.timeTill() }
                refreshAdapter(departures)
            } else {
                refreshAdapter(Departure.createDepartures(stopSegment!!.stop))
                hasDepartures = true
            }
        }
    }

    override fun onTimetableDownload(result: String?) {
        val message: String = when (result) {
            TimetableDownloader.RESULT_DOWNLOADED -> getString(R.string.refreshing_cache)
            TimetableDownloader.RESULT_NO_CONNECTIVITY -> getString(R.string.no_connectivity)
            TimetableDownloader.RESULT_UP_TO_DATE -> getString(R.string.timetable_up_to_date)
            TimetableDownloader.RESULT_FINISHED -> getString(R.string.timetable_downloaded)
            else -> getString(R.string.error_try_later)
        }
        try {
            Snackbar.make(findViewById(R.id.drawer_layout), message, Snackbar.LENGTH_LONG).show()
        } catch (e: IllegalArgumentException) {
        }
        //todo refresh
    }

    private fun selectTodayPage() { //fixme does not work
        val today = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1) % 7
        tabLayout.getTabAt(sectionsPagerAdapter!!.todayTab(today))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_stop, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId

        if (id == R.id.action_change_type) {
            if (timetableType == "departure") {
                timetableType = "full"
                item.icon = (ResourcesCompat.getDrawable(resources, R.drawable.ic_timetable_departure, this.theme))
                sectionsPagerAdapter?.relativeTime = false
                if (sourceType == SOURCE_TYPE_STOP)
                    refreshAdapter(timetable.getStopDepartures(stopSegment!!.stop))
                else
                    refreshAdapter(favourite!!.fullTimetable())
            } else {
                timetableType = "departure"
                item.icon = (ResourcesCompat.getDrawable(resources, R.drawable.ic_timetable_full, this.theme))
                sectionsPagerAdapter?.relativeTime = true
                if (sourceType == SOURCE_TYPE_STOP)
                    refreshAdapter(Departure.createDepartures(stopSegment!!.stop))
                else
                    refreshAdapter(favourite!!.allDepartures())
            }
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        receiver.removeOnTimetableDownloadListener(context)
        if (sourceType == SOURCE_TYPE_STOP) {
            receiver.removeOnVmListener(context)
            val intent = Intent(this, VmClient::class.java)
            intent.putExtra("stop", stopSegment)
            intent.action = "remove"
            startService(intent)
        } else
            favourite!!.deregisterOnVm(receiver, context)
        unregisterReceiver(receiver)
    }

    class PlaceholderFragment : Fragment() {

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            val rootView = inflater.inflate(R.layout.fragment_stop, container, false)

            val layoutManager = LinearLayoutManager(activity)
            val departuresList: RecyclerView = rootView.findViewById(R.id.departuresList)
            departuresList.addItemDecoration(DividerItemDecoration(departuresList.context, layoutManager.orientation))

            val departures = arguments?.getStringArrayList("departures")!!.map { Departure.fromString(it) }
            departuresList.adapter = DeparturesAdapter(activity as Context, departures,
                    arguments?.get("relativeTime") as Boolean)
            departuresList.layoutManager = layoutManager
            return rootView
        }

        companion object {
            private const val ARG_SECTION_NUMBER = "section_number"

            fun newInstance(sectionNumber: Int, departures: List<Departure>, relativeTime: Boolean):
                    PlaceholderFragment {
                val fragment = PlaceholderFragment()
                val args = Bundle()
                args.putInt(ARG_SECTION_NUMBER, sectionNumber)
                println("newInstance:")
                departures.forEach {
                    println("${it.lineText} -> ${it.direction} @ ${it.time}")
                }
                if (departures.isEmpty()) {
                    val d = ArrayList<String>()
                    departures.mapTo(d) { it.toString() }
                    args.putStringArrayList("departures", d)
                } else
                    args.putStringArrayList("departures", ArrayList<String>())
                args.putBoolean("relativeTime", relativeTime)
                fragment.arguments = args
                return fragment
            }
        }
    }

    inner class SectionsPagerAdapter(fm: FragmentManager, departures: Map<AgencyAndId, List<Departure>>) : FragmentStatePagerAdapter(fm) { //todo swipe

        var departures: Map<AgencyAndId, List<Departure>> = departures
            set(value) {
                println("setting:")
                value[AgencyAndId("4")]?.forEach {
                    println("${it.lineText} -> ${it.direction} @ ${it.time}")
                }
                field = value
                println("set:")
                this.departures[AgencyAndId("4")]?.forEach {
                    println("${it.lineText} -> ${it.direction} @ ${it.time}")
                }
            }

        private var modes = ArrayList<AgencyAndId>()

        init {
            val tab = tabLayout.newTab()
            tab.text = getString(R.string.today)
            tabLayout.addTab(tab)
            sectionsPagerAdapter?.notifyDataSetChanged()
        }

        var relativeTime = true

        fun todayTab(today: Int): Int {
            if (modes.isEmpty())
                return 0
            return modes.indexOf(modes.filter {
                timetable.calendarToMode(it).contains(today)
            }[0])
        }

        override fun getItemPosition(obj: Any): Int {
            return PagerAdapter.POSITION_NONE
        }

        override fun getItem(position: Int): Fragment {
            //fixme doesn't refresh after getting departures/switching. Thinks `departures` is empty. May be connected with swipe
            println("adapter:")
            departures[AgencyAndId("4")]?.forEach {
                println("${it.lineText} -> ${it.direction} @ ${it.time}")
            }
            val list = if (departures.isEmpty())
                ArrayList()
            else
                departures[modes[position]]!!
            return PlaceholderFragment.newInstance(position + 1, list, relativeTime)
        }

        override fun getCount() = if (departures.isEmpty()) 1 else modes.size
    }
}
