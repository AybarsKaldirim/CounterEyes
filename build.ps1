# Counter Eyes — APK derleme (Gradle gerekmez)
# Kullanım:
#   powershell -ExecutionPolicy Bypass -File .\build.ps1 -Sdk "C:\Android\sdk"
# Gerekli: Android SDK (build-tools 35.0.0 + platforms/android-35), JDK 11+ (javac/java/keytool), Python 3.
param(
    [string]$Sdk = $env:ANDROID_HOME,
    [string]$VersionName = '1.1.0',
    [int]$VersionCode = 10,
    [string]$OutName = 'Counter-Eyes'
)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

if (-not $Sdk -or -not (Test-Path -LiteralPath $Sdk)) {
    $candidates = @(
        "$env:LOCALAPPDATA\Android\Sdk",
        "$env:USERPROFILE\AppData\Local\Android\Sdk",
        'C:\Android\sdk',
        'C:\Users\aybar\Desktop\AybarsPi\tools\sdk'
    )
    $Sdk = $candidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}
if (-not $Sdk) { throw "Android SDK bulunamadı. -Sdk parametresiyle yolu ver." }

$bt = Join-Path $Sdk 'build-tools/35.0.0'
$platform = Join-Path $Sdk 'platforms/android-35/android.jar'
foreach ($needed in @("$bt/aapt2.exe", $platform)) {
    if (-not (Test-Path -LiteralPath $needed)) { throw "Eksik araç: $needed (SDK 35 kurulu mu?)" }
}

$build = Join-Path $root ('build-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
$classes = Join-Path $build 'classes'
$dex = Join-Path $build 'dex'
New-Item -ItemType Directory $classes, $dex -Force | Out-Null

function Invoke-BuildTool([string]$Exe, [string[]]$ArgsList) {
    & $Exe @ArgsList
    if ($LASTEXITCODE -ne 0) { throw "Araç başarısız: $Exe ($LASTEXITCODE)" }
}

$res = Join-Path $build 'resources.zip'
Invoke-BuildTool "$bt/aapt2.exe" @('compile', '--dir', "$root/res", '-o', $res)

$unsigned = Join-Path $build 'unsigned.apk'
$aligned = Join-Path $build 'aligned.apk'
$signed = Join-Path $build "$OutName-$VersionName.apk"
Invoke-BuildTool "$bt/aapt2.exe" @('link', '-o', $unsigned, '--manifest', "$root/AndroidManifest.xml", '-I', $platform,
    '--min-sdk-version', '24', '--target-sdk-version', '35', '--version-code', "$VersionCode",
    '--version-name', $VersionName, '-A', "$root/assets", $res)

$sources = @(Get-ChildItem -LiteralPath "$root/src" -Recurse -Filter '*.java' -File | ForEach-Object FullName)
Invoke-BuildTool 'javac' (@('--release', '8', '-encoding', 'UTF-8', '-classpath', $platform, '-d', $classes) + $sources)

$classFiles = @(Get-ChildItem -LiteralPath $classes -Recurse -Filter '*.class' -File | ForEach-Object FullName)
Invoke-BuildTool 'java' (@('-cp', "$bt/lib/d8.jar", 'com.android.tools.r8.D8', '--lib', $platform,
    '--min-api', '24', '--output', $dex) + $classFiles)

$packed = Join-Path $build 'packed.apk'
$zipHelper = Join-Path $build 'add-dex.py'
$zipLines = @(
    'import sys, zipfile',
    'src = zipfile.ZipFile(sys.argv[1])',
    'dst = zipfile.ZipFile(sys.argv[3], "w", zipfile.ZIP_DEFLATED)',
    'for item in src.infolist():',
    '    dst.writestr(item.filename, src.read(item), compress_type=item.compress_type)',
    'dst.write(sys.argv[2], "classes.dex")',
    'dst.close()',
    'src.close()'
)
Set-Content -LiteralPath $zipHelper -Value $zipLines -Encoding ASCII
Invoke-BuildTool 'python' @($zipHelper, $unsigned, "$dex/classes.dex", $packed)
Invoke-BuildTool "$bt/zipalign.exe" @('-f', '4', $packed, $aligned)

# Kendi imza anahtarın: repo'da yok, ilk derlemede üretilir. Güncellemelerde aynı dosyayı koru.
$key = Join-Path $root 'counter-eyes.keystore'
if (-not (Test-Path -LiteralPath $key)) {
    Invoke-BuildTool 'keytool' @('-genkeypair', '-keystore', $key, '-storepass', 'counter-eyes', '-keypass', 'counter-eyes',
        '-alias', 'countereyes', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '3650',
        '-dname', 'CN=Counter Eyes Build')
    Write-Host "Yeni imza anahtarı üretildi: $key (yedekle, güncellemelerde aynı dosya gerekir)"
}
Invoke-BuildTool 'java' @('-jar', "$bt/lib/apksigner.jar", 'sign', '--ks', $key, '--ks-key-alias', 'countereyes',
    '--ks-pass', 'pass:counter-eyes', '--key-pass', 'pass:counter-eyes', '--out', $signed, $aligned)
Invoke-BuildTool 'java' @('-jar', "$bt/lib/apksigner.jar", 'verify', '--verbose', $signed)
Invoke-BuildTool "$bt/zipalign.exe" @('-c', '4', $signed)

$hash = (Get-FileHash -LiteralPath $signed -Algorithm SHA256).Hash
Write-Host ""
Write-Host "APK=$signed"
Write-Host "SHA256=$hash"
Write-Host "Kurulum: adb install -r `"$signed`""
