package ml.adamsprogs.bimba.activities

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.EditText
import ml.adamsprogs.bimba.R
import ml.adamsprogs.bimba.RouteFinder
import ml.adamsprogs.bimba.calendarFromIso
import java.util.*

class PlanningActivity : AppCompatActivity() {

    private lateinit var et : EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_planning)
        et = findViewById(R.id.stop)
    }

    fun search(view: View) {
        RouteFinder.findRoute(et.text.toString(), "", Calendar.getInstance(), this)
    }
}
