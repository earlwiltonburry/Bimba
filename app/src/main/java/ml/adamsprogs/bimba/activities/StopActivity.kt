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
            sectionsPagerAdapter!!.departures = favourite!!.allDepartures()
        }
        runOnUiThread {
            sectionsPagerAdapter?.notifyDataSetChanged()
        }
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
                timetable.getTripsForStop(stopSegment!!.stop).forEach {
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
        if (timetableType == "departure" && stopSegment!!.contains(plateId)) {
            if (vmDepartures != null)
                this.vmDepartures[plateId] = vmDepartures
            else
                this.vmDepartures.remove(plateId)
            val departures = HashMap<AgencyAndId, List<Departure>>()
            if (this.vmDepartures.isNotEmpty()) {
                departures[timetable.getServiceForToday()] = this.vmDepartures.flatMap { it.value }.sortedBy { it.timeTill() }
                sectionsPagerAdapter?.departures = departures
            } else
                sectionsPagerAdapter?.departures = Departure.createDepartures(stopSegment!!.stop)
            sectionsPagerAdapter?.notifyDataSetChanged()
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
        Snackbar.make(findViewById(R.id.drawer_layout), message, Snackbar.LENGTH_LONG).show()
        //todo refresh
    }

    private fun selectTodayPage() { //todo Services
        val today = Calendar.getInstance()
        when (today.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> tabLayout.getTabAt(1)?.select()
            Calendar.SUNDAY -> tabLayout.getTabAt(2)?.select()
            else -> tabLayout.getTabAt(0)?.select()
        }
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
                if (sourceType == SOURCE_TYPE_STOP)
                    sectionsPagerAdapter?.departures = timetable.getStopDepartures(stopSegment!!.stop)
                else
                    sectionsPagerAdapter?.departures = favourite!!.fullTimetable()
                sectionsPagerAdapter?.relativeTime = false
                sectionsPagerAdapter?.notifyDataSetChanged()
            } else {
                timetableType = "departure"
                item.icon = (ResourcesCompat.getDrawable(resources, R.drawable.ic_timetable_full, this.theme))
                if (sourceType == SOURCE_TYPE_STOP)
                    sectionsPagerAdapter?.departures = Departure.createDepartures(stopSegment!!.stop)
                else
                    sectionsPagerAdapter?.departures = favourite!!.allDepartures()
                sectionsPagerAdapter?.relativeTime = true
                sectionsPagerAdapter?.notifyDataSetChanged()
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

    inner class SectionsPagerAdapter(fm: FragmentManager, var departures: Map<AgencyAndId, List<Departure>>) : FragmentStatePagerAdapter(fm) {

        private val modes = departures.keys.sortedBy {
            timetable.calendarToMode(AgencyAndId(it.id)).sorted()[0]
        }

        var relativeTime = true

        override fun getItemPosition(obj: Any): Int {
            return PagerAdapter.POSITION_NONE
        }

        override fun getItem(position: Int): Fragment {
            val list = if (departures.isEmpty())
                ArrayList()
            else
                departures[modes[position]]!!
            return PlaceholderFragment.newInstance(position + 1, list, relativeTime)
        }

        override fun getCount() = if (departures.isEmpty()) 1 else modes.size
    }
}
