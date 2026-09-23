param()

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$jdk = Join-Path $projectRoot ".jdk17\jdk-17.0.20.1+1"
$sdk = Join-Path $projectRoot ".android-sdk"
$keytool = Join-Path $jdk "bin\keytool.exe"
$apksigner = Join-Path $sdk "build-tools\35.0.0\apksigner.bat"
$signingDir = Join-Path $projectRoot ".signing"
$keystore = Join-Path $signingDir "secureledger-local-release.p12"
$secretFile = Join-Path $signingDir "local-release-password.dpapi"
$apk = Join-Path $projectRoot "app\build\outputs\apk\release\app-release.apk"

if (-not (Test-Path $keytool) -or -not (Test-Path $apksigner)) {
    throw "JDK 17 or Android SDK 35 build tools are missing from this workspace"
}
if (-not (Test-Path $signingDir)) { New-Item -ItemType Directory -Path $signingDir | Out-Null }
if ((Test-Path $keystore) -xor (Test-Path $secretFile)) {
    throw "Local signing key and protected password must exist together. Restore both from a backup."
}
if (-not (Test-Path $keystore)) {
    $password = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(36))
    & $keytool -genkeypair -alias secureledger -keyalg RSA -keysize 4096 `
        -sigalg SHA256withRSA -validity 10000 -storetype PKCS12 -keystore $keystore `
        -storepass $password -keypass $password `
        -dname "CN=SecureLedger Local Release, OU=Local Build" -noprompt
    if ($LASTEXITCODE -ne 0) { throw "Could not create local signing key" }
    $protected = ConvertTo-SecureString $password -AsPlainText -Force | ConvertFrom-SecureString
    Set-Content -LiteralPath $secretFile -Value $protected -NoNewline
}

$securePassword = Get-Content -LiteralPath $secretFile -Raw | ConvertTo-SecureString
$password = [System.Net.NetworkCredential]::new("", $securePassword).Password
$env:JAVA_HOME = $jdk
$env:ANDROID_SDK_ROOT = $sdk
$env:ANDROID_HOME = $sdk
$env:SECURELEDGER_RELEASE_KEYSTORE = $keystore
$env:SECURELEDGER_RELEASE_STORE_PASSWORD = $password
$env:SECURELEDGER_RELEASE_KEY_ALIAS = "secureledger"
$env:SECURELEDGER_RELEASE_KEY_PASSWORD = $password
try {
    Push-Location $projectRoot
    try {
        & .\gradlew.bat assembleRelease --console=plain
        if ($LASTEXITCODE -ne 0) { throw "Release build failed" }
    } finally { Pop-Location }
    if (-not (Test-Path $apk)) { throw "Signed release APK was not produced" }
    & $apksigner verify --verbose $apk
    if ($LASTEXITCODE -ne 0) { throw "Release APK signature verification failed" }
    Write-Output "Installable local release APK: $apk"
    Write-Output "Back up the .p12 signing key and its password separately; the DPAPI file alone is not portable."
} finally {
    Remove-Item Env:SECURELEDGER_RELEASE_KEYSTORE, Env:SECURELEDGER_RELEASE_STORE_PASSWORD, Env:SECURELEDGER_RELEASE_KEY_ALIAS, Env:SECURELEDGER_RELEASE_KEY_PASSWORD -ErrorAction SilentlyContinue
    $password = $null
}
