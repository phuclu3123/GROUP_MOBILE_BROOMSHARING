param()

# Đổi bản Java nhưng giữ nguyên tunnel và URL trong APK đang cài.
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$taskBackend = Join-Path $taskRoot 'backend/payments'
$taskRuntime = Join-Path $taskBackend '.runtime'
$taskStateFile = Join-Path $taskRuntime 'demo-processes.json'
$taskState = Get-Content -LiteralPath $taskStateFile -Raw | ConvertFrom-Json
$taskJava = Join-Path ${env:ProgramFiles} 'Android/Android Studio/jbr/bin/java.exe'
$taskSurvivors = [Collections.Generic.List[object]]::new()
foreach ($entry in $taskState.processes) {
    $taskProcess = Get-Process -Id $entry.id -ErrorAction SilentlyContinue
    if ($null -eq $taskProcess) { continue }
    if ($taskProcess.StartTime.ToUniversalTime().Ticks.ToString() -ne $entry.startedTicks) {
        throw 'A saved PID has been reused. No unrelated process was stopped.'
    }
    $taskCim = Get-CimInstance Win32_Process -Filter "ProcessId = $($entry.id)"
    if ($taskCim.ExecutablePath -eq $taskJava -and $taskCim.CommandLine -like '*vn.edu.ueh.broomsharing.payment.PaymentServer*') {
        Stop-Process -Id $entry.id
        $taskProcess.WaitForExit(10000) | Out-Null
    } else {
        if ($taskCim.ExecutablePath -ne (Join-Path $taskRuntime 'cloudflared.exe')) {
            throw 'Unexpected saved process. Review the demo process state.'
        }
        $taskSurvivors.Add($entry)
    }
}
@{processes=@($taskSurvivors.ToArray())} | ConvertTo-Json -Depth 5 |
    Set-Content -LiteralPath $taskStateFile -Encoding UTF8
if (Get-NetTCPConnection -State Listen -LocalPort 8090 -ErrorAction SilentlyContinue) {
    throw 'Port 8090 is still in use; its process has been left alone.'
}
$taskPreviousJava = $env:JAVA_HOME
try {
    $env:JAVA_HOME = Split-Path (Split-Path $taskJava)
    & (Join-Path $taskRoot 'gradlew.bat') -p $taskBackend installDist --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Backend build failed; server was not restarted.' }
} finally { $env:JAVA_HOME = $taskPreviousJava }
$taskLib = Join-Path $taskBackend 'build/install/broomsharing-payments/lib/*'
$taskServer = Start-Process -FilePath $taskJava -ArgumentList @('-cp', ('"' + $taskLib + '"'), 'vn.edu.ueh.broomsharing.payment.PaymentServer') `
    -WorkingDirectory $taskBackend -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $taskRuntime 'server-stdout.log') `
    -RedirectStandardError (Join-Path $taskRuntime 'server-stderr.log')
$taskSurvivors.Add(@{id=$taskServer.Id;path=$taskJava;startedTicks=$taskServer.StartTime.ToUniversalTime().Ticks.ToString()})
@{processes=@($taskSurvivors.ToArray())} | ConvertTo-Json -Depth 5 |
    Set-Content -LiteralPath $taskStateFile -Encoding UTF8
for ($attempt = 0; $attempt -lt 40; $attempt++) {
    if ($taskServer.HasExited) { throw 'Java exited. Inspect .runtime/server-stderr.log.' }
    try {
        $taskHealth = Invoke-RestMethod 'http://127.0.0.1:8090/health' -TimeoutSec 2
        if ($taskHealth.status -eq 'ok' -and $taskHealth.paymentFlow -eq 'INSTANT_5M') {
            Write-Output 'Java ready: instant payment with five-minute holds; tunnel URL preserved.'
            exit 0
        }
    } catch { }
    Start-Sleep -Seconds 1
}
throw 'Java did not become ready. Inspect .runtime/server-stderr.log.'
