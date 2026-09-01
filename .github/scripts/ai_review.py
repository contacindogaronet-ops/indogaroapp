import os
import sys
from google import genai

def main():
    # Mengambil kumpulan API key dari GitHub Secrets (mendukung rotasi koma atau key tunggal)
    keys_raw = os.getenv("AI_API_KEYS", "") or os.getenv("GEMINI_API_KEY", "")
    api_keys = [k.strip() for k in keys_raw.split(",") if k.strip()]
    
    if not api_keys:
        print("Error: Tidak ada API key yang ditemukan di environment variable.")
        sys.exit(1)

    # Membaca instruksi dari file Markdown otak buatan (GEMINI.md atau AGENTS.md)
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
    PENTING: Berikan HANYA kode akhirnya saja di dalam blok kode tanpa penjelasan tambahan agar bisa langsung ditulis ke file.
    """

    response_text = None
    for i, key in enumerate(api_keys):
        try:
            print(f"Mencoba menggunakan API Key #{i+1}...")
            client = genai.Client(api_key=key)
            response = client.models.generate_content(
                model="gemini-2.0-flash",
                contents=prompt
            )
            response_text = response.text.strip()
            print(f"Berhasil terhubung menggunakan API Key #{i+1}!")
            break
        except Exception as e:
            print(f"API Key #{i+1} gagal/kena limit (Error: {e}). Beralih ke key berikutnya...")
            continue

    if not response_text:
        print("Error: Semua API key dalam rotasi gagal atau mengalami gangguan.")
        sys.exit(1)

    # Membersihkan format markdown jika terbawa oleh output AI
    if response_text.startswith("```"):
        lines = response_text.splitlines()
        if lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].startswith("```"):
            lines = lines[:-1]
        response_text = "\n".join(lines)

    # Menuliskan kembali hasil perbaikan ke file target
    with open(target_file, "w", encoding="utf-8") as f:
        f.write(response_text)
    
    print(f"File {target_file} berhasil diperbarui secara otomatis!")

if __name__ == "__main__":
    main()
