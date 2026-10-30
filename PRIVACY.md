# Kebijakan Privasi — PDF Studio Pro

Terakhir diperbarui: 30 Oktober 2026

## 1. Pemrosesan Offline-First (100% On-Device)
PDF Studio Pro memproses semua dokumen, pindaian kamera, dan pengenalan teks (OCR) sepenuhnya secara lokal di dalam perangkat Anda. Tidak ada data berkas yang dikirim atau diunggah ke server eksternal mana pun.

## 2. Izin Perangkat (Permissions)
Aplikasi hanya meminta izin yang diperlukan secara ketat:
- **Kamera (`android.permission.CAMERA`)**: Hanya digunakan saat Anda secara aktif membuka fitur Pemindai Dokumen untuk mengambil foto fisik dokumen Anda.
- **Getaran (`android.permission.VIBRATE`)**: Memberikan umpan balik haptik lembut saat mengambil gambar atau menyelesaikan operasi.

## 3. Penyimpanan Berkas
Berkas PDF yang diimpor, dipindai, atau diekspor disimpan secara aman dalam penyimpanan internal terisolasi aplikasi (*Scoped Storage* / direktori berkas privat). Berkas hanya dibagikan ke aplikasi lain jika Anda secara eksplisit menekan tombol **Bagikan** melalui Android `FileProvider`.

## 4. Tanpa Iklan dan Pelacak
Aplikasi tidak menyematkan pustaka analitik pihak ketiga atau jaringan periklanan.
