# Cara Install TV Launcher di TV Coocaa (Google TV)

File APK: **tv-launcher.apk** (v1.6, sekitar 17 MB). Semua langkah bisa
dilakukan dari HP + TV saja, tanpa PC.

## Update v1.6 (27 Okt 2026) — FILE DI ATAS SEKARANG v1.6

- **Semua 10 set wallpaper kota sekarang lengkap di dalam APK:**
  Jakarta, Bandung, Surabaya, Yogyakarta, Semarang, Medan, Makassar,
  Denpasar, Balikpapan, dan Tanah Grogot — masing-masing 4 varian
  (cerah, berawan, hujan, malam), total 40 gambar. Tidak ada lagi kota
  yang meminjam gambar Jakarta; pilih kota di Setelan → Umum → Lokasi
  dan wallpaper home langsung memakai pemandangan kota itu, berganti
  mengikuti cuaca & siang/malam seperti sebelumnya.
- **Sudah termasuk semua perubahan v1.5 di bawah** (kartu kaca
  glassmorphism, jam 112sp bold, ikon di tombol Input, kolom widget
  mulai di bawah tombol, aturan dock "semua aplikasi TV yang ada") —
  v1.5 tidak sempat terkirim sendiri, jadi v1.6 adalah paket lengkapnya.
- **Ukuran APK naik jadi ±17 MB** karena 40 gambar wallpaper kota.

⚠️ **Install dari v1.4 yang terpasang di TV: perlu uninstall sekali.**
Komputer kantor sempat ter-reset dan kunci tanda tangan debug-nya
terganti (v1.1–v1.4 memakai kunci lama `c19b151d…`; v1.5/v1.6 memakai
kunci baru `da531f3e…`). Karena tanda tangannya beda, `adb install -r`
dari v1.4 akan ditolak (INSTALL_FAILED_UPDATE_INCOMPATIBLE). Urutannya:

```
adb uninstall id.bestie.tvlauncher
adb install ~/storage/downloads/tv-launcher.apk
```

(Dari build v1.5 yang tidak terkirim, v1.6 bisa langsung `install -r`
karena tanda tangannya sama.)

**Checklist setelah install v1.6** (setelan ikut terhapus karena
uninstall):

1. Buka launcher, masuk Setelan (ikon gir) → Umum → Lokasi: pilih lagi
   kota kamu (mis. Bandung).
2. Setelan → Perlindungan Gempa: aktifkan lagi **Izin Tampil di Atas
   Aplikasi** sampai statusnya "Aktif".
3. Di menu yang sama, aktifkan lagi **Izin Akses Notifikasi
   (InaEEWS)** — atau dari Termux:
   `adb shell cmd notification allow_listener id.bestie.tvlauncher/id.bestie.tvlauncher.NotifListener`
4. Jadikan launcher ini default lagi (tombol Home → pilih TV Launcher
   → "Selalu"), atau dari Termux:
   `adb shell cmd role add-role-holder android.app.role.HOME id.bestie.tvlauncher`
5. Tekan **Uji Pop-up Gempa** untuk memastikan pop-up peringatan muncul.

## Update v1.5 (27 Okt 2026) — TIDAK TERKIRIM SENDIRI; SEMUA PERUBAHANNYA IKUT DI DALAM v1.6 DI ATAS

Revisi dari foto pemakaian v1.4:

- **Kartu-kartu di home jadi kaca transparan (glassmorphism).** Kartu
  cuaca, jadwal sholat, gempa BMKG, strip gunung api, dan chip ramalan
  sekarang memakai kaca putih tembus pandang dengan tepi terang dan
  sudut membulat — wallpaper terlihat menembus kartu. Tile dock memakai
  gaya kaca yang sama, sedikit lebih pekat agar fokus remote tetap
  jelas. Layar Setelan tidak berubah.
- **Jam lebih besar dan tebal.** Ukuran jam naik dari 88sp ke 112sp dan
  fontnya sekarang bold; tanggal ikut sedikit lebih besar.
- **Tombol Input sekarang ada ikonnya** (panah masuk ke kotak) di
  sebelah kiri tulisan INPUT, seukuran ikon gir di sebelahnya.
- **Kolom widget mulai di bawah tombol.** Kartu pertama sekarang mulai
  jelas di bawah baris tombol Input + gir di kanan atas, tidak lagi
  sejajar/menempel di belakang tombol.
- **Wallpaper kota bertambah: Surabaya.** Set gambar Surabaya (cerah,
  berawan, hujan, malam) sudah ikut dibundel, bergabung dengan Jakarta
  dan Bandung. Kota yang set-nya belum selesai dibuat (Yogyakarta,
  Semarang, Medan, Makassar, Denpasar, Balikpapan, Tanah Grogot)
  sementara tetap memakai gambar Jakarta.
- **ATURAN DOCK BERUBAH (sesuai permintaan terbaru).** Saringan ketat
  v1.4 membuat dock hanya berisi Play Store, karena aplikasi lain di TV
  adalah aplikasi sistem bawaan. Sekarang dock kembali diisi **semua
  aplikasi yang ada di TV** yang punya ikon buka dan nama yang wajar —
  aplikasi sistem ikut tampil lagi (Multimedia Player, CastPlayTV,
  Wi-Fi Display Sink, TV Wizard). Yang tetap tidak tampil: launcher
  bawaan Google TV, aplikasi Setelan sistem (Setelan tetap hanya lewat
  ikon gir), aplikasi launcher ini sendiri, dan tile rusak yang namanya
  berupa nama paket (mis. "com.walton.startupbroadcast...").

⚠️ **Catatan tanda tangan:** komputer kantor ter-reset dan kunci tanda
tangan debug-nya terganti, jadi APK v1.5 ini tanda tangannya BERBEDA
dari v1.4 yang terpasang di TV. Akibatnya `adb install -r` akan ditolak
(INSTALL_FAILED_UPDATE_INCOMPATIBLE) — untuk memasang v1.5 perlu
sekali lagi `adb uninstall id.bestie.tvlauncher` lalu install biasa,
dan setelah itu setel ulang kota + aktifkan lagi izin overlay & akses
notifikasi. (Catatan lanjutan: v1.5 akhirnya tidak terkirim sendiri —
penggantinya adalah v1.6 di atas, dengan syarat uninstall sekali yang
sama dan checklist setelah install yang sama.)

## Update v1.4 (27 Okt 2026)

Revisi tampilan dari foto pemakaian v1.3:

- **Seluruh UI diperkecil ke skala ±0,75** — jam, kartu widget, dock,
  tombol, dan layar Setelan semuanya mengecil proporsional, supaya pas
  di TV 24". (Layar peringatan gempa full-screen sengaja tetap besar.)
- **Pilihan kota sekarang berupa daftar.** Di Setelan → Umum → Lokasi,
  ketuk barisnya dan pilih kota dari daftar 10 kota (ada tanda centang
  radio di kota yang aktif) — tidak lagi berganti-ganti setiap ketukan.
  Pilihan Bahasa dan Ambang Pop-up Gempa juga sekarang berupa daftar
  pilihan yang sama.
- **Ramalan cuaca 4 hari di bawah tanggal.** Di kolom kiri home, di
  bawah hari & tanggal, sekarang ada strip ramalan: hari ini + 3 hari
  berikutnya, masing-masing dengan ikon cuaca (cerah, berawan, mendung,
  kabut, gerimis, hujan, hujan lebat, badai petir) dan suhu min–maks,
  dari Open-Meteo untuk kota yang dipilih.
- **Dock dibersihkan lebih ketat.** Aplikasi sistem bawaan TV sekarang
  disembunyikan dari dock — kecuali aplikasi tontonan yang memang
  dikenal: Play Store, YouTube, Netflix, Disney+, Prime Video, Vidio,
  WeTV, HBO, Twitch, Spotify. Cara ini akhirnya membuang CastPlayTV dan
  Wi-Fi Display Sink yang masih lolos di v1.3. Aplikasi yang kamu
  install sendiri tetap tampil seperti biasa.
- **Layar Setelan baru, dua panel** mengikuti contoh desain yang
  dikirim: panel kiri berisi kategori **Umum, Penampilan,
  Pemberitahuan, Perlindungan Gempa, Akun, Tentang** (sorotan
  cyan/teal), panel kanan berisi isinya. Bahasa & Lokasi ada di Umum;
  ambang gempa, izin overlay, izin notifikasi InaEEWS, suara
  peringatan, dan tombol besar **Uji Pop-up Gempa** ada di
  Perlindungan Gempa; kode QR Hubungkan HP ada di Akun. Navigasi
  remote: atas/bawah memilih, kanan masuk ke isi, kiri kembali ke
  kategori.

Cara update dari v1.3: kirim APK baru ke HP, lalu dari Termux jalankan
`adb install -r ~/storage/downloads/tv-launcher.apk`. Tanda tangan APK v1.4
**sama** dengan v1.3 (sudah diverifikasi), jadi `install -r` langsung
berhasil — pengaturan & izin tidak perlu diulang. Kalau nama file hasil
unduhan di HP jadi `tv-launcher-1.apk`, sesuaikan namanya di perintah.

## Update v1.3 (27 Okt 2026)

- **Wallpaper sekarang per kota.** Set gambar mengikuti kota yang dipilih
  di Setelan: **Jakarta** (pemandangan kota + Monas) dan **Bandung**
  (Gedung Sate + pegunungan) — masing-masing punya 4 varian cuaca dari
  pemandangan yang sama persis: cerah, berawan, hujan, malam. Kota lain
  untuk sementara memakai pemandangan Jakarta dulu. Cuaca & waktu tetap
  yang memilih variannya, seperti v1.2 (mendung/kabut = berawan + tint
  abu-abu; hujan malam = gambar hujan yang digelapkan).

Cara update dari v1.2: kirim APK baru ke HP, lalu dari Termux jalankan
`adb install -r ~/storage/downloads/tv-launcher.apk`. Tanda tangan APK v1.3
**sama** dengan v1.2 (sudah diverifikasi), jadi `install -r` langsung
berhasil — pengaturan & izin tidak perlu diulang. Kalau nama file hasil
unduhan di HP jadi `tv-launcher-1.apk`, sesuaikan namanya di perintah.

## Update v1.2 (27 Okt 2026)

- **Wallpaper sekarang foto pemandangan asli** (kota + Monas, gambar dasar
  yang disetujui), bukan lagi ilustrasi gambar datar. Ada 4 varian dari
  pemandangan yang sama persis, dipilih otomatis mengikuti cuaca & waktu:
  **cerah** (pagi cerah), **berawan**, **hujan** (hujan deras), dan
  **malam**. Mendung/kabut memakai varian berawan dengan tint abu-abu;
  hujan di malam hari memakai gambar hujan yang digelapkan.
- Animasi garis hujan lama dihapus — gambar hujan sudah menampilkan hujan.

Cara update dari v1.1: kirim APK baru ke HP, lalu dari Termux jalankan
`adb install -r ~/storage/downloads/tv-launcher.apk`. Tanda tangan APK v1.2
**sama** dengan v1.1 (sudah diverifikasi), jadi `install -r` langsung
berhasil — pengaturan & izin tidak perlu diulang. Kalau nama file hasil
unduhan di HP jadi `tv-launcher-1.apk`, sesuaikan namanya di perintah.

## Update v1.1 (27 Okt 2026)

Perbaikan dari foto pemakaian pertama v1.0:

- Dock aplikasi di bawah tidak lagi menimpa kartu Gempa BMKG & Gunung Api —
  kolom widget sekarang berhenti di atas dock dan kartunya lebih ringkas.
- Tulisan sapaan ("Selamat Malam") tidak lagi renggang antar huruf.
- Dock tidak lagi menampilkan aplikasi sampah bawaan TV (nama paket mentah,
  TV Wizard, Wi-Fi Display Sink, CastPlayTV, dll.) — hanya aplikasi beneran
  berlabel jelas yang tampil.
- Label aplikasi yang sedang dipilih tidak lagi dobel — hanya di dalam tile.
- Wallpaper malam sekarang benar-benar gelap (mulai 18.30 sudah gelap),
  tidak terang seperti siang.

Cara update dari v1.0: kirim APK baru ke HP, lalu dari Termux jalankan
`adb install -r ~/storage/downloads/tv-launcher.apk` — pengaturan & data
v1.0 tetap tersimpan, tidak perlu izin ulang.

## 1. Kirim APK dari HP ke TV

Pilih salah satu cara:

- **Send Files to TV** (paling gampang): install aplikasi *Send Files to TV*
  di HP dan di TV (dari Play Store), buka di kedua perangkat, kirim file
  `tv-launcher.apk` dari HP, file akan masuk ke folder Download di TV.
- **LocalSend**: install LocalSend di HP dan TV, kirim file lewat Wi-Fi yang sama.
- **Flashdisk**: salin `tv-launcher.apk` ke flashdisk, colok ke USB TV,
  buka lewat aplikasi File Manager di TV.

## 2. Install di TV

1. Buka aplikasi **File Manager / Pengelola Berkas** di TV, cari `tv-launcher.apk`
   (folder Download atau flashdisk), lalu pilih file-nya.
2. TV akan meminta izin **"Install unknown apps / Instal aplikasi tidak dikenal"**
   untuk File Manager — **izinkan**, lalu kembali dan ulangi membuka file APK.
3. Pilih **Install**. Selesai — aplikasi bernama **TV Launcher** akan terpasang.

## 3. Aktifkan 2 izin wajib (sekali saja)

Kedua izin ini yang membuat pop-up gempa bisa muncul di atas YouTube/Netflix
dan notifikasi InaEEWS bisa diteruskan ke layar TV:

1. **Tampil di atas aplikasi lain (overlay)**
   - Cara cepat: buka TV Launcher → tekan ikon **gir (Setelan)** di pojok
     kanan atas → pilih tombol **Izin Overlay** → aktifkan untuk TV Launcher.
   - Cara manual: Pengaturan TV → Aplikasi → Akses aplikasi khusus →
     Tampil di atas aplikasi lain → TV Launcher → Izinkan.
2. **Akses notifikasi**
   - Dari Setelan TV Launcher → tombol **Izin Notifikasi** → aktifkan
     **TV Launcher** dalam daftar, lalu konfirmasi.
   - Cara manual: Pengaturan TV → Aplikasi → Akses aplikasi khusus →
     Akses notifikasi → TV Launcher → Izinkan.

## 4. Jadikan launcher default

Google TV **tidak** menyediakan menu pemilih launcher bawaan seperti HP Android
biasa, jadi gunakan salah satu cara berikut (cukup sekali):

- **Cara gampang — aplikasi Launcher Manager:**
  1. Install *Launcher Manager* dari Play Store TV.
  2. Buka → pilih **TV Launcher** sebagai launcher default.
- **Cara ADB (dari HP, pakai aplikasi remote ADB seperti "Remote ADB Shell"):**
  1. Hubungkan ke IP TV (port 5555; aktifkan dulu *Opsi pengembang* di TV:
     Pengaturan → Tentang → klik "Build" 7 kali, lalu aktifkan *Debugging jaringan/USB*).
  2. Jalankan perintah:
     ```
     adb shell cmd role add-role-holder android.app.role.HOME id.bestie.tvlauncher
     ```
  3. Alternatif bila perintah di atas gagal:
     ```
     adb shell pm set-home-activity id.bestie.tvlauncher/.MainActivity
     ```

  Setelah itu tekan tombol **Home** di remote — TV Launcher akan muncul.
  Untuk kembali ke launcher bawaan: buka Launcher Manager lagi / jalankan
  `adb shell cmd role remove-role-holder android.app.role.HOME id.bestie.tvlauncher`.

  Catatan jujur: sebagian TV Google TV tetap menampilkan launcher bawaan
  sesaat setelah restart sebelum beralih — itu perilaku sistem Google TV,
  bukan kerusakan. Jangan menonaktifkan launcher bawaan
  (`com.google.android.apps.tv.launcherx`) kecuali paham risikonya.

## 5. Checklist pertama kali (di Setelan — ikon gir pojok kanan atas)

- [ ] **Lokasi**: pilih kota tempat TV berada (mempengaruhi cuaca, jadwal
      sholat, kualitas udara, dan wallpaper).
- [ ] **Bahasa**: Indonesia / English sesuai selera.
- [ ] **Ambang Pop-up Gempa**: bawaan M5.0 — gempa di bawah ini hanya tampil
      di widget home, tidak memunculkan pop-up.
- [ ] **Suara Peringatan**: aktifkan agar gempa besar berbunyi.
- [ ] **Uji Pop-up Gempa**: tekan untuk memastikan pop-up M5.4 percobaan
      muncul di layar.
- [ ] **Hubungkan HP** (opsional): scan QR di TV pakai kamera HP, halaman
      kontrol terbuka di browser HP (Wi-Fi harus sama) — bisa ganti lokasi,
      bahasa, ambang, dan uji pop-up dari HP.
- [ ] Install aplikasi **InaEEWS** dari Play Store TV bila belum ada —
      shortcut-nya muncul otomatis di dock, dan notifikasinya akan
      diteruskan TV Launcher sebagai pop-up peringatan.

## Syarat & catatan

- TV harus **terhubung internet** agar cuaca, jadwal sholat, kualitas udara,
  dan data gempa BMKG bisa tampil.
- Layanan pemantau gempa berjalan otomatis setiap TV menyala (cek data BMKG
  tiap 60 detik) dan tetap jalan walau HP mati.
- APK ini adalah build *debug* (ditandatangani kunci debug standar) — aman
  untuk dipakai sendiri di TV rumah; bukan untuk Play Store.

## Update v1.7 (dari v1.6 — PERLU UNINSTALL SEKALI LAGI)
Kunci tanda tangan kantor berubah lagi karena reset environment (signer baru 99066d91...), jadi dari v1.6 WAJIB: `adb uninstall id.bestie.tvlauncher` lalu `adb install` file ini. Isi v1.7: 4 app disembunyikan dari dock (CastPlayTV, Multimedia Player, TV Wizard, Wi-Fi Display Sink), query dock diperluas (LEANBACK + MAIN/LAUNCHER), bar gelap dock dihilangkan, semua menu Pengaturan jadi glassmorphism. Setelah install: set ulang kota, aktifkan izin overlay + akses notifikasi, jadikan home utama lagi (matikan launcherx via pm disable bila perlu), uji pop-up.

## Update v1.8 (28 Okt 2026)
Berisi: kaca dark-smoky (teks tajam di foto terang), chip forecast satu baris di bawah tanggal, latar Pengaturan = foto kota asli + scrim gelap, baris Pengaturan tidak melar. SIGNER BERUBAH LAGI (9af22634 vs v1.7 99066d91) — dari v1.7 wajib adb uninstall id.bestie.tvlauncher dulu, lalu install baru; setelah itu set ulang kota (Bandung), izin overlay, akses notifikasi, dan home utama (pm disable-user --user 0 com.google.android.apps.tv.launcherx).

## Update v1.9 (28 Okt 2026)
Berisi 12 poin review pemilik: teks info 15-20sp + info diringkas; panel kaca pekat (alpha 0,90-0,85) + scrim area jam; ringkasan gempa terbaca + halaman Detail Gempa (OK dari kartu, ada Coba Lagi); status data per sumber (Diperbarui HH:mm / Gagal memuat - data terakhir); hitung mundur sholat bergerak tiap 30 detik tanpa jaringan; crossfade wallpaper 1,5 detik; fokus remote dipulihkan + dock tidak dibangun ulang; urutan dock = paling sering dipakai di depan; pemuatan 5 sumber paralel; kategori Akun -> Kontrol dari HP; deskripsi aksesibilitas ikon prakiraan; zona waktu mengikuti kota pilihan untuk hitungan sholat. SIGNER BERUBAH LAGI (47ceaa vs v1.8 9af226) - dari v1.8 wajib adb uninstall dulu + install baru + checklist setelan.

## Update v2.0 (28 Okt 2026)
Berisi 5 poin review kedua: tinggi kartu mengikuti isi (tanpa ruang kosong, strip gunung api tetap terlihat); ringkasan sholat utuh (format Berikutnya dipendekkan, semua jam jadi 2 baris eksplisit) + detail gempa via dialog native AlertDialog yang bisa digulir (QuakeDetailActivity dihapus), Back mengembalikan fokus ke kartu gempa; kontras DIUKUR per varian scene (kartu sudah 15,1-17,5:1 sehingga opasitas tidak dinaikkan; scrim jam diperkuat #E0 fade 62%, titik terlemah p90 cerah 3,22:1 >= 3:1); teks pendukung naik ke 16sp; PERBAIKAN DOCK: manifest kini punya <queries> (MAIN+LAUNCHER dan MAIN+LEANBACK_LAUNCHER) agar aplikasi install-an pengguna terlihat oleh query (package visibility Android 14) - verifikasi perangkat: bandingkan dock dengan pm list packages -3. SIGNER BERUBAH LAGI (697d1b vs v1.9 47ceaa) - dari v1.9 wajib uninstall dulu (kalau adb error, lewat Pengaturan TV > Aplikasi > TV Launcher > Uninstall seperti kemarin) + install baru + checklist.

## Update v2.1 (28 Okt 2026)
Ikon judul kartu (5 vector baru: cuaca, udara, sholat, gempa, gunung api; dekoratif, contentDescription null); dock TERSEMBUNYI secara default - overlay kaca transparan, muncul via D-pad BAWAH dari elemen terbawah (animasi 200ms), ATAS/BACK menyembunyikan + fokus kembali; prakiraan jadi grid 2x2 (ikon 44dp dominan, placeholder stabil saat memuat); dedupe dock per label (varian leanback/TV menang - kasus LOKTV ganda); strip gunung api diperbaiki: akar masalahnya kolom non-scroll kehabisan tinggi di v1.9 (status lines + teks besar) sehingga strip ter-clip di luar layar - kini kolom dibungkus ScrollView + 114dp dock lama dikembalikan + trim spasi. SIGNER SAMA dengan v2.0 (697d1b) - update cukup adb install -r, setelan tersimpan.

## Update v2.2 (28 Okt 2026)
Grid forecast 78 persen lebar area kiri, rata kiri dengan tanggal, ikon cuaca 44->56dp, sel lebih mendekati persegi (209x126dp); kolom kanan terverifikasi seragam (padding 11/8dp, jarak 5dp); kartu cuaca diringkas (suhu+kondisi+kota saja; AQI/PM2.5 pindah ke dialog OK); kartu sholat hanya waktu berikutnya (jadwal lengkap via dialog OK); kartu gempa baris kedua hanya tanggal+waktu (kedalaman tetap di dialog); batas kartu diam diturunkan alpha-nya (cyan hanya saat fokus). SIGNER BERBEDA lagi (95324eda vs v2.1 697d1b8f) - update v2.1->v2.2 perlu uninstall + install segar.

## Update v2.3 (28 Okt 2026)
Perilaku dock: sembunyi otomatis saat fokus pindah ke widget (OnGlobalFocusChangeListener, tanpa menarik fokus balik); sembunyi saat tile diluncurkan (sebelum startActivity); ATAS/BACK tetap menutup + fokus kembali (jalur dispatchKeyEvent tidak diubah); anti-resurreksi animasi - runnable post di showDock memeriksa dockOpen, withEndAction hide dibungkus if(!dockOpen), animate().cancel() di kedua arah. SIGNER BERBEDA lagi (620385cc vs v2.2 95324eda) - uninstall + install segar lagi.
