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

        audioThread = thread(start = true, priority = Thread.MAX_PRIORITY) {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)

            // Preferred sample rate
            val sampleRate = 48000

            // Choose an audio source that avoids "voice-only" processing
            val audioSource = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // API 24+: try to request raw/unprocessed mic if available
                MediaRecorder.AudioSource.UNPROCESSED
            } else {
                // fallback to plain mic (CAMCORDER sometimes provides wider capture)
                MediaRecorder.AudioSource.MIC
            }

            // Channel configs
            val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
            val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT

            // Get min buffer size and guard against errors
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat)
            if (minBuf == AudioRecord.ERROR || minBuf == AudioRecord.ERROR_BAD_VALUE) {
                // can't continue reliably
                isRunning = false
                return@thread
            }
            // Use a slightly larger buffer for stability
            val bufferSizeInShorts = (minBuf / 2) // minBuf is bytes for PCM_16, so /2 gives shorts
            val bufferSizeBytes = bufferSizeInShorts * 2

            // Create AudioRecord
            val recorder = AudioRecord(
                audioSource,
                sampleRate,
                channelConfigIn,
                audioFormat,
                bufferSizeBytes
            )

            // Create AudioTrack for playback
            val track = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfigOut)
                    .build(),
                bufferSizeBytes,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            // Safety checks
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                isRunning = false
                return@thread
            }
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                recorder.release()
                isRunning = false
                return@thread
            }

            // IMPORTANT: Do NOT attach or create NoiseSuppressor/AcousticEchoCanceler/AutomaticGainControl here
            // if your goal is to capture ambient sounds. Some apps created them like:
            // if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(recorder.audioSessionId)
            // Do NOT do the above — that will reduce ambient sound.

            // Software amplification factor (tweakable). Start low (1.0 = no change).
            val gain = 1.5f // try 1.2, 1.5, 2.0 ... higher -> more noise

            // Buffer using shorts (PCM 16-bit)
            val shortBuffer = ShortArray(bufferSizeInShorts)

            try {
                recorder.startRecording()
                track.play()

                while (isRunning) {
                    // read returns number of samples (shorts) when using read(shortArray,...)
                    val readSamples = recorder.read(shortBuffer, 0, shortBuffer.size)
                    if (readSamples > 0) {
                        // Apply simple software gain with clipping
                        if (gain != 1.0f) {
                            var i = 0
                            while (i < readSamples) {
                                // apply gain and clamp to 16-bit signed range
                                val amplified = (shortBuffer[i] * gain).toInt()
                                shortBuffer[i] = when {
                                    amplified > Short.MAX_VALUE -> Short.MAX_VALUE
                                    amplified < Short.MIN_VALUE -> Short.MIN_VALUE
                                    else -> amplified.toShort()
                                }
                                i++
                            }
                        }

                        // write back to AudioTrack (shorts)
                        var written = 0
                        while (written < readSamples) {
                            val w = track.write(shortBuffer, written, readSamples - written)
                            if (w <= 0) break
                            written += w
                        }
                    } else {
                        // handle errors / sleep briefly
                        // readSamples may be AudioRecord.ERROR_INVALID_OPERATION etc.
                        // avoid busy loop
                        Thread.sleep(2)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    recorder.stop()
                } catch (e: Exception) { }
                try { recorder.release() } catch (e: Exception) { }

                try {
                    track.stop()
                } catch (e: Exception) { }
                try { track.release() } catch (e: Exception) { }
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