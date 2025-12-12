package com.example.voice_loopback

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.widget.Toast
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
    private val CHANNEL = "com.example.your_app/audio"

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            val intent = Intent(this, AudioLoopbackService::class.java)
            
            if (call.method == "startLoopback") {
                // --- NEW CHECK: Only start if headset is connected ---
                if (isHeadsetConnected()) {
                    intent.action = "START"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    result.success("Started")
                } else {
                    // Show Error Toast
                    Toast.makeText(this, "Connect Headphones (Wired or Bluetooth) first!", Toast.LENGTH_LONG).show()
                    // Tell Flutter we failed so the UI doesn't turn green
                    result.error("NO_HEADSET", "Headset not connected", null)
                }
                // ----------------------------------------------------

            } else if (call.method == "stopLoopback") {
                intent.action = "STOP"
                startService(intent)
                result.success("Stopped")
            } else if (call.method == "setVolume") {
                val volume = call.argument<Double>("volume")?.toFloat() ?: 1.0f
                
                // Call the running service safely
                AudioLoopbackService.instance?.updateVolume(volume)
                
                result.success(null)
            } else if (call.method == "checkStatus") {
                result.success(AudioLoopbackService.isServiceRunning)
            } else {
                result.notImplemented()
            }
        }
    }

    // Helper function to check for any type of headset
    private fun isHeadsetConnected(): Boolean {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in devices) {
                val type = device.type
                if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET || 
                    type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || 
                    type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    type == AudioDeviceInfo.TYPE_USB_DEVICE) { // USB for Type-C dongles
                    return true
                }
            }
            return false
        } else {
            // Fallback for very old Android versions (< 6.0)
            return audioManager.isWiredHeadsetOn || audioManager.isBluetoothA2dpOn
        }
    }
}