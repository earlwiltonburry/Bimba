package ml.adamsprogs.bimba.activities

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import ml.adamsprogs.bimba.R
import ml.adamsprogs.bimba.gtfs.AgencyAndId
import ml.adamsprogs.bimba.models.Timetable

class StopSpecifyActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STOP_IDS = "stopIds"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stop_specify)

        val ids = intent.getStringExtra(EXTRA_STOP_IDS).split(",").map {AgencyAndId(it)}.toSet()
        val headlines = Timetable.getTimetable().getHeadlinesForStop(ids)

        headlines.forEach {
            println(it)
        }

        //todo select shed

        val shed = AgencyAndId("1")

        /*intent = Intent(this, StopActivity::class.java)
        intent.putExtra(StopActivity.SOURCE_TYPE, StopActivity.SOURCE_TYPE_STOP)
        intent.putExtra(StopActivity.EXTRA_STOP_ID, shed)
        startActivity(intent)*/
    }
}
