package ml.adamsprogs.bimba.datasources

import android.annotation.TargetApi
import android.app.*
import android.content.*
import android.support.v4.app.NotificationCompat
import java.io.*
import android.os.Build
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

            var httpCon: HttpURLConnection
            try {
                try {
                    val url = URL("https://adamsprogs.ml/gtfs")
                    httpCon = url.openConnection() as HttpsURLConnection
                    httpCon.addRequestProperty("ETag", localETag)
                    httpCon.connect()
                } catch (e:SSLException) {
                    val url = URL("http://adamsprogs.ml/gtfs")
                    httpCon = url.openConnection() as HttpURLConnection
                    httpCon.addRequestProperty("ETag", localETag)
                    httpCon.connect()
                }
                if (httpCon.responseCode == HttpsURLConnection.HTTP_NOT_MODIFIED) {
                    sendResult(RESULT_UP_TO_DATE)
                    return
                }
                if (httpCon.responseCode != HttpsURLConnection.HTTP_OK) {
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

            notify(0, R.string.timetable_downloading, R.string.timetable_uncompressing, sizeCompressed, sizeUncompressed)


            val gtfsDb = File(getSecondaryExternalFilesDir(), "timetable.db")

            val inputStream = httpCon.inputStream
            val gzipInputStream = GZIPInputStream(inputStream)
            val outputStream = FileOutputStream(gtfsDb)

            gzipInputStream.listenableCopyTo(outputStream) {
                notify((it / 1024).toInt(), R.string.timetable_downloading, R.string.timetable_uncompressing, sizeCompressed, sizeUncompressed)
            }

            val prefsEditor = prefs.edit()
            prefsEditor.putString("etag", newETag)
            prefsEditor.apply()

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

    private fun notify(progress: Int, titleId: Int, messageId: Int, sizeCompressed: Int, sizeUncompressed: Int) {
        val quotient = sizeCompressed.toFloat() / sizeUncompressed.toFloat()
        val message = getString(messageId, Math.max(progress * quotient, sizeCompressed.toFloat()),
                sizeCompressed, (progress.toFloat() / sizeUncompressed.toFloat()) * 100)
        val title = getString(titleId)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            notifyCompat(progress, title, message, sizeUncompressed)
        else
            notifyStandard(progress, title, message, sizeUncompressed)
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
