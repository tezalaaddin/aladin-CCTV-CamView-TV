# Aladin CCTV v1.4.0

Bu sürüm, Hikvision ağ kayıt cihazları için ilk NVR ve geçmiş kayıt desteğini ekler.

## Yenilikler

- Hikvision DS-7616NI-Q1 ve DS-7104NI-Q1/4P/M kayıt cihazı profilleri.
- Ping yanıtı vermeyen cihazlar için HTTP, RTSP ve üretici portlarına dayalı geliştirilmiş ağ keşfi.
- Keşfedilen NVR adaylarını kayıt cihazı kurulum ekranına yönlendirme.
- Hikvision ISAPI Digest kimlik doğrulamasıyla cihaz bilgisi ve aktif kanal keşfi.
- Tek NVR altında birden fazla kanalın ana ve alt RTSP akışlarıyla kaydedilmesi.
- NVR kanallarının normal IP kameralarla birlikte ana izleme ekranında gösterilmesi.
- Kanal ve tarih bazında NVR kayıt araması ve LibVLC ile geçmiş kayıt oynatma.
- Kayıt araması kullanılamadığında NVR web arayüzünü açma seçeneği.
- Android TV kumandası ve dikey telefon ekranları için yeni NVR/kayıt arayüzleri.
- Room veritabanı şema v5 ve veri kaybetmeyen migrasyon.
- `ALADIN_NVR` ve `ALADIN_REPLAY` güvenli tanılama logları.

## Güvenlik ve veri

- Normal kameraların benzersiz IP kuralı korunur; aynı NVR IP'sini paylaşan kanallar ayrı NVR kanal kayıtlarında tutulur.
- NVR kimlik bilgileri ve kimlik bilgisi içeren RTSP adresleri Android Keystore tabanlı şifrelemeyle saklanır.
- Uygulama logları kullanıcı adı, parola veya playback URI içermez.

## Doğrulama

- Birim testleri, Android lint ve debug derlemeleri başarılıdır.
- Room 4→5 migrasyonu ve kanal benzersizliği Android TV üzerinde cihaz testiyle doğrulanmıştır.
- NVR ekranları Android 9 `armeabi-v7a` TV üzerinde D-pad ve açılış açısından test edilmiştir.
- Gerçek NVR kanal/replay kabul testi, hedef kayıt cihazının bulunduğu ağda ayrıca yapılmalıdır.
