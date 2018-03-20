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
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.univocity.parsers.csv.CsvParser
import com.univocity.parsers.csv.CsvParserSettings
import ir.mahdi.mzip.zip.ZipArchive
import ml.adamsprogs.bimba.NetworkStateReceiver
import ml.adamsprogs.bimba.NotificationChannels
import ml.adamsprogs.bimba.R
import ml.adamsprogs.bimba.getSecondaryExternalFilesDir
import ml.adamsprogs.bimba.models.Timetable
import java.net.ConnectException
import java.net.URL
import java.util.Calendar
import javax.net.ssl.HttpsURLConnection
import kotlin.collections.*

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

    override fun onHandleIntent(intent: Intent?) {

        if (intent != null) {
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val prefs = this.getSharedPreferences("ml.adamsprogs.bimba.prefs", Context.MODE_PRIVATE)!!
            if (!NetworkStateReceiver.isNetworkAvailable(this)) {
                sendResult(RESULT_NO_CONNECTIVITY)
                return
            }

            sendResult(RESULT_UP_TO_DATE)
            return

            val httpCon: HttpsURLConnection
            try {
                val url = URL("https://adamsprogs.ml/gtfs")
                httpCon = url.openConnection() as HttpsURLConnection
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

            val lastModified = httpCon.getHeaderField("Content-Disposition").split("=")[1].trim('\"').split("_")[0]
            size = httpCon.getHeaderField("Content-Length").toInt() / 1024

            val force = intent.getBooleanExtra(EXTRA_FORCE, false)
            val currentLastModified = prefs.getString("timetableLastModified", "19791012")
            if (lastModified <= currentLastModified && !force) {
                sendResult(RESULT_UP_TO_DATE)
                return
            }

            notify(0, getString(R.string.timetable_downloading), size)


            val gtfs = File(getSecondaryExternalFilesDir(), "timetable.zip")
            copyInputStreamToFile(httpCon.inputStream, gtfs)
            val prefsEditor = prefs.edit()
            prefsEditor.putString("timetableLastModified", lastModified)
            prefsEditor.apply()
            sendResult(RESULT_DOWNLOADED)

            notify(getString(R.string.timetable_converting))

            val target = File(getSecondaryExternalFilesDir(), "gtfs_files")
            target.deleteRecursively()
            target.mkdir()
            ZipArchive.unzip(gtfs.path, target.path, "")

            val string = getString(R.string.timetable_converting)
            notify(0, string, 1_030_000)

            println(Calendar.getInstance().timeInMillis)

            gtfs.delete()

            createIndices()
            Timetable.getTimetable(this).refresh()

            println(Calendar.getInstance().timeInMillis)

            cancelNotification()

            sendResult(RESULT_FINISHED)
        }
    }

    private fun createIndices() {
        val settings = CsvParserSettings()
        settings.format.setLineSeparator("\r\n")
        settings.format.quote = '"'
        settings.isHeaderExtractionEnabled = true

        val parser = CsvParser(settings)

        val stopIndexFile = File(getSecondaryExternalFilesDir(), "gtfs_files/stop_index.yml")
        val tripIndexFile = File(getSecondaryExternalFilesDir(), "gtfs_files/trip_index.yml")

        val stopsIndex = HashMap<String, List<Long>>()
        val tripsIndex = HashMap<String, List<Long>>()

        parser.parseAll(File(getSecondaryExternalFilesDir(), "gtfs_files/trips.txt")).forEach {
            tripsIndex[it[2]] = ArrayList()
        }

        parser.parseAll(File(getSecondaryExternalFilesDir(), "gtfs_files/stops.txt")).forEach {
            stopsIndex[it[0]] = ArrayList()
        }

        val string = getString(R.string.timetable_converting)

        parser.beginParsing(File(getSecondaryExternalFilesDir(), "gtfs_files/stop_times.txt"))
        var line: Array<String>? = null
        while ({ line = parser.parseNext(); line }() != null) {
            val lineNumber = parser.context.currentLine()
            (tripsIndex[line!![0]] as ArrayList).add(lineNumber)
            (stopsIndex[line!![3]] as ArrayList).add(lineNumber)
            if (lineNumber % 10_300 == 0L)
                notify(lineNumber.toInt(), string, 1_030_000)
        }

        println(Calendar.getInstance().timeInMillis)
        stopsIndex.filter { it.value.contains(0) }.forEach { println("${it.key}: ${it.value.joinToString()}") }
        println(Calendar.getInstance().timeInMillis)

        serialiseIndex(stopsIndex, stopIndexFile)
        serialiseIndex(tripsIndex, tripIndexFile)
    }

    private fun serialiseIndex(index: HashMap<String, List<Long>>, file: File) {
        val stopsRootObject = JsonObject()
        index.forEach {
            val stop = JsonArray()
            it.value.forEach {
                stop.add(it)
            }
            stopsRootObject.add(it.key, stop)
        }

        val writer = BufferedWriter(file.writer())
        writer.write(Gson().toJson(stopsRootObject))
        writer.close()
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
