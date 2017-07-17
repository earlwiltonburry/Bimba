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
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup

import android.widget.TextView
import ml.adamsprogs.bimba.models.Departure
import ml.adamsprogs.bimba.models.Timetable

class StopActivity : AppCompatActivity() {

    private lateinit var stopId: String
    private var departures: HashMap<String, ArrayList<Departure>>? = null

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

        //todo hiding on scroll
        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)

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

        //todo move to view
        val fab = findViewById(R.id.fab) as FloatingActionButton
        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }

    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_stop, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId


        if (id == R.id.action_settings) {
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    class PlaceholderFragment : Fragment() {

        //todo add list
        override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                                  savedInstanceState: Bundle?): View? {
            val rootView = inflater!!.inflate(R.layout.fragment_stop, container, false)
            val textView = rootView.findViewById(R.id.section_label) as TextView
            var ttString: String = ""
            for (row in arguments.getStringArrayList("departures")) {
                ttString += row + "\n"
            }
            textView.text = getString(R.string.section_format, arguments.getInt(ARG_SECTION_NUMBER),
                    arguments.getString("stop")) + "\n===\n" + ttString
            return rootView
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

        override fun getCount(): Int {
            return 3
        }
    }
}
