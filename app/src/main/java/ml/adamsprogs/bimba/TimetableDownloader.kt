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
import android.util.Log
import android.app.NotificationManager


class TimetableDownloader : IntentService("TimetableDownloader") {
    lateinit var notificationManager: NotificationManager
    var size: Int = 0

    override fun onHandleIntent(intent: Intent?) {

        if (intent != null) {
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val prefs = this.getSharedPreferences("ml.adamsprogs.bimba.prefs", Context.MODE_PRIVATE)!!
            if (!isNetworkAvailable(this))
                return
            val metadataUrl = URL("https://adamsprogs.ml/w/_media/programmes/bimba/timetable.db.meta")
            var httpCon = metadataUrl.openConnection() as HttpURLConnection
            if (httpCon.responseCode != HttpURLConnection.HTTP_OK)
                throw Exception("Failed to connect")
            Log.i("Downloader", "Got metadata")
            val reader = BufferedReader(InputStreamReader(httpCon.inputStream))
            val lastModified = reader.readLine()
            val checksum = reader.readLine()
            size = Integer.parseInt(reader.readLine()) / 1024
            val currentLastModified = prefs.getString("timetableLastModified", "1979-10-12T00:00")
            if (lastModified <= currentLastModified && !intent.getBooleanExtra("force", false))
                return
            Log.i("Downloader", "timetable is newer ($lastModified > $currentLastModified)")

            notify(0)

            val xzDbUrl = URL("https://adamsprogs.ml/w/_media/programmes/bimba/timetable.db.xz")
            httpCon = xzDbUrl.openConnection() as HttpURLConnection
            if (httpCon.responseCode != HttpURLConnection.HTTP_OK)
                throw Exception("Failed to connect")
            Log.i("Downloader", "connected to db")
            val xzIn = XZInputStream(httpCon.inputStream)
            val file = File(this.filesDir, "new_timetable.db")
            if (copyInputStreamToFile(xzIn, file, checksum)) {
                Log.i("Downloader", "downloaded")
                val oldFile = File(this.filesDir, "timetable.db")
                oldFile.delete()
                file.renameTo(File("timetable.db"))
                val prefsEditor = prefs.edit()
                prefsEditor.putString("timetableLastModified", lastModified)
                prefsEditor.apply()
                val broadcastIntent = Intent()
                broadcastIntent.action = "ml.adamsprogs.bimba.timetableDownloaded"
                broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT)
                sendBroadcast(broadcastIntent)
            } else {
                Log.i("Downloader", "downloaded but is wrong")
            }

            cancelNotification()
        }
    }

    private fun notify(progress: Int) {
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
            val buf = ByteArray(1024) //todo bigger?
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
                Log.i("Downloader", "downloading $len B: $lenSum KiB")
            }
            out.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            ins.close()
            val digest = md.digest()
            for (i in 0..digest.size - 1) {
                hex += Integer.toString((digest[i] and 0xff.toByte()) + 0x100, 16).padStart(3, '0').substring(1)
            }
            Log.i("Downloader", "checksum is $checksum, and hex is $hex")
            return checksum == hex
        }
    }
}
