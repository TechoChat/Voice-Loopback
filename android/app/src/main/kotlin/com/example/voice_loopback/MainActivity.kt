package com.example.voice_loopback

import android.content.Intent
import android.os.Build
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
    private val CHANNEL = "com.example.your_app/audio"

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            // Use the correct package context here
            val intent = Intent(this, AudioLoopbackService::class.java)
            
            if (call.method == "startLoopback") {
                intent.action = "START"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                result.success("Started")
            } else if (call.method == "stopLoopback") {
                intent.action = "STOP"
                startService(intent) // startService is used to deliver the STOP intent too
                result.success("Stopped")
            } else {
                result.notImplemented()
            }
        }
    }
}