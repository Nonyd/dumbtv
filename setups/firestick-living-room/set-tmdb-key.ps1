# Saves your TMDB API key to tmdb.key (in this folder) and sends it to the Fire TV Stick.
# The key is typed at a hidden prompt and never printed. Usage: .\set-tmdb-key.ps1 [-Device 192.168.100.3:5555]
param([string]$Device = "192.168.100.3:5555")

Set-Location $PSScriptRoot
$secure = Read-Host "Paste your TMDB API Key (the short one), then press Enter" -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try { $key = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr).Trim() }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }

if ($key -notmatch '^[0-9a-fA-F]{32}$') {
    Write-Host "That doesn't look like a TMDB API Key (32 letters/digits). Nothing was saved."
    Write-Host "Use the short 'API Key', not the long 'API Read Access Token'."
    exit 1
}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot "tmdb.key"), $key)
$key = $null
Write-Host "Saved tmdb.key ($((Get-Item tmdb.key).Length) bytes)."

& adb connect $Device | Out-Null
& adb -s $Device push tmdb.key /sdcard/Android/media/tv.dumbtv.home/tmdb.key
$size = (& adb -s $Device shell "wc -c < /sdcard/Android/media/tv.dumbtv.home/tmdb.key" 2>$null) -join ""
if ($size.Trim() -eq "32") { Write-Host "On the Stick: OK. Press Home on the remote." }
else { Write-Host "The push didn't arrive on the Stick (got '$size'). Is the Stick on and on the same Wi-Fi?"; exit 2 }
