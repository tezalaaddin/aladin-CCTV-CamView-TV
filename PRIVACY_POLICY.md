# Aladin CCTV Privacy Policy

Last updated: 26 July 2026

Aladin CCTV is a local-network camera viewer. The application connects directly from the Android device to IP cameras selected by the user. The developer does not operate a cloud service for camera streams and the application does not include advertising or analytics SDKs.

## Data stored on the device

The application may store camera names, local IP addresses, RTSP and ONVIF usernames/passwords, stream addresses, camera identifiers such as UUID/MAC, application preferences and snapshots. Camera credentials and credential-bearing stream addresses are encrypted at rest with a key held by Android Keystore. The application PIN is stored as a salted one-way hash.

Android cloud backup and device-transfer backup are disabled for application databases, preferences and files. Configuration exports intentionally omit camera usernames, passwords and the application PIN. Users must enter camera credentials again after importing a configuration.

## Network communication

The application communicates with cameras and devices on the user's local network using protocols such as RTSP, ONVIF, HTTP, mDNS and WS-Discovery. Many cameras use unencrypted local HTTP or RTSP connections. Users are responsible for the security and configuration of their cameras and local network.

The application does not send camera streams, credentials or usage data to the developer. A camera's own web interface may operate according to the camera manufacturer's privacy policy.

## Permissions

- Internet/network permissions are used to discover and connect to local cameras.
- Wi-Fi multicast access is used for local discovery where available.
- Boot completion is used only when the user enables “Start on TV boot”.

## User controls and deletion

Users can edit or delete individual cameras, clear application data from Android settings, uninstall the application, disable automatic network recovery/boot/maintenance options and delete exported configuration or snapshot files through the system file manager.

## Children

Aladin CCTV is a utility for configuring and viewing privately operated cameras. It is not directed to children and does not knowingly collect children's personal data.

## Changes and contact

Material changes to this policy will be published with an updated date. Privacy questions can be submitted through the public project repository:

https://github.com/tezalaaddin/aladin-CCTV-CamView-TV/issues

---

# Aladin CCTV Gizlilik Politikası

Son güncelleme: 26 Temmuz 2026

Aladin CCTV, yerel ağ üzerinde çalışan bir kamera izleme uygulamasıdır. Uygulama Android cihazdan kullanıcının seçtiği IP kameralara doğrudan bağlanır. Geliştirici kamera yayınları için bir bulut hizmeti işletmez; uygulamada reklam veya analiz SDK'sı bulunmaz.

Uygulama; kamera adı, yerel IP adresi, RTSP/ONVIF kullanıcı bilgileri, yayın adresleri, UUID/MAC gibi kamera kimlikleri, uygulama tercihleri ve anlık görüntüleri cihazda saklayabilir. Kamera kimlik bilgileri Android Keystore anahtarıyla şifrelenir; uygulama PIN'i salt eklenmiş tek yönlü hash olarak saklanır. Bulut ve cihaz aktarım yedekleri kapalıdır. Dışa aktarılan yapılandırmalar kamera kullanıcı adı/parolalarını ve uygulama PIN'ini içermez.

Uygulama yerel ağdaki kameralarla RTSP, ONVIF, HTTP, mDNS ve WS-Discovery gibi protokollerle iletişim kurar. Bazı kameralar şifrelenmemiş yerel HTTP veya RTSP bağlantıları kullanabilir. Kamera ve yerel ağ güvenliğinden kullanıcı sorumludur. Uygulama kamera yayınlarını, kimlik bilgilerini veya kullanım verilerini geliştiriciye göndermez.

Kullanıcılar kameraları silebilir, Android ayarlarından uygulama verilerini temizleyebilir, uygulamayı kaldırabilir ve otomatik ağ kurtarma/açılış/bakım seçeneklerini kapatabilir. Gizlilik soruları proje deposundaki Issues alanından iletilebilir.
