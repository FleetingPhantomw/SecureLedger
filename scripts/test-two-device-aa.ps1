param(
    [Parameter(Mandatory)][string]$EmulatorSerial,
    [Parameter(Mandatory)][string]$PhoneSerial,
    [Parameter(Mandatory)][string]$EmulatorPassword,
    [Parameter(Mandatory)][string]$PhonePassword
)
$ErrorActionPreference = 'Stop'
$testRoot = Join-Path (Split-Path -Parent $PSScriptRoot) 'app\build\two-device-aa'
New-Item -ItemType Directory -Force -Path $testRoot | Out-Null
$testProjectId = 'p_device_test_' + [guid]::NewGuid().ToString('N')
function Run-Stage([string]$serial, [string]$password, [string]$stage) {
    $output = & adb -s $serial shell am instrument -w -e class com.secureledger.TwoDeviceAaScenarioTest -e aaScenario true -e stage $stage -e testProjectId $testProjectId -e vaultPassword $password com.secureledger.test/androidx.test.runner.AndroidJUnitRunner 2>&1
    $text = $output -join "`n"
    if ($LASTEXITCODE -ne 0 -or $text -notmatch 'OK \(1 test\)' -or $text -match 'FAILURES|Process crashed') { throw "$serial stage $stage failed: $text" }
    Write-Output "$serial $stage passed"
}
function Copy-Exchange([string]$from, [string]$to, [string]$name) {
    if ($name -notin @('request.txt', 'invitation.txt', 'sync.txt', 'approval.txt')) { throw 'Invalid fixture name' }
    $exportInfo = [System.Diagnostics.ProcessStartInfo]::new('adb')
    foreach ($argument in @('-s', $from, 'exec-out', 'run-as', 'com.secureledger', 'cat', "files/aa-device-test/$name")) { $exportInfo.ArgumentList.Add($argument) }
    $exportInfo.UseShellExecute = $false
    $exportInfo.RedirectStandardOutput = $true
    $exportProcess = [System.Diagnostics.Process]::Start($exportInfo)
    $artifactFile = [System.IO.File]::Create((Join-Path $testRoot $name))
    try { $exportProcess.StandardOutput.BaseStream.CopyTo($artifactFile) } finally { $artifactFile.Dispose() }
    $exportProcess.WaitForExit()
    if ($exportProcess.ExitCode -ne 0) { throw 'Fixture export failed' }
    & adb -s $to shell run-as com.secureledger mkdir -p files/aa-device-test | Out-Null
    $importInfo = [System.Diagnostics.ProcessStartInfo]::new('adb')
    foreach ($argument in @('-s', $to, 'shell', "run-as com.secureledger sh -c 'cat > files/aa-device-test/$name'")) { $importInfo.ArgumentList.Add($argument) }
    $importInfo.UseShellExecute = $false
    $importInfo.RedirectStandardInput = $true
    $importProcess = [System.Diagnostics.Process]::Start($importInfo)
    $artifactFile = [System.IO.File]::OpenRead((Join-Path $testRoot $name))
    try { $artifactFile.CopyTo($importProcess.StandardInput.BaseStream) } finally { $artifactFile.Dispose(); $importProcess.StandardInput.Close() }
    $importProcess.WaitForExit()
    if ($importProcess.ExitCode -ne 0) { throw 'Fixture import failed' }
}
Run-Stage $PhoneSerial $PhonePassword 'request'
Copy-Exchange $PhoneSerial $EmulatorSerial 'request.txt'
Run-Stage $EmulatorSerial $EmulatorPassword 'invite'
Copy-Exchange $EmulatorSerial $PhoneSerial 'invitation.txt'
Copy-Exchange $EmulatorSerial $PhoneSerial 'sync.txt'
Run-Stage $PhoneSerial $PhonePassword 'join'
Copy-Exchange $PhoneSerial $EmulatorSerial 'sync.txt'
Run-Stage $EmulatorSerial $EmulatorPassword 'settle'
Copy-Exchange $EmulatorSerial $PhoneSerial 'approval.txt'
Copy-Exchange $EmulatorSerial $PhoneSerial 'sync.txt'
Run-Stage $PhoneSerial $PhonePassword 'approve'
Copy-Exchange $PhoneSerial $EmulatorSerial 'approval.txt'
Run-Stage $EmulatorSerial $EmulatorPassword 'merge'
Write-Output "Project ${testProjectId}: 3 bills, 240.01 CNY, two independent approvals verified"
