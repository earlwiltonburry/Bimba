package ml.adamsprogs.bimba

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.annotation.RequiresApi


class NotificationChannels {
    companion object {
        const val CHANNEL_UPDATES = "updates"

        @RequiresApi(Build.VERSION_CODES.O)
        fun makeChannel(id: String, name: String, manager: NotificationManager) {
            try {
                manager.getNotificationChannel(id)
            } catch (e: RuntimeException) {
                val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_MIN)
                channel.enableLights(false)
                channel.enableVibration(false)
                channel.setShowBadge(false)
                manager.createNotificationChannel(channel)
            }
        }
    }
}