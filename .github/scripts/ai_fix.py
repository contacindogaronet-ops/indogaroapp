import os
from google import genai

def main():
    # Mengambil kumpulan API key dari GitHub Secrets (dipisah koma)
    keys_raw = os.getenv("AI_API_KEYS", "")
    api_keys = [k.strip() for k in keys_raw.split(",") if k.strip()]
    
    if not api_keys:
        raise ValueError("Variabel AI_API_KEYS belum diatur di GitHub Secrets!")

    # Membaca instruksi dari GEMINI.md (yang memuat @AGENTS.md)
    instructions = "Perbaiki dan optimalkan kode berikut."
    if os.path.exists("GEMINI.md"):
        with open("GEMINI.md", "r", encoding="utf-8") as f:
            instructions = f.read()

    target_file = "main.py"
    if not os.path.exists(target_file):
        print(f"File {target_file} tidak ditemukan.")
        return

    with open(target_file, "r", encoding="utf-8") as f:
        code_content = f.read()

    prompt = f"""
    {instructions}

    Berikut adalah kode sumber yang harus diproses:
    ```python
    {code_content}
    ```
    """

    # Sistem rotasi key otomatis jika terkena limit atau error
    response_text = None
    for i, key in enumerate(api_keys):
        try:
            client = genai.Client(api_key=key)
            response = client.models.generate_content(
                model="gemini-2.0-flash",
                contents=prompt
            )
            response_text = response.text.strip()
            print(f"Berhasil memproses menggunakan Key #{i+1}")
            break
        except Exception as e:
            print(f"Key #{i+1} gagal atau terkena limit: {e}")
            continue

    if not response_text:
        raise RuntimeError("Semua API key dalam rotasi habis atau mengalami kegagalan.")

    # Membersihkan format markdown jika terbawa oleh output AI
    if response_text.startswith("```"):
        lines = response_text.splitlines()
        if lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].startswith("```"):
            lines = lines[:-1]
        response_text = "\n".join(lines)

    # Menuliskan kembali hasil perbaikan ke file asli
    with open(target_file, "w", encoding="utf-8") as f:
        f.write(response_text)
    
    print(f"File {target_file} berhasil diperbarui secara otomatis!")

if __name__ == "__main__":
    main()

