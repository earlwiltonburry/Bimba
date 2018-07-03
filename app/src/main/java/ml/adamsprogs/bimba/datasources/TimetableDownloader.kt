package ml.adamsprogs.bimba.datasources

import android.annotation.TargetApi
import android.app.*
import android.content.*
import android.support.v4.app.NotificationCompat
import java.io.*
import android.os.Build
import android.preference.PreferenceManager.getDefaultSharedPreferences
import ml.adamsprogs.bimba.*
import java.net.*
import java.util.zip.GZIPInputStream
import javax.net.ssl.*

class TimetableDownloader : IntentService("TimetableDownloader") {
    companion object {
        const val ACTION_DOWNLOADED = "ml.adamsprogs.bimba.timetableDownloaded"
        const val EXTRA_FORCE = "force"
        const val EXTRA_RESULT = "result"
        const val RESULT_NO_CONNECTIVITY = "no connectivity"
        const val RESULT_UP_TO_DATE = "up-to-date"
        const val RESULT_FINISHED = "finished"
    }

    private lateinit var notificationManager: NotificationManager
    private var sizeCompressed: Int = 0
    private var sizeUncompressed: Int = 0

    override fun onHandleIntent(intent: Intent?) {

        if (intent != null) {
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val prefs = this.getSharedPreferences("ml.adamsprogs.bimba.prefs", Context.MODE_PRIVATE)!!
            if (!NetworkStateReceiver.isNetworkAvailable(this)) {
                sendResult(RESULT_NO_CONNECTIVITY)
                return
            }

            val localETag = prefs.getString("etag", "")

            val httpCon: HttpURLConnection
            try {
                var sourceUrl = getDefaultSharedPreferences(this).getString(getString(R.string.key_timetable_source_url), getString(R.string.timetable_source_url))
                sourceUrl = sourceUrl.replace(Regex("^.*://", RegexOption.IGNORE_CASE), "")
                sourceUrl = "https://$sourceUrl"
                val url = URL(sourceUrl)
                try {
                    httpCon = url.openConnection() as HttpsURLConnection
                    httpCon.addRequestProperty("If-None-Match", localETag)
                    httpCon.connect()
                } catch (e: SSLException) {
                    sendResult(RESULT_NO_CONNECTIVITY)
                    return
                }
                if (httpCon.responseCode == HttpsURLConnection.HTTP_NOT_MODIFIED) {
                    sendResult(RESULT_UP_TO_DATE)
                    return
                }
                if (httpCon.responseCode != HttpsURLConnection.HTTP_OK) {
                    println(httpCon.responseMessage)
                    sendResult(RESULT_NO_CONNECTIVITY)
                    return
                }
            } catch (e: IOException) {
                sendResult(RESULT_NO_CONNECTIVITY)
                return
            } catch (e: EOFException) {
                sendResult(RESULT_NO_CONNECTIVITY)
                return
            } catch (e: ConnectException) {
                sendResult(RESULT_NO_CONNECTIVITY)
                return
            }

            val newETag = httpCon.getHeaderField("ETag")
            sizeCompressed = httpCon.getHeaderField("Content-Length").toInt() / 1024
            sizeUncompressed = httpCon.getHeaderField("X-Uncompressed-Content-Length").toInt() / 1024

            notify(0, R.string.timetable_downloading, R.string.timetable_downloading_progress, R.string.timetable_decompressing, sizeCompressed, sizeUncompressed)


            val gtfsDb = File(getSecondaryExternalFilesDir(), "timetable_new.db")

            val inputStream = httpCon.inputStream
            val gzipInputStream = GZIPInputStream(inputStream)
            val outputStream = FileOutputStream(gtfsDb)

            gzipInputStream.listenableCopyTo(outputStream) {
                notify((it / 1024).toInt(), R.string.timetable_downloading, R.string.timetable_downloading_progress, R.string.timetable_decompressing, sizeCompressed, sizeUncompressed)
            }

            val prefsEditor = prefs.edit()
            prefsEditor.putString("etag", newETag)
            prefsEditor.apply()

            val oldDb = File(getSecondaryExternalFilesDir(), "timetable.db")
            gtfsDb.renameTo(oldDb) // todo<p:1> delete old before downloading (may require stopping VmClient), and mutex with VmClient

            cancelNotification()

            sendResult(RESULT_FINISHED)
        }
    }

    private fun sendResult(result: String) {
        val broadcastIntent = Intent()
        broadcastIntent.action = ACTION_DOWNLOADED
        broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT)
        broadcastIntent.putExtra(EXTRA_RESULT, result)
        sendBroadcast(broadcastIntent)
    }

    private fun notify(downloadedKBytes: Int, titleId: Int, dwdMessageId: Int, dcmMessageId: Int, sizeCompressed: Int, sizeUncompressed: Int) {
        val progress = Math.min(downloadedKBytes, sizeCompressed)
        val message = if (progress < sizeCompressed)
            getString(dwdMessageId,
                    progress / 1024F, sizeCompressed / 1024F)
        else
            ""
        val title = if (progress < sizeCompressed)
            getString(titleId)
        else
            getString(dcmMessageId)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            notifyCompat(downloadedKBytes, title, message, sizeUncompressed)
        else
            notifyStandard(downloadedKBytes, title, message, sizeUncompressed)
    }

    @Suppress("DEPRECATION")
    private fun notifyCompat(progress: Int, title: String, message: String, max: Int) {
        val builder = NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(title)
                .setContentText(message)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setOngoing(true)
                .setProgress(max, progress, false)
        notificationManager.notify(42, builder.build())
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun notifyStandard(progress: Int, title: String, message: String, max: Int) {
        NotificationChannels.makeChannel(NotificationChannels.CHANNEL_UPDATES, "Updates", notificationManager)
        val builder = Notification.Builder(this, NotificationChannels.CHANNEL_UPDATES)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(title)
                .setContentText(message)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setOngoing(true)
                .setProgress(max, progress, false)
        notificationManager.notify(42, builder.build())
    }

    private fun cancelNotification() {
        notificationManager.cancel(42)
    }
}
