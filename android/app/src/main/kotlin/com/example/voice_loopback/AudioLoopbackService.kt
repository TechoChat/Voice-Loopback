package com.example.voice_loopback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AudioLoopbackService : Service() {

    // 1. Load the C++ library
    companion object {
        init {
            System.loadLibrary("native-lib")
        }
    }

    // 2. Define the Native methods
    private external fun startOboe(): Boolean
    private external fun stopOboe()

    private var isRunning = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "START") {
            if (!isRunning) {
                startForegroundService()
                // Call C++ Engine
                if (startOboe()) {
                    isRunning = true
                }
            }
        } else if (intent?.action == "STOP") {
            stopOboe() // Stop C++ Engine
            stopForeground(true)
            stopSelf()
            isRunning = false
        }
        return START_NOT_STICKY
    }

    private fun startForegroundService() {
        val channelId = "AudioLoopbackChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Voice Loopback Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Low Latency Loopback Active")
            .setContentText("Oboe Engine Running")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()

        startForeground(1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}