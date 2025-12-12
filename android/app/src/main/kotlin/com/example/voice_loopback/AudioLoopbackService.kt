package com.example.voice_loopback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.app.PendingIntent

class AudioLoopbackService : Service() {

    companion object {
        init { System.loadLibrary("native-lib") }
        var instance: AudioLoopbackService? = null
        var isServiceRunning = false
    }

    // CHANGE: Update definition to accept Int
    private external fun startOboe(inputDeviceId: Int): Boolean
    private external fun stopOboe()
    private external fun setNativeVolume(v: Float)

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    fun updateVolume(volume: Float) {
        setNativeVolume(volume)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "START") {
            startForegroundService()
            val micId = getBuiltInMicId()
            
            if (startOboe(micId)) {
                setNativeVolume(1.0f)
                // --- NEW: Mark as running ---
                isServiceRunning = true
            }
        } else if (intent?.action == "STOP") {
            stopOboe()
            stopForeground(true)
            stopSelf()
            // --- NEW: Mark as stopped ---
            isServiceRunning = false
        }
        return START_NOT_STICKY
    }

    // --- NEW HELPER FUNCTION ---
    private fun getBuiltInMicId(): Int {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            for (device in devices) {
                // Look for the main built-in microphone
                if (device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
                    return device.id
                }
            }
        }
        return 0 // 0 means "Default" (OS decides)
    }

    private fun startForegroundService() {
        val channelId = "AudioLoopbackChannel"
        
        // 1. Create the Notification Channel (Required for Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Voice Loopback Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        // 2. Create the "Stop" Action Intent
        // This Intent targets THIS service directly
        val stopIntent = Intent(this, AudioLoopbackService::class.java)
        stopIntent.action = "STOP"

        // 3. Wrap it in a PendingIntent
        // FLAG_IMMUTABLE is required for Android 12+ (API 31+) security
        val pendingStopIntent = PendingIntent.getService(
            this, 
            0, 
            stopIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 4. Build the Notification with the Action Button
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Hearing Aid Active")
            .setContentText("Tap 'Stop' to turn off")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            
            // --- NEW: Add the Action Button ---
            // 'android.R.drawable.ic_media_pause' is a built-in pause icon
            .addAction(android.R.drawable.ic_media_pause, "STOP", pendingStopIntent)
            // ----------------------------------
            
            // Make it non-dismissible while running (standard for foreground services)
            .setOngoing(true) 
            .build()

        startForeground(1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}