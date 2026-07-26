# AladinCamViewer — Proje Haritası ve Geliştirme Talimatları

> Son doğrulama: 26 Temmuz 2026
>
> Belge kapsamı: Güncel `master` çalışma ağacı; kesin sürüm için `git log` kullanılır
>
> Kaynak kod deposu: https://github.com/tezalaaddin/aladin-CCTV-CamView-TV

Bu belge, projeye ilk kez giren bir insanın veya yapay zekâ aracının uygulamanın amacını, çalışma biçimini, önemli dosyalarını, veri akışlarını ve değişiklik kurallarını tek yerden anlayabilmesi için hazırlanmıştır. Kod her zaman nihai doğruluk kaynağıdır; bu belge kodla çelişirse kod incelenmeli ve belge aynı değişiklik içinde düzeltilmelidir.

---

## 1. Zorunlu çalışma talimatları

Projede değişiklik yapacak herkes aşağıdaki sırayı izlemelidir:

1. Bu dosyanın tamamını, `README.md`, `app/build.gradle.kts` ve değiştirilecek sınıfları oku.
2. Mevcut davranışı ve değişikliğin UI, ViewModel, veri, ağ, medya ve yaşam döngüsü etkilerini belirle.
3. Android TV kumandası, odak yönetimi, düşük RAM/CPU kapasitesi ve uzun süreli çalışma koşullarını hesaba kat.
4. Parola, PIN veya kullanıcı bilgisi içeren RTSP/HTTP adreslerini kaynak koda, örnek veriye, teste, loga veya commit mesajına yazma. Loglarda URL yerine maskeleme veya yalnızca `IP:port` kullan.
5. Yeni kamera ekleme veya IP değiştirme işlemlerinde benzersiz IP kuralını koru. Veritabanı kısıtını UI kontrolünün yerine geçecek şekilde kaldırma.
6. DHCP kurtarmada yalnızca geçerli UUID veya MAC eşleşmesini otomatik kimlik kanıtı say. Aynı kullanıcı/parola/yol yalnızca yayın uyumluluğunu kanıtlar; fiziksel kamera kimliğini kanıtlamaz ve kullanıcı onayı gerektirir.
7. Room şeması değişirse sürümü artır, geri dönüşü olmayan veri kaybı yaratmayan açık bir `Migration` ekle ve geçişi test et. `fallbackToDestructiveMigration` kullanma.
8. Ağ ve LibVLC işlemlerini ana UI iş parçacığında çalıştırma. Coroutine yaşam döngüsünü ilgili Activity/ViewModel veya uygulama kapsamına bağla.
9. Yeni loglar `ALADIN_*` etiketi kullanmalı. Geliştirme aşamasında tanı için yeterli bağlam verilmeli; kimlik bilgileri kesinlikle loglanmamalı.
10. Yeni veya değişen davranış için mümkünse birim testi ekle. En azından `testDebugUnitTest`, `assembleDebug` ve `lintDebug` çalıştır.
11. TV’ye özgü değişiklikleri gerçek cihazda D-pad, yeniden bağlanma ve oynatma açısından dene.
12. Kod, dosya, bağımlılık, veritabanı, kullanıcı akışı veya çalışma kuralı değiştiğinde bu belgeyi aynı commit içinde güncelle. En alta tarihli geliştirme kaydı ekle.
13. Commit öncesinde `git diff`, test sonuçları ve gizli bilgi taramasını kontrol et. IDE’ye veya yerel cihaza özgü dosyaları commit etme.

### Belgeleme standardı

- Her yeni Kotlin dosyası Bölüm 5’te doğru gruba eklenmelidir.
- Yeni önemli public fonksiyon için amacı, girdisi, çıktısı/yan etkisi ve çağıranı belirtilmelidir.
- Yeni kullanıcı akışı Bölüm 4’e; yeni log etiketi Bölüm 8’e; yeni test Bölüm 9’a eklenmelidir.
- Pazarlama iddiası gibi doğrulanmamış oranlar ve “garanti”, “hatasız”, “tüm kameralar” ifadeleri kullanılmamalıdır.
- Geçici geliştirme kararı ile kalıcı mimari karar açıkça ayrılmalıdır.

---

## 2. Uygulama nedir?

**AladinCamViewer (Aladin CCTV)**, ağırlıklı olarak Android TV için geliştirilen bir yerel ağ IP kamera izleme ve yönetim uygulamasıdır. Android 7.0+ (`minSdk 24`) cihazlarda çalışır; TV kumandası ve dokunmatik olmayan cihazlar desteklenir.

Başlıca yetenekler:

- Dört kameralı, sayfalı ana izleme ızgarası ve tek kamera tam ekran görünümü
- LibVLC ile RTSP/H.264/H.265 oynatma, kontrollü tekrar deneme ve bağlantı durumu
- ONVIF, mDNS, port/ping ve yerel ağ verilerini birleştiren kamera keşfi
- UUID/MAC tabanlı DHCP IP değişikliği kurtarma
- Kimliği olmayan eski kayıtlarda RTSP doğrulamasından sonra kullanıcı onayı
- Aynı IP adresinin mükerrer kamera olarak kaydedilmesini engelleme
- ONVIF PTZ, optik zoom ve tur modu
- Kamera ayarları, yapılandırma içe/dışa aktarma ve teknik tanılama
- Türkçe/İngilizce arayüz, açılışta otomatik başlatma ve günlük bakım planı

Uygulama bir bulut/NVR sunucusu değildir. Kamera akışlarına cihazdan doğrudan yerel ağ üzerinden bağlanır. HLS veya WebRTC dönüştürme sunucusu şu an yoktur. `WebPlaybackActivity`, kameranın kendi web arayüzüne yönelik yardımcı/fallback ekrandır; RTSP’yi HLS/WebRTC’ye dönüştürmez.

---

## 3. Teknoloji, derleme ve dağıtım özeti

| Alan | Mevcut seçim |
|---|---|
| Dil | Kotlin 2.2.10, Coroutines, Flow |
| Android | compile SDK 37, target SDK 36, min SDK 24, Java/JVM 11 |
| Yapı | Tek `app` modülü; Activity + ViewModel + Repository yaklaşımı |
| Video | LibVLC `3.6.5` (`libvlc-all`) |
| Veri | Room 2.8.4, şema sürümü 4, KSP2 |
| Ağ | OkHttp 4.12.0, UDP WS-Discovery, Android NSD/mDNS, soket/ping taraması |
| UI | AppCompat/Material, XML layout, RecyclerView, özel `RadarView` |
| Serileştirme | kotlinx.serialization JSON |
| Güvenlik | AndroidX Security; geliştirme durumundaki mevcut yaklaşım Bölüm 10’da |
| Paket | `com.aladin.aladincamviewer`, versionName `1.3`, versionCode `4` |

LibVLC yerel ikili dosyaları APK boyutunun büyük bölümünü oluşturur. Bu nedenle `armeabi-v7a`, `arm64-v8a`, `x86` ve `x86_64` için ayrı APK üretilir; evrensel APK kapalıdır. AAB derlemesinde ABI split otomatik kapatılır; Google Play hedef cihaza uygun paketi AAB içinden üretir. Release derlemesinde R8 küçültme ve kaynak küçültme açıktır. Media3 bağımlılık sürümleri katalogda kalmış olsa da `app` modülünün aktif oynatma bağımlılıklarına eklenmemiştir.

---

## 4. Kritik çalışma akışları

### 4.1 Açılış ve ana ekran

1. Android `CctvApplication` sınıfını oluşturur ve günlük bakım/yeniden başlatma planını kurar.
2. `MainActivity`, `MainViewModel` üzerinden Room’daki kameraları gözlemler.
3. Kamera kayıtları `CameraModel` nesnelerine çevrilir ve sayfalı ızgaraya bağlanır.
4. Her görünür hücre için `CameraAdapter`, `CctvPlayerManager` ile LibVLC oynatmayı başlatır.
5. Odaklanan kameranın sesi açılır; geri kalan akışlar sessizde tutulur.
6. Ağ geri geldiğinde veya kullanıcı tetiklediğinde `NetworkTracker` kurtarma taraması yapabilir.

### 4.2 RTSP oynatma ve hata yönetimi

- `CctvPlayerManager` LibVLC örneğini, medya seçeneklerini, donanım hızlandırmayı, olay dinleyicisini ve kaynak bırakmayı yönetir.
- Bağlantı hatasında `RetryPolicy`, artan bekleme süreleriyle sınırlı yeniden deneme zamanlarını sağlar.
- `PlaybackStallDetector`, LibVLC `Playing` durumunda kalsa ve medya saati ilerlese bile gösterilen kare sayısı 25 saniye artmazsa donmayı belirler; `CctvPlayerManager` kontrollü yeniden bağlantı başlatır.
- Buffer logları yalnızca anlamlı yüzde eşiklerinde yazılır; böylece Logcat gereksiz yere dolmaz.
- Görünüm geri dönüştürüldüğünde veya Activity yok edildiğinde oynatıcı durdurulmalı ve VLC kaynakları serbest bırakılmalıdır.
- Bazı standart dışı RTSP kameralarında Media3/ExoPlayer oynatamadığı için ana motor LibVLC’dir. Gelecekte APK boyutunu daha da düşürmek için kamera bazlı Media3→LibVLC fallback düşünülebilir; ancak iki motoru birlikte paketlemek boyutu ve test matrisini artırır.

### 4.3 Kamera keşfi

1. `DiscoveryActivity`, `HybridScanner.startFullScan` çağırır.
2. Tarayıcı ONVIF WS-Discovery, mDNS/NSD, yerel subnet erişilebilirlik/port kontrolleri ve ARP/MAC bilgisini birleştirir.
3. Sonuçlar IP bazında tekilleştirilir ve `DiscoveryDevice` olarak yayınlanır.
4. `BrandMatcher`, OUI, yanıt içeriği ve port ipuçlarından marka tahmini yapar.
5. Kullanıcı toplu ekleme yaptığında aynı IP’nin kayıtlı olup olmadığı tekrar kontrol edilir.
6. Yalnızca ping yanıtı bir cihazı kamera veya kimlik kaynağı saymak için yeterli değildir.

### 4.4 DHCP IP kurtarma kararı

`NetworkTracker` 15 dakikada bir veya kullanıcı isteğiyle tarama yapar:

1. Kayıtlı IP’de RTSP/ONVIF cihaz bulunursa kamera çözülmüş sayılır; eksik/geçersiz UUID veya boş MAC güvenli biçimde tamamlanır.
2. IP değişmişse `CameraIdentityMatcher.strongMatch` geçerli UUID veya normalize MAC eşleşmesi arar. Tek ve güçlü eşleşmede IP ile ana/alt akış URL hostları otomatik güncellenir.
3. Kalıcı kimliği olmayan eski bir kayıtta marka uyumlu adaylar aranır ve `RtspStreamVerifier` ile kayıtlı yol/kimlik bilgileri kullanılarak yayın denenir.
4. Tek oynatılabilir aday varsa `RecoveryProposal` oluşturulur. `MainActivity` eski IP, yeni IP ve varsa MAC’i göstererek kullanıcı onayı ister.
5. Onay sırasında IP benzersizliği ve RTSP yayını yeniden doğrulanır; ardından kayıt güncellenir. Rette hiçbir veri değişmez.
6. Sıfır adayda veya birden fazla oynatılabilir adayda otomatik değişiklik yapılmaz ve durum loglanır.

Önemli ilke: aynı kullanıcı adı, parola ve stream yolu fiziksel kimlik değildir. Aynı marka/model kurulumlarda bu bilgiler ortak olabilir.

### 4.5 Veri kaydetme ve mükerrer IP önleme

- `CameraEntity.ipAddress` üzerinde benzersiz Room indeksi vardır.
- DAO insert stratejisi `ABORT` olduğundan var olan kaydın sessizce üzerine yazılmaz.
- UI, keşif ve içe aktarma akışları kayıt öncesi `CameraRepository.isIpAlreadyUsed` ile kontrol yapar.
- Düzenlemede mevcut kameranın kendi kimliği `excludeId` ile hariç tutulur.
- 2→3 migrasyonu eski mükerrer kayıtlarda en küçük ID’li kaydı tutar ve benzersiz indeksi oluşturur.

---

## 5. Kaynak ağacı ve dosya sorumlulukları

### 5.1 Proje kökü

- `settings.gradle.kts`: Modül ve repository tanımları.
- `build.gradle.kts`: Kök eklenti yapılandırması.
- `gradle/libs.versions.toml`: Merkezi bağımlılık ve eklenti sürümleri.
- `gradle.properties`: Gradle/KSP çalışma ayarları; KSP2 etkindir.
- `app/build.gradle.kts`: Android, ABI split, release küçültme ve uygulama bağımlılıkları.
- `app/proguard-rules.pro`: Release küçültme/obfuscation kuralları.
- `README.md`: Kurulum, derleme, güvenli örnekler ve hızlı Logcat kullanımı.
- `RELEASE_NOTES_v1.3.md`: v1.3 kullanıcıya dönük değişiklik özeti, uyumluluk, doğrulama ve dağıtım notları.
- `PRIVACY_POLICY.md`: Play Store için İngilizce/Türkçe gizlilik politikası.
- `PLAY_STORE_RELEASE_CHECKLIST.md`: İmzalama, mağaza beyanları, Android TV varlıkları ve test kanalı kontrol listesi.
- `LICENSE`, `THIRD_PARTY_NOTICES.md`: Proje ve üçüncü taraf lisans bildirimleri.
- `keystore.properties.example`: Git'e eklenmeyen release/upload anahtarı yapılandırmasının güvenli şablonu.
- `scripts/logcat-aladin.ps1`: Bağlı cihazdan yalnızca proje tanı etiketlerini gösteren PowerShell aracı.
- `.github/workflows/secret-scan.yml`: GitHub’a gönderilen içerikte kimlik bilgisi benzeri sırları tarayan CI işi.
- `.githooks/pre-commit`: Commit öncesi yerel gizli bilgi taramasını çalıştırır.

### 5.2 Uygulama, yaşam döngüsü ve yardımcılar

- `CctvApplication.kt`: Uygulama sınıfı; ortak başlangıç işlerini ve watchdog planını başlatır.
- `CctvWatchdog.kt`: `scheduleDailyRestart` ile gece bakım/yeniden başlatma alarmını planlar. Bu bir çökme yakalayıcı veya kesintisizlik garantisi değildir.
- `BootReceiver.kt`: `BOOT_COMPLETED` alındığında ana ekranı başlatır.
- `PreferenceHelper.kt`: Genel ayarlar, PIN ve alarm tercihlerine erişim sağlar.
- `SecurityUtils.kt`: `checkPin` ile ayarlara erişim öncesi salt eklenmiş hash üzerinden PIN doğrulaması yapar; sabit geçiş PIN'i yoktur.
- `CredentialCrypto.kt`: Android Keystore AES-GCM anahtarıyla kamera kimlik bilgilerini ve credential içeren yayın adreslerini veritabanında şifreler.
- `AppLog.kt`: Debug derlemelerinde veya kullanıcının açtığı tanılama modunda log üretir; normal release varsayılanında ağ/kamera ayrıntılarını susturur.
- `LocaleHelper.kt`: `setLocale` ile seçili TR/EN locale bağlamını üretir.
- `SnapshotUtils.kt`: `takeSnapshot` ile görünümden kare almayı, uygun yüzeyi bulmayı ve resmi saklamayı yönetir.
- `TvFocusManager.kt`: Tüm Activity’lerde etkileşimli öğeleri D-pad odağına hazırlar; odaklanan kart, buton, form alanı ve kontrol için turuncu çift halo, stroke, yükselme ve kısa ölçek animasyonu uygular. Yeni ekranlar `setContentView` sonrasında `TvFocusManager.install(this)` çağırmalıdır.

### 5.3 UI ve ViewModel katmanı

- `MainActivity.kt`: Ana ızgara, sayfalama, kumanda girişleri, ağ durumu ve DHCP kurtarma onay diyaloğunun sahibidir.
- `MainViewModel.kt`: Kamera Flow’unu UI modeline taşır; ana ekran veri durumunu yönetir.
- `CameraAdapter.kt`: Kamera hücrelerini bağlar, VLC oynatıcılarını başlatır/durdurur, odak/ses ve geri dönüşüm kaynaklarını yönetir.
- `FullScreenCameraActivity.kt`: Tek kamera oynatma, D-pad girişleri, dört ana yönlü PTZ modu, optik zoom, tur ve saat davranışı.
- `CameraConfigurationResolver.kt`: Discovery ve düzenleme ekranlarının ortak kamera kurulum hattı; önce seçili marka profilinin ana/alt RTSP yayınlarını doğrular, ONVIF’i bağımsız metadata/PTZ kaynağı olarak çalıştırır ve marka yolları başarısızsa doğrulanmış ONVIF stream URI’lerini yedek olarak kullanır.
- `RtspEndpointVerifier.kt`: RTSP `DESCRIBE` ile Basic/Digest kimlik doğrulaması ve yayın yolu kontrolü yapar; loglarda kullanıcı adı/parola yerine yalnız güvenli endpoint bilgisini kullanır.
- `CameraBrandProfiles.kt`: Hikvision, Dahua, Tiandy, Uniview, Reolink, Axis, Hanwha, Vivotek, Foscam, Tapo, AJCloud ve XMeye için bilinen ana/alt yayın yollarını merkezi olarak tanımlar.
- `SettingsActivity.kt`: Kamera yuvaları, dil seçimi, yapılandırma içe/dışa aktarma, tanı ve ayar ekranı olayları.
- `SettingsViewModel.kt`: Kamera listesi, PIN/offline alarm tercihleri ve JSON içe/dışa aktarma işlemleri.
- `EditCameraActivity.kt`: Kamera formu, marka/yol üretimi, silme, ortak ayar uygulama, ONVIF onarma ve mükerrer IP kontrollü kaydetme.
- `EditCameraViewModel.kt`: Kamera getirme/kaydetme/silme işlemlerini repository üzerinden yürütür; kontrol sonuçlarını UI’a bildirir.
- `DiscoveryActivity.kt`: Tarama ilerlemesi ve sonuç listesi; ortak kimlik bilgileriyle seçili kameraları toplu ekler ve mükerrer IP’leri atlar.
- `DiagnosticActivity.kt`: RTSP bağlantı seçeneklerini ve LibVLC olaylarını ayrıntılı loglayan geliştirme/tanı ekranı.
- `WebPlaybackActivity.kt`: Kameranın HTTP web sayfasını WebView içinde açan yardımcı ekran.
- `RadarView.kt`: Keşif ekranındaki radar animasyonunu çizer; görünüm eklenince animasyonu başlatır, ayrılınca durdurur.

### 5.4 Medya ve kamera kontrolü

- `CctvPlayerManager.kt`: LibVLC/MediaPlayer yaşam döngüsü, video layout bağlantısı, ağ cache’i, decoder seçimi, ses, olaylar, retry ve temizleme işlemleri. TV MediaCodec kilitlenmesini önlemek için ızgara alt akışları yazılımsal çözülür; tam ekran önce donanımı dener, decoder hatası veya kare donmasında yazılıma düşer.
- `RetryPolicy.kt`: `delayForAttempt` ile deneme numarasına karşılık gelen bekleme süresini verir; sınır aşılırsa `null` döner.
- `PlaybackStallDetector.kt`: Duvar saati ile öncelikle LibVLC `displayedPictures` sayacını karşılaştırır; hata olayı üretmeyen decoder/render donmalarını belirler. İstatistik yoksa medya saatine döner.
- `RtspStreamVerifier.kt`: `canPlay` ile görünür UI oluşturmadan kısa süreli LibVLC probe yapar; Playing durumunu kimlik bilgilerini loglamadan doğrular. `endpoint` log için güvenli uç bilgisi üretir.
- `PtzManager.kt`: ONVIF SOAP üzerinden sürekli pan/tilt/zoom komutları ve durdurma isteği gönderir.
- `OnvifManager.kt`: Cihaz/medya servislerini sorgular, profil ve stream URI bilgisini çıkarır, encoder ayarlarını standartlaştırmayı dener ve WS-Security digest üretir.
- `CameraConfigManager.kt`: Desteklenen markalarda SmartCodec/U-Code benzeri uyumsuzluk yaratan ayarları CGI/LAPI istekleriyle onarmayı dener.

### 5.5 Ağ, keşif ve kimlik

- `HybridScanner.kt`: `startFullScan` ile tüm keşif kaynaklarını paralel çalıştırır, sonuçları IP bazında birleştirir ve ilerleme callback’i verir. `extractUuid` yalnızca gerçek RFC UUID kabul eder.
- `OnvifScanner.kt`: `discoverDevices` ile UDP 3702 WS-Discovery Probe gönderir; IP, UUID ve marka ipuçlarını çıkarır.
- `BrandMatcher.kt`: `getBrandByMac`, `detectFromResponse` ve `detectFromPort` ile marka tahmini yapar.
- `CameraIdentityMatcher.kt`: `strongMatch` ile canonical UUID veya normalize MAC eşleşmesi; `isBrandCompatible` ile aday filtresi; `isValidUuid` ile RFC UUID doğrulaması yapar.
- `NetworkTracker.kt`: Periyodik/anlık tarama, kimlik zenginleştirme, güçlü kimlikte otomatik IP değiştirme ve eski kayıtta onay teklifi akışını yönetir.
- `RecoveryProposal` (`NetworkTracker.kt`): UI’a taşınan kamera ID/ad, eski-yeni IP, UUID, MAC ve marka verisi.
- `NetworkMonitor.kt`: Android bağlantı callback’leriyle yerel ağın kullanılabilirlik değişimlerini yayınlar.

### 5.6 Veri katmanı ve modeller

- `CameraEntity.kt`: Room’daki `cameras` tablosu; ad, benzersiz IP, kullanıcı/parola, ana/alt RTSP URL, marka, PTZ, sıra, UUID ve MAC alanları.
- `CameraDao.kt`: Kamera Flow’u, ABORT insert/insertAll, update/delete, ID sorgusu ve `countByIp` işlemleri.
- `CameraRepository.kt`: DAO’yu UI/ağ katmanlarından ayırır; CRUD ve `isIpAlreadyUsed` sağlar.
- `ConfigValidator.kt`: JSON içe aktarma öncesi boyut, IP, RTSP URL ve ekran sırası kurallarını doğrular.
- `AppDatabase.kt`: Room singleton, şema sürümü 4 ve `MIGRATION_1_2`/`MIGRATION_2_3`/`MIGRATION_3_4` geçişleri.
- `CameraModel.kt`: Oynatma/UI için kullanılan kamera modeli ve entity dönüşümü.
- `DiscoveryDevice.kt`: Keşfedilen IP, port/protokol, marka/model, UUID ve MAC bilgileri.
- `ConfigModel.kt`: JSON dışa/içe aktarma veri biçimi.

### 5.7 Kaynak dosyaları

- `res/layout/activity_main.xml`: Ana kamera ızgarası ve sayfa kontrolleri.
- `res/layout/item_camera.xml`: Her VLC kamera hücresinin görünümü.
- `res/layout/activity_full_screen.xml`: Tam ekran video; canlı durum/saati gösteren premium üst bar, büyük kumanda hedefli kamera araçları ve dört ana yön + zoom içeren yarı saydam PTZ paneli.
- `res/layout/activity_settings.xml`, `item_camera_slot.xml`: Ayarlar ve kamera yuvaları.
- `res/layout/activity_edit_camera.xml`: Kamera formu.
- `res/layout/activity_discovery.xml`, `item_discovery_card.xml`, `dialog_batch_credentials.xml`: Ağ keşfi ve toplu ekleme.
- `res/layout/activity_diagnostic.xml`: Teknik RTSP tanı ekranı.
- `res/layout/activity_web_playback.xml`: WebView fallback ekranı.
- `res/values/strings.xml`, `res/values-tr/strings.xml`: İngilizce/Türkçe metinler. Kullanıcıya gösterilen yeni metinler iki dosyaya da eklenmelidir.
- `res/drawable/camera_item_selector.xml`: TV odağı çerçevesi.
- `res/drawable-nodpi/aladin_cctv_logo.png`: Aladin Player marka ailesinden türetilen, diyafram/lens motifli özgün Aladin CCTV uygulama logosu; launcher, üst menü, splash ve TV banner tarafından kullanılır.
- `res/drawable/led_online.xml`, `led_offline.xml`: Bağlantı göstergeleri.
- `res/drawable/ptz_btn_bg.xml`, `res/drawable/premium_panel_background.xml`, `res/drawable/premium_top_bar.xml`, `res/color/premium_action_tint.xml`, `res/values/styles.xml`: Ana menü, tam ekran araçları ve PTZ kontrollerinin ortak premium görünüm/durum kaynakları.
- `res/xml/backup_rules.xml`, `data_extraction_rules.xml`: Android yedekleme kuralları.
- `AndroidManifest.xml`: İzinler, TV launcher, Activity ve boot receiver tanımları. Yerel RTSP/HTTP cihazları nedeniyle cleartext trafik şu an açıktır.

---

## 6. Veri modeli ve kalıcı kurallar

`CameraEntity` alanları:

| Alan | Amaç / kural |
|---|---|
| `id` | Otomatik artan yerel anahtar |
| `name` | Kullanıcıya görünen kamera adı |
| `ipAddress` | Kamera host/IP; veritabanında benzersiz |
| `username`, `password` | RTSP kimlik bilgileri; loglanmamalı |
| `onvifUsername`, `onvifPassword` | Ayrı ONVIF hesabı; boşsa RTSP kimlik bilgileri kullanılır |
| `mainStreamUrl`, `subStreamUrl` | Ana ve düşük kaynaklı RTSP akışları |
| `brand` | URL üretimi, onarım ve aday filtreleme ipucu |
| `ptzSupported` | Tam ekran PTZ kontrollerinin kullanılabilirliği |
| `displayOrder` | Izgaradaki yerleşim/sıra |
| `uuid` | Yalnızca geçerli RFC UUID; DHCP güçlü kimliği |
| `macAddress` | Normalize edilerek karşılaştırılan güçlü cihaz kimliği |

Kimlik alanları boş olabilir; eski kayıtlar aynı IP’de tekrar görüldüğünde güvenilir ONVIF/RTSP keşif bilgisinden tamamlanır. MAC/UUID yokken kimlik bilgileri ortak olabileceği için sessiz IP değişikliği yapılmaz.

---

## 7. Mimari sınırlar ve teknik borçlar

- Proje MVVM yaklaşımını kullanır ancak tüm sınıflar katman paketlerine ayrılmış değildir; Kotlin dosyaları tek pakettedir. Büyüme halinde `ui`, `data`, `network`, `media`, `domain` paketlerine kademeli geçiş önerilir.
- Bazı Activity’lerde iş kuralları bulunur. Yeni karmaşık mantık ViewModel/use-case benzeri test edilebilir sınıflara taşınmalıdır.
- LibVLC kamera uyumluluğunu artırırken APK boyutunu büyütür. ABI split mevcut çözümün ana boyut azaltımıdır.
- `usesCleartextTraffic=true`, yerel HTTP/RTSP kameraları için gereklidir ancak uygulama genelinde açıktır. Yayın öncesi network security config ile yerel ağ kapsamına daraltılması araştırılmalıdır.
- `WebPlaybackActivity` WebView güvenlik ayarları yayın öncesi ayrıca denetlenmelidir.
- Kamera parolalarının kalıcı saklanması ve yedekleme kapsamı yayın öncesi güvenlik çalışmasının parçasıdır. Geliştirme logları parola güvenliğinden bağımsız olarak hiçbir zaman sır içermemelidir.
- `exportSchema=false`; yayın öncesi Room şemalarını version control altında dışa aktarmak ve migration testleri eklemek önerilir.
- Ağ taraması çoğunlukla /24 subnet varsayımına dayanır; VLAN, birden fazla ağ arayüzü, IPv6 ve Android ARP erişim kısıtları ayrıca ele alınmalıdır.

---

## 8. Logcat ve tanılama sözleşmesi

Projeye ait filtreli log:

```powershell
.\scripts\logcat-aladin.ps1 -Device 192.168.1.54:5555
```

Kullanılan etiketler:

| Etiket | İçerik |
|---|---|
| `ALADIN_VLC` | Oynatma durumu, anlamlı buffer eşikleri, retry ve VLC hataları |
| `ALADIN_NETWORK` | Genel ağ kullanılabilirliği |
| `ALADIN_NETWORK_TRACKER` | DHCP tarama, kimlik eşleşmesi, teklif ve IP güncelleme |
| `ALADIN_DISCOVERY` | Hibrit keşif ve bulunan cihazlar |
| `ALADIN_DEBUG_ONVIF` | ONVIF istek/yanıt tanısı; sır içermemeli |
| `ALADIN_PTZ` | PTZ komutları ve sonuçları |
| `ALADIN_WATCHDOG` | Günlük bakım/alarm planı |
| `ALADIN_DIAG` | Teknik tanı ekranı |

Yeni etiket adları `ALADIN_` ile başlamalı. URL loglanacaksa kullanıcı/parola kaldırılmalı; tercihen yalnızca kamera adı, IP/port, olay, süre, deneme numarası ve hata sınıfı yazılmalıdır.

---

## 9. Test ve doğrulama

Yerel doğrulama:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

Mevcut birim testleri:

- `CameraIdentityMatcherTest.kt`: UUID/MAC normalizasyonu, geçersiz UUID reddi, kimliksiz eşleşmeme ve marka uyumu.
- `RetryPolicyTest.kt`: Retry gecikme dizisi ve sınır davranışı.
- `PlaybackStallDetectorTest.kt`: İlerleyen, sabit kalan, kullanılamayan ve sıfırlanan medya saati senaryoları.
- `CameraBrandProfilesTest.kt`: Marka eş adları, RTSP kimlik bilgisi kodlama ve aday URL tekilleştirme davranışı.
- `ExampleUnitTest.kt`: Temel örnek test; anlamlı proje testiyle değiştirilmesi önerilir.
- `ExampleInstrumentedTest.kt`: Temel cihaz testi; Room migration ve TV akışlarıyla genişletilmelidir.

TV kontrol listesi:

- APK doğru ABI için üretildi mi (`armeabi-v7a`, `arm64-v8a`, `x86` veya `x86_64`)?
- Ana/alt RTSP akışları açılıyor ve player kaynakları ekran geçişinde bırakılıyor mu?
- D-pad odağı tüm ekranlarda görünür ve geri dönüşlü mü?
- Odaklanan öğe; kamera görüntüsü, menü, buton, form alanı ve liste kartlarının her birinde turuncu halo/çerçeve ve yükselme ile açıkça ayırt ediliyor mu?
- Ağ kesilip geldiğinde retry ve kurtarma doğru mu?
- UUID/MAC eşleşmesi otomatik; kimliksiz eşleşme onaylı mı?
- Aynı IP ekleme UI’da ve veritabanında reddediliyor mu?
- Loglarda kullanıcı adı/parola veya kimlik bilgili URL var mı?
- Crash/ANR oluşuyor mu?

---

## 10. Güvenlik ve repository hijyeni

- Gerçek kamera URL’leri ve kimlik bilgileri Git geçmişinden temizlenmiştir.
- Yerel pre-commit ve GitHub Actions gizli bilgi taraması vardır.
- README/test/örneklerde yalnızca dokümantasyon için ayrılmış sahte IP ve kimlik bilgileri kullanılmalıdır.
- `.gitignore`, yerel IDE/derleme/kimlik dosyalarını dışarıda tutmalıdır.
- Parolaların uygulama içindeki nihai saklama modeli yayın öncesi ayrıca sertleştirilecektir; bu ertelenmiş karar, sırların loglanmasına veya Git’e eklenmesine izin vermez.
- Gizli bilgi yanlışlıkla commit edilirse yalnızca son commit’i silmek yeterli değildir: kimlik bilgisi değiştirilir, tüm geçmiş temizlenir ve uzak depo taranır.

---

## 11. Geliştirme geçmişi

### 23 Temmuz 2024 — LibVLC geçişi

- Eksik/standart dışı RTSP metadata nedeniyle Media3/ExoPlayer’da açılamayan bazı kameralar için ana oynatma motoru LibVLC’ye taşındı.
- VLC video layout, donanım çözme ve düşük gecikme seçenekleri eklendi.
- Teknik RTSP tanı ekranı ayarlar altına taşındı.

### 26 Temmuz 2024 — PTZ ve TV odağı

- Sekiz yönlü ONVIF PTZ ve optik zoom kontrolleri eklendi.
- PTZ açık/kapalı durumuna göre D-pad odak ve hareket davranışı düzenlendi.
- Türkçe/İngilizce yerelleştirme kapsamı genişletildi.
- Preference başlatma sırasındaki context çökmesi giderildi.

### Temmuz 2026 — Kararlılık, ağ ve dağıtım iyileştirmeleri

- RTSP yeniden deneme politikası, ağ durumu takibi ve ayrıntılı geliştirme logları eklendi.
- ONVIF/PTZ istekleri ve kamera yapılandırma işlemleri düzenlendi.
- LibVLC boyutunu azaltmak için ABI split (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`) etkinleştirildi; AAB ve split APK görevleri uyumlu biçimde ayrıldı.
- Gerçek RTSP parolaları Git geçmişinden temizlendi; yerel ve CI gizli bilgi taraması eklendi.
- Gradle 9.5.0, AGP 9.3.1, Kotlin 2.2.10, KSP2 2.3.2 ve Room 2.8.4 uyumluluğu sağlandı.

### 25 Temmuz 2026 — Kimlik tabanlı DHCP kurtarma ve mükerrer IP koruması

- Otomatik IP değişikliği geçerli UUID veya MAC eşleşmesiyle sınırlandı.
- Eski/kimliksiz kayıtlar için LibVLC RTSP probe ve kullanıcı onaylı `RecoveryProposal` akışı eklendi.
- Aynı kullanıcı/parola/stream yolunun fiziksel kamera kimliği olmadığı kuralı uygulandı.
- Yalnızca ping yanıtı veren cihazın kamera kabul edilmesi engellendi.
- ONVIF anonymous addressing değerlerinin UUID olarak saklanması engellendi; geçersiz UUID’ler temizlenip güvenilir kimlik bilgileri tamamlandı.
- `ipAddress` için benzersiz Room indeksi, ABORT insert ve 2→3 veri migrasyonu eklendi.
- Düzenleme, keşif, toplu ekleme ve config import akışlarında mükerrer IP kontrolleri eklendi.
- Buffer logları eşiklere indirildi, PTZ etiketi standartlaştırıldı ve `scripts/logcat-aladin.ps1` eklendi.
- `.37 → .55` gerçek DHCP senaryosu Android TV’de doğrulandı; onaylı güncelleme, MAC kaydı, benzersiz IP kısıtı ve iki akışın oynatılması test edildi.

### 25 Temmuz 2026 — Belge yeniden yapılandırması

- Dosya, uygulamayı tek başına anlamaya yetecek proje özeti, kritik akışlar, dosya/fonksiyon kataloğu, veri modeli, log sözleşmesi, test adımları, güvenlik kuralları ve teknik borçlarla yeniden düzenlendi.
- Ölçülmemiş başarı oranları ve garanti ifadeleri kaldırıldı; kodun nihai doğruluk kaynağı olduğu açıklandı.

### 25 Temmuz 2026 — Sessiz RTSP donma algılama

- LibVLC hata vermeden `Playing` durumunda kalan fakat gösterilen kare sayısı ilerlemeyen akışlar için `PlaybackStallDetector` eklendi.
- `displayedPictures` sayacı 25 saniye ilerlemediğinde mevcut kontrollü retry akışıyla RTSP oturumu yeniden başlatılır; sayaç alınamazsa medya saati fallback olarak kullanılır.
- Her 30 saniyede gösterilen/çözülen kare ve demux byte sayaçları `ALADIN_VLC` sağlık kaydına yazılır; donma `video_frames_stalled` nedeni ile loglanır.
- MediaCodec sürücü kilitlenmesi nedeniyle düşük çözünürlüklü ızgara alt akışları yazılımsal decoder’a alındı. Tam ekran akış önce donanımı kullanır; ilk decoder hatası veya kare donmasında otomatik olarak yazılımsal decoder’a düşer.
- Zaman damgası bozuk RTSP akışlarında LibVLC’nin varsayılan saat düzeltmesini devre dışı bırakan `clock-jitter=0` kaldırıldı.
- Gerçek Android TV testinde iki ızgara akışının gösterilen/çözülen kare sayaçlarının sürekli ilerlediği ve önceki yaklaşık 30 saniyelik decoder donmasının ortadan kalktığı doğrulandı.
- Aynı gerçek cihaz oturumunda DHCP ile IP’si değişen kamera, uygulama yeniden açıldığında UUID/MAC tabanlı kurtarma akışıyla otomatik olarak güncellendi.

### 25 Temmuz 2026 — Premium Android TV odak sistemi ve CCTV marka kimliği

- `TvFocusManager` ile ana ekran, tam ekran, ayarlar, kamera düzenleme, keşif, tanı ve web ekranlarında ortak D-pad odak dili oluşturuldu.
- Odak görünürlüğü; turuncu çift katmanlı halo, bileşen stroke’u, kısa ölçek animasyonu ve yükselme/gölge ile güçlendirildi.
- Kamera, kamera yuvası ve keşif kartlarına parent focus durumunu doğrudan çizen foreground selector eklendi.
- Aladin Player referansındaki A/play karakteri korunarak merkezinde kamera diyaframı bulunan özgün Aladin CCTV logosu üretildi.
- Yeni logo launcher/round icon, splash, Android TV banner ve ana ekran üst menüsüne uygulandı.
- Splash ekranında logo kare `260dp` alanda en-boy oranı korunarak ve TV izleme mesafesine uygun daha büyük boyutta gösterilir.
- Ana ekran üst barı daha güçlü marka hiyerarşisi, 54dp kumanda hedefleri, durum çerçeveleri ve monospace saat ile yenilendi.
- Tam ekran görünümüne canlı durum göstergesi, seçili kalabilen PTZ düğmesi ve işlevsel anlık görüntü düğmesi içeren kamera araçları paneli eklendi.
- PTZ paneli dört ana yönü uzaktan okunabilir, aynı standarttaki fiziksel düğme görünümüyle sunar; çapraz kontroller kaldırıldı. Zoom alanında aynı tasarımda `− / +` düğmeleri bulunur ve PTZ modunda kumandanın `Kanal − / Kanal +` (eşdeğer cihazlarda Page Down/Page Up) tuşları uzaklaştırma/yakınlaştırma yapar.
- Ağ keşif kartları model, MAC, firmware ve protokol alanlarını sabit örneklerden değil doğrudan `DiscoveryDevice` sonucundan gösterir. Kamera ekleme ekranı yalnız ONVIF, RTSP veya üretici SDK izi bulunan adayları listeler; ping-only modem/telefon/TV gibi genel ağ cihazlarını `ALADIN_DISCOVERY` loglarında tutar ancak kamera olarak sunmaz.
- Discovery toplu ekleme kullanıcı onayından önce marka profilindeki ana/alt RTSP yollarını ayrı ayrı sınar; aynı işlemde ONVIF `GetDeviceInformation`, `GetCapabilities`, `GetProfiles` ve `GetStreamUri` verilerini metadata/PTZ için toplar. Marka profili çalışmıyorsa ONVIF profilleri çözünürlüğe göre ana/alt yayın olarak sıralanıp doğrulanmış yedek olarak kullanılır.
- ONVIF kullanılamadığında merkezi marka profilleri sırayla denenir; yalnız kimlik doğrulamalı ve oynatılabilir yol kaydedilir. Sonuç özeti başarılı/başarısız kameraları kullanıcıya gösterir ve yalnız doğrulananlar eklenir.
- Kamera düzenleme ekranındaki “Keşfet ve doğrula” aynı çözümleyiciyi kullanır; “Kaydet” de mevcut veya yeniden bulunan ana RTSP yayınını doğrulamadan veritabanını güncellemez.
- Kamera düzenleme ve toplu kimlik bilgisi ekranları premium koyu panel, marka vurgusu, durum kartı ve TV odağına uygun büyük kontrol hedefleri kullanır.
- RTSP ve ONVIF hesapları ayrı olabilir. `CameraEntity` veritabanı v4 ile `onvifUsername/onvifPassword` alanlarını saklar; boş değerler “RTSP ile aynı” anlamına gelir. UI’da bu seçenek varsayılan açıktır, kapatıldığında ayrı ONVIF alanları görünür ve PTZ komutları bu hesabı kullanır.

### 26 Temmuz 2026 — v1.3 release hazırlığı

- Uygulama sürümü `versionName 1.3` ve `versionCode 4` olarak güncellendi.
- AAB derlemesinde ABI split otomatik kapatılarak dört ABI'nin tek bundle içinde paketlenmesi; APK derlemesinde ise dört ayrı mimari çıktısı üretilmesi sağlandı.
- `RELEASE_NOTES_v1.3.md` eklendi; README aktif LibVLC motoru, v1.3 özellikleri, ayrı RTSP/ONVIF hesapları ve güncel dağıtım mimarileriyle eşitlendi.
- Unit test, lint, R8 release derlemesi, ABI içeriği ve sabit kimlik bilgisi taraması başarıyla doğrulandı.
- Android Keystore ile kamera sırlarının şifrelenmesi, hash tabanlı PIN, yedekleme dışlama ve sır içermeyen config export eklendi.
- Config import doğrulama + Room transaction ile veri kaybına dayanıklı hale getirildi.
- Boot, otomatik IP kurtarma, günlük bakım ve release tanılama logları kullanıcı tercihine bağlandı.
- Ethernet TV görünürlüğü, AAB dil kaynakları, WebView host/file erişimi, Android 16 geri navigasyonu ve erişilebilirlik/lokalizasyon lint bulguları düzeltildi.
- 320×180 Android TV banner, gizlilik politikası, MIT lisansı, üçüncü taraf bildirimleri ve Play Store kontrol listesi eklendi.
- Debug uygulamasına `.debug` applicationId suffix verilerek cihaz testlerinin production paket verisini kaldırması engellendi.
