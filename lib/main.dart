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
  String statusText = "Ready";
  double _volume = 1.0;

  // --- NEW: Toggle for Oboe vs Java ---
  bool useLowLatency = false;
  // ------------------------------------

  @override
  void initState() {
    super.initState();
    _syncServiceState();
  }

  Future<void> _syncServiceState() async {
    try {
      final bool isActive = await platform.invokeMethod('checkStatus');
      if (isActive) {
        setState(() {
          isRunning = true;
          statusText = "Hearing Aid Active";
        });
      }
    } catch (e) {
      debugPrint("Error syncing: $e");
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
        // --- NEW: Pass the boolean to Native ---
        await platform.invokeMethod('startLoopback', {
          "useOboe": useLowLatency,
        });
        // ---------------------------------------

        setState(() {
          isRunning = true;
          statusText = useLowLatency
              ? "Active (Low Latency)"
              : "Active (Safe Mode)";
        });
        _setVolume(_volume);
      }
    } on PlatformException catch (e) {
      debugPrint("Error: $e");
      setState(() {
        isRunning = false;
        statusText = "Error: Connect Headset First";
      });
    }
  }

  Future<void> _setVolume(double value) async {
    try {
      if (isRunning) {
        await platform.invokeMethod('setVolume', {"volume": value});
      }
    } catch (e) {}
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text("Hearing Aid App")),
      // 1. Wrap the entire body in a Scroll View to prevent overflow errors
      body: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.all(30.0),
          child: Center(
            // Center allows the content to be centered if it's smaller than the screen
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                // Add some top spacing so it doesn't touch the top in landscape
                const SizedBox(height: 20),

                Icon(
                  isRunning ? Icons.hearing : Icons.hearing_disabled,
                  size: 100,
                  color: isRunning
                      ? (useLowLatency ? Colors.green : Colors.orange)
                      : Colors.grey,
                ),
                const SizedBox(height: 20),
                Text(
                  statusText,
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 30),

                // CHECKBOX UI
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    border: Border.all(color: Colors.grey),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: SwitchListTile(
                    title: const Text("High Performance Mode"),
                    subtitle: const Text("Uncheck if app crashes (Safe Mode)"),
                    value: useLowLatency,
                    onChanged: isRunning
                        ? null
                        : (val) {
                            setState(() => useLowLatency = val);
                          },
                  ),
                ),

                const SizedBox(height: 30),
                const Text(
                  "Microphone Boost",
                  style: TextStyle(fontWeight: FontWeight.bold),
                ),

                // Volume Slider Row
                Row(
                  children: [
                    const Icon(Icons.volume_mute),
                    Expanded(
                      child: Slider(
                        value: _volume,
                        min: 1.0,
                        max: 10.0,
                        divisions: 18,
                        label: "${_volume.toStringAsFixed(1)}x",
                        onChanged: (value) {
                          setState(() => _volume = value);
                          _setVolume(value);
                        },
                      ),
                    ),
                    const Icon(Icons.volume_up),
                  ],
                ),

                Text(
                  "Mic Level: ${_volume.toStringAsFixed(1)}x",
                  style: const TextStyle(color: Colors.blue, fontSize: 16),
                ),

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

                // Add bottom spacing so the last element isn't cut off
                const SizedBox(height: 20),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
