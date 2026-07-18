# PROJE DÖKÜMANTASYONU VE GELİŞTİRME REHBERİ

> **ÖNEMLİ TALİMATLAR:**
> 1. Bu dosya projenin güncel "kaynak dökümanı"dır. Projeye eklenen her yeni özellik, dosya veya fonksiyon mutlaka bu dökümana eklenmelidir.
> 2. Bir geliştirici bu dökümanı okuduğunda; projenin ne olduğunu, dosya yapısını ve hangi fonksiyonun ne işe yaradığını eksiksiz anlamalıdır.
> 3. Her güncelleme sonrası "Geliştirme Geçmişi" bölümüne yapılan değişikliklerin özeti eklenmelidir.
> 4. **Mimari Uyumluluk:** Proje MVVM (Model-View-ViewModel) mimarisini kullanır. Yeni eklenen her Activity/Fragment için mutlaka ilgili bir ViewModel oluşturulmalı ve dökümante edilmelidir.
> 5. **Donanım Odaklılık:** Android TV ve Box cihazlarındaki kısıtlamalar (RAM, CPU, Codec) göz önünde bulundurularak yapılan optimizasyonlar (H.264 zorlama, watchdog vb.) özel olarak belirtilmelidir.
> 6. **Modüler Tanımlama:** Yeni dosyalar eklenirken "Teknik Mimari" bölümünde ilgili katmana (UI, Data, Logic, Utils) uygun şekilde gruplandırılmalıdır.

---

## 1. Proje Tanımı
**AladinCamViewer (Aladin CCTV)**, Android TV ve mobil cihazlar için geliştirilmiş, yüksek performanslı bir IP Kamera (CCTV) izleme ve yönetim uygulamasıdır. 7/24 kesintisiz çalışma (Watchdog), ONVIF desteği, hibrit ağ tarama motoru ve akıllı marka tanıma gibi premium özellikler sunar.

---

## 2. Teknik Mimari ve Dosya Haritası

### 2.1. Uygulama Yaşam Döngüsü, Güvenlik ve Kararlılık (Core & Stability)
*   **`CctvApplication.kt`**: Uygulama giriş noktası. Watchdog kurulumunu başlatır.
*   **`CctvWatchdog.kt`**: Uygulamanın çökmesi durumunda 3-10 saniye içinde otomatik yeniden başlatılmasını sağlar. Gece 04:00 bakımlarını yönetir.
*   **`BootReceiver.kt`**: Cihaz açıldığında uygulamanın otomatik olarak başlamasını (Auto-start on boot) sağlar.
*   **`SecurityUtils.kt`**: PIN doğrulama ve şifreleme yardımcıları.
*   **`PreferenceHelper.kt`**: `EncryptedSharedPreferences` kullanarak hassas verileri (kamera şifreleri, PIN) saklar.

### 2.2. Sunum Katmanı (UI - Activities & ViewModels)
*   **`MainActivity.kt` & `MainViewModel.kt`**: Ana ızgara ekranı. 4'lü kamera görünümü ve sayfalama mantığını yönetir.
*   **`FullScreenCameraActivity.kt`**: Tekli kamera izleme. PTZ kontrolleri, dijital zoom ve "Tur Modu" burada yer alır.
*   **`DiscoveryActivity.kt`**: Radar animasyonu eşliğinde ağ tarama ekranı. `HybridScanner` sonuçlarını listeler.
*   **`SettingsActivity.kt` & `SettingsViewModel.kt`**: Uygulama ayarları, PIN yönetimi ve veri yedekleme.
*   **`EditCameraActivity.kt` & `EditCameraViewModel.kt`**: Manuel kamera ekleme/düzenleme ve ONVIF parametre onarımı.
*   **`WebPlaybackActivity.kt`**: Bazı kameraların RTSP üzerinden playback vermediği durumlarda web tabanlı izleme sağlayan fallback ekranı.
*   **`RadarView.kt`**: Tarama ekranındaki dairesel radar animasyonunu sağlayan custom view.

### 2.3. Video ve Kamera Yönetimi (Media & Control)
*   **`CctvPlayerManager.kt`**: ExoPlayer motoru. Düşük gecikmeli RTSP akışları için TCP protokolünü ve donanım hızlandırmayı zorlar.
*   **`PtzManager.kt`**: ONVIF SOAP protokolü ile kamerayı hareket ettirme (Pan, Tilt, Zoom) işlemlerini yapar.
*   **`CameraConfigManager.kt`**: **Kritik TV Optimizasyonu.** Kameraların (örneğin Uniview/AJCloud) H.265 yerine TV'lerin daha kolay işleyebildiği H.264 formatında yayın yapmasını sağlayan CGI komutlarını gönderir.
*   **`SnapshotUtils.kt`**: Kumanda üzerindeki sarı tuşla tetiklenen, o anki ekran görüntüsünü galeriye kaydeden yardımcı sınıf.
*   **`CameraAdapter.kt`**: Grid görünümünde kameraların performanslı bir şekilde yüklenmesini ve kaynak yönetimini sağlar.

### 2.4. Akıllı Ağ ve Keşif (Networking)
*   **`HybridScanner.kt`**: ONVIF, mDNS ve ARP taramalarını birleştirerek ağdaki tüm kameraları bulur.
*   **`OnvifScanner.kt`**: WS-Discovery (UDP 3702) üzerinden ONVIF cihaz keşfi yapar.
*   **`BrandMatcher.kt`**: MAC adresi (OUI) ve ONVIF yanıtlarından kamera markasını (Dahua, Hikvision, XMeye vb.) tespit eder.
*   **`NetworkTracker.kt`**: Kameranın IP'si değiştiğinde UUID üzerinden yeni IP'yi bulup veritabanını güncelleyen "Auto-Reconnect" motoru.
*   **`NetworkMonitor.kt`**: Cihazın ağ bağlantısını anlık izler.

### 2.5. Veri Katmanı (Data & Models)
*   **`AppDatabase.kt` & `CameraDao.kt`**: Room veritabanı erişim noktaları.
*   **`CameraRepository.kt`**: Veritabanı işlemlerini ViewModel katmanına asenkron Flow olarak sunar.
*   **`CameraEntity.kt`**: Veritabanı tablosu.
*   **`CameraModel.kt` / `ConfigModel.kt` / `DiscoveryDevice.kt`**: Uygulama içinde kullanılan veri modelleri.

---

## 3. Öne Çıkan Özellikler (Features)

1.  **7/24 Watchdog:** Kesintisiz çalışma garantisi.
2.  **Auto-Start on Boot:** TV açıldığında doğrudan kamera ekranına geçiş.
3.  **Smart Codec Fix:** H.265 uyumsuzluğu olan TV'lerde kamerayı H.264'e otomatik zorlama.
4.  **DHCP IP Recovery:** Dinamik IP alan kameraları UUID ile takip etme.
5.  **Multi-Source Discovery:** ONVIF, mDNS ve ARP ile %99 bulma oranı.
6.  **Smart Focus Audio:** Odaklanan kameranın sesini otomatik açma.

---

## 4. Kullanılan Teknolojiler (Tech Stack)
- **Dil:** Kotlin + Coroutines & Flow
- **Video:** Media3 ExoPlayer (RTSP, H.264/H.265)
- **Veritabanı:** Room (SQLite)
- **Network:** OkHttp, NsdManager, Multicast UDP
- **UI:** Android TV Leanback uyumlu Layoutlar, Custom Views

---

## 5. Geliştirme Geçmişi

### [2024-07-18] Ağ Tarama ve Cihaz Keşfetme (Premium Upgrade)
- Hibrit tarama (ONVIF + mDNS + ARP) eklendi.
- MAC OUI tabanlı marka tanıma motoru kuruldu.
- Android TV özel Radar UI animasyonu eklendi.

### [2024-07-19] Ağ ve Performans Optimizasyonu
- **NSD Entegrasyonu:** MAC kısıtlamasını aşmak için `NsdManager` eklendi.
- **Darboğaz Koruması:** `Semaphore(20)` ile Ping Sweep optimize edildi.
- **CPU Tasarrufu:** `NetworkTracker` event-driven yapıya dönüştürüldü.
- **Fail-Safe Batch Add:** Toplu eklemede `SupervisorJob` ile hata toleransı sağlandı.
- **Kapsamlı Dökümantasyon:** Proje haritası ve fonksiyon detayları `DOSYA_YAPISI_ve_TALiMATLAR.md` dosyasına işlendi.

### [2024-07-20] Mimari Düzenleme ve Eksiklerin Giderilmesi
- Dökümantasyon MVVM yapısına göre yeniden düzenlendi.
- `BootReceiver`, `CameraConfigManager`, `SnapshotUtils` ve `WebPlaybackActivity` gibi kritik dosyalar dökümana eklendi.
- "Önemli Talimatlar" bölümü mimari ve donanım odaklı kurallarla güncellendi.

---
*Bu döküman her geliştirmede yukarıdaki formatta güncellenmeye devam etmelidir.*
