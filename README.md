# PDF Studio Pro

**PDF Studio Pro** adalah aplikasi Android Native modern, elegan, responsif, dan 100% offline-first yang menggabungkan Penampil Dokumen PDF profesional, Pemindai Dokumen Cerdas, Pengenal Teks OCR (ML Kit), dan Alat Manipulasi PDF lengkap.

---

## Fitur Utama

1. **Penampil PDF Profesional (PDF Reader)**
   - Rendering halaman cepat dengan native `PdfRenderer` dan memory cache LRU.
   - Dukungan gesture pinch-to-zoom dan double-tap zoom.
   - Pilihan mode gulir vertikal kontinu dan mode lembaran horizontal (*page flip*).
   - Penanda buku (*Bookmarks*) dengan navigasi instan ke halaman target.
   - Dialog lompat ke halaman (*Jump to Page*).
   - Mode malam (*Dark reader mode*) untuk kenyamanan mata.
   - Integrasi cetak langsung dengan *Android Print Framework*.
   - Dukungan pembukaan berkas PDF eksternal via `ACTION_VIEW`.

2. **Pemindai Dokumen Cerdas (Smart Scanner)**
   - Menggunakan CameraX untuk *live viewfinder* dengan bingkai panduan dokumen.
   - Mode flash kamera (On/Off).
   - Impor banyak foto langsung dari galeri perangkat via Android Photo Picker.
   - Pemrosesan filter citra dokumen secara *real-time*:
     - **Original**: Tanpa perubahan
     - **Magic Color**: Meningkatkan kontras dan kecerahan teks
     - **B&W**: Konversi hitam-putih biner kontras tinggi
     - **Grayscale**: Warna abu-abu bersih
   - Rotasi sudut 90° dan penghapusan halaman per lembar.
   - Ekspor kumpulan pindaian langsung ke format PDF berkualitas tinggi.

3. **Pengenal Teks OCR (ML Kit Text Recognition)**
   - Pemrosesan OCR 100% offline di perangkat (*on-device*).
   - Ekstraksi teks dari gambar atau hasil pindaian dokumen.
   - Editor teks hasil pengenalan yang dapat diedit langsung.
   - Tombol satu ketukan untuk salin ke *clipboard* dan bagikan (*share*).
   - Simpan hasil ekstraksi teks ke berkas `.txt` di penyimpanan aplikasi.

4. **Alat PDF Lengkap (PDF Tools Hub)**
   - **Gabung PDF (Merge)**: Satukan dua atau lebih berkas PDF menjadi satu dokumen utuh.
   - **Pisah PDF (Split)**: Ekstrak rentang halaman tertentu (misal: hal 1-5).
   - **Kompres PDF**: Perkecil ukuran dokumen PDF untuk kemudahan pengiriman.
   - **Foto ke PDF**: Ubah satu atau banyak gambar menjadi berkas PDF standar A4.
   - **PDF ke Gambar**: Ekstrak setiap halaman dokumen menjadi berkas PNG beresolusi tinggi.
   - **Putar Halaman (Rotate)**: Putar orientasi seluruh halaman sebesar 90°.
   - **Hapus Halaman (Delete Pages)**: Pilih nomor halaman yang ingin dibuang dari berkas PDF.
   - **Watermark**: Bubuhkan tanda air teks pengaman (seperti "RAHASIA", "CONFIDENTIAL", "DRAFT") dengan rotasi diagonal dan transparansi elegan.

5. **Pengelola Dokumen & Ruang Kerja (Document Manager)**
   - Tab navigasi: **Semua**, **Favorit**, dan **Hasil Pindai**.
   - Pencarian instan berdasarkan nama dokumen.
   - Pengurutan berdasarkan tanggal, nama, dan ukuran berkas.
   - Ganti nama (*Rename*), tandai favorit (*Favorite*), hapus (*Delete*), dan bagikan (*Share* via `FileProvider`).

---

## Panduan Membangun (Build Guide)

### Perintah Gradle

**Linux / macOS:**
```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:bundleRelease
```

**Windows:**
```bat
gradlew.bat :app:assembleDebug
gradlew.bat :app:assembleRelease
gradlew.bat :app:bundleRelease
```

### Lokasi Berkas Output APK & AAB
- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release APK**: `app/build/outputs/apk/release/app-release.apk`
- **Release AAB (App Bundle)**: `app/build/outputs/bundle/release/app-release.aab`

---

## Arsitektur & Teknologi

- **Bahasa**: Kotlin 2.2
- **UI Framework**: Jetpack Compose (Material Design 3)
- **Arsitektur**: Clean Architecture + MVVM
- **Database Lokal**: Room Database (KSP)
- **Pemrosesan PDF**: Android Native `PdfRenderer` + `PdfDocument`
- **Kamera & Pemindai**: CameraX (Camera2, Lifecycle, View)
- **Mesin OCR**: Google ML Kit Text Recognition (Latin on-device)
- **Tema Desain**: Premium Navy Blue (`#0B1F33`) & Bright Teal (`#2DD4BF`)
