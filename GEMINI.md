# SYSTEM CORE: INDOGARO V2RAY CLONE ARCHITECTURE

## 1. CONTEXT
Repositori ini adalah aplikasi klien V2Ray/Xray untuk Android (Kotlin) dengan *engine proxy* berbasis Golang (`modules/xray-core`). 

## 2. STRICT ARCHITECTURAL PROTOCOLS (WAJIB DIPATUHI AI REVIEWER)
Semua modifikasi dan review kode WAJIB mematuhi standar berikut tanpa kompromi:

### A. GOLANG PROXY ENGINE (Zero-Alloc & Network I/O)
- **Mindset**: Terapkan *Pure Zero-Alloc Mindset*.
- **Logging**: DILARANG KERAS menggunakan *package* `fmt` atau `log` bawaan. Wajib menggunakan `zerolog` yang *Thread-Safe*.
- **I/O Parsing**: Dilarang menggunakan *wrapper* `io.ReadFull` untuk mem-parsing soket mentah. Wajib menggunakan native `net.Conn.Read` di dalam *loop iteratif*.
- **Memory Pool**: Terapkan *Dual-Pool*: 32KB (Reguler/Anti-Jitter) vs 4MB (VVIP/MTProto). Wajib `SetReadBuffer(4MB)` khusus VVIP untuk mencegah paket enkripsi terpotong.
- **Latency**: Larang *hardcode buffer* 4MB secara global (biarkan OS Auto-tune). Wajib mengaktifkan `SetNoDelay(true)`. 
- **Zero-Copy**: Gunakan `io.CopyBuffer` pada TCP-relay untuk men-trigger eksekusi Linux `splice(2)`.
- **Teardown**: Gunakan *Aggressive Tear-down* (`.Close()` dari kedua sisi saat EOF). DILARANG menggunakan *channel* `<-done`.

### B. PROTOCOL & ROUTING (SOCKS5 & V2Ray)
- **SOCKS5/TCP**: Patuhi RFC 1928. Wajib kirim *reply SUCCESS* ke klien sebelum *stream relay* dieksekusi.
- **Sniffing**: Pastikan *engine* mengutamakan *string* Domain (SNI), bukan Raw IP Cloudflare.
- **Subdomain Scanner**: Pencarian *rule* harus O(N). Jika domain tidak cocok persis di Fastcache, potong target per titik (`.`) dan loop dari belakang (contoh: `ads.tiktok.com` -> `tiktok.com` -> `com`).
- **Rule Sanitizer**: Saat mem-parsing file `.txt` (geosite/geoip), bersihkan IP awalan (0.0.0.0), komentar (`#`), exception (`!`), dan modifier uBlock (`||`, `^`).

## 3. INSTRUCTIONS
Render spesifikasi untuk `AGENTS.md` yang memuat 3 agen: `GoProxyArchitect`, `V2RayRouterReviewer`, dan `KotlinJNIAnalyzer`. Output harus berformat Markdown.
