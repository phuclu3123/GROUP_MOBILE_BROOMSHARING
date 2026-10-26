param([switch]$Stop)

# Chạy Java và tunnel HTTPS bằng tiến trình ẩn; không đăng ký Windows service hoặc in secret.
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$taskBackend = Join-Path $taskRoot 'backend/payments'
$taskRuntime = Join-Path $taskBackend '.runtime'
$taskStateFile = Join-Path $taskRuntime 'demo-processes.json'

function Stop-Demo {
    if (!(Test-Path -LiteralPath $taskStateFile)) { return }
    $taskState = Get-Content -LiteralPath $taskStateFile -Raw | ConvertFrom-Json
    foreach ($entry in @($taskState.processes)) {
        $taskProcess = Get-Process -Id $entry.id -ErrorAction SilentlyContinue
        if ($null -ne $taskProcess -and $taskProcess.Path -eq $entry.path -and
            $taskProcess.StartTime.ToUniversalTime().Ticks.ToString() -eq $entry.startedTicks) {
            Stop-Process -Id $entry.id
        }
    }
    Remove-Item -LiteralPath $taskStateFile
}

if ($Stop) { Stop-Demo; Write-Output 'Stopped owned BroomSharing payment demo processes.'; exit }
New-Item -ItemType Directory -Force -Path $taskRuntime | Out-Null
$taskCredential = Join-Path $taskBackend 'credentials/service-account.json'
if (!(Test-Path -LiteralPath $taskCredential)) { throw 'Missing backend/payments/credentials/service-account.json.' }
$taskTunnel = Join-Path $taskRuntime 'cloudflared.exe'
if (!(Test-Path -LiteralPath $taskTunnel)) { throw 'Missing verified cloudflared portable runtime.' }
$taskRelease = Get-Content -LiteralPath (Join-Path $taskRuntime 'cloudflared-release.json') -Raw | ConvertFrom-Json
if ((Get-FileHash -LiteralPath $taskTunnel -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskRelease.sha256) {
    throw 'cloudflared does not match the verified official release digest.'
}
$taskJava = Join-Path ${env:ProgramFiles} 'Android/Android Studio/jbr/bin/java.exe'
if (!(Test-Path -LiteralPath $taskJava)) { throw 'Set up the Android Studio JBR before starting this demo.' }
$taskLib = Join-Path $taskBackend 'build/install/broomsharing-payments/lib/*'
if (!(Test-Path -LiteralPath (Join-Path $taskBackend 'build/install/broomsharing-payments/lib'))) {
    throw 'Run gradlew.bat -p backend/payments installDist first.'
}
Stop-Demo
if (Get-NetTCPConnection -State Listen -LocalPort 8090 -ErrorAction SilentlyContinue) {
    throw 'Port 8090 is owned by another process; it has been left running.'
}

function Set-LocalValue([string]$Path, [string]$Name, [string]$Value) {
    $taskLines = if (Test-Path -LiteralPath $Path) { [IO.File]::ReadAllLines($Path) } else { @() }
    if (!(Test-Path -LiteralPath ($Path + '.before-payos'))) {
        [IO.File]::WriteAllLines($Path + '.before-payos', $taskLines, [Text.UTF8Encoding]::new($false))
    }
    $taskFound = $false
    $taskUpdated = @($taskLines | ForEach-Object {
        if ($_.StartsWith($Name + '=')) { $taskFound = $true; $Name + '=' + $Value } else { $_ }
    })
    if (!$taskFound) { $taskUpdated += $Name + '=' + $Value }
    [IO.File]::WriteAllLines($Path, $taskUpdated, [Text.UTF8Encoding]::new($false))
}

$taskOwned = [Collections.Generic.List[object]]::new()
function Save-OwnedProcess($Process) {
    $taskOwned.Add(@{id=$Process.Id; path=$Process.Path; startedTicks=$Process.StartTime.ToUniversalTime().Ticks.ToString()})
    @{processes=@($taskOwned.ToArray())} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $taskStateFile -Encoding UTF8
}

try {
    $taskTunnelLog = Join-Path $taskRuntime 'tunnel.log'
    if (Test-Path -LiteralPath $taskTunnelLog) { Move-Item -LiteralPath $taskTunnelLog -Destination ($taskTunnelLog + '.' + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()) }
    $taskTunnelProcess = Start-Process -FilePath $taskTunnel -ArgumentList @('tunnel','--no-autoupdate','--protocol','http2','--url','http://127.0.0.1:8090','--logfile',$taskTunnelLog) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $taskRuntime 'tunnel-stdout.log') -RedirectStandardError (Join-Path $taskRuntime 'tunnel-stderr.log')
    Save-OwnedProcess $taskTunnelProcess
    $taskUrl = $null
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        if (Test-Path -LiteralPath $taskTunnelLog) {
            $taskLogStream = [IO.FileStream]::new($taskTunnelLog, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
            $taskLogReader = [IO.StreamReader]::new($taskLogStream)
            try { $taskText = $taskLogReader.ReadToEnd() } finally { $taskLogReader.Dispose() }
            $taskMatch = [regex]::Match($taskText, 'https://[a-z0-9-]+\.trycloudflare\.com')
            if ($taskMatch.Success) { $taskUrl = $taskMatch.Value; break }
        }
        Start-Sleep -Seconds 2
    }
    if (!$taskUrl) { throw 'HTTPS tunnel did not return a URL; inspect its local log.' }
    Set-LocalValue (Join-Path $taskBackend '.env') 'PUBLIC_BASE_URL' $taskUrl
    Set-LocalValue (Join-Path $taskRoot '.env') 'PAYMENT_API_BASE_URL' $taskUrl
    $taskServerProcess = Start-Process -FilePath $taskJava -ArgumentList @('-cp', ('"' + $taskLib + '"'), 'vn.edu.ueh.broomsharing.payment.PaymentServer') -WorkingDirectory $taskBackend -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $taskRuntime 'server-stdout.log') -RedirectStandardError (Join-Path $taskRuntime 'server-stderr.log')
    Save-OwnedProcess $taskServerProcess
    $taskHealthy = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        if ($taskServerProcess.HasExited) { throw 'Java backend exited; inspect server-stderr.log. No secrets are printed here.' }
        try {
            $taskHealth = Invoke-RestMethod -Uri 'http://127.0.0.1:8090/health' -TimeoutSec 2
            if ($taskHealth.status -eq 'ok') { $taskHealthy = $true; break }
        } catch { }
        Start-Sleep -Seconds 2
    }
    if (!$taskHealthy) { throw 'Java backend did not become ready within the startup window.' }
    $taskPublicHealth = Invoke-RestMethod -Uri ($taskUrl + '/health') -TimeoutSec 30
    if ($taskPublicHealth.status -ne 'ok') { throw 'Public HTTPS health check failed.' }
    @{baseUrl=$taskUrl; webhookUrl=($taskUrl + '/api/payos/webhook'); startedAt=[DateTime]::UtcNow.ToString('o'); localReady=$true; publicReady=$true; providerWebhookChanged=$false; relayRegistered=$false} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskRuntime 'demo-ready.json') -Encoding UTF8
    Write-Output ('Java payment backend ready: ' + $taskUrl)
    Write-Output 'Android .env updated. Rebuild the APK for this temporary HTTPS address.'
    Write-Output 'The existing payOS webhook has not been changed.'
    try { & (Join-Path $PSScriptRoot 'connect-relay.ps1') -Quiet }
    catch { Write-Warning 'Java backend is ready; WEB_TCC relay registration still needs completion.' }
} catch {
    Stop-Demo
    throw
}
