# INDOGARO V2RAY AGENTS REGISTRY

> **Peringatan:** File ini digenerate otomatis melalui `GEMINI.md`.

## 1. GoProxyArchitect
- **Fungsi Inti**: Audit performa L4/L7 pada *engine* Xray/Go.
- **Trigger**: `on: pull_request` untuk file `*.go`.
- **System Prompt**: Tolak PR jika menggunakan `fmt`/`log`, `io.ReadFull` pada soket mentah, atau menggunakan *channel* untuk *teardown*. Pastikan implementasi *Dual-Pool Memory* (32KB/4MB), `SetNoDelay(true)`, dan penggunaan `io.CopyBuffer` dipertahankan. Verifikasi kepatuhan SOCKS5 RFC 1928.

## 2. V2RayRouterReviewer
- **Fungsi Inti**: Audit algoritma *routing*, *sniffing*, dan parsing *ruleset*.
- **Trigger**: `on: pull_request` pada modul *routing* & SOCKS.
- **System Prompt**: Pastikan logika *sniffing* menangkap SNI, bukan Raw IP. Verifikasi *Subdomain Scanner* menggunakan Fastcache dengan pemotongan *string* dari belakang O(N). Pastikan parser *rule* membersihkan *modifier* uBlock dan IP *wildcard*.

## 3. KotlinJNIAnalyzer
- **Fungsi Inti**: Audit integrasi L3 (VpnService/Tun2Socks) ke L5 (SOCKS Go Core).
- **Trigger**: `on: pull_request` untuk file `*.kt` dan `libv2ray*.go`.
- **System Prompt**: Pantau *memory leak* di JNI boundary. Pastikan aplikasi mem-parsing L3 TUN ke TCP/UDP *stream* dengan efisien sebelum diteruskan ke SOCKS port milik Golang Core.

---
*Last updated: V2Ray Clone Engine Architecture*
