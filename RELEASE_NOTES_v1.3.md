# Aladin CCTV v1.3

Release date: 26 July 2026

Aladin CCTV v1.3 improves long-running RTSP playback, camera discovery and setup, DHCP recovery, Android TV remote navigation and the overall visual experience.

## Highlights

- Introduced a premium Android TV interface with a consistent orange focus halo, elevation and scale feedback across cameras, menus, buttons, forms and discovery cards.
- Added the new Aladin CCTV logo to the launcher, splash screen, TV banner and main toolbar.
- Redesigned the full-screen camera and PTZ interface. PTZ uses four standard direction controls; zoom uses `−` and `+` buttons and can also be controlled with Channel/Page Down and Channel/Page Up remote keys.
- Added profile-first camera setup for Hikvision, Dahua, Tiandy, Uniview, Reolink, Axis, Hanwha, Vivotek, Foscam, Tapo, AJCloud and XMeye cameras.
- Camera setup now verifies main and sub-stream RTSP addresses before saving. ONVIF is used independently for device metadata, PTZ capabilities and verified stream fallback.
- Added separate RTSP and ONVIF credential support. Existing cameras remain compatible because empty ONVIF credentials mean “use RTSP credentials”.

## Stability and network improvements

- Added silent video-freeze detection based primarily on LibVLC displayed-frame statistics, with controlled stream restart and software-decoder fallback.
- Improved long-running Android TV playback and reduced noisy buffer logging.
- Added UUID/MAC-based DHCP address recovery. Cameras with a strong identity match can have their IP and RTSP hosts updated automatically.
- Identity-less camera replacements require user confirmation after stream verification; shared credentials or stream paths are not treated as physical camera identity.
- Added database- and UI-level duplicate-IP protection.
- Discovery results now use real device metadata and hide ping-only network devices from the camera list while retaining diagnostic logs.

## Data and compatibility

- Room database schema is now version 4, with a non-destructive `3 → 4` migration for separate ONVIF credentials.
- Minimum Android version remains Android 7.0 / API 24.
- Release APKs are generated separately for `armeabi-v7a`, `arm64-v8a`, `x86` and `x86_64`.
- The Android App Bundle contains all four supported ABIs for store-side device delivery.
- LibVLC remains the primary playback engine for compatibility with non-standard RTSP cameras.

## Verification

- Unit tests and Android lint completed successfully.
- Release AAB and all four ABI-specific APKs completed with R8 code/resource shrinking.
- Package contents were checked to confirm that every split APK contains only its intended ABI.
- The staged source was checked for credential-bearing RTSP URLs and literal passwords.
- Playback, D-pad focus and DHCP recovery were exercised on an Android TV during development.

## Distribution note

Release artifacts must be signed with the project's private release key before installation or store upload. Private keys and credentials must never be committed to the repository.

## Store-readiness hardening

- Camera credentials and credential-bearing RTSP addresses are encrypted at rest with Android Keystore.
- The application PIN is stored as a salted one-way hash; the fixed administrator override was removed.
- Cloud/device-transfer backup is disabled for application data.
- Configuration exports omit camera credentials and the app PIN; validated imports replace cameras atomically.
- Boot start, automatic DHCP recovery, daily maintenance and production diagnostic logging are user-controlled.
- WebView navigation is restricted to the selected private camera host and file/content access is disabled.
- Android TV Ethernet discovery, 320×180 banner, adaptive launcher icon, AAB language delivery and accessibility issues were corrected.
- Release signing, privacy policy, third-party notices and Play Store checklist were added.
