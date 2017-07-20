package ml.adamsprogs.bimba

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent
import ml.adamsprogs.bimba.models.Timetable


class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(Timetable(this).isDatabaseHealthy())
            startActivity(Intent(this, MainActivity::class.java))
        else
            startActivity(Intent(this, NoDbActivity::class.java))
        finish()
    }
}
