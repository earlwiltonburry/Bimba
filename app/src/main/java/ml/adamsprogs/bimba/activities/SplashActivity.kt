package ml.adamsprogs.bimba.activities

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent
import ml.adamsprogs.bimba.models.Timetable


class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val timetable = Timetable(this)
        if(timetable.isDatabaseHealthy())
            startActivity(Intent(this, DashActivity::class.java))
        else
            startActivity(Intent(this, NoDbActivity::class.java))
        timetable.close()
        finish()
    }
}
