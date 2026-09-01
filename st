#!/bin/bash
# ==========================================
# JARGO AUTO-SYNC PIPELINE (CLEAN)
# ==========================================

sync
echo "⚙️  Merapikan go.mod dan go.sum..."
go mod tidy

if [ -z "$(git status --porcelain)" ]; then
    echo "✅ Tidak ada perubahan kode baru."
    git push -u origin main || { echo "❌ FATAL: Gagal push!"; exit 1; }
    exit 0
fi

git add .
MSG="$*"
if [ -z "$MSG" ]; then
    MSG="chore(core): auto-sync update $(date +'%Y-%m-%d %H:%M:%S')"
fi

echo "📝 Eksekusi Commit: $MSG"
git commit -m "$MSG"

echo "🚀 Mendorong kode ke GitHub Origin..."
git push -u origin main || { echo "❌ FATAL: Gagal melakukan push ke GitHub!"; exit 1; }
echo "✅ Push selesai! GitHub Actions sedang melakukan kompilasi."
