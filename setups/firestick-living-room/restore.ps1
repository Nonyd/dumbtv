# Undo everything this project did to the Fire TV Stick (Windows version of restore.sh):
#   1. re-enable the stock launcher,
#   2. re-enable every package in disable-list.txt (and any extra one logged in log\disabled.txt),
#   3. undo the replacement launcher's HOME takeover (Projectivy: accessibility service, overlay,
#      battery exemption, alarm tweak) and leave the launcher installed but inactive,
#   4. make the stock launcher the Home app again and press Home.
#
# Usage (from this folder):
#   .\restore.ps1                         restore
#   .\restore.ps1 -DryRun                 only show what would happen
#   .\restore.ps1 -LauncherOnly           only undo the replacement launcher (keep the debloat)
#   .\restore.ps1 -Device 192.168.1.50    use another address
# If scripts are blocked: powershell -ExecutionPolicy Bypass -File .\restore.ps1
param(
    [string]$Device = "192.168.100.3:5555",
    [switch]$DryRun,
    [switch]$LauncherOnly
)
# Replacement launchers this project may have installed (dumbtv home appletv build, Projectivy).
$AltPkgs = @("tv.dumbtv.home", "com.spocky.projengmenu")

if ($Device -notmatch ":") { $Device = "${Device}:5555" }
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
$LauncherPkg = "com.amazon.tv.launcher"
$LauncherHome = "com.amazon.tv.launcher/.ui.HomeActivity_vNext"

function Invoke-Shell([string[]]$ShellArgs) {
    $out = & adb -s $Device shell @ShellArgs
    return @($out | ForEach-Object { "$_".TrimEnd("`r") })
}

Write-Host "== Connecting to $Device"
& adb connect $Device | Out-Null
$state = (& adb -s $Device get-state) | Out-String
if ($state.Trim() -ne "device") {
    Write-Host "ERROR: the Stick at $Device is not reachable. Is it on, on the same Wi-Fi, with ADB debugging on?"
    exit 1
}

# Packages to restore: the disable list plus anything Phase 2 logged, launcher first.
$sources = @(Join-Path $Here "disable-list.txt")
$logged = Join-Path $Here "log\disabled.txt"
if (Test-Path $logged) { $sources += $logged }
$pkgs = New-Object System.Collections.Generic.List[string]
$pkgs.Add($LauncherPkg)
foreach ($f in $sources) {
    foreach ($line in Get-Content $f) {
        $p = ($line -replace "#.*", "").Trim()
        if ($p -match "^[A-Za-z][A-Za-z0-9_.]*$" -and -not $pkgs.Contains($p)) { $pkgs.Add($p) }
    }
}

# Never "restore" something that was already disabled before this project started.
$wasDisabled = @()
$before = Join-Path $Here "backup\packages-before.txt"
if (Test-Path $before) {
    $wasDisabled = Get-Content $before | Where-Object { $_ -notmatch "^#" } |
        ForEach-Object { $c = $_ -split "`t"; if ($c.Count -ge 2 -and $c[1] -eq "disabled") { $c[0] } }
}

$installed = Invoke-Shell @("pm", "list", "packages") | ForEach-Object { $_ -replace "^package:", "" }
$disabledNow = Invoke-Shell @("pm", "list", "packages", "-d") | ForEach-Object { $_ -replace "^package:", "" }

function Invoke-OrSay([string[]]$ShellArgs) {
    if ($DryRun) { Write-Host "  WOULD    $($ShellArgs -join ' ')" }
    else { Invoke-Shell $ShellArgs | Out-Null; Write-Host "  done     $($ShellArgs -join ' ')" }
}

Write-Host "== Replacement launcher"
$foundAlt = $false
foreach ($AltPkg in $AltPkgs) {
    if ($installed -notcontains $AltPkg) { continue }
    $foundAlt = $true
    Write-Host "  -- $AltPkg"
    # Read the accessibility list before force-stop: Android drops a force-stopped app's service from it.
    # Any entry of the package counts, in short (pkg/.Svc) or full (pkg/pkg.Svc) form.
    $a11y = (Invoke-Shell @("settings", "get", "secure", "enabled_accessibility_services")) -join ""
    if ($a11y -eq "null") { $a11y = "" }
    $rest = (($a11y -split ":") | Where-Object { $_ -and -not $_.StartsWith("$AltPkg/") }) -join ":"
    if ($a11y -ne $rest) {
        if ($rest) { Invoke-OrSay @("settings", "put", "secure", "enabled_accessibility_services", $rest) }
        else {
            Invoke-OrSay @("settings", "delete", "secure", "enabled_accessibility_services")
            Invoke-OrSay @("settings", "put", "secure", "accessibility_enabled", "0")
        }
    } else { Write-Host "  ok       its accessibility service is not enabled" }
    Invoke-OrSay @("am", "force-stop", $AltPkg)
    Invoke-OrSay @("appops", "set", $AltPkg, "SYSTEM_ALERT_WINDOW", "default")
    Invoke-OrSay @("dumpsys", "deviceidle", "whitelist", "-$AltPkg")
    Write-Host "  note     $AltPkg stays installed; remove it with: adb uninstall $AltPkg"
}
if (-not $foundAlt) { Write-Host "  ok       no replacement launcher installed" }

# The screensaver: back to Amazon's (or whatever backup\screensaver-before.txt recorded) if it is ours.
$saver = (Invoke-Shell @("settings", "get", "secure", "screensaver_components")) -join ""
if ($saver.StartsWith("tv.dumbtv.home/")) {
    $orig = "com.amazon.ftv.screensaver/.app.services.ScreensaverService"
    $saverBefore = Join-Path $Here "backup\screensaver-before.txt"
    if (Test-Path $saverBefore) {
        $line = Get-Content $saverBefore | Where-Object { $_ -match '^screensaver_components=' } | Select-Object -First 1
        $v = "$line" -replace '^screensaver_components=', ''
        if ($v -and $v -ne "null") { $orig = $v.Trim() }
    }
    Invoke-OrSay @("settings", "put", "secure", "screensaver_components", $orig)
} else {
    Write-Host "  ok       screensaver is not ours"
}

# The HOME takeover's alarm tweak: drop min_futurity=0 unless it was already there before we started,
# keeping any other alarm_manager_constants entries.
$alarm = (Invoke-Shell @("settings", "get", "global", "alarm_manager_constants")) -join ""
if ($alarm -eq "null") { $alarm = "" }
$launcherBefore = Join-Path $Here "backup\launcher-before.txt"
$hadTweak = (Test-Path $launcherBefore) -and ((Get-Content $launcherBefore) -match "min_futurity=0")
if ((($alarm -split ",") -contains "min_futurity=0") -and -not $hadTweak) {
    $keep = (($alarm -split ",") | Where-Object { $_ -and -not $_.StartsWith("min_futurity=") }) -join ","
    if ($keep) { Invoke-OrSay @("settings", "put", "global", "alarm_manager_constants", $keep) }
    else { Invoke-OrSay @("settings", "delete", "global", "alarm_manager_constants") }
} else {
    Write-Host "  ok       no alarm tweak of ours to remove"
}

if ($LauncherOnly) { $pkgs = @($LauncherPkg) }
$nEnable = 0; $nOk = 0; $nAbsent = 0; $nKept = 0; $nFail = 0
foreach ($p in $pkgs) {
    if ($installed -notcontains $p) { Write-Host "  absent   $p"; $nAbsent++; continue }
    if ($wasDisabled -contains $p) { Write-Host "  skip     $p (was already disabled before we started)"; $nKept++; continue }
    if ($disabledNow -notcontains $p) { Write-Host "  ok       $p (already enabled)"; $nOk++; continue }
    if ($DryRun) {
        Write-Host "  WOULD    pm enable $p"; $nEnable++
    } else {
        $out = (Invoke-Shell @("pm", "enable", $p)) -join " "
        if ($out -match "new state: enabled") { Write-Host "  enabled  $p"; $nEnable++ }
        else { Write-Host "  FAILED   ${p}: $out"; $nFail++ }
    }
}

Write-Host "== Home app"
if ($DryRun) {
    if ((Invoke-Shell @("cmd", "package", "help")) -match "set-home-activity") {
        Write-Host "  WOULD    cmd package set-home-activity $LauncherHome   (command available on this device)"
    } else {
        Write-Host "  WARNING  set-home-activity not available; would rely on the stock launcher being the only Home app"
    }
    Write-Host "  WOULD    input keyevent KEYCODE_HOME"
} else {
    Invoke-Shell @("cmd", "package", "set-home-activity", $LauncherHome) | Out-Host
    Invoke-Shell @("input", "keyevent", "KEYCODE_HOME") | Out-Null
    $homeNow = Invoke-Shell @("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
    Write-Host "  current Home: $($homeNow[-1])"
}

Write-Host ""
if ($DryRun) { Write-Host "DRY RUN - nothing was changed. Would enable: $nEnable" }
else { Write-Host "Enabled: $nEnable" }
Write-Host "Already enabled: $nOk   Not on device: $nAbsent   Left alone (pre-existing): $nKept   Failed: $nFail"
if ($nFail -gt 0) { Write-Host "Some packages failed. As a last resort see README.md (factory reset)."; exit 2 }
exit 0
