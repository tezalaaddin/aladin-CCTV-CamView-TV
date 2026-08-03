# Aladin CCTV — NVR ve Geçmiş Kayıt Geliştirme Planı

> Başlangıç: 3 Ağustos 2026
>
> Öncelikli cihazlar: Hikvision DS-7616NI-Q1 (16 kanal), Hikvision DS-7104NI-Q1/4P/M (4 kanal PoE)
> Kural: Bir madde yalnız kodu, testi ve zorunlu dokümantasyonu tamamlandığında `[x]` yapılır. Gerçek NVR doğrulaması ayrıca işaretlenir.

## 1. Analiz ve tasarım

- [x] Mevcut keşif, ONVIF, Room ve LibVLC akışındaki NVR engellerini belirle.
- [x] Kayıt cihazını kameradan ayıran `Recorder` + `RecorderChannel` veri modelini tasarla.
- [x] Hikvision DS-7616NI-Q1 ve DS-7104NI-Q1/4P/M için canlı yayın ve kayıt oynatma yaklaşımını belirle.
- [ ] Gerçek cihazlarda kullanılan HTTP, RTSP ve ONVIF portlarını doğrula.

## 2. NVR keşfi ve ekleme

- [x] Ping yanıtına bağlı olmadan yerel ağda kamera/NVR portlarını tara.
- [x] ONVIF WS-Discovery, Hikvision SDK/HTTP/RTSP izlerinden model bilgisi alınabilen NVR adayını sınıflandır ve NVR formuna yönlendir.
- [x] IP adresi ve isteğe bağlı HTTP/RTSP portlarıyla manuel NVR ekleme akışı oluştur.
- [x] NVR kimlik bilgilerini loglara veya kimlik bilgili URL olarak yazmadan doğrula.
- [x] Aynı kayıt cihazının mükerrer eklenmesini IP + port ve seri numarasıyla engelle.

## 3. Veri katmanı

- [x] `RecorderEntity` ve `RecorderChannelEntity` tablolarını ekle.
- [x] Room şema sürümünü artır ve veri kaybetmeyen migration ekle.
- [x] DAO/repository işlemlerini ve kanal bazlı benzersizlik kuralını ekle.
- [x] Kamera tablosundaki IP benzersizliği kuralını değiştirmeden NVR kanallarının aynı IP’yi paylaşmasını sağla.
- [x] Migration ve benzersizlik davranışlarını test et.

## 4. Hikvision kanal keşfi ve canlı yayın

- [x] ISAPI ile cihaz bilgisi ve IP kanal listesini sorgula.
- [x] ONVIF profillerini video-source/kanal kimliğine göre grupla.
- [x] Hikvision `Streaming/Channels/{kanal}01` ana ve `{kanal}02` alt yayın yollarını üret.
- [ ] Yalnız oynatılabilir kanalları seçilebilir biçimde kullanıcıya göster.
- [x] Seçilen NVR kanallarını mevcut ana kamera ızgarasına bağla.
- [x] DS-7616NI-Q1 için 16, DS-7104NI-Q1/4P/M için 4 kanal sınırlarını profil olarak ekle.

## 5. Geçmiş kayıt arama ve oynatma

- [x] Hikvision ISAPI `ContentMgmt/search` kayıt aramasını uygula.
- [x] Arama sonucundaki güvenli playback URI bilgisini ayrıştır.
- [ ] Destekleyen cihazlarda ONVIF Profile G kayıt/replay istemcisini ekle.
- [ ] Hikvision başarısızsa ONVIF Profile G’ye; o da başarısızsa açıklamalı web fallback’e geç.
- [x] ISAPI kayıt araması başarısız olduğunda açıklamalı NVR web arayüzü fallback'i sun.
- [x] LibVLC ile kayıt akışını oynat, durdur ve yeniden başlat.
- [ ] Tarih/saat aralığı, saat dilimi ve UTC dönüşümünü test et.

## 6. TV ve telefon kullanıcı arayüzü

- [x] Ayarlara “Kayıt cihazları” girişini ekle.
- [x] Premium NVR listesi, ekleme formu ve kanal seçim ekranını ekle.
- [x] Kamera tam ekranına kayıt cihazı kanalları için “Kayıtlar” eylemi ekle.
- [x] TV kumandasına uygun tarih seçimi, kayıt listesi ve zaman çizelgesi oluştur.
- [x] Telefon dokunmatik kullanımını ve dikey düzeni koru.
- [x] Tüm yeni ekranlara `TvFocusManager` uygula.

## 7. Tanılama, test ve dokümantasyon

- [x] `ALADIN_NVR` ve `ALADIN_REPLAY` güvenli log etiketlerini ekle.
- [ ] URL maskeleme, XML ayrıştırma, kanal yolu, kayıt aralığı ve fallback testlerini ekle. (Kanal yolu/model profili tamamlandı; cihaz yanıtı fixture testleri bekliyor.)
- [x] `testDebugUnitTest`, `assembleDebug` ve `lintDebug` çalıştır.
- [x] Gizli bilgi taraması yap.
- [x] `DOSYA_YAPISI_ve_TALiMATLAR.md` ve `README.md` dosyalarını güncelle.
- [ ] Hikvision DS-7616NI-Q1 üzerinde keşif, kanal ekleme, canlı yayın ve geçmiş kayıt testi yap.
- [ ] Hikvision DS-7104NI-Q1/4P/M üzerinde keşif, kanal ekleme, canlı yayın ve geçmiş kayıt testi yap.

## Kabul sınırı

Kod ve cihazdan bağımsız testlerin tamamlanması “uygulandı” anlamına gelir. Restorandaki cihazlarda kimlik doğrulamalı kayıt arama ve replay doğrulanmadan ilgili gerçek cihaz maddeleri tamamlandı olarak işaretlenmez.
