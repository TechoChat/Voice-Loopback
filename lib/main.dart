import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';

void main() {
  runApp(const MaterialApp(home: AudioLoopbackApp()));
}

class AudioLoopbackApp extends StatefulWidget {
  const AudioLoopbackApp({super.key});

  @override
  State<AudioLoopbackApp> createState() => _AudioLoopbackAppState();
}

class _AudioLoopbackAppState extends State<AudioLoopbackApp> {
  static const platform = MethodChannel('com.example.your_app/audio');

  bool isRunning = false;
  String statusText = "Connect Headset & Press Start";

  // Volume State
  double _volume = 1.0; // Starts at 1x (Normal)

  @override
  void initState() {
    super.initState();
    _syncServiceState();
  }

  Future<void> _syncServiceState() async {
    try {
      // Ask Java/Kotlin if the service is running
      final bool isActive = await platform.invokeMethod('checkStatus');

      if (isActive) {
        setState(() {
          isRunning = true;
          statusText = "Hearing Aid Active (Background)";
        });
      }
    } catch (e) {
      debugPrint("Error syncing state: $e");
    }
  }

  Future<void> _checkPermissions() async {
    await [
      Permission.microphone,
      Permission.bluetoothConnect,
      Permission.notification,
    ].request();
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
        await platform.invokeMethod('startLoopback');
        setState(() {
          isRunning = true;
          statusText = "Hearing Aid Active";
        });
        // Send current slider value immediately on start
        _setVolume(_volume);
      }
    } on PlatformException catch (e) {
      debugPrint("Error: $e");
      setState(() {
        isRunning = false;
        statusText = "Error: Connect Headset First ";
      });
    }
  }

  // Send volume to Native C++
  Future<void> _setVolume(double value) async {
    try {
      if (isRunning) {
        await platform.invokeMethod('setVolume', {"volume": value});
      }
    } catch (e) {
      debugPrint("Error setting volume: $e");
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text("Hearing Aid App")),
      body: Padding(
        padding: const EdgeInsets.all(30.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              isRunning ? Icons.hearing : Icons.hearing_disabled,
              size: 100,
              color: isRunning ? Colors.green : Colors.grey,
            ),
            const SizedBox(height: 20),
            Text(
              statusText,
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 50),

            // --- VOLUME SLIDER ---
            const Text(
              "Microphone Boost (Gain)",
              style: TextStyle(fontWeight: FontWeight.bold),
            ),
            Row(
              children: [
                const Icon(Icons.volume_mute),
                Expanded(
                  child: Slider(
                    value: _volume,
                    min: 1.0, // Normal volume
                    max: 10.0, // 10x Amplification
                    divisions: 18, // Steps of 0.5
                    label: "${_volume.toStringAsFixed(1)}x",
                    onChanged: (value) {
                      setState(() => _volume = value);
                      _setVolume(value); // Update in real-time
                    },
                  ),
                ),
                const Icon(Icons.volume_up),
              ],
            ),
            Text(
              "Current Boost: ${_volume.toStringAsFixed(1)}x",
              style: const TextStyle(color: Colors.blue, fontSize: 16),
            ),

            // ---------------------
            const SizedBox(height: 40),
            ElevatedButton(
              onPressed: _toggleService,
              style: ElevatedButton.styleFrom(
                backgroundColor: isRunning ? Colors.red : Colors.blue,
                padding: const EdgeInsets.symmetric(
                  horizontal: 40,
                  vertical: 15,
                ),
              ),
              child: Text(
                isRunning ? "STOP" : "START",
                style: const TextStyle(color: Colors.white),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
