package ml.adamsprogs.bimba

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent


class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //todo if no db
        //startActivity(Intent(this, NoDbActivity::class.java))
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
