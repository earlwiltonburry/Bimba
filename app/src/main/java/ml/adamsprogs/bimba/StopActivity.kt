package ml.adamsprogs.bimba

import android.support.design.widget.TabLayout
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar

import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.view.ViewPager
import android.os.Bundle
import android.support.v4.content.res.ResourcesCompat
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup

import ml.adamsprogs.bimba.models.Departure
import ml.adamsprogs.bimba.models.Timetable
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import ml.adamsprogs.bimba.models.DeparturesAdapter
import ml.adamsprogs.bimba.models.fromString
import android.support.v7.widget.DividerItemDecoration
import android.util.Log
import java.util.*


class StopActivity : AppCompatActivity() {

    private lateinit var stopId: String
    private var departures: HashMap<String, ArrayList<Departure>>? = null
    private var timetableType = "timetable"

    /**
     * The [android.support.v4.view.PagerAdapter] that will provide
     * fragments for each of the sections. We use a
     * [FragmentPagerAdapter] derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * [android.support.v4.app.FragmentStatePagerAdapter].
     */
    private var sectionsPagerAdapter: SectionsPagerAdapter? = null

    /**
     * The [ViewPager] that will host the section contents.
     */
    private var viewPager: ViewPager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stop)
        stopId = intent.getStringExtra("stop")

        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)

        /*todo when Internet connection
        exists -> download vm
        else -> timetable
         */
        val timetable = Timetable(this)
        supportActionBar?.title = timetable.getStopName(stopId) ?: "Stop"
        departures = timetable.getStopDepartures(stopId)
        //todo if departures == null -> …

        sectionsPagerAdapter = SectionsPagerAdapter(supportFragmentManager)

        viewPager = findViewById(R.id.container) as ViewPager
        viewPager!!.adapter = sectionsPagerAdapter

        val tabLayout = findViewById(R.id.tabs) as TabLayout

        viewPager!!.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(tabLayout))
        tabLayout.addOnTabSelectedListener(TabLayout.ViewPagerOnTabSelectedListener(viewPager))

        val fab = findViewById(R.id.fab) as FloatingActionButton
        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
            //todo favourites
        }

    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_stop, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId


        if (id == R.id.action_change_type) {
            if(timetableType == "timetable") {
                timetableType = "vm"
                item.icon = (ResourcesCompat.getDrawable(resources, R.drawable.ic_vm, null))
            }
            else {
                timetableType = "timetable"
                item.icon = (ResourcesCompat.getDrawable(resources, R.drawable.ic_timetable, null))
            }
            //todo change type
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    class PlaceholderFragment : Fragment() {

        override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                                  savedInstanceState: Bundle?): View? {
            val rootView = inflater!!.inflate(R.layout.fragment_stop, container, false)

            val layoutManager = LinearLayoutManager(activity)
            val departuresList: RecyclerView = rootView.findViewById(R.id.departuresList) as RecyclerView
            val dividerItemDecoration = DividerItemDecoration(departuresList.context, layoutManager.orientation)
            departuresList.addItemDecoration(dividerItemDecoration)
            val adapter = DeparturesAdapter(activity,
                    filterDepartures(arguments.getStringArrayList("departures").map { fromString(it) }))
            departuresList.adapter = adapter
            departuresList.layoutManager = layoutManager
            return rootView
        }

        fun filterDepartures(departures: List<Departure>): List<Departure> { //todo and tomorrow
            val filtered = ArrayList<Departure>()
            val lines = HashMap<String, Int>()
            val now = Calendar.getInstance()
            for (departure in departures) {
                val time = Calendar.getInstance()
                time.set(Calendar.HOUR_OF_DAY, Integer.parseInt(departure.time.split(":")[0]))
                time.set(Calendar.MINUTE, Integer.parseInt(departure.time.split(":")[1]))
                var lineExistedTimes = lines[departure.line]
                Log.i("Filter", "line: ${departure.line} existed $lineExistedTimes times")
                if (now.before(time) && lineExistedTimes ?: 0 < 3) {
                    Log.i("Filter", "less than 3 so adding")
                    lineExistedTimes = (lineExistedTimes ?: 0) + 1
                    lines[departure.line] = lineExistedTimes
                    Log.i("Filter", "and increment so now existed ${lines[departure.line]} times")
                    filtered.add(departure)
                }
            }
            return filtered
        }

        companion object {
            /**
             * The fragment argument representing the section number for this
             * fragment.
             */
            private val ARG_SECTION_NUMBER = "section_number"

            /**
             * Returns a new instance of this fragment for the given section
             * number.
             */
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

    /**
     * A [FragmentPagerAdapter] that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    inner class SectionsPagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm) {

        override fun getItem(position: Int): Fragment {
            var mode: String? = null
            when (position) {
                0 -> mode = "workdays"
                1 -> mode = "saturdays"
                2 -> mode = "sundays"
            }
            return PlaceholderFragment.newInstance(position + 1, stopId, departures?.get(mode))
        }

        override fun getCount() = 3
    }
}
