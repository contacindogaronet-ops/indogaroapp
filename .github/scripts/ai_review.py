import os
import subprocess
import requests

def run_ai_review():
    gemini_key = os.environ.get("GEMINI_API_KEY")
    github_token = os.environ.get("GITHUB_TOKEN")
    repo = os.environ.get("GITHUB_REPOSITORY")
    pr_number = os.environ.get("PR_NUMBER")

    if not gemini_key:
        print("Error: GEMINI_API_KEY tidak ditemukan!")
        return

    try:
        diff = subprocess.check_output(["git", "diff", "HEAD~1", "HEAD"]).decode("utf-8")
    except Exception:
        diff = ""

    if not diff.strip():
        print("Tidak ada perubahan kode yang terdeteksi.")
        return

    diff_truncated = diff[:5000]
    
    # URL diupdate menggunakan gemini-3.6-flash
    url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key={gemini_key}"
    
    prompt = (
        "Kamu adalah Senior Code Reviewer. Tinjau git diff berikut. "
        "Beri tahu jika ada bug, masalah performa, atau celah keamanan, dan berikan saran perbaikan:\n\n"
        f"```diff\n{diff_truncated}\n```"
    )

    payload = {"contents": [{"parts": [{"text": prompt}]}]}
    headers = {"Content-Type": "application/json"}
    response = requests.post(url, json=payload, headers=headers)
    
    if response.status_code == 200:
        result = response.json()
        review_text = result['candidates'][0]['content']['parts'][0]['text']
        print("\n================= AI CODE REVIEW =================\n")
        print(review_text)
        
        # Eksekusi komentar ke PR jika ada
        if github_token and repo and pr_number and pr_number != "false":
            post_comment_to_pr(review_text, github_token, repo, pr_number)
    else:
        print(f"Gagal memanggil AI ({response.status_code}): {response.text}")

def post_comment_to_pr(comment, token, repo, pr_number):
    url = f"https://api.github.com/repos/{repo}/issues/{pr_number}/comments"
    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github.v3+json"
    }
    payload = {"body": f"### 🤖 AI Code Review\n\n{comment}"}
    res = requests.post(url, json=payload, headers=headers)
    
    if res.status_code == 201:
        print("✅ Berhasil mengirim komentar ke PR!")
    else:
        print(f"❌ Gagal mengirim komentar PR: {res.status_code} - {res.text}")

if __name__ == "__main__":
    run_ai_review()
