package ml.adamsprogs.bimba.activities

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.support.v7.app.AppCompatDelegate
import ml.adamsprogs.bimba.models.Timetable
import java.io.FileNotFoundException


class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO)
        startActivity(Intent(this, DashActivity::class.java))
        finish()
    }
}
