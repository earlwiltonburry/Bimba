package ml.adamsprogs.bimba

import android.net.ConnectivityManager
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context

class NetworkStateReceiver : BroadcastReceiver() {

    private val onConnectivityChangeListeners = HashSet<OnConnectivityChangeListener>()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.extras != null) {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val ni = connectivityManager.activeNetworkInfo

            if (ni != null && ni.isConnectedOrConnecting) {
                for (listener in onConnectivityChangeListeners)
                    listener.onConnectivityChange(true)
            } else if (intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, java.lang.Boolean.FALSE)) {
                for (listener in onConnectivityChangeListeners)
                    listener.onConnectivityChange(false)
            }
        }
    }

    fun addOnConnectivityChangeListener(listener: OnConnectivityChangeListener) {
        onConnectivityChangeListeners.add(listener)
    }

    fun removeOnConnectivityChangeListener(listener: OnConnectivityChangeListener) {
        onConnectivityChangeListeners.remove(listener)
    }

    interface OnConnectivityChangeListener {
        fun onConnectivityChange(connected: Boolean)
    }

    companion object {
        lateinit var manager: ConnectivityManager

        fun init(context: Context) {
            manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        }

        fun isNetworkAvailable(): Boolean {
            val activeNetworkInfo = manager.activeNetworkInfo
            return activeNetworkInfo != null && activeNetworkInfo.isConnected
        }
    }
}