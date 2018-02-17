package ml.adamsprogs.bimba.activities

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent
import ml.adamsprogs.bimba.models.Timetable
import java.io.FileNotFoundException


class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val timetable = Timetable.getTimetable(this)
            if (timetable.isEmpty())
                startActivity(Intent(this, NoDbActivity::class.java))
            else
                startActivity(Intent(this, DashActivity::class.java))
        } catch(e: FileNotFoundException) {
            startActivity(Intent(this, NoDbActivity::class.java))
        }
        finish()
    }
}
