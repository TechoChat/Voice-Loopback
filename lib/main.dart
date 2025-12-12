import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';

void main() {
  runApp(
    const MaterialApp(
      home: AudioLoopbackApp(),
      debugShowCheckedModeBanner: false,
    ),
  );
}

class AudioLoopbackApp extends StatefulWidget {
  const AudioLoopbackApp({super.key});

  @override
  State<AudioLoopbackApp> createState() => _AudioLoopbackAppState();
}

class _AudioLoopbackAppState extends State<AudioLoopbackApp> {
  static const platform = MethodChannel('com.example.your_app/audio');
  bool isRunning = false;
  String statusText = "Ready";

  Future<void> _checkPermissions() async {
    // Request Microphone and Bluetooth permissions
    Map<Permission, PermissionStatus> statuses = await [
      Permission.microphone,
      Permission.bluetoothConnect, // Required for Android 12+
    ].request();

    if (statuses[Permission.microphone] != PermissionStatus.granted) {
      setState(() => statusText = "Microphone permission required");
    }
  }

  Future<void> _toggleService() async {
    await _checkPermissions();

    try {
      if (isRunning) {
        await platform.invokeMethod('stopLoopback');
        setState(() {
          isRunning = false;
          statusText = "Stopped";
        });
      } else {
        // Attempt to start
        await platform.invokeMethod('startLoopback');
        // If the line above doesn't throw an error, it succeeded
        setState(() {
          isRunning = true;
          statusText = "Hearing Aid Active";
        });
      }
    } on PlatformException catch (e) {
      // This runs if Native sends "NO_HEADSET" error
      setState(() {
        isRunning = false; // Keep the switch off
        statusText = "Connect Headphones to start";
      });
      // The Toast message will appear automatically from Native code
      debugPrint("Error: ${e.message}");
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text("Hearing Aid")),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              isRunning ? Icons.mic : Icons.mic_off,
              size: 80,
              color: isRunning ? Colors.green : Colors.grey,
            ),
            const SizedBox(height: 20),
            Text(statusText, style: const TextStyle(fontSize: 18)),
            const SizedBox(height: 40),
            ElevatedButton(
              onPressed: _toggleService,
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.symmetric(
                  horizontal: 40,
                  vertical: 15,
                ),
              ),
              child: Text(isRunning ? "STOP LOOPBACK" : "START LOOPBACK"),
            ),
            const Padding(
              padding: EdgeInsets.all(20.0),
              child: Text(
                "Note: Connect Wired/wireless headset in order to start. "
                "The audio will route automatically.",
                textAlign: TextAlign.center,
                style: TextStyle(color: Colors.grey),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
