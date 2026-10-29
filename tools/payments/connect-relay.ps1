param([switch]$Quiet)

# Đăng ký đường chuyển tiếp ở WEB_TCC; không đổi URL webhook tại payOS.
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$taskBackend = Join-Path $taskRoot 'backend/payments'
$taskConfig = @{}
foreach ($line in [IO.File]::ReadAllLines((Join-Path $taskBackend '.env'))) {
    if ($line.Trim().StartsWith('#') -or !$line.Contains('=')) { continue }
    $taskParts = $line -split '=', 2
    $taskConfig[$taskParts[0].Trim()] = $taskParts[1].Trim().Trim('"').Trim("'")
}
$taskBase = $taskConfig['PAYOS_RELAY_BASE_URL']
$taskToken = $taskConfig['PAYOS_RELAY_TOKEN']
$taskDestination = $taskConfig['PUBLIC_BASE_URL'].TrimEnd('/') + '/api/payos/webhook'
if ($taskBase -ne 'https://ueh-tcc-backend.onrender.com' -or [string]::IsNullOrWhiteSpace($taskToken)) {
    throw 'Configure the WEB_TCC relay origin and its existing server-only setup token first.'
}
$taskStatus = Invoke-RestMethod -Uri ($taskBase + '/api/payos/webhook') -TimeoutSec 30
if ($taskStatus.relayVersion -ne 'broomsharing-v1') {
    throw 'WEB_TCC is still running the previous build. Deploy relay commit d007164 on Render first.'
}
$taskResponse = Invoke-RestMethod -Uri ($taskBase + '/api/payos/relay/broomsharing') -Method Post -Headers @{'x-payos-confirm-token'=$taskToken} -ContentType 'application/json' -Body (@{destination=$taskDestination} | ConvertTo-Json -Compress) -TimeoutSec 30
if (!$taskResponse.success -or $taskResponse.destination -ne $taskDestination) {
    throw 'The WEB_TCC relay did not confirm the intended destination.'
}
$taskReadyFile = Join-Path $taskBackend '.runtime/demo-ready.json'
$taskReady = Get-Content -LiteralPath $taskReadyFile -Raw | ConvertFrom-Json
$taskReady.PSObject.Properties.Remove('webhookRegistered')
$taskReady | Add-Member -NotePropertyName relayRegistered -NotePropertyValue $true -Force
$taskReady | Add-Member -NotePropertyName providerWebhookUnchanged -NotePropertyValue $true -Force
$taskReady | Add-Member -NotePropertyName webhookIngress -NotePropertyValue ($taskBase + '/api/payos/webhook') -Force
$taskReady | ConvertTo-Json | Set-Content -LiteralPath $taskReadyFile -Encoding UTF8
if (!$Quiet) { Write-Output 'WEB_TCC webhook relay connected to the running Java backend. payOS configuration unchanged.' }
