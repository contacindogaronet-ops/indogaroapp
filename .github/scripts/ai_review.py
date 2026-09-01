import os
import sys
import json
import urllib.request

def main():
    # Tarik API Key dan bersihkan dari whitespace/newline (MENCEGAH 404 URL CORRUPTION)
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
        
    # Gunakan model stabil gemini-1.5-pro
    url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:generateContent?key={api_key}"
    
    prompt = (
        "Kamu adalah core engine CI/CD. Baca file otak (GEMINI.md) berikut "
        "dan hasilkan spesifikasi AI Agents. Output harus format teks/Markdown murni "
        "yang akan ditulis ke AGENTS.md.\n\n"
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
                
                with open("AGENTS.md", 'w', encoding='utf-8') as f:
                    f.write(result)
                print("File AGENTS.md berhasil diperbarui secara otomatis!")
            else:
                print(f"[-] HTTP Error {response.status}")
                sys.exit(1)
    except urllib.error.HTTPError as e:
        print(f"[-] HTTP Error: {e.code} - Alasan: {e.reason}")
        # Tangkap body error untuk debugging presisi
        error_body = e.read().decode('utf-8')
        print(f"[-] Detail Error dari Server:\n{error_body}")
        sys.exit(1)
    except Exception as e:
        print(f"[-] Exception saat eksekusi REST API: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
