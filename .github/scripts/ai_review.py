import os
import sys
import json
import urllib.request

def main():
    raw_api_key = os.getenv("GEMINI_API_KEY")
    if not raw_api_key:
        print("[-] Fatal: GEMINI_API_KEY tidak dikonfigurasi pada GitHub Secrets!")
        sys.exit(1)
        
    api_key = raw_api_key.strip()
        
    try:
        with open("GEMINI.md", 'r', encoding='utf-8') as f:
            brain = f.read()
    except FileNotFoundError:
        print("[-] Fatal: File OTAK (GEMINI.md) tidak ditemukan di root.")
        sys.exit(1)
        
    # Menggunakan model -latest yang sudah terbukti berhasil (HTTP 200) di environment Anda
    url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro-latest:generateContent?key={api_key}"
    
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
    
    print("Mencoba menggunakan API Key #1 via REST API...")
    try:
        with urllib.request.urlopen(req) as response:
            if response.status == 200:
                print("Berhasil terhubung menggunakan API Key #1!")
                data = json.loads(response.read().decode('utf-8'))
                result = data['candidates'][0]['content']['parts'][0]['text']
                
                # Hardcode output ke AGENTS.md, hapus logika main.py
                with open("AGENTS.md", 'w', encoding='utf-8') as f:
                    f.write(result)
                print("File AGENTS.md berhasil diperbarui secara otomatis!")
            else:
                print(f"[-] HTTP Error {response.status}")
                sys.exit(1)
    except urllib.error.HTTPError as e:
        print(f"[-] HTTP Error: {e.code} - Alasan: {e.reason}")
        print(f"[-] Detail Error dari Server:\n{e.read().decode('utf-8')}")
        sys.exit(1)
    except Exception as e:
        print(f"[-] Exception saat eksekusi REST API: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
