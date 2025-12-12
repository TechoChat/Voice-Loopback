package com.example.voice_loopback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.*
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlin.concurrent.thread

class AudioLoopbackService : Service() {

    private var isRunning = false
    private var audioThread: Thread? = null

    // Audio Configuration for Low Latency
    // 48kHz is standard for modern devices; 16-bit PCM is standard for voice
    private val sampleRate = 48000 
    private val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
    private val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "START") {
            if (!isRunning) {
                startForegroundService()
                startAudioLoop()
            }
        } else if (intent?.action == "STOP") {
            stopAudioLoop()
            stopForeground(true)
            stopSelf()
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
            .setContentTitle("Voice Loopback Active")
            .setContentText("Relaying microphone to speaker...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now) // Replace with your icon
            .build()

        // ID must be > 0
        startForeground(1, notification)
    }

    private fun startAudioLoop() {
        isRunning = true
        
        // Use a high priority thread for audio to prevent stutter
        audioThread = thread(start = true, priority = Thread.MAX_PRIORITY) {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)

            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat)
            
            // Input: Mic
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_PERFORMANCE, // Tuned for low latency
                sampleRate,
                channelConfigIn,
                audioFormat,
                bufferSize
            )

            // Output: Speaker (Bluetooth/Wired is handled automatically by OS routing)
            val track = AudioTrack(
                AudioManager.STREAM_MUSIC, // Use MUSIC for better quality on BT speakers
                sampleRate,
                channelConfigOut,
                audioFormat,
                bufferSize,
                AudioTrack.MODE_STREAM
            )

            val audioBuffer = ByteArray(bufferSize)

            try {
                recorder.startRecording()
                track.play()

                while (isRunning) {
                    // Read from Mic
                    val readResult = recorder.read(audioBuffer, 0, bufferSize)
                    if (readResult > 0) {
                        // Write directly to Speaker
                        track.write(audioBuffer, 0, readResult)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Cleanup
                try {
                    recorder.stop()
                    recorder.release()
                    track.stop()
                    track.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun stopAudioLoop() {
        isRunning = false
        audioThread?.join(1000) // Wait for thread to finish safely
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}