# TV Launcher — Coocaa / Google TV

Launcher Android TV custom (Kotlin) untuk Coocaa SWTV-24AE-FHD (Google TV 14), dibangun sesuai kebutuhan pemiliknya.

## Fitur

- Jam besar + sapaan, tanggal, dan ramalan cuaca 4 hari (Open-Meteo) di atas wallpaper foto kota
- Wallpaper per kota (10 kota Indonesia × varian cerah/berawan/hujan/malam) mengikuti cuaca & waktu
- Kartu cuaca + kualitas udara (AQI & PM2.5)
- Jadwal sholat + hitung mundur waktu berikutnya (Aladhan)
- **Peringatan gempa BMKG**: kartu gempa terakhir + pop-up overlay saat menonton aplikasi lain, eskalasi layar penuh untuk gempa besar (data BMKG TEWS)
- Cermin notifikasi **InaEEWS** menjadi alert TV (Notification Listener)
- Kartu gunung api (VONA / MAGMA PVMBG)
- Dock aplikasi melayang (glassmorphism) — menampilkan semua aplikasi TV yang terinstall
- Pengaturan dua panel bergaya kaca: bahasa (Indonesia/English), lokasi, ambang pop-up gempa, izin overlay & notifikasi
- Kontrol dari HP lewat Wi-Fi yang sama (server lokal + QR pairing): ganti kota/bahasa/ambang, uji pop-up gempa

## Instalasi

Lihat [DEPLOY.md](DEPLOY.md) — ringkasnya: kirim APK ke TV, install lewat ADB dari Termux di HP
(`adb install tv-launcher.apk`), aktifkan izin overlay + akses notifikasi, lalu uji pop-up dari Pengaturan.

APK versi terbaru ada di halaman **Releases** repo ini.

## Build

```
./gradlew :app:assembleDebug
```

Package: `id.bestie.tvlauncher` • minSdk 26 • targetSdk 34
