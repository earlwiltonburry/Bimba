package ml.adamsprogs.bimba.activities

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import ml.adamsprogs.bimba.R
import ml.adamsprogs.bimba.RouteFinder
import ml.adamsprogs.bimba.calendarFromIso
import java.util.*

class PlanningActivity : AppCompatActivity() {

    private lateinit var start: EditText
    private lateinit var end: EditText
    private lateinit var route: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_planning)
        start = findViewById(R.id.stop)
        end = findViewById(R.id.stop2)
        route = findViewById(R.id.route)

    }

    fun search(view: View) {
        val x = Calendar.getInstance()
        System.out.println(x.get(Calendar.DAY_OF_WEEK))
        route.text = RouteFinder.findRoute(start.text.toString(), end.text.toString(), Calendar.getInstance(), this)
    }
}
