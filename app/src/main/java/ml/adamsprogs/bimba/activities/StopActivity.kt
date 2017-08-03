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
import android.util.Log

import ml.adamsprogs.bimba.models.*
import ml.adamsprogs.bimba.*


class StopActivity : AppCompatActivity(), MessageReceiver.OnVmListener {

    private lateinit var stopId: String
    private lateinit var stopSymbol: String
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
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stop)
        stopId = intent.getStringExtra("stopId")
        stopSymbol = intent.getStringExtra("stopSymbol")

        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)

        sharedPreferences = this.getSharedPreferences("ml.adamsprogs.bimba.prefs", Context.MODE_PRIVATE)

        createTimerTask()

        prepareOnDownloadListener()

        timetable = Timetable.getTimetable()
        supportActionBar?.title = timetable.getStopName(stopId) ?: "Stop"

        viewPager = findViewById(R.id.container) as ViewPager
        tabLayout = findViewById(R.id.tabs) as TabLayout

        sectionsPagerAdapter = SectionsPagerAdapter(supportFragmentManager, createDepartures(stopId))

        viewPager!!.adapter = sectionsPagerAdapter
        viewPager!!.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(tabLayout))
        tabLayout.addOnTabSelectedListener(TabLayout.ViewPagerOnTabSelectedListener(viewPager))

        selectTodayPage()

        scheduleRefresh()

        val fab = findViewById(R.id.fab) as FloatingActionButton

        val favourites = FavouriteStorage.getFavouriteStorage(context)
        if (!favourites.has(stopSymbol)) {
            fab.setImageDrawable(ResourcesCompat.getDrawable(context.resources, R.drawable.ic_favourite_empty, this.theme))
        }

        fab.setOnClickListener {
            Log.i("FAB", "Click")
            if (!favourites.has(stopSymbol)) {
                Log.i("FAB", "Add")
                val items = ArrayList<HashMap<String, String>>()
                timetable.getLines(stopId).forEach {
                    val o = HashMap<String, String>()
                    o["stop"] = stopId
                    o["line"] = it
                    items.add(o)
                }
                Log.i("FAB", "Say")
                favourites.add(stopSymbol, items)
                Log.i("FAB", "Change")
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
                vmIntent.putExtra("stopId", stopId)
                vmIntent.putExtra("stopSymbol", stopSymbol)
                startService(vmIntent)
            }
        }
    }

    private fun prepareOnDownloadListener() {
        val filter = IntentFilter("ml.adamsprogs.bimba.departuresCreated")
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        registerReceiver(receiver, filter)
        receiver.addOnVmListener(context as MessageReceiver.OnVmListener)
    }

    override fun onVm(departures: HashMap<String, ArrayList<Departure>>) {
        sectionsPagerAdapter?.departures = departures
        sectionsPagerAdapter?.notifyDataSetChanged()
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
        timer.scheduleAtFixedRate(timerTask, 0, 15000)
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
                sectionsPagerAdapter?.departures = timetable.getStopDepartures(stopId)!!
                sectionsPagerAdapter?.relativeTime = false
                sectionsPagerAdapter?.notifyDataSetChanged()
                timer.cancel()
            } else {
                timetableType = "departure"
                item.icon = (ResourcesCompat.getDrawable(resources, R.drawable.ic_timetable_full, this.theme))
                sectionsPagerAdapter?.departures = createDepartures(stopId)
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
            val departuresList: RecyclerView = rootView.findViewById(R.id.departuresList) as RecyclerView
            val dividerItemDecoration = DividerItemDecoration(departuresList.context, layoutManager.orientation)
            departuresList.addItemDecoration(dividerItemDecoration)
            val adapter = DeparturesAdapter(activity, arguments.getStringArrayList("departures").map { fromString(it) },
                    arguments["relativeTime"] as Boolean)
            departuresList.adapter = adapter
            departuresList.layoutManager = layoutManager
            return rootView
        }

        companion object {
            private val ARG_SECTION_NUMBER = "section_number"

            fun newInstance(sectionNumber: Int, stopId: String, departures: ArrayList<Departure>?, relativeTime: Boolean):
                    PlaceholderFragment {
                val fragment = PlaceholderFragment()
                val args = Bundle()
                args.putInt(ARG_SECTION_NUMBER, sectionNumber)
                args.putString("stop", stopId)
                val d = ArrayList<String>()
                departures?.mapTo(d) { it.toString() }
                args.putStringArrayList("departures", d)
                args.putBoolean("relativeTime", relativeTime)
                fragment.arguments = args
                return fragment
            }
        }
    }

    inner class SectionsPagerAdapter(fm: FragmentManager, var departures: HashMap<String, ArrayList<Departure>>) : FragmentStatePagerAdapter(fm) {

        var relativeTime = true

        override fun getItemPosition(obj: Any?): Int {
            return PagerAdapter.POSITION_NONE
        }

        override fun getItem(position: Int): Fragment {
            var mode: String? = null
            when (position) {
                0 -> mode = "workdays"
                1 -> mode = "saturdays"
                2 -> mode = "sundays"
            }
            return PlaceholderFragment.newInstance(position + 1, stopId, departures[mode], relativeTime)
        }

        override fun getCount() = 3
    }
}
