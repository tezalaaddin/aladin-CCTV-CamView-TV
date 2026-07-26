# Aladin CCTV CamView for Android TV

Aladin CCTV is a professional, high-performance CCTV viewer application specifically optimized for Android TV. It supports low-latency RTSP streaming and advanced ONVIF PTZ controls with dynamic discovery, making it compatible with various camera brands including **AJCloud**, **Tiandy**, **Hikvision**, and **Dahua**.

## 🚀 Key Features

- **Optimized for Android TV:** Premium, clearly visible D-pad focus behavior across cameras, menus, forms and PTZ controls.
- **Robust RTSP Playback:** LibVLC supports cameras whose non-standard RTSP streams are not handled reliably by Media3.
- **🎮 Dynamic ONVIF PTZ:** 
    - Intelligent Port Discovery (80, 8899, 8000, 8080 etc.)
    - Dynamic Service Discovery via ONVIF `GetCapabilities`.
    - Supports Continuous Move (Up, Down, Left, Right) and Zoom.
- **📁 Multi-Camera Support:** Add and manage multiple cameras with custom branding.
- **Local-first:** Camera streams are opened directly over the local network and camera records are stored in Room.
- **Separate credentials:** RTSP and ONVIF accounts can be configured independently when a camera requires it.
- **🛠️ Automated Fixes:** "Fix Camera" feature to automatically switch older cameras to H.264 for TV compatibility.

## 🛠️ Technical Stack

- **Language:** Kotlin
- **Video Engine:** LibVLC 3.6.5
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

Release signing is read from the ignored `keystore.properties` file. Copy
`keystore.properties.example`, point it to the private upload keystore and never
commit either file. See [PLAY_STORE_RELEASE_CHECKLIST.md](PLAY_STORE_RELEASE_CHECKLIST.md).

### APK architecture

Release builds produce separate APKs for `arm64-v8a`, `armeabi-v7a`, `x86` and `x86_64`. LibVLC
contains large native codec libraries, so installing the APK that matches the TV
avoids shipping every architecture to one device. The release AAB contains all supported ABIs
and Google Play generates the appropriate device package. Most current Android TVs use
`arm64-v8a`; `adb shell getprop ro.product.cpu.abi` shows the device ABI.

### Focused development logs

To show only Aladin diagnostics, crashes, network recovery and playback events:

```powershell
.\scripts\logcat-aladin.ps1 -Device 192.168.1.54:5555
```

The equivalent direct ADB filter is:

```text
adb logcat -s ALADIN_VLC:V ALADIN_NETWORK:V ALADIN_NETWORK_TRACKER:V ALADIN_DISCOVERY:V ALADIN_DEBUG_ONVIF:V ALADIN_PTZ:V ALADIN_WATCHDOG:V ALADIN_DIAG:V AndroidRuntime:E *:S
```

## ⚙️ Configuration

- **RTSP credentials:** Credentials used to play the camera stream.
- **ONVIF credentials:** By default these match RTSP credentials, but a separate account can be entered.
- **IP Address:** Local IP of the camera.
- **PTZ:** Ensure ONVIF is enabled in your camera settings.

## Version History

- **v1.3 (Current):** Premium Android TV UI and focus system, profile-first verified camera setup, separate RTSP/ONVIF credentials, identity-based DHCP recovery, duplicate-IP protection and RTSP freeze recovery. See [RELEASE_NOTES_v1.3.md](RELEASE_NOTES_v1.3.md).
- **v1.0:** Initial Android TV camera viewing and ONVIF/PTZ implementation.

## 📄 License
This project is licensed under the MIT License - see the LICENSE file for details.

See [PRIVACY_POLICY.md](PRIVACY_POLICY.md) for the privacy policy and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for dependency notices.

---
**Developed by Aladin Development**
