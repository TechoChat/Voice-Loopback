# OpenEar: Low Latency Hearing Aid & Voice Loopback

![Flutter](https://img.shields.io/badge/Flutter-3.0%2B-blue) ![Android](https://img.shields.io/badge/Android-Min%20SDK%2026-green) ![Language](https://img.shields.io/badge/Language-Kotlin%20%7C%20C%2B%2B-orange) ![License](https://img.shields.io/badge/License-MIT-lightgrey)

**OpenEar** is a real-time voice loopback application designed to function as a software Hearing Aid for Android. It captures environmental sound using the phone's **internal microphone** and streams it directly to a connected **Wired or Bluetooth Headset** with minimal latency.

> **Key Tech:** This project uses a hybrid engine. It defaults to Google's **Oboe (C++)** for ultra-low latency (<10ms) but includes a robust **Java Fallback (Safe Mode)** for devices with aggressive custom ROMs (e.g., Redmi, Realme).

## 🚀 Features

* **Dual Audio Engine:**
    * **High Performance Mode:** Uses Native C++ (Oboe/AAudio) for the lowest possible delay.
    * **Safe Mode:** Uses standard Android Audio APIs to prevent crashes on incompatible devices.
* **Smart Mic Switching:** Forces the use of the *Phone's Internal Mic* even when wired headphones are plugged in (prevents "pocket noise" from the headset mic).
* **Volume Booster:** Digital Gain Slider (1x to 10x amplification) with a hard limiter to prevent clipping and hearing damage.
* **Background Service:** Runs continuously in the background with a persistent notification.
* **Quick Controls:** Stop the hearing aid directly from the Android Notification shade.
* **Strict Safety Checks:** Prevents operation unless a valid Headset (Bluetooth/Wired) is connected (ignores USB charging cables).

## 🛠️ Architecture

The app uses a 3-tier architecture to balance UI flexibility with raw audio performance:

1.  **Flutter (UI):**
    * Manages user permissions and state.
    * Provides the Volume Slider and "Safe Mode" toggle.
    * Communicates with Native code via `MethodChannels`.

2.  **Kotlin (Android Service):**
    * Runs as a Foreground Service to keep the app alive.
    * Handles Hardware Connectivity checks (Headset detection).
    * Routes the audio logic to either C++ or Java based on user selection.

3.  **C++ (Oboe Engine):**
    * **Input:** Direct stream from `TYPE_BUILTIN_MIC` (InputPreset: `Unprocessed`).
    * **Processing:** Digital Gain -> Hard Limiting.
    * **Output:** Direct low-latency stream to the audio hardware.

## 📱 Device Compatibility Matrix

| Brand | High Performance Mode (C++) | Safe Mode (Java) | Notes |
| :--- | :--- | :--- | :--- |
| **Google Pixel** | ✅ Excellent | ✅ Working | Lowest latency (<10ms). |
| **Samsung** | ✅ Good | ✅ Working | Standard performance. |
| **Nothing Phone** | ✅ Good | ✅ Working | Works flawlessly. |
| **Motorola** | ✅ Good | ✅ Working | Near stock Android latency. |
| **Redmi (Xiaomi)** | ❌ Crash Likely | ✅ **Recommended** | MIUI audio buffers are often incompatible with low-latency Oboe. Use Safe Mode. |
| **Realme/Oppo** | ⚠️ Unstable | ✅ **Recommended** | May crash in High Performance mode. Use Safe Mode. |

## ⚙️ Installation & Setup

### Prerequisites
* **Flutter SDK:** 3.0 or higher.
* **Android Studio:** With **NDK (Side by side)** and **CMake** installed via SDK Tools.
* **Physical Device:** An Android phone (Emulators do not support microphone loopback).

### Steps
1.  **Clone the repository:**
    ```bash
    git clone [https://github.com/yourusername/openear.git](https://github.com/yourusername/openear.git)
    cd openear
    ```

2.  **Install Dependencies:**
    ```bash
    flutter pub get
    ```

3.  **Build & Run:**
    Connect your Android device via USB and run:
    ```bash
    flutter run
    ```
    *Note: The first build will take longer as it compiles the C++ Oboe library.*

## 📖 How to Use

1.  **Connect Headphones:** Plug in wired earphones or connect a Bluetooth headset.
    * *Note: If you start without headphones, the app will show an error toast.*
2.  **Select Mode:**
    * Keep **"High Performance Mode"** CHECKED for best speed.
    * If the app crashes immediately, UNCHECK it to use **"Safe Mode"**.
3.  **Press START:** The screen will turn green, and audio loopback will begin.
4.  **Adjust Volume:** Use the slider to amplify quiet sounds.
5.  **Background Use:** You can minimize the app. Use the **Notification Panel** to stop the service quickly.

## ⚠️ Troubleshooting

**Q: The app crashes when I press Start.**
A: This is common on Redmi/Realme devices. Uncheck the "High Performance Mode" switch and try again.

**Q: I hear a "Beep Beep" or robotic sound.**
A: This is a sample rate mismatch on cheap Bluetooth headphones. Restart the app; the C++ engine now auto-detects `kUnspecified` sample rates to fix this.

**Q: The volume is too low.**
A: First, increase your phone's physical media volume to 100%. Then, use the in-app slider to boost the digital gain.

**Q: It says "Connect Headset First" but my phone is plugged into my laptop.**
A: This is intentional. The app ignores generic USB connections (like laptops) to prevent feedback loops. It only recognizes devices strictly identified as `TYPE_USB_HEADSET` or `TYPE_WIRED_HEADSET`.

## 💻 Tech Stack

* **Frontend:** Flutter (Dart)
* **Native Bridge:** JNI / MethodChannels
* **Audio Engine:**
    * Primary: C++17 with [Google Oboe](https://github.com/google/oboe)
    * Fallback: Android `AudioRecord` / `AudioTrack` (Kotlin)
* **Platform:** Android (Min SDK 21, Target SDK 34)

## 🤝 Contributing

Pull requests are welcome! Specifically, we are looking for help with:
1.  Improving Oboe buffer configuration for MIUI (Xiaomi) devices.
2.  Adding a Dynamic Range Compressor (DRC) to the C++ engine to smooth out loud noises.

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.