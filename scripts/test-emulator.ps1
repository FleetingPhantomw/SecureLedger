param(
    [string]$AvdName = "SecureLedger_API35",
    [string]$DeviceSerial = "emulator-5558"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$projectSdk = Join-Path $projectRoot ".android-sdk"
$projectJdk = Join-Path $projectRoot ".jdk17\jdk-17.0.20.1+1"
$userSdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$adb = Join-Path $userSdk "platform-tools\adb.exe"
$emulator = Join-Path $userSdk "emulator\emulator.exe"

if (-not (Test-Path $adb) -or -not (Test-Path $emulator)) {
    throw "Android Studio SDK or Emulator is not installed under $userSdk"
}

$env:JAVA_HOME = $projectJdk
$env:ANDROID_SDK_ROOT = $projectSdk
$env:ANDROID_HOME = $projectSdk

$connected = & $adb devices | Select-String -SimpleMatch $DeviceSerial
if (-not $connected) {
    Start-Process -FilePath $emulator -WindowStyle Hidden -ArgumentList @(
        "-avd", $AvdName, "-no-window", "-no-audio", "-no-boot-anim",
        "-gpu", "swiftshader_indirect", "-no-snapshot-load"
    )
    & $adb wait-for-device
}

$deadline = [DateTime]::UtcNow.AddMinutes(3)
do {
    $bootCompleted = (& $adb -s $DeviceSerial shell getprop sys.boot_completed 2>$null).Trim()
    if ($bootCompleted -eq "1") { break }
    Start-Sleep -Seconds 2
} while ([DateTime]::UtcNow -lt $deadline)
if ($bootCompleted -ne "1") { throw "Emulator did not finish booting" }

Push-Location $projectRoot
try {
    & .\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }
    & $adb -s $DeviceSerial install -r "app\build\outputs\apk\debug\app-debug.apk"
    & $adb -s $DeviceSerial install -r "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
    & $adb -s $DeviceSerial shell pm grant com.secureledger.test android.permission.CAMERA
    $instrumentationOutput = & $adb -s $DeviceSerial shell am instrument -w -r com.secureledger.test/androidx.test.runner.AndroidJUnitRunner 2>&1
    $instrumentationExitCode = $LASTEXITCODE
    $instrumentationOutput | Write-Output
    $instrumentationText = $instrumentationOutput -join "`n"
    if (
        $instrumentationExitCode -ne 0 -or
        $instrumentationText -match "FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed" -or
        $instrumentationText -notmatch "(?m)^OK \(\d+ tests?\)$"
    ) {
        throw "Instrumentation tests failed"
    }
} finally {
    & $adb -s $DeviceSerial uninstall com.secureledger.test | Out-Null
    Pop-Location
}
