import os
import sys
import json
import urllib.request
from urllib.error import HTTPError, URLError

def get_available_model(api_key: str) -> str:
    """Mendeteksi secara dinamis model yang valid dan aktif untuk API Key ini."""
    list_url = f"https://generativelanguage.googleapis.com/v1beta/models?key={api_key}"
    req = urllib.request.Request(list_url, headers={'Content-Type': 'application/json'})
    
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            models = data.get("models", [])
            
            # Prioritas model: cari yang support generateContent
            candidates = []
            for m in models:
                if "generateContent" in m.get("supportedGenerationMethods", []):
                    candidates.append(m.get("name"))
            
            # Filter model terbaik: prioritaskan 1.5-flash atau 1.5-pro jika ada
            for preferred in ["gemini-1.5-flash", "gemini-1.5-pro", "gemini-pro"]:
                for c in candidates:
                    if preferred in c:
                        # Format biasanya 'models/gemini-1.5-flash' -> kita ambil namanya
                        return c.replace("models/", "")
            
            if candidates:
                return candidates[0].replace("models/", "")
            
            print("[-] Fatal: Tidak ada model yang mendukung generateContent pada API Key ini.")
            sys.exit(1)
    except Exception as e:
        print(f"[-] Gagal auto-detect model: {e}")
        # Fallback paling universal
        return "gemini-1.5-flash"

def main():
    raw_api_key = os.getenv("GEMINI_API_KEY")
    if not raw_api_key:
        print("[-] Fatal: GEMINI_API_KEY tidak ditemukan di environment/secrets.")
        sys.exit(1)
        
    api_key = raw_api_key.strip()
        
    try:
        with open("GEMINI.md", 'r', encoding='utf-8') as f:
            brain = f.read()
    except FileNotFoundError:
        print("[-] Fatal: GEMINI.md tidak ditemukan.")
        sys.exit(1)
        
    # Auto-resolve model
    print("Mencoba menghubungkan API dan mendeteksi model yang tersedia...")
    target_model = get_available_model(api_key)
    print(f"[+] Model yang digunakan: {target_model}")
    
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{target_model}:generateContent?key={api_key}"
    
    prompt = (
        "Kamu adalah core engine CI/CD. Baca file otak (GEMINI.md) berikut "
        "dan hasilkan spesifikasi AI Agents. Output harus format teks/Markdown murni "
        "yang akan ditulis ke AGENTS.md. Dilarang memberikan teks pengantar atau penutup.\n\n"
        f"--- OTAK (GEMINI.md) ---\n{brain}"
    )
    
    payload = {"contents": [{"parts": [{"text": prompt}]}]}
    req = urllib.request.Request(
        url, 
        data=json.dumps(payload).encode('utf-8'),
        headers={'Content-Type': 'application/json'}, 
        method='POST'
    )
    
    try:
        with urllib.request.urlopen(req) as response:
            if response.status == 200:
                print("Berhasil terhubung menggunakan API Key!")
                data = json.loads(response.read().decode('utf-8'))
                result = data['candidates'][0]['content']['parts'][0]['text']
                
                with open("AGENTS.md", 'w', encoding='utf-8') as f:
                    f.write(result)
                print("File AGENTS.md berhasil diperbarui secara otomatis!")
            else:
                print(f"[-] HTTP Error: {response.status}")
                sys.exit(1)
    except HTTPError as e:
        print(f"[-] HTTP Error: {e.code} - {e.reason}")
        print(f"[-] Body:\n{e.read().decode('utf-8')}")
        sys.exit(1)
    except Exception as e:
        print(f"[-] Exception: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
