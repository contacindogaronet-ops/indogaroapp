import os
import sys
import json
import urllib.request
import urllib.error

def main():
    keys_raw = os.getenv("AI_API_KEYS", "") or os.getenv("GEMINI_API_KEY", "")
    api_keys = [k.strip() for k in keys_raw.split(",") if k.strip()]
    
    if not api_keys:
        print("Error: Tidak ada API key yang ditemukan di environment variable.")
        sys.exit(1)

    instructions = "Periksa dan perbaiki kode berikut sesuai standar terbaik."
    if os.path.exists("GEMINI.md"):
        with open("GEMINI.md", "r", encoding="utf-8") as f:
            instructions = f.read()
    elif os.path.exists("AGENTS.md"):
        with open("AGENTS.md", "r", encoding="utf-8") as f:
            instructions = f.read()

    target_file = "main.py"
    code_content = ""
    if os.path.exists(target_file):
        with open(target_file, "r", encoding="utf-8") as f:
            code_content = f.read()

    prompt = f"""
    {instructions}

    Berikut adalah kode sumber yang harus diproses:
    ```python
    {code_content}
    ```
    PENTING: Berikan HANYA kode akhirnya saja di dalam blok kode tanpa penjelasan tambahan.
    """

    # Diubah ke model gemini-3.6-flash sesuai instruksi error API Google
    url_template = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key={}"
    payload = {
        "contents": [{
            "parts": [{"text": prompt}]
        }]
    }
    data = json.dumps(payload).encode('utf-8')

    response_text = None
    for i, key in enumerate(api_keys):
        try:
            print(f"Mencoba menggunakan API Key #{i+1} via REST API...")
            url = url_template.format(key)
            req = urllib.request.Request(
                url,
                data=data,
                headers={'Content-Type': 'application/json'},
                method='POST'
            )
            
            with urllib.request.urlopen(req) as response:
                res_body = json.loads(response.read().decode('utf-8'))
                candidate = res_body.get('candidates', [])[0]
                text = candidate.get('content', {}).get('parts', [])[0].get('text', '')
                response_text = text.strip()
                print(f"Berhasil terhubung menggunakan API Key #{i+1}!")
                break
        except urllib.error.HTTPError as e:
            err_message = e.read().decode('utf-8')
            print(f"API Key #{i+1} gagal (HTTP Error {e.code}: {err_message}). Beralih ke key berikutnya...")
            continue
        except Exception as e:
            print(f"API Key #{i+1} gagal (Error: {e}). Beralih ke key berikutnya...")
            continue

    if not response_text:
        print("Error: Semua API key dalam rotasi gagal atau mengalami gangguan.")
        sys.exit(1)

    if response_text.startswith("```"):
        lines = response_text.splitlines()
        if lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].startswith("```"):
            lines = lines[:-1]
        response_text = "\n".join(lines)

    with open(target_file, "w", encoding="utf-8") as f:
        f.write(response_text)
    
    print(f"File {target_file} berhasil diperbarui secara otomatis!")

if __name__ == "__main__":
    main()
