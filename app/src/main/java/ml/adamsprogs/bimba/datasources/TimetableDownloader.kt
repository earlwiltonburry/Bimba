package ml.adamsprogs.bimba.datasources

import android.annotation.TargetApi
import android.app.IntentService
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.support.v4.app.NotificationCompat
import java.net.HttpURLConnection
import java.net.URL
import java.io.*
import java.security.MessageDigest
import android.app.NotificationManager
import android.os.Build
import ir.mahdi.mzip.zip.ZipArchive
import ml.adamsprogs.bimba.NetworkStateReceiver
import ml.adamsprogs.bimba.NotificationChannels
import ml.adamsprogs.bimba.R
import ml.adamsprogs.bimba.models.Timetable
import java.util.*

class TimetableDownloader : IntentService("TimetableDownloader") {
    companion object {
        const val ACTION_DOWNLOADED = "ml.adamsprogs.bimba.timetableDownloaded"
        const val EXTRA_FORCE = "force"
        const val EXTRA_RESULT = "result"
        const val RESULT_NO_CONNECTIVITY = "no connectivity"
        const val RESULT_UP_TO_DATE = "up-to-date"
        const val RESULT_DOWNLOADED = "downloaded"
        const val RESULT_FINISHED = "finished"
    }

    private lateinit var notificationManager: NotificationManager
    private var size: Int = 0

    override fun onHandleIntent(intent: Intent?) { //fixme throws something

        if (intent != null) {
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val prefs = this.getSharedPreferences("ml.adamsprogs.bimba.prefs", Context.MODE_PRIVATE)!!
            if (!NetworkStateReceiver.isNetworkAvailable(this)) {
                sendResult(RESULT_NO_CONNECTIVITY)
                return
            }
            val url = URL("http://ztm.poznan.pl/pl/dla-deweloperow/getGTFSFile")
            val httpCon = url.openConnection() as HttpURLConnection
            if (httpCon.responseCode != HttpURLConnection.HTTP_OK) { //IOEXCEPTION or EOFEXCEPTION or ConnectException
                sendResult(RESULT_NO_CONNECTIVITY)
                return
            }
            val lastModified = httpCon.getHeaderField("Content-Disposition").split("=")[1].trim('\"').split("_")[0]
            size = httpCon.getHeaderField("Content-Length").toInt() / 1024

            val force = intent.getBooleanExtra(EXTRA_FORCE, false)
            val currentLastModified = prefs.getString("timetableLastModified", "19791012")
            if (lastModified <= currentLastModified && lastModified <= today() && !force) {
                sendResult(RESULT_UP_TO_DATE)
                return
            }

            notifyDownloading(0)

            val gtfs = File(this.filesDir, "timetable.zip")
            //val db = File(this.filesDir, "timetable.db")
            copyInputStreamToFile(httpCon.inputStream, gtfs)
            val prefsEditor = prefs.edit()
            prefsEditor.putString("timetableLastModified", lastModified)
            prefsEditor.apply()
            sendResult(RESULT_DOWNLOADED)

            //notifyConverting() //fixme

            //db.delete()
            val target = File(this.filesDir, "gtfs_files")
            target.deleteRecursively()
            println("deleted")
            target.mkdir()
            ZipArchive.unzip(gtfs.path, target.path, "")
            println("unzipped")
            gtfs.delete()
            Timetable.getTimetable().refresh()
            println("refreshed")

            cancelNotification()

            sendResult(RESULT_FINISHED)
        }
    }

    private fun today(): String {
        val cal = Calendar.getInstance()
        val d = cal[Calendar.DAY_OF_MONTH]
        val m = cal[Calendar.MONTH] + 1
        val y = cal[Calendar.YEAR]

        return "%d%02d%02d".format(y, m, d)
    }

    private fun sendResult(result: String) {
        val broadcastIntent = Intent()
        broadcastIntent.action = ACTION_DOWNLOADED
        broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT)
        broadcastIntent.putExtra(EXTRA_RESULT, result)
        sendBroadcast(broadcastIntent)
    }

    private fun notifyDownloading(progress: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            notifyCompat(progress)
        else
            notifyStandard(progress)
    }

    @Suppress("DEPRECATION")
    private fun notifyCompat(progress: Int) {
        val builder = NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(getString(R.string.timetable_downloading))
                .setContentText("$progress KiB/$size KiB")
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setOngoing(true)
                .setProgress(size, progress, false)
        notificationManager.notify(42, builder.build())
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun notifyStandard(progress: Int) {
        NotificationChannels.makeChannel(NotificationChannels.CHANNEL_UPDATES, "Updates", notificationManager)
        val builder = Notification.Builder(this, NotificationChannels.CHANNEL_UPDATES)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(getString(R.string.timetable_downloading))
                .setContentText("$progress KiB/$size KiB")
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setOngoing(true)
                .setProgress(size, progress, false)
        notificationManager.notify(42, builder.build())
    }

    private fun notifyConverting() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            notifyCompatConverting()
        else
            notifyStandardConverting()

    }

    @Suppress("DEPRECATION")
    private fun notifyCompatConverting() {
        val builder = NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(getString(R.string.timetable_converting))
                .setContentText("")
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setOngoing(true)
                .setProgress(0, 0, true)
        notificationManager.notify(42, builder.build())
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun notifyStandardConverting() {
        NotificationChannels.makeChannel(NotificationChannels.CHANNEL_UPDATES, "Updates", notificationManager)
        val builder = Notification.Builder(this, NotificationChannels.CHANNEL_UPDATES)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(getString(R.string.timetable_converting))
                .setContentText("")
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setOngoing(true)
                .setProgress(0, 0, true)
        notificationManager.notify(42, builder.build())
    }

    private fun cancelNotification() {
        notificationManager.cancel(42)
    }

    private fun copyInputStreamToFile(ins: InputStream, file: File) {
        val md = MessageDigest.getInstance("SHA-512")
        try {
            val out = FileOutputStream(file)
            val buf = ByteArray(5 * 1024)
            var lenSum = 0.0f
            var len = 42
            while (len > 0) {
                len = ins.read(buf)
                if (len <= 0)
                    break
                md.update(buf, 0, len)
                out.write(buf, 0, len)
                lenSum += len.toFloat() / 1024.0f
                notifyDownloading(lenSum.toInt())
            }
            out.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            ins.close()
        }
    }
}
