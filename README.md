# Aladin CCTV CamView for Android TV 📺

Aladin CCTV is a professional, high-performance CCTV viewer application specifically optimized for Android TV. It supports low-latency RTSP streaming and advanced ONVIF PTZ controls with dynamic discovery, making it compatible with various camera brands including **AJCloud**, **Tiandy**, **Hikvision**, and **Dahua**.

## 🚀 Key Features

- **📲 Ready to Install:** You can find the compiled APKs in the [release_apk](release_apk/) folder.
- **📺 Optimized for Android TV:** Native D-Pad navigation and leanback-style interface for the best TV experience.
- **⚡ High-Performance RTSP:** Powered by Google's Media3 (ExoPlayer) with RTSP extension for stable, low-latency streams.
- **🎮 Dynamic ONVIF PTZ:** 
    - Intelligent Port Discovery (80, 8899, 8000, 8080 etc.)
    - Dynamic Service Discovery via ONVIF `GetCapabilities`.
    - Supports Continuous Move (Up, Down, Left, Right) and Zoom.
- **📁 Multi-Camera Support:** Add and manage multiple cameras with custom branding.
- **🛡️ Secure & Local:** Uses Room Database for local encrypted storage and Android Security Crypto for credentials.
- **🛠️ Automated Fixes:** "Fix Camera" feature to automatically switch older cameras to H.264 for TV compatibility.

## 🛠️ Technical Stack

- **Language:** Kotlin
- **Video Engine:** Android Media3 (ExoPlayer 1.5.1)
- **Database:** Room Persistence Library (with KSP)
- **Networking:** ONVIF SOAP (XML) implementation
- **Architecture:** MVVM (ViewModel, Repository, Flow)

## 📦 Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/tezalaaddin/aladin-CCTV-CamView-TV.git
   ```
2. Open in Android Studio (Ladybug or newer).
3. Build the project using Gradle.
4. Deploy to your Android TV via ADB.

## ⚙️ Configuration

- **Username/Password:** Admin credentials for ONVIF/RTSP.
- **IP Address:** Local IP of the camera.
- **PTZ:** Ensure ONVIF is enabled in your camera settings.

## 📝 Version History
- **v1.0 (Current):** 
    - Initial release.
    - Dynamic ONVIF port/service discovery.
    - Full D-Pad PTZ control support.
    - Stability fixes for Room/KSP on AGP 9.2.1.

## 📄 License
This project is licensed under the MIT License - see the LICENSE file for details.

---
**Developed by Aladin Development**
