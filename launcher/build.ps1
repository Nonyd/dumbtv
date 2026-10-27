# Windows counterpart of build.sh: builds launcher\build\dumbtv-home.apk from the bare SDK tools,
# no Gradle, no libraries. Same steps and flags as build.sh; only the tool names (.exe/.bat) and the
# zip step (.NET instead of the zip command, which Git Bash lacks) differ.
#   Needs a JDK 17 and an Android SDK with platforms;android-$MinSdk and any build-tools.
#   .\build.ps1                 MIN_SDK 25, one APK for Fire OS 6, 7 and 8 (the published release)
#   .\build.ps1 -MinSdk 28      Android 9+ only
#   .\build.ps1 -Net            adds the INTERNET permission, for Top shelf's TMDB pictures; the
#                               default build has none and Top shelf then shows the wallpapers
param([int]$MinSdk = 25, [switch]$Net)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$version = @{}
foreach ($line in Get-Content version.env) {
    if ($line -match '^\s*([A-Z_]+)=(.*)$') { $version[$Matches[1]] = $Matches[2].Trim() }
}

$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { $sdk = "C:\Android\Sdk" }
$jar = Join-Path $sdk "platforms\android-$MinSdk\android.jar"
if (-not (Test-Path $jar)) { throw "$jar missing: sdkmanager 'platforms;android-$MinSdk'" }
$bt = Get-ChildItem (Join-Path $sdk "build-tools") -Directory -ErrorAction SilentlyContinue |
    Sort-Object { [version]($_.Name -replace '[^0-9.].*$', '') } | Select-Object -Last 1 -ExpandProperty FullName
if (-not $bt -or -not (Test-Path "$bt\aapt2.exe")) { throw "no build-tools in ${sdk}: sdkmanager 'build-tools;34.0.0'" }

# build-tools' d8 crashed on class files from JDK 27 (see build.sh), so prefer a JDK 17.
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\javac.exe")) {
    $env:JAVA_HOME = Get-ChildItem "C:\Program Files\Microsoft", "C:\Program Files\Eclipse Adoptium" -Directory `
        -Filter "jdk-17*" -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\javac.exe")) {
    throw "no JDK found: winget install Microsoft.OpenJDK.17"
}
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# Windows PowerShell 5.1 turns a native tool's stderr into errors under "Stop" (keytool and d8 log there),
# so the exit code alone decides success.
function Invoke-Tool([string]$exe, [string[]]$toolArgs) {
    $ErrorActionPreference = "Continue"
    & $exe @toolArgs 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { throw "$(Split-Path -Leaf $exe) failed with exit code $LASTEXITCODE" }
}

$keystore = if ($env:DUMBTV_KEYSTORE) { $env:DUMBTV_KEYSTORE } else { Join-Path $PWD "release.keystore" }
$keypass = if ($env:DUMBTV_KEYPASS) { $env:DUMBTV_KEYPASS } else { "dumbtvhome" }
if (-not (Test-Path $keystore)) {
    Invoke-Tool "$env:JAVA_HOME\bin\keytool.exe" @("-genkeypair", "-keystore", $keystore, "-alias", "home",
        "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000", "-storepass", $keypass,
        "-keypass", $keypass, "-dname", "CN=dumbtv home")
    Write-Host "created signing key $keystore (keep it: updates must be signed with the same key)"
}

$work = Join-Path ([IO.Path]::GetTempPath()) ("dumbtv-home." + [IO.Path]::GetRandomFileName())
New-Item -ItemType Directory -Force "$work\gen", "$work\classes", "build" | Out-Null
try {
    $manifest = "AndroidManifest.xml"
    if ($Net) {
        $manifest = "$work\AndroidManifest.xml"
        $text = Get-Content AndroidManifest.xml -Raw
        $text = $text -replace '(\s*)<application', "`n    <uses-permission android:name=`"android.permission.INTERNET`" />`n`$1<application"
        [IO.File]::WriteAllText($manifest, $text)
    }
    Invoke-Tool "$bt\aapt2.exe" @("compile", "--dir", "res", "-o", "$work\res.zip")
    Invoke-Tool "$bt\aapt2.exe" @("link", "-I", $jar, "--manifest", $manifest, "$work\res.zip",
        "--min-sdk-version", "$MinSdk", "--target-sdk-version", "28",
        "--version-code", $version.VERSION_CODE, "--version-name", $version.VERSION_NAME,
        "--java", "$work\gen", "-o", "$work\app.apk")

    Get-ChildItem src, "$work\gen" -Recurse -Filter *.java | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' } |
        Set-Content -Encoding ascii "$work\sources.txt"
    Invoke-Tool "$env:JAVA_HOME\bin\javac.exe" @("--release", "8", "-Xlint:all", "-Xlint:-options", "-Werror",
        "-encoding", "UTF-8", "-classpath", $jar, "-d", "$work\classes", "@$work\sources.txt")

    Get-ChildItem "$work\classes" -Recurse -Filter *.class | ForEach-Object { $_.FullName } |
        Set-Content -Encoding ascii "$work\classes.txt"
    Invoke-Tool "$bt\d8.bat" @("--release", "--min-api", "$MinSdk", "--lib", $jar, "--output", $work, "@$work\classes.txt")

    Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::Open("$work\app.apk", [IO.Compression.ZipArchiveMode]::Update)
    try { [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$work\classes.dex", "classes.dex") }
    finally { $zip.Dispose() }

    Invoke-Tool "$bt\zipalign.exe" @("-f", "-p", "4", "$work\app.apk", "$work\aligned.apk")
    Invoke-Tool "$bt\apksigner.bat" @("sign", "--ks", $keystore, "--ks-pass", "pass:$keypass", "--key-pass", "pass:$keypass",
        "--out", "build\dumbtv-home.apk", "$work\aligned.apk")
    Invoke-Tool "$bt\apksigner.bat" @("verify", "build\dumbtv-home.apk")
} finally {
    Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue
}

$perms = @(& "$bt\aapt2.exe" dump permissions build\dumbtv-home.apk | Select-String 'uses-permission').Count
Write-Host ("built build\dumbtv-home.apk ({0})  {1} bytes  {2} uses-permission entries" -f $(if ($Net) { "net" } else { "offline" }), (Get-Item build\dumbtv-home.apk).Length, $perms)
