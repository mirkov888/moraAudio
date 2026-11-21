#!/usr/bin/env bash
set -Eeuo pipefail

ARCHIVE="${1:-vendor/Mora_Audio_Suite_Unified_v3.6.0.zip}"
OUT_DIR="${2:-artifacts/module-source-review}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

[[ -s "$ARCHIVE" ]] || { echo "Missing module archive: $ARCHIVE" >&2; exit 1; }
unzip -tq "$ARCHIVE" >/dev/null
mkdir -p "$OUT_DIR"
unzip -q "$ARCHIVE" -d "$TMP/source"

count=0
while IFS= read -r -d '' file; do
  case "$file" in
    *.sh|*.js|*.html|*.md|*.prop|*.conf|*.json|*.xml) ;;
    *) continue ;;
  esac
  rel="${file#"$TMP/source"/}"
  dest="$OUT_DIR/$rel"
  mkdir -p "$(dirname "$dest")"
  cp "$file" "$dest"
  count=$((count + 1))
done < <(find "$TMP/source" -type f -print0)

if (( count == 0 )); then
  echo "No readable module sources were exported" >&2
  exit 1
fi

(
  cd "$OUT_DIR"
  find . -type f -print | sort > FILES.txt
  zip -qr ../module-source-review.zip .
)
echo "Exported $count readable module source files"
