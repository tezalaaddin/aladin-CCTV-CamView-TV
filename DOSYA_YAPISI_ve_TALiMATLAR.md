# PROJE DÖKÜMANTASYONU VE GELİŞTİRME REHBERİ

> **ÖNEMLİ TALİMATLAR (MUTLAKA OKUNMALI VE UYULMALIDIR):**
> 1. **Kendi Kendini Güncelleyen Döküman:** Bu dosya projenin "Beyni" ve "Haritası"dır. Geliştirici (İnsan veya Yapay Zeka), bu dökümanı her işlem öncesi okumalı ve yaptığı/değiştirdiği tüm özellikleri, fonksiyonları ve dosya yapılarını **anlık olarak** bu dökümana işlemelidir. Bu dosya projenin güncel "kaynak dökümanı"dır. Projeye eklenen her yeni özellik, dosya veya fonksiyon mutlaka bu dökümana eklenmelidir. Bir geliştirici bu dökümanı okuduğunda; projenin ne olduğunu, dosya yapısını ve hangi fonksiyonun ne işe yaradığını eksiksiz anlamalıdır. Her güncelleme sonrası "Geliştirme Geçmişi" bölümüne yapılan değişikliklerin özetini eklemek zorunludur.
> 2. **Talimat Mirası:** Bu talimatları okuyan her varlık, bu kuralları kendi çalışma prensiplerine (system prompt/instruction) dahil etmeli ve dökümana sadık kalmalıdır.
> 3. **MVVM ve Temiz Kod:** Proje MVVM mimarisini kullanır. Yeni eklenen her bileşen bu mimariye uygun olmalı, ilgili ViewModel ve Repository bağlantıları dökümana eklenmelidir.
> 4. **Donanım ve TV Optimizasyonu:** Uygulama Android TV önceliklidir. RAM, CPU ve Codec (H.264 zorlama vb.) optimizasyonları dökümanda özel olarak vurgulanmalıdır.
> 5. **UX ve Görsel Standartlar:** Kullanıcı deneyimi (Loading state, Adaptive UI, Focus management) her zaman en üst seviyede tutulmalı, yapılan görsel iyileştirmeler "Öne Çıkan Özellikler" kısmına eklenmelidir.
> 6. **Modüler Tanımlama:** Yeni dosyalar eklenirken "Teknik Mimari" bölümünde ilgili katmana (UI, Data, Logic, Utils) uygun şekilde gruplandırılmalıdır.

---

## 1. Proje Tanımı
**AladinCamViewer (Aladin CCTV)**, Android TV ve mobil cihazlar için geliştirilmiş, yüksek performanslı bir IP Kamera (CCTV) izleme ve yönetim uygulamasıdır. 7/24 kesintisiz çalışma (Watchdog), ONVIF desteği, hibrit ağ tarama motoru ve akıllı marka tanıma gibi premium özellikler sunar.

---

## Github Adresi:
**https://github.com/tezalaaddin/aladin-CCTV-CamView-TV**

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
*   **`DiagnosticActivity.kt`**: Sorunlu RTSP akışlarını test etmek için kullanılan teknik tanı ekranı (Ayarlar altından erişilebilir).

### 2.3. Video ve Kamera Yönetimi (Media & Control)
*   **`CctvPlayerManager.kt`**: **Kritik Medya Motoru.** Standart Android oynatıcılarının (ExoPlayer) aksine, eksik metadata gönderen kameraları açabilmek için **LibVLC (VLC Engine)** kullanır.
*   **`PtzManager.kt`**: ONVIF SOAP protokolü ile kamerayı hareket ettirme (Pan, Tilt, Zoom) işlemlerini yapar.
*   **`CameraConfigManager.kt`**: Kameraların (örneğin Uniview/Redline) SmartCodec/U-Code gibi özellikleri kapatmasını sağlayan profesyonel CGI/LAPI komutlarını yönetir.
*   **`SnapshotUtils.kt`**: Kumanda üzerindeki sarı tuşla tetiklenen, o anki ekran görüntüsünü galeriye kaydeden yardımcı sınıf.
*   **`CameraAdapter.kt`**: Grid görünümünde kameraların performanslı bir şekilde yüklenmesini ve `VLCVideoLayout` kaynak yönetimini sağlar.

### 2.4. Akıllı Ağ ve Keşif (Networking)
*   **`HybridScanner.kt`**: ONVIF, mDNS ve ARP taramalarını birleştirerek ağdaki tüm kameraları bulur.
*   **`OnvifScanner.kt`**: WS-Discovery (UDP 3702) üzerinden ONVIF cihaz keşfi yapar.
*   **`BrandMatcher.kt`**: MAC adresi (OUI) ve ONVIF yanıtlarından kamera markasını (Dahua, Hikvision, XMeye vb.) tespit eder.
*   **`NetworkTracker.kt`**: Kameranın IP'si değiştiğinde UUID üzerinden yeni IP'yi bulup veritabanını güncelleyen "Auto-Reconnect" motoru.
*   **`NetworkMonitor.kt`**: Cihazın ağ bağlantısını anlık izler.
*   **`LocaleHelper.kt`**: Uygulama dilini çalışma zamanında değiştirmek ve yerelleştirme (Localization) ayarlarını uygulamak için yardımcı sınıf.

### 2.5. Veri Katmanı (Data & Models)
*   **`AppDatabase.kt` & `CameraDao.kt`**: Room veritabanı erişim noktaları.
*   **`CameraRepository.kt`**: Veritabanı işlemlerini ViewModel katmanına asenkron Flow olarak sunar.
*   **`CameraEntity.kt`**: Veritabanı tablosu.
*   **`CameraModel.kt` / `ConfigModel.kt` / `DiscoveryDevice.kt`**: Uygulama içinde kullanılan veri modelleri.

### 2.6. Görsel Kaynaklar ve UI Bileşenleri (Resources)
*   **`led_online.xml` / `led_offline.xml`**: Kamera bağlantı durumunu gösteren, parlama efektli dinamik LED göstergeleri.
*   **`camera_item_selector.xml`**: TV kumandası odağını yöneten gelişmiş görsel çerçeve.
*   **`item_camera.xml`**: Grid görünümündeki her bir kamera hücresinin tasarımı (`VLCVideoLayout` entegreli).


---

## 3. Öne Çıkan Özellikler (Features)

1.  **7/24 Watchdog:** Kesintisiz çalışma garantisi.
2.  **Auto-Start on Boot:** TV açıldığında doğrudan kamera ekranına geçiş.
3.  **VLC Playback Engine:** Standart oynatıcıların (ExoPlayer) "missing sprop" hatası vererek reddettiği Aselsan ve Redline (Uniview) kameraları dahi sorunsuz açabilme yeteneği.
4.  **DHCP IP Recovery:** Dinamik IP alan kameraları UUID ile takip etme.
5.  **Multi-Source Discovery:** ONVIF, RTSP, mDNS ve ARP ile %99 bulma oranı.
6.  **Smart Focus Audio:** Odaklanan kameranın sesini otomatik açma.
7.  **Dynamic Localization:** İlk açılışta dil seçimi ve ayarlardan anlık dil değiştirme (TR/EN) desteği.

---

## 4. Kullanılan Teknolojiler (Tech Stack)
- **Dil:** Kotlin + Coroutines & Flow
- **Video:** LibVLC (v3.6.5) - RTSP, H.264, H.265 (HEVC) desteği.
- **Veritabanı:** Room (SQLite)
- **Network:** OkHttp (CGI/LAPI), NsdManager, Multicast UDP
- **UI:** Android TV Leanback uyumlu Layoutlar, Custom Views

---

## 5. Geliştirme Geçmişi

### [2024-07-23] Final RTSP Fix & LibVLC Integration (The "Aselsan/Redline" Victory)
- **Mimari Değişikliği:** ExoPlayer'ın "missing sprop" (eksik metadata) takıntısı nedeniyle bazı kameraları açamaması sorunu, projenin tamamı **LibVLC** motoruna geçirilerek çözüldü.
- **Aselsan & Redline Desteği:** Standart Android oynatıcılarının reddettiği bu markaların H.265 ve H.264 yayınları artık VLC kalitesinde ve hatasız açılıyor.
- **VLCVideoLayout Entegrasyonu:** `item_camera.xml` ve `activity_full_screen.xml` içindeki `PlayerView` bileşenleri, VLC'nin native video render katmanı olan `VLCVideoLayout` ile değiştirildi.
- **Hibrit Hata Yönetimi:** `CctvPlayerManager` tamamen VLC API'lerine göre yeniden yazıldı; donanım hızlandırma ve düşük gecikme (network-caching=500ms) optimizasyonları yapıldı.
- **Diagnostic Panel:** Tanı amaçlı kullanılan `DiagnosticActivity`, geliştirme sonrası ana akıştan kaldırılarak Ayarlar (Settings) ekranının en altına teknik bir buton olarak eklendi.
- **Kapsamlı Dökümantasyon Güncellemesi:** Tüm teknik mimari ve özellik listesi VLC motoruna göre revize edildi.

### [2024-07-26] Modern PTZ UI, D-pad Focus Management & Stability (The "Pro" Release)
- **8-Yönlü PTZ Kontrolü:** `FullScreenCameraActivity` içine, gönderilen referans görseline sadık kalınarak modern bir PTZ arayüzü eklendi. Sadece 4 ana yön değil, çapraz yönler (Sol-Üst, Sağ-Alt vb.) için de tam ONVIF desteği (`PtzManager.kt`) sağlandı.
- **Entegre Optik Zoom:** PTZ dairesel panelinin altına görsel olarak şık **(+)** ve **(-)** zoom butonları eklendi. Bu butonlar hem dokunmatik hem de kumanda tetiklemeli çalışır hale getirildi.
- **Akıllı D-pad Navigasyonu:** Kumanda kullanıcıları için kritik bir UX iyileştirmesi yapıldı: 
    - PTZ kapalıyken yön tuşları sağ paneldeki fonksiyonel butonlar arasında gezer. 
    - PTZ aktif edildiğinde kumanda odağı doğrudan kameraya geçer ve yön tuşları kamerayı fiziksel olarak hareket ettirir. 
    - **GERİ** tuşuna basıldığında PTZ modundan çıkılır ve odak butonlara geri döner.
- **Açılış Kararlılığı (Crash Fix):** `EncryptedSharedPreferences` bileşeninin Android `attachBaseContext` aşamasında henüz hazır olmamasından kaynaklanan "need an Android context" çökmesi, ayarların "Hassas (PIN)" ve "Genel (Dil)" olarak ayrıştırılmasıyla çözüldü (`PreferenceHelper.kt`).
- **Tam Yerelleştirme (Localization):** Uygulamanın en derin menüleri dahil (Ağ tarama sonuçları, NVR yuva isimleri, diyaloglar) %100 Türkçe ve İngilizce desteğine kavuşturuldu.
- **Release Hazırlığı:** Proje, gereksiz debug kodlarından arındırıldı ve hatasız bir `assembleRelease` derlemesi için tüm kaynak (`tint`, `style`) hataları giderildi.
- **Yeni Dosyalar:**
    - `ptz_btn_bg.xml`: PTZ butonları için modern ripple efektli arkaplan.
    - `styles.xml`: `PtzBtn` merkezi stil tanımı eklendi.

---
*Bu döküman her geliştirmede yukarıdaki formatta güncellenmeye devam etmelidir.*
