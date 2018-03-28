package ml.adamsprogs.bimba.activities

import android.support.design.widget.TabLayout
import android.support.v7.app.AppCompatActivity

import android.support.v4.app.*
import android.os.Bundle
import android.view.*

import kotlinx.android.synthetic.main.activity_line_specify.*
import kotlinx.android.synthetic.main.fragment_line_specify.view.*
import ml.adamsprogs.bimba.R
import ml.adamsprogs.bimba.models.Timetable
import ml.adamsprogs.bimba.models.gtfs.*

class LineSpecifyActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_LINE_ID = "line_id"
    }

    private var sectionsPagerAdapter: SectionsPagerAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_line_specify)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val line = intent.getStringExtra(EXTRA_LINE_ID)

        val timetable = Timetable.getTimetable()
        val graphs = timetable.getTripGraphs(AgencyAndId(line))

        sectionsPagerAdapter = SectionsPagerAdapter(supportFragmentManager, graphs)

        container.adapter = sectionsPagerAdapter

        for (i in 0 until tabs.tabCount) {
            tabs.getTabAt(i)?.text = graphs[i].second
        }

        container.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(tabs))
        tabs.addOnTabSelectedListener(TabLayout.ViewPagerOnTabSelectedListener(container))
    }

    inner class SectionsPagerAdapter(fm: FragmentManager, private val graphs: Array<Pair<HashMap<Int, HashSet<Int>>, String>>) : FragmentPagerAdapter(fm) {

        override fun getItem(position: Int): Fragment {
            return PlaceholderFragment.newInstance(position + 1, graphs[position])
        }

        override fun getCount() = 2
    }

    class PlaceholderFragment : Fragment() {

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                                  savedInstanceState: Bundle?): View? {
            val rootView = inflater.inflate(R.layout.fragment_line_specify, container, false)
            rootView.section_label.text = arguments?.getString("graph") //todo draw it + clickable
            return rootView
        }

        companion object {
            private const val ARG_SECTION_NUMBER = "section_number"

            fun newInstance(sectionNumber: Int, graph: Pair<HashMap<Int, HashSet<Int>>, String>): PlaceholderFragment {
                val fragment = PlaceholderFragment()
                val args = Bundle()
                args.putInt(ARG_SECTION_NUMBER, sectionNumber)
                args.putString("graph", graph.first.map { "${it.key}: ${it.value.joinToString(", ")}" }.joinToString("\n"))
                fragment.arguments = args
                return fragment
            }
        }
    }
}
