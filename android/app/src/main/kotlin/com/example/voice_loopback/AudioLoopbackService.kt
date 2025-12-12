package com.example.voice_loopback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.*
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlin.concurrent.thread

class AudioLoopbackService : Service() {

    companion object {
        init { System.loadLibrary("native-lib") }
        var instance: AudioLoopbackService? = null
        var isServiceRunning = false
    }

    // --- C++ Oboe Functions ---
    private external fun startOboe(inputDeviceId: Int): Boolean
    private external fun stopOboe()
    private external fun setNativeVolume(v: Float)

    // --- Java "Safe Mode" Variables ---
    private var isJavaRunning = false
    private var javaThread: Thread? = null
    private var javaVolume = 1.0f

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    fun updateVolume(volume: Float) {
        setNativeVolume(volume) // Update C++
        javaVolume = volume     // Update Java
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "START") {
            startForegroundService()
            
            val useOboe = intent.getBooleanExtra("USE_OBOE", true)
            
            // Get the actual Phone Mic Device (not headset mic)
            val builtInMic = getBuiltInMicDevice()

            if (useOboe) {
                // TRY C++ ENGINE
                // Pass the ID (Int) to C++
                val micId = builtInMic?.id ?: 0 
                if (startOboe(micId)) {
                    setNativeVolume(1.0f)
                    isServiceRunning = true
                }
            } else {
                // TRY JAVA ENGINE (SAFE MODE)
                // Pass the full Device Object to Java
                startJavaLoop(builtInMic) 
                isServiceRunning = true
            }

        } else if (intent?.action == "STOP") {
            stopOboe()
            stopJavaLoop()
            
            stopForeground(true)
            stopSelf()
            isServiceRunning = false
        }
        return START_NOT_STICKY
    }

    // --- THE JAVA FALLBACK ENGINE ---
    private fun startJavaLoop(preferredDevice: AudioDeviceInfo?) {
        if (isJavaRunning) return
        isJavaRunning = true
        
        javaThread = thread(start = true, priority = Thread.MAX_PRIORITY) {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)

            val sampleRate = 44100 
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2
            
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, 
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            // --- FIX FOR WIRED HEADSET IN SAFE MODE ---
            // If we found a built-in mic, force AudioRecord to use it.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && preferredDevice != null) {
                recorder.preferredDevice = preferredDevice
            }
            // ------------------------------------------

            val track = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM
            )

            val buffer = ShortArray(bufferSize / 2) 

            try {
                recorder.startRecording()
                track.play()

                while (isJavaRunning) {
                    val readSize = recorder.read(buffer, 0, buffer.size)
                    if (readSize > 0) {
                        
                        // Java Volume Logic
                        val currentVol = javaVolume
                        if (currentVol != 1.0f) {
                            for (i in 0 until readSize) {
                                var amplified = (buffer[i] * currentVol).toInt()
                                if (amplified > 32767) amplified = 32767
                                if (amplified < -32768) amplified = -32768
                                buffer[i] = amplified.toShort()
                            }
                        }

                        track.write(buffer, 0, readSize)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    recorder.stop()
                    recorder.release()
                    track.stop()
                    track.release()
                } catch (e: Exception) {}
            }
        }
    }

    private fun stopJavaLoop() {
        isJavaRunning = false
        try {
            javaThread?.join(500)
        } catch (e: Exception) {}
    }

    // --- UPDATED HELPER FUNCTION ---
    // Returns the entire AudioDeviceInfo object, not just the ID.
    private fun getBuiltInMicDevice(): AudioDeviceInfo? {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            for (device in devices) {
                if (device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
                    return device
                }
            }
        }
        return null
    }
    // -------------------------------

    private fun startForegroundService() {
        val channelId = "AudioLoopbackChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Voice Loopback", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, AudioLoopbackService::class.java)
        stopIntent.action = "STOP"
        val pendingStopIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Hearing Aid Active")
            .setContentText("Tap 'Stop' to turn off")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .addAction(android.R.drawable.ic_media_pause, "STOP", pendingStopIntent)
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