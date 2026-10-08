package com.phonelink.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Foreground service: keeps sockets, Bluetooth and calls alive when the screen is off or the app
 * is in the background. There is no timer anywhere, so a call can last as long as you like.
 */
class LinkService : Service() {

    companion object {
        @Volatile var instance: LinkService? = null
        private const val CH_SERVICE = "phonelink_service"
        private const val CH_EVENTS = "phonelink_events"
        private const val ID_SERVICE = 1
        private const val ACTION_STOP = "com.phonelink.app.STOP"
    }

    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null
    private var eventId = 100

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        Hub.attach(applicationContext)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_SERVICE, "PhoneLink running", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_EVENTS, "Messages and calls", NotificationManager.IMPORTANCE_HIGH))
        try {
            ServiceCompat.startForeground(
                this, ID_SERVICE, serviceNotification("Ready"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } catch (e: Exception) {
            // on some versions this fails if started while the app is hidden; the app keeps working while open
        }
        Hub.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Hub.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        callMode(false, false, false)
        instance = null
        super.onDestroy()
    }

    private fun serviceNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, LinkService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CH_SERVICE)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("PhoneLink")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(0, "Stop", stop)
            .build()
    }

    /** Heads-up notification for messages, file offers and incoming calls while the app is hidden. */
    fun notifyEvent(title: String, text: String) {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, CH_EVENTS)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try { getSystemService(NotificationManager::class.java).notify(eventId++, n) } catch (_: Exception) {}
    }

    /**
     * Call mode: microphone/camera foreground types, call audio routing, and locks that keep the
     * CPU and Wi-Fi awake so the call never drops when the screen turns off.
     */
    @Suppress("DEPRECATION")
    fun callMode(on: Boolean, video: Boolean, speaker: Boolean) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (on) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (video) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            try {
                ServiceCompat.startForeground(this, ID_SERVICE, serviceNotification("Call in progress"), type)
            } catch (e: Exception) {
                Hub.toast("Allow microphone/camera permission for calls")
            }
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = speaker
            if (wake == null) {
                wake = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PhoneLink:call").apply { acquire() }
            }
            if (wifi == null) {
                wifi = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                    .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "PhoneLink:call").apply { acquire() }
            }
        } else {
            am.mode = AudioManager.MODE_NORMAL
            am.isSpeakerphoneOn = false
            wake?.let { if (it.isHeld) it.release() }
            wake = null
            wifi?.let { if (it.isHeld) it.release() }
            wifi = null
            try {
                ServiceCompat.startForeground(
                    this, ID_SERVICE, serviceNotification("Ready"),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } catch (_: Exception) {}
        }
    }

    @Suppress("DEPRECATION")
    fun setSpeaker(on: Boolean) {
        (getSystemService(Context.AUDIO_SERVICE) as AudioManager).isSpeakerphoneOn = on
    }
}
