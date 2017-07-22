package ml.adamsprogs.bimba

import android.support.design.widget.*
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar

import android.support.v4.app.*
import android.support.v4.view.ViewPager
import android.os.Bundle
import android.support.v4.content.res.ResourcesCompat
import android.support.v4.view.PagerAdapter
import android.view.*

import ml.adamsprogs.bimba.models.*
import android.support.v7.widget.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


class StopActivity : AppCompatActivity() { //todo refresh

    private lateinit var stopId: String
    private var timetableType = "departure"
    private var sectionsPagerAdapter: SectionsPagerAdapter? = null
    private var viewPager: ViewPager? = null
    private lateinit var timetable: Timetable
    private val today = Calendar.getInstance()
    private lateinit var tabLayout: TabLayout

    override fun onCreate(savedInstanceState: Bundle?) { //todo select current mode
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stop)
        stopId = intent.getStringExtra("stop")

        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)

        /*todo when Internet connection
        exists -> download vm
        else -> timetable
         */
        timetable = Timetable(this)
        supportActionBar?.title = timetable.getStopName(stopId) ?: "Stop"

        viewPager = findViewById(R.id.container) as ViewPager
        tabLayout = findViewById(R.id.tabs) as TabLayout

        sectionsPagerAdapter = SectionsPagerAdapter(supportFragmentManager, createDepartures())

        viewPager!!.adapter = sectionsPagerAdapter
        viewPager!!.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(tabLayout))
        tabLayout.addOnTabSelectedListener(TabLayout.ViewPagerOnTabSelectedListener(viewPager))

        selectTodayPage()

        val fab = findViewById(R.id.fab) as FloatingActionButton
        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
            //todo favourites
        }
    }

    private fun createDepartures(): HashMap<String, ArrayList<Departure>> {
        val departures = timetable.getStopDepartures(stopId)
        val moreDepartures = timetable.getStopDepartures(stopId)
        val rolledDepartures = HashMap<String, ArrayList<Departure>>()

        for ((_, tomorrowDepartures) in moreDepartures!!) {
            tomorrowDepartures.forEach{it.tomorrow = true}
        }

        for ((mode, _) in departures!!) {
            rolledDepartures[mode] = (departures[mode] as ArrayList<Departure> +
                    moreDepartures[mode] as ArrayList<Departure>) as ArrayList<Departure>
            rolledDepartures[mode] = filterDepartures(rolledDepartures[mode])
        }

        return rolledDepartures
    }

    private fun filterDepartures(departures: List<Departure>?): ArrayList<Departure> {
        val filtered = ArrayList<Departure>()
        val lines = HashMap<String, Int>()
        val now = Calendar.getInstance()
        for (departure in departures!!) {
            val time = Calendar.getInstance()
            time.set(Calendar.HOUR_OF_DAY, Integer.parseInt(departure.time.split(":")[0]))
            time.set(Calendar.MINUTE, Integer.parseInt(departure.time.split(":")[1]))
            time.set(Calendar.SECOND, 0)
            time.set(Calendar.MILLISECOND, 0)
            if (departure.tomorrow)
                time.add(Calendar.DAY_OF_MONTH, 1)
            var lineExistedTimes = lines[departure.line]
            if (now.before(time) && lineExistedTimes ?: 0 < 3) {
                lineExistedTimes = (lineExistedTimes ?: 0) + 1
                lines[departure.line] = lineExistedTimes
                filtered.add(departure)
            }
        }
        return filtered
    }

    private fun selectTodayPage() {
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
                sectionsPagerAdapter?.changeDepartures(timetable.getStopDepartures(stopId)!!)
                sectionsPagerAdapter?.notifyDataSetChanged()
            } else {
                timetableType = "departure"
                item.icon = (ResourcesCompat.getDrawable(resources, R.drawable.ic_timetable_full, this.theme))
                sectionsPagerAdapter?.changeDepartures(createDepartures())
                sectionsPagerAdapter?.notifyDataSetChanged()
            }
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    class PlaceholderFragment : Fragment() {

        override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                                  savedInstanceState: Bundle?): View? {
            val rootView = inflater!!.inflate(R.layout.fragment_stop, container, false)

            val layoutManager = LinearLayoutManager(activity)
            val departuresList: RecyclerView = rootView.findViewById(R.id.departuresList) as RecyclerView
            val dividerItemDecoration = DividerItemDecoration(departuresList.context, layoutManager.orientation)
            departuresList.addItemDecoration(dividerItemDecoration)
            val adapter = DeparturesAdapter(activity, arguments.getStringArrayList("departures").map { fromString(it) })
            departuresList.adapter = adapter
            departuresList.layoutManager = layoutManager
            return rootView
        }

        companion object {
            private val ARG_SECTION_NUMBER = "section_number"

            fun newInstance(sectionNumber: Int, stopId: String, departures: ArrayList<Departure>?): PlaceholderFragment {
                val fragment = PlaceholderFragment()
                val args = Bundle()
                args.putInt(ARG_SECTION_NUMBER, sectionNumber)
                args.putString("stop", stopId)
                val d = ArrayList<String>()
                departures?.mapTo(d) { it.toString() }
                args.putStringArrayList("departures", d)
                fragment.arguments = args
                return fragment
            }
        }
    }

    inner class SectionsPagerAdapter(fm: FragmentManager, var departures: HashMap<String, ArrayList<Departure>>) : FragmentStatePagerAdapter(fm) {

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
            return PlaceholderFragment.newInstance(position + 1, stopId, departures[mode])
        }

        fun changeDepartures(departures: HashMap<String, ArrayList<Departure>>) {
            this.departures = departures
        }

        override fun getCount() = 3
    }
}
