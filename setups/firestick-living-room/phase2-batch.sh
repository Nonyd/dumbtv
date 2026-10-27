#!/usr/bin/env bash
# Phase 2 helper: disable one batch from disable-list.txt, then health-check the Stick.
#   ./phase2-batch.sh 3              disable batch 3 and check
#   ./phase2-batch.sh 3 --rollback   re-enable batch 3
#   ./phase2-batch.sh 0 --check      only run the health check
# Every command is appended to log/phase2.log; packages actually disabled go to log/disabled.txt
# (restore.sh / restore.ps1 also read that file). Never uses `pm uninstall`.
set -u
export MSYS_NO_PATHCONV=1   # stop Git Bash rewriting on-device paths like /sdcard into Windows paths
BATCH="${1:?batch number}"; MODE="${2:-}"
DEVICE="${DEVICE:-192.168.100.3:5555}"
HERE="$(cd "$(dirname "$0")" && pwd)"
LOG="$HERE/log/phase2.log"; DONE="$HERE/log/disabled.txt"
mkdir -p "$HERE/log/screens"; touch "$DONE"

log() { echo "$(date '+%F %T') $*" >> "$LOG"; }
# </dev/null: adb shell would otherwise swallow the rest of the package list fed to the while-read loops.
run() { log "\$ adb shell $*"; local o; o=$(adb -s "$DEVICE" shell "$@" 2>&1 </dev/null | tr -d '\r'); log "  -> ${o:-(no output)}"; echo "$o"; }
q()   { adb -s "$DEVICE" shell "$@" 2>/dev/null </dev/null | tr -d '\r'; }

batch_pkgs() {
  awk -v b="## batch $1 " 'index($0, "## batch ")==1 { on = (index($0 " ", b)==1) ; next } on' "$HERE/disable-list.txt" \
    | sed 's/#.*//' | tr -d ' \t\r' | grep -E '^[A-Za-z][A-Za-z0-9_.]*$'
}

health() {
  local ok=1 resumed home net
  [[ "$(adb -s "$DEVICE" get-state 2>/dev/null | tr -d '\r')" == device ]] || { echo "  FAIL adb not responding"; return 1; }
  echo "  ok   adb responds"
  q input keyevent KEYCODE_HOME >/dev/null
  for _ in 1 2 3 4 5 6; do
    q sleep 1
    resumed=$(q dumpsys activity activities | grep -m1 -E 'mResumedActivity|ResumedActivity:')
    [[ "$resumed" == *com.amazon.tv.launcher* ]] && break
  done
  if [[ "$resumed" == *com.amazon.tv.launcher* ]]; then echo "  ok   Home in foreground: $(grep -oE 'com\.amazon\.tv\.launcher/[^ }]+' <<<"$resumed")"
  else echo "  FAIL Home not in foreground: $resumed"; ok=0; fi
  home=$(q cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)
  [[ "$home" == com.amazon.tv.launcher/* ]] && echo "  ok   Home app = $home" || { echo "  FAIL Home app = $home"; ok=0; }
  q pidof com.amazon.tv.launcher >/dev/null && echo "  ok   launcher process running" || { echo "  FAIL launcher not running"; ok=0; }
  net=$(q dumpsys connectivity | grep -m1 -oE 'WIFI.*VALIDATED' | grep -oE 'VALIDATED' | head -1)
  [[ "$net" == VALIDATED ]] && echo "  ok   Wi-Fi connected + internet validated" || { echo "  WARN Wi-Fi not reported VALIDATED"; }
  local shot="$HERE/log/screens/batch${BATCH}${MODE:+-${MODE#--}}.png"
  # exec-out streaming is very slow over Wi-Fi on this stick; write on-device, then pull.
  q screencap -p /sdcard/phase2-shot.png >/dev/null
  local local_shot="$shot"; command -v cygpath >/dev/null && local_shot=$(cygpath -m "$shot")
  adb -s "$DEVICE" pull /sdcard/phase2-shot.png "$local_shot" >/dev/null 2>&1 && echo "  screenshot: $shot"
  q rm -f /sdcard/phase2-shot.png
  (( ok ))
}

if [[ "$MODE" == --check ]]; then health; exit $?; fi

pkgs=$(batch_pkgs "$BATCH")
[[ -n "$pkgs" ]] || { echo "No batch $BATCH in disable-list.txt"; exit 1; }
log "===== batch $BATCH ${MODE:-disable}"

if [[ "$MODE" == --rollback ]]; then
  while IFS= read -r p; do
    echo "  $(run pm enable "$p" | tail -1)"
    grep -vx "$p" "$DONE" > "$DONE.tmp"; mv "$DONE.tmp" "$DONE"
  done <<<"$pkgs"
  health; exit $?
fi

never="com.amazon.tv.launcher com.amazon.firehomestarter com.amazon.vizzini com.amazon.device.messaging com.amazon.imp com.amazon.tv.settings.v2 com.amazon.tv.settings.core com.amazon.tv.ime com.android.systemui"
while IFS= read -r p; do
  if [[ " $never " == *" $p "* ]]; then echo "  REFUSED $p (never-disable list)"; log "  refused $p"; continue; fi
  out=$(run pm disable-user --user 0 "$p" | tail -1)
  if [[ "$out" == *"new state: disabled-user"* ]]; then echo "  disabled  $p"; grep -qx "$p" "$DONE" || echo "$p" >> "$DONE"
  elif [[ "$out" == *protected* ]]; then echo "  PROTECTED $p"
  else echo "  ERROR     $p: $out"; fi
done <<<"$pkgs"

echo "Health check after batch $BATCH:"
if health; then log "  health OK"; echo "BATCH $BATCH OK"
else log "  health FAILED -> rolling back"; echo "Health check failed, rolling back batch $BATCH"; "$0" "$BATCH" --rollback; exit 3; fi
