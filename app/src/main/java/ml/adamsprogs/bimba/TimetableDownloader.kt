package ml.adamsprogs.bimba

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.support.v4.app.NotificationCompat
import java.net.HttpURLConnection
import java.net.URL
import org.tukaani.xz.XZInputStream
import java.io.*
import java.security.MessageDigest
import kotlin.experimental.and
import android.app.NotificationManager
import ml.adamsprogs.bimba.models.Timetable


class TimetableDownloader : IntentService("TimetableDownloader") {
    companion object {
        val ACTION_DOWNLOADED = "ml.adamsprogs.bimba.timetableDownloaded"
        val EXTRA_FORCE = "force"
        val EXTRA_RESULT = "result"
        val RESULT_NO_CONNECTIVITY = "no connectivity"
        val RESULT_VERSION_MISMATCH = "version mismatch"
        val RESULT_UP_TO_DATE = "up-to-date"
        val RESULT_DOWNLOADED = "downloaded"
        val RESULT_VALIDITY_FAILED = "validity failed"
    }
    private lateinit var notificationManager: NotificationManager
    private var size: Int = 0

    override fun onHandleIntent(intent: Intent?) {

        if (intent != null) {
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val prefs = this.getSharedPreferences("ml.adamsprogs.bimba.prefs", Context.MODE_PRIVATE)!!
            if (!NetworkStateReceiver.isNetworkAvailable(this)) {
                sendResult(RESULT_NO_CONNECTIVITY)
                return
            }
            val metadataUrl = URL("https://adamsprogs.ml/w/_media/programmes/bimba/timetable.db.meta")
            var httpCon = metadataUrl.openConnection() as HttpURLConnection
            if (httpCon.responseCode != HttpURLConnection.HTTP_OK){
                sendResult(RESULT_NO_CONNECTIVITY)
                return
            }
            val reader = BufferedReader(InputStreamReader(httpCon.inputStream))
            val lastModified = reader.readLine()
            val checksum = reader.readLine()
            size = Integer.parseInt(reader.readLine()) / 1024
            val dbVersion = reader.readLine()
            if (Integer.parseInt(dbVersion.split(".")[0]) > Timetable.version) {
                sendResult(RESULT_VERSION_MISMATCH)
                return
            }
            val dbFilename = reader.readLine()
            val currentLastModified = prefs.getString("timetableLastModified", "19791012")
            if (lastModified <= currentLastModified && !intent.getBooleanExtra(EXTRA_FORCE, false)) {
                sendResult(RESULT_UP_TO_DATE)
                return
            }

            notify(0)

            val xzDbUrl = URL("https://adamsprogs.ml/w/_media/programmes/bimba/$dbFilename")
            httpCon = xzDbUrl.openConnection() as HttpURLConnection
            if (httpCon.responseCode != HttpURLConnection.HTTP_OK){
                sendResult(RESULT_NO_CONNECTIVITY)
                return
            }
            val xzIn = XZInputStream(httpCon.inputStream)
            val file = File(this.filesDir, "new_timetable.db")
            if (copyInputStreamToFile(xzIn, file, checksum)) {
                val oldFile = File(this.filesDir, "timetable.db")
                oldFile.delete()
                file.renameTo(oldFile)
                val prefsEditor = prefs.edit()
                prefsEditor.putString("timetableLastModified", lastModified)
                prefsEditor.apply()
                sendResult(RESULT_DOWNLOADED)
            } else {
                sendResult(RESULT_VALIDITY_FAILED)
            }

            cancelNotification()
        }
    }

    private fun sendResult(result: String) {
        val broadcastIntent = Intent()
        broadcastIntent.action = ACTION_DOWNLOADED
        broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT)
        broadcastIntent.putExtra(EXTRA_RESULT, result)
        sendBroadcast(broadcastIntent)
    }

    private fun notify(progress: Int) {
        //todo create channel
        val builder = NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(getString(R.string.timetable_downloading))
                .setContentText("$progress KiB/$size KiB")
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setOngoing(true)
                .setProgress(size, progress, false)

        notificationManager.notify(42, builder.build())
    }

    private fun cancelNotification() {
        notificationManager.cancel(42)
    }

    private fun copyInputStreamToFile(ins: InputStream, file: File, checksum: String): Boolean {
        val md = MessageDigest.getInstance("SHA-512")
        var hex = ""
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
                notify(lenSum.toInt())
            }
            out.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            ins.close()
            val digest = md.digest()
            for (i in 0 until digest.size) {
                hex += Integer.toString((digest[i] and 0xff.toByte()) + 0x100, 16).padStart(3, '0').substring(1)
            }
            return checksum == hex
        }
    }
}
