package ml.adamsprogs.bimba.activities

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.content.IntentFilter
import ml.adamsprogs.bimba.*
import kotlinx.android.synthetic.main.activity_nodb.*
import ml.adamsprogs.bimba.datasources.TimetableDownloader
import ml.adamsprogs.bimba.models.Timetable

class NoDbActivity : AppCompatActivity(), NetworkStateReceiver.OnConnectivityChangeListener, MessageReceiver.OnTimetableDownloadListener {
    private val networkStateReceiver = NetworkStateReceiver()
    private val timetableDownloadReceiver = MessageReceiver.getMessageReceiver()
    private var serviceRunning = false
    private var askedForNetwork = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nodb)
        var filter = IntentFilter(TimetableDownloader.ACTION_DOWNLOADED)
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        registerReceiver(timetableDownloadReceiver, filter)
        timetableDownloadReceiver.addOnTimetableDownloadListener(this)

        if (!NetworkStateReceiver.isNetworkAvailable(this)) {
            askedForNetwork = true
            no_db_caption.text = getString(R.string.no_db_connect)
            filter = IntentFilter("android.net.conn.CONNECTIVITY_CHANGE")
            registerReceiver(networkStateReceiver, filter)
            networkStateReceiver.addOnConnectivityChangeListener(this)
        } else
            downloadTimetable()

        skip_button.setOnClickListener {
            /*
            val editor = getSharedPreferences("ml.adamsprogs.bimba.prefs", Context.MODE_PRIVATE).edit()
            editor.putBoolean(Timetable.ONLY_ONLINE, true)
            editor.apply()*/
            startActivity(Intent(this, DashActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val timetable = Timetable.getTimetable(this, true)
            if (!timetable.isEmpty()) {
                startActivity(Intent(this, DashActivity::class.java))
                finish()
            }
        } catch (e:Exception){}
        var filter = IntentFilter(TimetableDownloader.ACTION_DOWNLOADED)
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        registerReceiver(timetableDownloadReceiver, filter)
        if (!NetworkStateReceiver.isNetworkAvailable(this)) {
            askedForNetwork = true
            no_db_caption.text = getString(R.string.no_db_connect)
            filter = IntentFilter("android.net.conn.CONNECTIVITY_CHANGE")
            registerReceiver(networkStateReceiver, filter)
            networkStateReceiver.addOnConnectivityChangeListener(this)
        } else if (!serviceRunning)
            downloadTimetable()
    }

    private fun downloadTimetable() {
        no_db_caption.text = getString(R.string.no_db_downloading)
        serviceRunning = true
        intent = Intent(this, TimetableDownloader::class.java)
        intent.putExtra(TimetableDownloader.EXTRA_FORCE, true)
        startService(intent)
    }

    override fun onConnectivityChange(connected: Boolean) {
        if (connected && !serviceRunning)
            downloadTimetable()
        /*if (!connected)
            serviceRunning = false*/
    }

    override fun onTimetableDownload(result: String?) {
        when (result) {
            TimetableDownloader.RESULT_FINISHED -> {
                timetableDownloadReceiver.removeOnTimetableDownloadListener(this)
                networkStateReceiver.removeOnConnectivityChangeListener(this)
                startActivity(Intent(this, DashActivity::class.java))
                finish()
            }
            else -> no_db_caption.text = getString(R.string.error_try_later)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(timetableDownloadReceiver)
        if (askedForNetwork)
            unregisterReceiver(networkStateReceiver)
    }
}
