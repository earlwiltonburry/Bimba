package ml.adamsprogs.bimba.datasources

import android.annotation.TargetApi
import android.app.IntentService
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.support.v4.app.NotificationCompat
import java.io.*
import android.app.NotificationManager
import android.os.Build
import com.univocity.parsers.csv.CsvWriter
import com.univocity.parsers.csv.CsvWriterSettings
import ir.mahdi.mzip.zip.ZipArchive
import ml.adamsprogs.bimba.NetworkStateReceiver
import ml.adamsprogs.bimba.NotificationChannels
import ml.adamsprogs.bimba.R
import ml.adamsprogs.bimba.models.Timetable
import org.supercsv.io.CsvListReader
import org.supercsv.prefs.CsvPreference
import java.net.*
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

            notify(0, getString(R.string.timetable_downloading), size)

            val gtfs = File(this.filesDir, "timetable.zip")
            copyInputStreamToFile(httpCon.inputStream, gtfs)
            val prefsEditor = prefs.edit()
            prefsEditor.putString("timetableLastModified", lastModified)
            prefsEditor.apply()
            sendResult(RESULT_DOWNLOADED)

            notify(getString(R.string.timetable_converting))

            val target = File(this.filesDir, "gtfs_files")
            target.deleteRecursively()
            target.mkdir()
            ZipArchive.unzip(gtfs.path, target.path, "")

            val stopTimesFile = File(filesDir, "gtfs_files/stop_times.txt")

            val reader = CsvListReader(FileReader(stopTimesFile), CsvPreference.STANDARD_PREFERENCE)
            val header = reader.getHeader(true)

            val headers = HashMap<String, Boolean>()
            val mapReader = CsvListReader(FileReader(stopTimesFile), CsvPreference.STANDARD_PREFERENCE)

            val string = getString(R.string.timetable_converting)

            notify(0, string, 1_030_000)

            println(Calendar.getInstance().timeInMillis)

            var row: List<Any>? = null
            while ({ row = mapReader.read(); row }() != null) {
                val stopId = row!![3] as String
                val outFile = File(filesDir, "gtfs_files/stop_times_$stopId.txt")
                val writer = CsvWriter(CsvWriterSettings())
                if (headers[stopId] == null) {
                    val h = writer.writeHeadersToString(header.asList())
                    outFile.appendText("$h\r\n")
                    headers[stopId] = true
                }
                if (mapReader.rowNumber % 10_300 == 0)
                    notify(mapReader.rowNumber, string, 1_030_000)
                val line = writer.writeRowToString(row!!)
                outFile.appendText("$line\r\n")
            }
            mapReader.close()

            gtfs.delete()

            stopTimesFile.delete()
            Timetable.getTimetable(this).refresh()

            println(Calendar.getInstance().timeInMillis)

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

    private fun notify(progress: Int, message: String, max: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            notifyCompat(progress, message, max)
        else
            notifyStandard(progress, message, max)
    }

    @Suppress("DEPRECATION")
    private fun notifyCompat(progress: Int, message: String, max: Int) {
        val builder = NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(message)
                .setContentText("${(progress.toDouble() / max.toDouble() * 100).toInt()} %")
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setOngoing(true)
                .setProgress(max, progress, false)
        notificationManager.notify(42, builder.build())
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun notifyStandard(progress: Int, message: String, max: Int) {
        NotificationChannels.makeChannel(NotificationChannels.CHANNEL_UPDATES, "Updates", notificationManager)
        val builder = Notification.Builder(this, NotificationChannels.CHANNEL_UPDATES)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(message)
                .setContentText("${(progress.toDouble() / max.toDouble() * 100).toInt()} %")
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setOngoing(true)
                .setProgress(max, progress, false)
        notificationManager.notify(42, builder.build())
    }

    private fun notify(message: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            notifyCompat(message)
        else
            notifyStandard(message)

    }

    @Suppress("DEPRECATION")
    private fun notifyCompat(message: String) {
        val builder = NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(message)
                .setContentText("")
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setOngoing(true)
                .setProgress(0, 0, true)
        notificationManager.notify(42, builder.build())
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun notifyStandard(message: String) {
        NotificationChannels.makeChannel(NotificationChannels.CHANNEL_UPDATES, "Updates", notificationManager)
        val builder = Notification.Builder(this, NotificationChannels.CHANNEL_UPDATES)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(message)
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
        try {
            val out = FileOutputStream(file)
            val buf = ByteArray(5 * 1024)
            var lenSum = 0.0f
            var len = 42
            while (len > 0) {
                len = ins.read(buf)
                if (len <= 0)
                    break
                out.write(buf, 0, len)
                lenSum += len.toFloat() / 1024.0f
                notify(lenSum.toInt(), getString(R.string.timetable_downloading), size)
            }
            out.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            ins.close()
        }
    }
}
