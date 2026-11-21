#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE_ZIP="${1:-$ROOT/vendor/Mora_Audio_Suite_Unified_v3.6.0.zip}"
OUT_DIR="${2:-$ROOT/artifacts}"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

if [[ ! -s "$SOURCE_ZIP" ]]; then
  echo "ERROR: module source archive not found: $SOURCE_ZIP" >&2
  exit 2
fi

unzip -tq "$SOURCE_ZIP"
while IFS= read -r entry; do
  case "$entry" in
    /*|../*|*/../*|*/..)
      echo "ERROR: unsafe archive entry: $entry" >&2
      exit 3
      ;;
  esac
done < <(unzip -Z1 "$SOURCE_ZIP")

mkdir -p "$OUT_DIR"
unzip -q "$SOURCE_ZIP" -d "$WORK_DIR/module"
MODULE_DIR="$WORK_DIR/module"
if [[ ! -f "$MODULE_DIR/module.prop" ]]; then
  echo "ERROR: module.prop must be at the archive root" >&2
  exit 4
fi

grep -qx 'id=mora_audio_suite' "$MODULE_DIR/module.prop" || {
  echo "ERROR: unexpected module id; expected mora_audio_suite" >&2
  exit 5
}
VERSION="$(sed -n 's/^version=//p' "$MODULE_DIR/module.prop" | head -n1)"
VERSION_CODE="$(sed -n 's/^versionCode=//p' "$MODULE_DIR/module.prop" | head -n1)"
[[ -n "$VERSION" && -n "$VERSION_CODE" ]] || {
  echo "ERROR: module version/versionCode missing" >&2
  exit 6
}

OUTPUT="$OUT_DIR/Mora-Audio-Suite-${VERSION}-${VERSION_CODE}.zip"
rm -f "$OUTPUT"
(
  cd "$MODULE_DIR"
  zip -qr "$OUTPUT" .
)
unzip -tq "$OUTPUT"
unzip -Z1 "$OUTPUT" | grep -qx 'module.prop'
echo "Built: $OUTPUT"
sha256sum "$OUTPUT"
