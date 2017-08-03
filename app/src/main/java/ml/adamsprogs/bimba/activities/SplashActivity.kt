package ml.adamsprogs.bimba.activities

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent
import android.database.sqlite.SQLiteCantOpenDatabaseException
import ml.adamsprogs.bimba.models.Timetable


class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            Timetable.getTimetable(this)
            startActivity(Intent(this, DashActivity::class.java))
        } catch(e: SQLiteCantOpenDatabaseException) {
            startActivity(Intent(this, NoDbActivity::class.java))
        }
        finish()
    }
}
