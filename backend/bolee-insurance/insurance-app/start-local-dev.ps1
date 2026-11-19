$ErrorActionPreference = 'Stop'

$listenPort = 17065
$jarPath = Join-Path $PSScriptRoot 'target/insurance-app.jar'
$logOut = Join-Path $env:TEMP 'bolee-insurance-app.stdout.log'
$logErr = Join-Path $env:TEMP 'bolee-insurance-app.stderr.log'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$localEnvFile = Join-Path $repositoryRoot '.env'
$localEnvKeys = @(
    'NACOS_ADDRESS',
    'AGNO_ENABLED', 'AGNO_SERVICE_URL', 'AGNO_OAUTH_CHAT_TOKEN_URL',
    'AGNO_OAUTH_CHAT_TOKEN_KEY_URL', 'AGNO_JAVA_OAUTH_TOKEN_KEY_URL',
    'AGNO_OAUTH_CLIENT_ID', 'AGNO_OAUTH_SCOPE',
    'AGNO_CHAT_OAUTH_CLIENT_ID', 'AGNO_CHAT_OAUTH_CLIENT_SECRET', 'AGNO_CHAT_OAUTH_SCOPE',
    'AGNO_OPERATOR_CLEANUP_OAUTH_CLIENT_ID', 'AGNO_OPERATOR_CLEANUP_OAUTH_CLIENT_SECRET',
    'AGNO_OPERATOR_CLEANUP_OAUTH_SCOPE', 'AGNO_CONNECT_TIMEOUT_MS', 'AGNO_READ_TIMEOUT_MS',
    'AI_PERSISTENCE_ENABLED', 'AI_PERSISTENCE_CLEANUP_INTERVAL_MS', 'AI_PERSISTENCE_TRACE_CLEANUP_CRON',
    'AI_RETENTION_CONVERSATION_DAYS', 'AI_RETENTION_TRACE_DAYS', 'AI_RETENTION_CLEANUP_TASK_DAYS',
    'AI_IDEMPOTENCY_ENCRYPTION_KEY', 'AI_DELETION_LEDGER_PATH', 'AI_DELETION_LEDGER_REPLAY_ON_STARTUP'
)

if (Test-Path -LiteralPath $localEnvFile) {
    foreach ($line in Get-Content -LiteralPath $localEnvFile) {
        if ($line -match '^\s*#' -or $line -notmatch '^\s*([^=\s]+)\s*=(.*)$') { continue }
        $name = $matches[1]
        if ($name -notin $localEnvKeys) { continue }
        $value = $matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
}

if (-not (Test-Path -LiteralPath $jarPath)) {
    throw "Insurance app jar not found: $jarPath. Build insurance-app first."
}

$nacosAddress = if ([string]::IsNullOrWhiteSpace($env:NACOS_ADDRESS)) { '127.0.0.1:8848' } else { $env:NACOS_ADDRESS }
$nacosBaseUrl = "http://$nacosAddress"
try {
    $null = Invoke-WebRequest -Uri "$nacosBaseUrl/nacos/v1/console/health/readiness" -TimeoutSec 5 -UseBasicParsing
    $null = Invoke-WebRequest -Uri "$nacosBaseUrl/nacos/v1/ns/instance/list?serviceName=bolee-startup-check&groupName=SEATA_GROUP" -TimeoutSec 5 -UseBasicParsing
} catch {
    throw "Nacos config/discovery preflight failed at $nacosAddress. Repair this environment before starting insurance-app."
}

$listener = Get-NetTCPConnection -LocalPort $listenPort -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 1
if ($listener) {
    $owner = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
    if ($owner.CommandLine -like '*insurance-app.jar*') {
        Write-Output "Bolee insurance-app is already listening on $listenPort (PID $($listener.OwningProcess))."
        return
    }
    throw "Port $listenPort is already owned by another process (PID $($listener.OwningProcess))."
}

# Nacos serves UTF-8 YAML; this Windows environment defaults Java to GBK.
$env:JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8'
try {
    $process = Start-Process -FilePath 'java' `
        -ArgumentList @('-jar', $jarPath, '--spring.profiles.active=dev') `
        -WorkingDirectory $repositoryRoot `
        -WindowStyle Hidden `
        -PassThru `
        -RedirectStandardOutput $logOut `
        -RedirectStandardError $logErr
    Write-Output "Started Bolee insurance-app (PID $($process.Id)); health endpoint: http://127.0.0.1:$listenPort/actuator/health"
}
finally {
    Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
}
