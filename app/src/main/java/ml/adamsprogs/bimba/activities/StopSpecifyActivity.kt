package ml.adamsprogs.bimba.activities

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.activity_stop_specify.*
import ml.adamsprogs.bimba.R
import android.content.Context
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import ml.adamsprogs.bimba.ProviderProxy

class StopSpecifyActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STOP_NAME = "stopName"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stop_specify)

        val name = intent.getStringExtra(EXTRA_STOP_NAME)
        val providerProxy = ProviderProxy(this)
        providerProxy.getSheds(name) {
            val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
            val departuresList: androidx.recyclerview.widget.RecyclerView = list_view

            departuresList.adapter = ShedAdapter(this, it, name)
            departuresList.layoutManager = layoutManager
        }
        /*val timetable = Timetable.getTimetable(this)
        val headlines = timetable.getHeadlinesForStop(name)*/


        setSupportActionBar(toolbar)
        supportActionBar?.title = name
    }

    class ShedAdapter(val context: Context, private val values: Map<String, Set<String>>, private val stopName: String) :
            androidx.recyclerview.widget.RecyclerView.Adapter<ShedAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val context = parent.context
            val inflater = LayoutInflater.from(context)

            val rowView = inflater.inflate(R.layout.row_shed, parent, false)
            return ViewHolder(rowView)
        }

        override fun getItemCount(): Int = values.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.root.setOnClickListener {
                val code = values.keys.sorted()[position]
                val intent = Intent(context, StopActivity::class.java)
                intent.putExtra(StopActivity.SOURCE_TYPE, StopActivity.SOURCE_TYPE_STOP)
                intent.putExtra(StopActivity.EXTRA_STOP_CODE, code)
                intent.putExtra(StopActivity.EXTRA_STOP_NAME, stopName)
                context.startActivity(intent)
            }
            holder.stopCode.text = values.keys.sorted()[position]
            holder.stopHeadlines.text = values.entries.sortedBy { it.key }[position].value
                    .sortedBy { it.split(" → ")[0].toInt() }
                    .joinToString()
        }

        inner class ViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
            val root = itemView.findViewById<View>(R.id.shed_row)!!
            val stopCode: TextView = itemView.findViewById(R.id.stop_code)
            val stopHeadlines: TextView = itemView.findViewById(R.id.stop_headlines)
        }
    }
}
