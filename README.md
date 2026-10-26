# WuWa Config Installer
Cara dapat APK (tanpa Android Studio):
1. Buat repo GitHub baru, upload SEMUA isi folder ini (termasuk folder .github).
2. Buka tab Actions -> "Build APK" -> tunggu selesai (~5 menit).
3. Unduh artifact `WuWaConfigInstaller-apk`, ekstrak, pasang app-debug.apk.
4. Pasang & jalankan Shizuku, buka app, izinkan, lalu Pasang Config.

Tambah config lain: buat folder di app/src/main/assets/ bernama
`balanced_high`, `balanced_medium`, `flagship_medium`, lalu isi dengan
Engine.ini dan DeviceProfiles.ini masing-masing.

Mode otomatis: saat app dibuka, izin Shizuku diminta otomatis, profil dipilih
berdasarkan RAM (>=10 GiB = Flagship, selain itu Balanced), folder game dicari
otomatis, lalu config dipasang tanpa menekan tombol (bisa dimatikan lewat switch).
Satu-satunya langkah manual: menjalankan service Shizuku (tidak bisa diotomatisasi).
