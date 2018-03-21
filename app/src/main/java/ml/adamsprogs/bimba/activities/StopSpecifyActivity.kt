package ml.adamsprogs.bimba.activities

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.activity_stop_specify.*
import ml.adamsprogs.bimba.R
import ml.adamsprogs.bimba.models.gtfs.AgencyAndId
import ml.adamsprogs.bimba.models.Timetable
import android.content.Context
import android.widget.TextView
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater

//todo in night dark on dark
class StopSpecifyActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STOP_IDS = "stopIds"
        const val EXTRA_STOP_NAME = "stopName"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stop_specify)

        val ids = intent.getStringExtra(EXTRA_STOP_IDS).split(",").map { AgencyAndId(it) }.toSet()
        val name = intent.getStringExtra(EXTRA_STOP_NAME)
        val timetable = Timetable.getTimetable()
        val headlines = timetable.getHeadlinesForStop(ids)

        val layoutManager = LinearLayoutManager(this)
        val departuresList: RecyclerView = list_view

        departuresList.adapter = ShedAdapter(this, headlines)
        departuresList.layoutManager = layoutManager

        setSupportActionBar(toolbar)
        supportActionBar?.title = name
    }

    class ShedAdapter(val context: Context, private val values: Map<AgencyAndId, Pair<String, Set<String>>>) :
            RecyclerView.Adapter<ShedAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val context = parent.context
            val inflater = LayoutInflater.from(context)

            val rowView = inflater.inflate(R.layout.row_shed, parent, false)
            return ViewHolder(rowView)
        }

        override fun getItemCount(): Int = values.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.root.setOnClickListener {
                val id = values.entries.sortedBy { it.value.first }[position].key
                val intent = Intent(context, StopActivity::class.java)
                intent.putExtra(StopActivity.SOURCE_TYPE, StopActivity.SOURCE_TYPE_STOP)
                intent.putExtra(StopActivity.EXTRA_STOP_ID, id)
                context.startActivity(intent)
            }
            holder.stopCode.text = values.values.sortedBy { it.first }[position].first
            holder.stopHeadlines.text = values.values.sortedBy { it.first }[position].second
                    .sortedBy { it } // fixme<p:1> natural sort
                    .joinToString()
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val root = itemView.findViewById<View>(R.id.shed_row)!!
            val stopCode: TextView = itemView.findViewById(R.id.stop_code)
            val stopHeadlines: TextView = itemView.findViewById(R.id.stop_headlines)
        }
    }
}
