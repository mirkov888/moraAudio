#!/usr/bin/env bash
set -Eeuo pipefail

ARCHIVE="${1:-vendor/Mora_Audio_Suite_Unified_v3.6.0.zip}"
OUT="${2:-artifacts/module-audit.txt}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

fail() { echo "FAIL: $*" | tee -a "$OUT"; exit 1; }
mkdir -p "$(dirname "$OUT")"
: > "$OUT"

[[ -s "$ARCHIVE" ]] || fail "source archive missing: $ARCHIVE"
unzip -tq "$ARCHIVE" >/dev/null || fail "ZIP integrity check failed"

while IFS= read -r entry; do
  case "$entry" in
    /*|../*|*/../*|..|*\\*)
      fail "unsafe archive path: $entry"
      ;;
  esac
done < <(unzip -Z1 "$ARCHIVE")

unzip -q "$ARCHIVE" -d "$TMP/source"
ROOT="$TMP/source"
[[ -f "$ROOT/module.prop" ]] || fail "module.prop must be at archive root"

required=(
  module.prop service.sh post-fs-data.sh action.sh customize.sh uninstall.sh
  get_audio_status.sh set_audio_mode.sh mora_cfg.sh bass_ctl.sh
  viper_fix.sh viper_status.sh backup_ctl.sh
)
for file in "${required[@]}"; do
  [[ -f "$ROOT/$file" ]] || fail "required module file missing: $file"
  printf 'PASS required: %s\n' "$file" | tee -a "$OUT"
done

id="$(sed -n 's/^id=//p' "$ROOT/module.prop" | head -n1)"
version="$(sed -n 's/^version=//p' "$ROOT/module.prop" | head -n1)"
version_code="$(sed -n 's/^versionCode=//p' "$ROOT/module.prop" | head -n1)"
[[ "$id" == "mora_audio_suite" ]] || fail "unexpected module id: $id"
[[ -n "$version" && -n "$version_code" ]] || fail "version metadata incomplete"
printf '\nModule: %s\nVersion: %s (%s)\n' "$id" "$version" "$version_code" | tee -a "$OUT"

shell_count=0
while IFS= read -r -d '' script; do
  bash -n "$script" || fail "shell syntax error: ${script#"$ROOT"/}"
  shell_count=$((shell_count + 1))
done < <(find "$ROOT" -type f -name '*.sh' -print0)
printf 'PASS shell syntax: %s scripts parsed by bash -n\n' "$shell_count" | tee -a "$OUT"

for optional in setup_check.sh report.sh monitor.sh; do
  if [[ -f "$ROOT/$optional" ]]; then
    printf 'INFO optional present: %s\n' "$optional" | tee -a "$OUT"
  else
    printf 'INFO optional absent (app must not call it): %s\n' "$optional" | tee -a "$OUT"
  fi
done

printf '\nAUDIT RESULT: PASS\n' | tee -a "$OUT"
