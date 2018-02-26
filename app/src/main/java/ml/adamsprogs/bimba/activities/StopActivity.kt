package ml.adamsprogs.bimba.activities

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.support.design.widget.TabLayout
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity

import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.os.Bundle
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v4.content.res.ResourcesCompat
import android.support.v4.view.PagerAdapter
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup

import kotlinx.android.synthetic.main.activity_stop.*
import ml.adamsprogs.bimba.MessageReceiver
import ml.adamsprogs.bimba.R
import ml.adamsprogs.bimba.datasources.TimetableDownloader
import ml.adamsprogs.bimba.datasources.VmClient
import ml.adamsprogs.bimba.getMode
import ml.adamsprogs.bimba.models.gtfs.AgencyAndId
import ml.adamsprogs.bimba.models.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.thread

class StopActivity : AppCompatActivity(), MessageReceiver.OnTimetableDownloadListener, MessageReceiver.OnVmListener, Favourite.OnVmPreparedListener {

    private var sectionsPagerAdapter: SectionsPagerAdapter? = null

    companion object {
        const val EXTRA_STOP_ID = "stopId"
        const val EXTRA_FAVOURITE = "favourite"
        const val SOURCE_TYPE = "sourceType"
        const val SOURCE_TYPE_STOP = "stop"
        const val SOURCE_TYPE_FAV = "favourite"

        const val MODE_WORKDAYS = 0
        const val MODE_SATURDAYS = 1
        const val MODE_SUNDAYS = 2
    }

    private var stopSegment: StopSegment? = null
    private var favourite: Favourite? = null
    private var timetableType = "departure"
    private lateinit var timetable: Timetable
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

        sectionsPagerAdapter = SectionsPagerAdapter(supportFragmentManager, null)

        container.adapter = sectionsPagerAdapter

        container.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(tabs))
        tabs.addOnTabSelectedListener(TabLayout.ViewPagerOnTabSelectedListener(container))

        selectTodayPage()

        showFab()

        prepareOnDownloadListener()
    }

    private fun getFavouriteDepartures() {
        thread {
            refreshAdapter(favourite!!.allDepartures())
        }
    }

    private fun refreshAdapterFromStop() {
        val departures = HashMap<AgencyAndId, List<Departure>>()
        if (this.vmDepartures.isNotEmpty()) {
            departures[timetable.getServiceForToday()] = this.vmDepartures.flatMap { it.value }.sortedBy { it.timeTill(true) }
            refreshAdapter(departures)
        } else {
            refreshAdapter(Departure.createDepartures(stopSegment!!.stop))
            hasDepartures = true
        }
    }

    private fun refreshAdapter(departures: Map<AgencyAndId, List<Departure>>?) {
        if (departures != null)
            sectionsPagerAdapter?.departures = departures
        runOnUiThread {
            sectionsPagerAdapter?.notifyDataSetChanged()
            selectTodayPage()
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
            refreshAdapterFromStop()
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

    private fun selectTodayPage() {
        tabs.getTabAt(sectionsPagerAdapter!!.todayTab())!!.select()
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
                    refreshAdapterFromStop()
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

    inner class SectionsPagerAdapter(fm: FragmentManager, var departures: Map<AgencyAndId, List<Departure>>?) : FragmentStatePagerAdapter(fm) {
        var relativeTime = true

        override fun getItem(position: Int): Fragment {
            if (departures == null)
                return PlaceholderFragment.newInstance(null, relativeTime)
            if (departures!!.isEmpty())
                return PlaceholderFragment.newInstance(ArrayList(), relativeTime)
            val sat = timetable.getServiceFor("saturday")
            val sun = timetable.getServiceFor("sunday")
            val list: List<Departure> = when (position) {
                1 -> departures!![sat] ?: ArrayList()
                2 -> departures!![sun] ?: ArrayList()
                0 -> try {
                    departures!!
                            .filter { it.key != sat && it.key != sun }
                            .filter { it.value.isNotEmpty() }.toList()[0].second
                } catch (e: IndexOutOfBoundsException) {
                    departures!!.filter { it.key != sat && it.key != sun }.toList()[0].second
                } catch (e: Exception) {
                    ArrayList<Departure>()
                }
                else -> throw IndexOutOfBoundsException("No tab at index $position")
            }
            return PlaceholderFragment.newInstance(list, relativeTime)
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
            return rootView
        }

        companion object {
            fun newInstance(departures: List<Departure>?, relativeTime: Boolean): PlaceholderFragment {
                val fragment = PlaceholderFragment()
                val args = Bundle()
                if (departures != null){
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
