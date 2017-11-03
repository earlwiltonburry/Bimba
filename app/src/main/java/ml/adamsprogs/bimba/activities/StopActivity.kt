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

class StopActivity : AppCompatActivity(), MessageReceiver.OnVmListener {

    companion object {
        val EXTRA_STOP_ID = "stopId"
        val EXTRA_STOP_SYMBOL = "stopSymbol"
        val EXTRA_FAVOURITE = "favourite"
        val REQUESTER_ID = "stopActivity"
        val SOURCE_TYPE = "sourceType"
        val SOURCE_TYPE_STOP = "stop"
        val SOURCE_TYPE_FAV = "favourite"
    }

    private var stopId: String? = null
    private var stopSymbol: String? = null
    private var favourite: Favourite? = null
    private var timetableType = "departure"
    private var sectionsPagerAdapter: SectionsPagerAdapter? = null
    private var viewPager: ViewPager? = null
    private lateinit var timetable: Timetable
    private val today = Calendar.getInstance()
    private lateinit var tabLayout: TabLayout
    private var timer = Timer()
    private lateinit var timerTask: TimerTask
    private val context = this
    private val receiver = MessageReceiver()

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
                stopId = intent.getStringExtra(EXTRA_STOP_ID)
                stopSymbol = intent.getStringExtra(EXTRA_STOP_SYMBOL)
                supportActionBar?.title = timetable.getStopName(stopId!!)
            }
            SOURCE_TYPE_FAV -> {
                favourite = intent.getParcelableExtra(EXTRA_FAVOURITE)
                supportActionBar?.title = favourite!!.name

            }
        }

        createTimerTask()

        prepareOnDownloadListener()

        viewPager = container
        tabLayout = tabs

        sectionsPagerAdapter = SectionsPagerAdapter(supportFragmentManager, null)
        thread {
            if (sourceType == SOURCE_TYPE_STOP) {
                @Suppress("UNCHECKED_CAST")
                sectionsPagerAdapter!!.departures = Departure.createDepartures(stopId!!) as HashMap<String, ArrayList<Departure>>
            } else {
                @Suppress("UNCHECKED_CAST")
                sectionsPagerAdapter!!.departures = favourite!!.allDepartures() as HashMap<String, ArrayList<Departure>>
            }
            runOnUiThread {
                sectionsPagerAdapter?.notifyDataSetChanged()
            }
        }

        viewPager!!.adapter = sectionsPagerAdapter
        viewPager!!.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(tabLayout))
        tabLayout.addOnTabSelectedListener(TabLayout.ViewPagerOnTabSelectedListener(viewPager))

        selectTodayPage()

        scheduleRefresh()

        showFab()
    }

    private fun showFab() {
        if (sourceType == SOURCE_TYPE_FAV)
            return

        val favourites = FavouriteStorage.getFavouriteStorage(context)
        if (!favourites.has(stopSymbol!!)) {
            fab.setImageDrawable(ResourcesCompat.getDrawable(context.resources, R.drawable.ic_favourite_empty, this.theme))
        }

        fab.setOnClickListener {
            if (!favourites.has(stopSymbol!!)) {
                val items = HashSet<Plate>()
                timetable.getLines(stopId!!).forEach {
                    val o = Plate(it, stopId!!, null)
                    items.add(o)
                }
                favourites.add(stopSymbol as String, items)
                fab.setImageDrawable(ResourcesCompat.getDrawable(context.resources, R.drawable.ic_favourite, this.theme))
            } else {
                Snackbar.make(it, getString(R.string.stop_already_fav), Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show()
            }
        }
    }

    private fun createTimerTask() {
        timerTask = object : TimerTask() {
            override fun run() {
                val vmIntent = Intent(context, VmClient::class.java)
                vmIntent.putExtra(VmClient.EXTRA_STOP_SYMBOL, stopSymbol)
                vmIntent.putExtra(VmClient.EXTRA_REQUESTER, REQUESTER_ID)
                startService(vmIntent)
                runOnUiThread {
                    sectionsPagerAdapter?.notifyDataSetChanged()
                }
            }
        }
    }

    private fun prepareOnDownloadListener() {
        val filter = IntentFilter(VmClient.ACTION_DEPARTURES_CREATED)
        filter.addAction(VmClient.ACTION_NO_DEPARTURES)
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        registerReceiver(receiver, filter)
        receiver.addOnVmListener(context as MessageReceiver.OnVmListener)
    }

    override fun onVm(vmDepartures: ArrayList<Departure>?, requester: String) {
        if (timetableType == "departure" && requester == REQUESTER_ID && sourceType == SOURCE_TYPE_STOP) {
            @Suppress("UNCHECKED_CAST")
            val fullDepartures = Departure.createDepartures(stopId!!) as HashMap<String, ArrayList<Departure>>
            if (vmDepartures != null) {
                fullDepartures[today.getMode()] = vmDepartures
            }
            sectionsPagerAdapter?.departures = fullDepartures
            sectionsPagerAdapter?.notifyDataSetChanged()
        }
    }

    private fun selectTodayPage() {
        when (today.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> tabLayout.getTabAt(1)?.select()
            Calendar.SUNDAY -> tabLayout.getTabAt(2)?.select()
            else -> tabLayout.getTabAt(0)?.select()
        }
    }

    private fun scheduleRefresh() {
        timer.cancel()
        timer = Timer()
        createTimerTask()
        timer.scheduleAtFixedRate(timerTask, 15000, 15000)
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
                @Suppress("UNCHECKED_CAST")
                if (sourceType == SOURCE_TYPE_STOP)
                    sectionsPagerAdapter?.departures = timetable.getStopDepartures(stopId!!) as HashMap<String, ArrayList<Departure>>
                else
                    sectionsPagerAdapter?.departures = favourite!!.fullTimetable() as HashMap<String, ArrayList<Departure>>
                sectionsPagerAdapter?.relativeTime = false
                sectionsPagerAdapter?.notifyDataSetChanged()
                timer.cancel()
            } else {
                timetableType = "departure"
                item.icon = (ResourcesCompat.getDrawable(resources, R.drawable.ic_timetable_full, this.theme))
                @Suppress("UNCHECKED_CAST")
                if (sourceType == SOURCE_TYPE_STOP)
                    sectionsPagerAdapter?.departures = Departure.createDepartures(stopId!!) as HashMap<String, ArrayList<Departure>>
                else
                    sectionsPagerAdapter?.departures = favourite!!.allDepartures() as HashMap<String, ArrayList<Departure>>
                sectionsPagerAdapter?.relativeTime = true
                sectionsPagerAdapter?.notifyDataSetChanged()
                scheduleRefresh()
            }
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        receiver.removeOnVmListener(context as MessageReceiver.OnVmListener)
        unregisterReceiver(receiver)
        timer.cancel()
    }

    class PlaceholderFragment : Fragment() {

        override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                                  savedInstanceState: Bundle?): View? {
            val rootView = inflater!!.inflate(R.layout.fragment_stop, container, false)

            val layoutManager = LinearLayoutManager(activity)
            val departuresList: RecyclerView = rootView.findViewById(R.id.departuresList)
            departuresList.addItemDecoration(DividerItemDecoration(departuresList.context, layoutManager.orientation))

            val departures = arguments.getStringArrayList("departures")?.map { Departure.fromString(it) }
            departuresList.adapter = DeparturesAdapter(activity, departures,
                    arguments["relativeTime"] as Boolean)
            departuresList.layoutManager = layoutManager
            return rootView
        }

        companion object {
            private val ARG_SECTION_NUMBER = "section_number"

            fun newInstance(sectionNumber: Int, departures: ArrayList<Departure>?, relativeTime: Boolean):
                    PlaceholderFragment {
                val fragment = PlaceholderFragment()
                val args = Bundle()
                args.putInt(ARG_SECTION_NUMBER, sectionNumber)
                if (departures != null) {
                    val d = ArrayList<String>()
                    departures.mapTo(d) { it.toString() }
                    args.putStringArrayList("departures", d)
                } else
                    args.putStringArrayList("departures", null)
                args.putBoolean("relativeTime", relativeTime)
                fragment.arguments = args
                return fragment
            }
        }
    }

    inner class SectionsPagerAdapter(fm: FragmentManager, var departures: HashMap<String, ArrayList<Departure>>?) : FragmentStatePagerAdapter(fm) {

        var relativeTime = true

        override fun getItemPosition(obj: Any?): Int {
            return PagerAdapter.POSITION_NONE
        }

        override fun getItem(position: Int): Fragment {
            var mode: String? = null
            when (position) {
                0 -> mode = Timetable.MODE_WORKDAYS
                1 -> mode = Timetable.MODE_SATURDAYS
                2 -> mode = Timetable.MODE_SUNDAYS
            }
            return PlaceholderFragment.newInstance(position + 1, departures?.get(mode), relativeTime)
        }

        override fun getCount() = 3
    }
}
