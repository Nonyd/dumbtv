#!/usr/bin/env bash
# Undo everything this project did to the Fire TV Stick:
#   1. re-enable the stock launcher,
#   2. re-enable every package in disable-list.txt (and any extra one logged in log/disabled.txt),
#   3. undo the replacement launcher's HOME takeover (Projectivy: accessibility service, overlay,
#      battery exemption, alarm tweak) and leave the launcher installed but inactive,
#   4. make the stock launcher the Home app again and press Home.
#
# Usage:  ./restore.sh                  restore, using the default Stick address
#         ./restore.sh --dry-run        only show what would happen, change nothing
#         ./restore.sh --launcher-only  only undo the replacement launcher (keep the debloat)
#         ./restore.sh 192.168.1.50     use another address (":5555" is added if missing)
set -u
export MSYS_NO_PATHCONV=1   # stop Git Bash rewriting on-device paths

DRY_RUN=0
LAUNCHER_ONLY=0
DEVICE="192.168.100.3:5555"
for a in "$@"; do
  case "$a" in
    --dry-run|-n) DRY_RUN=1 ;;
    --launcher-only) LAUNCHER_ONLY=1 ;;
    -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
    *) DEVICE="$a" ;;
  esac
done
[[ "$DEVICE" == *:* ]] || DEVICE="$DEVICE:5555"

HERE="$(cd "$(dirname "$0")" && pwd)"
LAUNCHER_PKG="com.amazon.tv.launcher"
LAUNCHER_HOME="com.amazon.tv.launcher/.ui.HomeActivity_vNext"
# Replacement launchers this project may have installed (dumbtv home appletv build, Projectivy).
ALT_PKGS="tv.dumbtv.home com.spocky.projengmenu"

# </dev/null: otherwise adb shell swallows the rest of the package list fed to the while-read loop.
sh_adb() { adb -s "$DEVICE" shell "$@" </dev/null | tr -d '\r'; }

echo "== Connecting to $DEVICE"
adb connect "$DEVICE" >/dev/null 2>&1
if [[ "$(adb -s "$DEVICE" get-state 2>/dev/null | tr -d '\r')" != "device" ]]; then
  echo "ERROR: the Stick at $DEVICE is not reachable. Is it on, on the same Wi-Fi, with ADB debugging on?"
  exit 1
fi

# Packages to restore: the disable list plus anything Phase 2 logged, launcher first.
pkgs=$( { cat "$HERE/disable-list.txt"; [[ -f "$HERE/log/disabled.txt" ]] && cat "$HERE/log/disabled.txt"; } \
        | sed 's/#.*//' | tr -d ' \t\r' | grep -E '^[A-Za-z][A-Za-z0-9_.]*$' | awk '!seen[$0]++' )
pkgs="$LAUNCHER_PKG"$'\n'"$(grep -vx "$LAUNCHER_PKG" <<<"$pkgs")"

# Never "restore" something that was already disabled before this project started.
was_disabled=""
[[ -f "$HERE/backup/packages-before.txt" ]] && was_disabled=$(awk -F'\t' '$2=="disabled"{print $1}' "$HERE/backup/packages-before.txt")

installed=$(sh_adb pm list packages | sed 's/^package://')
disabled_now=$(sh_adb pm list packages -d | sed 's/^package://')

do_or_say() { if (( DRY_RUN )); then echo "  WOULD    $*"; else sh_adb "$@" >/dev/null; echo "  done     $*"; fi; }

echo "== Replacement launcher"
found_alt=0
for ALT_PKG in $ALT_PKGS; do
  grep -qx "$ALT_PKG" <<<"$installed" || continue
  found_alt=1
  echo "  -- $ALT_PKG"
  # Read the accessibility list before force-stop: Android drops a force-stopped app's service from it.
  # Any entry of the package counts, in short (pkg/.Svc) or full (pkg/pkg.Svc) form.
  a11y=$(sh_adb settings get secure enabled_accessibility_services); [[ "$a11y" == null ]] && a11y=""
  rest=$(tr ':' '\n' <<<"$a11y" | grep -v -e "^$ALT_PKG/" -e '^$' | paste -sd: -)
  if [[ "$a11y" != "$rest" ]]; then
    if [[ -n "$rest" ]]; then do_or_say settings put secure enabled_accessibility_services "$rest"
    else do_or_say settings delete secure enabled_accessibility_services; do_or_say settings put secure accessibility_enabled 0; fi
  else echo "  ok       its accessibility service is not enabled"; fi
  do_or_say am force-stop "$ALT_PKG"
  do_or_say appops set "$ALT_PKG" SYSTEM_ALERT_WINDOW default
  do_or_say dumpsys deviceidle whitelist -"$ALT_PKG"
  echo "  note     $ALT_PKG stays installed; remove it with: adb uninstall $ALT_PKG"
done
(( found_alt )) || echo "  ok       no replacement launcher installed"

# The screensaver: back to Amazon's (or whatever backup/screensaver-before.txt recorded) if it is ours.
saver=$(sh_adb settings get secure screensaver_components)
if [[ "$saver" == tv.dumbtv.home/* ]]; then
  orig=$(sed -n 's/^screensaver_components=//p' "$HERE/backup/screensaver-before.txt" 2>/dev/null | tr -d '\r')
  [[ -z "$orig" || "$orig" == null ]] && orig="com.amazon.ftv.screensaver/.app.services.ScreensaverService"
  do_or_say settings put secure screensaver_components "$orig"
else
  echo "  ok       screensaver is not ours"
fi

# The HOME takeover's alarm tweak: drop min_futurity=0 unless it was already there before we started,
# keeping any other alarm_manager_constants entries.
alarm=$(sh_adb settings get global alarm_manager_constants); [[ "$alarm" == null ]] && alarm=""
if [[ ",$alarm," == *,min_futurity=0,* ]] \
   && ! grep -q 'min_futurity=0' "$HERE/backup/launcher-before.txt" 2>/dev/null; then
  keep=$(tr ',' '\n' <<<"$alarm" | grep -v -e '^min_futurity=' -e '^$' | paste -sd, -)
  if [[ -n "$keep" ]]; then do_or_say settings put global alarm_manager_constants "$keep"
  else do_or_say settings delete global alarm_manager_constants; fi
else
  echo "  ok       no alarm tweak of ours to remove"
fi

n_enable=0; n_ok=0; n_absent=0; n_kept=0; n_fail=0
(( LAUNCHER_ONLY )) && pkgs="$LAUNCHER_PKG"
while IFS= read -r p; do
  [[ -z "$p" ]] && continue
  if ! grep -qx "$p" <<<"$installed"; then echo "  absent   $p"; n_absent=$((n_absent+1)); continue; fi
  if grep -qx "$p" <<<"$was_disabled"; then echo "  skip     $p (was already disabled before we started)"; n_kept=$((n_kept+1)); continue; fi
  if ! grep -qx "$p" <<<"$disabled_now"; then echo "  ok       $p (already enabled)"; n_ok=$((n_ok+1)); continue; fi
  if (( DRY_RUN )); then
    echo "  WOULD    pm enable $p"; n_enable=$((n_enable+1))
  else
    out=$(sh_adb pm enable "$p" 2>&1)
    if grep -q "new state: enabled" <<<"$out"; then echo "  enabled  $p"; n_enable=$((n_enable+1))
    else echo "  FAILED   $p: $out"; n_fail=$((n_fail+1)); fi
  fi
done <<<"$pkgs"

echo "== Home app"
if (( DRY_RUN )); then
  if sh_adb cmd package help | grep -q "set-home-activity"; then
    echo "  WOULD    cmd package set-home-activity $LAUNCHER_HOME   (command available on this device)"
  else
    echo "  WARNING  set-home-activity not available; would rely on the stock launcher being the only Home app"
  fi
  echo "  WOULD    input keyevent KEYCODE_HOME"
else
  sh_adb cmd package set-home-activity "$LAUNCHER_HOME"
  sh_adb input keyevent KEYCODE_HOME
  echo "  current Home: $(sh_adb cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)"
fi

echo
if (( DRY_RUN )); then echo "DRY RUN — nothing was changed. Would enable: $n_enable"
else echo "Enabled: $n_enable"; fi
echo "Already enabled: $n_ok   Not on device: $n_absent   Left alone (pre-existing): $n_kept   Failed: $n_fail"
(( n_fail == 0 )) || { echo "Some packages failed. As a last resort see README.md (factory reset)."; exit 2; }
