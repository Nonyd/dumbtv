#!/usr/bin/env bash
# Installs the appletv build of dumbtv home on the Stick and hands it the HOME button, the way
# dumbtv's own launcher/install.sh does on Fire OS 8. Every command is logged to log/phase3.log.
# Undo: ./restore.sh --launcher-only  (or .\restore.ps1 -LauncherOnly)
set -u
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")"
D=${DEVICE:-192.168.100.3:5555}
PKG=tv.dumbtv.home
GUARD=tv.dumbtv.home/tv.dumbtv.home.Guard
# The APK from build.ps1: next to this folder's dumbtv/ checkout, or two levels up inside the fork.
APK=${APK:-dumbtv/launcher/build/dumbtv-home.apk}
[[ -f $APK ]] || APK=../../launcher/build/dumbtv-home.apk
MEDIA=/sdcard/Android/media/$PKG
LOG=log/phase3.log

q() { adb -s "$D" shell "$@" </dev/null 2>&1 | tr -d '\r'; }
run() { echo "$(date '+%F %T') \$ adb shell $*" >> "$LOG"; local o; o=$(q "$@"); echo "  -> ${o:-(no output)}" >> "$LOG"; echo "  $* ${o:+-> $o}"; }
fail() { echo "ERROR: $*"; echo "  ERROR: $*" >> "$LOG"; exit 1; }

echo "===== $(date '+%F %T') dumbtv home (appletv) install" >> "$LOG"
[[ "$(adb -s "$D" get-state 2>/dev/null | tr -d '\r')" == device ]] || fail "Stick not reachable at $D"
[[ -f $APK ]] || fail "$APK missing: run dumbtv\\launcher\\build.ps1 first"

echo "[1] save current settings -> backup/launcher-before.txt"
# A reinstall must not record its own min_futurity=0 as "before", or the undo would keep it.
if [[ -n "$(q pm list packages $PKG)" && -f backup/launcher-before.txt ]]; then
  echo "  already installed: keeping the earlier backup"
else
{ echo "# captured $(date) before dumbtv home"
  echo "enabled_accessibility_services=$(q settings get secure enabled_accessibility_services)"
  echo "accessibility_enabled=$(q settings get secure accessibility_enabled)"
  echo "alarm_manager_constants=$(q settings get global alarm_manager_constants)"
  echo "home=$(q cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)"
} > backup/launcher-before.txt
fi
sed -n '2,5p' backup/launcher-before.txt

echo "[2] install"
echo "$(date '+%F %T') \$ adb install -r $APK" >> "$LOG"
out=$(adb -s "$D" install -r "$(cygpath -m "$PWD/$APK" 2>/dev/null || echo "$APK")" 2>&1 | tr -d '\r' | grep -m1 -E 'Success|Failure|failed')
echo "  -> $out" >> "$LOG"; echo "  $out"
[[ $out == Success ]] || fail "install: $out"

echo "[3] settings file + wallpaper into the app's media folder (adb can write there on Fire OS 8)"
run mkdir -p "$MEDIA/wallpapers"
echo "$(date '+%F %T') \$ adb push home.conf $MEDIA/home.conf" >> "$LOG"
adb -s "$D" push home.conf "$MEDIA/home.conf" >/dev/null 2>&1 && echo "  pushed home.conf"
if [[ -z "$(q ls "$MEDIA/wallpapers")" ]]; then
  echo "$(date '+%F %T') \$ adb push wallpapers/placeholder.jpg $MEDIA/wallpaper.jpg" >> "$LOG"
  adb -s "$D" push wallpapers/placeholder.jpg "$MEDIA/wallpaper.jpg" >/dev/null 2>&1 && echo "  pushed placeholder wallpaper"
fi

echo "[4] keep it alive, skip the 5 s app-switch delay"
run dumpsys deviceidle whitelist +$PKG
run cmd package set-home-activity $PKG/.Home
cur=$(q settings get global alarm_manager_constants); [[ $cur == null ]] && cur=
rest=$(tr ',' '\n' <<<"$cur" | grep -v '^min_futurity=' | grep -v '^$' | paste -sd, -)
run settings put global alarm_manager_constants "${rest:+$rest,}min_futurity=0"

echo "[5] enable the Guard accessibility service, keeping any others"
orig=$(q settings get secure enabled_accessibility_services); [[ $orig == null ]] && orig=
new=$(printf '%s\n%s\n' "$orig" "$GUARD" | tr ':' '\n' | grep -v '^$' | awk '!s[$0]++' | paste -sd: -)
run settings put secure enabled_accessibility_services '""'
q sleep 1 >/dev/null
run settings put secure enabled_accessibility_services "$new"
run settings put secure accessibility_enabled 1
bound=0
for _ in $(seq 1 15); do
  if q dumpsys accessibility | grep -q 'Bound services:{.*label=dumbtv home button'; then bound=1; break; fi
  q sleep 1 >/dev/null
done
if (( bound )); then echo "  Guard bound"; echo "  Guard bound" >> "$LOG"
else
  echo "  A11yAuthorizer: $(q logcat -d -b all | grep -c 'A11yAuthorizer.*tv.dumbtv')"
  fail "Fire OS did not bind the Guard; run ./restore.sh --launcher-only"
fi

# No system screensaver change: Fire OS 8 runs none while Amazon's Ambient service is disabled
# (Phase 2), so the home screen shows the photo slideshow itself (home.conf idle=3).

echo "[6] open it"
q am start -W -n $PKG/.Home | grep -E 'Status|TotalTime'
echo "done. Undo: ./restore.sh --launcher-only"
