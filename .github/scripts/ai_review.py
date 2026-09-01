import os
import subprocess
import requests

def run_ai_review():
    gemini_key = os.environ.get("GEMINI_API_KEY")
    if not gemini_key:
        print("GEMINI_API_KEY belum terpasang di Secrets!")
        return

    # Ambil perbedaan kode terbaru
    try:
        diff = subprocess.check_output(["git", "diff", "HEAD~1", "HEAD"]).decode("utf-8")
    except Exception as e:
        diff = ""

    if not diff.strip():
        print("Tidak ada perubahan kode yang terdeteksi.")
        return

    # Batasi log diff agar tidak melebihi kuota token gratis
    diff_truncated = diff[:5000]

    # Panggil Gemini API (1.5 Flash - Gratis)
    url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key={gemini_key}"
    prompt = (
        "Kamu adalah Senior Code Reviewer. Tinjau git diff berikut. "
        "Beri tahu jika ada bug, masalah performa, celah keamanan, dan berikan saran perbaikan kodenya:\n\n"
        f"```diff\n{diff_truncated}\n```"
    )

    payload = {"contents": [{"parts": [{"text": prompt}]}]}
    response = requests.post(url, json=payload)
    
    if response.status_code == 200:
        result = response.json()
        review_text = result['candidates'][0]['content']['parts'][0]['text']
        print("\n================= AI CODE REVIEW RESULT =================\n")
        print(review_text)
        print("\n=========================================================\n")
    else:
        print(f"Gagal memanggil AI: {response.status_code} - {response.text}")

if __name__ == "__main__":
    run_ai_review()
