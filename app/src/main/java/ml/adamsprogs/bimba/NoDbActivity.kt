package ml.adamsprogs.bimba

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.content.IntentFilter
import android.widget.TextView


class NoDbActivity : AppCompatActivity(), NetworkStateReceiver.OnConnectivityChangeListener, MessageReceiver.OnTimetableDownloadListener {
    val networkStateReceiver = NetworkStateReceiver()
    val timetableDownloadReceiver = MessageReceiver()
    var serviceRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nodb)
        var filter: IntentFilter
        filter = IntentFilter("ml.adamsprogs.bimba.timetableDownloaded")
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        registerReceiver(timetableDownloadReceiver, filter)
        timetableDownloadReceiver.addOnTimetableDownloadListener(this)

        if (!isNetworkAvailable(this)) {
            (findViewById(R.id.noDbCaption) as TextView).text = getString(R.string.no_db_connect)
            filter = IntentFilter("android.net.conn.CONNECTIVITY_CHANGE")
            registerReceiver(networkStateReceiver, filter)
            networkStateReceiver.addOnConnectivityChangeListener(this)
        } else
            downloadTimetable()
    }

    fun downloadTimetable() {
        (findViewById(R.id.noDbCaption) as TextView).text = getString(R.string.no_db_downloading)
        serviceRunning = true
        intent = Intent(this, TimetableDownloader::class.java)
        intent.putExtra("force", true)
        startService(intent)
    }

    override fun onConnectivityChange(connected: Boolean) {
        if (connected && !serviceRunning)
            downloadTimetable()
        /*if (!connected)
            serviceRunning = false*/
    }

    override fun onTimetableDownload() {
        timetableDownloadReceiver.removeOnTimetableDownloadListener(this)
        networkStateReceiver.removeOnConnectivityChangeListener(this)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(timetableDownloadReceiver)
        unregisterReceiver(networkStateReceiver)
    }
}
