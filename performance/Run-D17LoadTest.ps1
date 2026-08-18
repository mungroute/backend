[CmdletBinding()]
param(
    [ValidateSet('rest', 'websocket', 'all')]
    [string]$Mode = 'all',
    [ValidateRange(1, 5000)]
    [int[]]$Targets = @(500, 1000, 2500, 5000),
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$WsUrl = '',
    [string]$FixturePath = (Join-Path $PSScriptRoot 'presence-fixtures.json'),
    [string]$OutputRoot = (Join-Path (Split-Path $PSScriptRoot -Parent) 'reports/d17'),
    [switch]$Smoke,
    [switch]$DirectRamp,
    [switch]$ContinueOnFailure,
    [switch]$GenerateMissingFixtures
)

$ErrorActionPreference = 'Stop'

function Get-DotEnvValue([string]$Name) {
    $envPath = Join-Path (Split-Path $PSScriptRoot -Parent) '.env'
    if (-not (Test-Path -LiteralPath $envPath)) { return $null }
    $line = Get-Content -LiteralPath $envPath | Where-Object { $_ -match "^\s*$([regex]::Escape($Name))=(.*)$" } | Select-Object -First 1
    if ($line -and $line -match '^[^=]+=(.*)$') { return $matches[1].Trim() }
    return $null
}

function Get-ListenerProcessId([int]$Port) {
    $line = netstat -ano -p TCP | Where-Object {
        $_ -match "^\s*TCP\s+\S+:$Port\s+\S+\s+LISTENING\s+(\d+)\s*$"
    } | Select-Object -First 1
    if (-not $line -or $line -notmatch 'LISTENING\s+(\d+)\s*$') {
        throw "Could not resolve the backend process listening on port $Port."
    }
    return [int]$matches[1]
}

function Start-D17Monitor([string]$OutputPath, [int]$BackendProcessId, [int]$RedisPort) {
    return Start-Job -ArgumentList $OutputPath, $BackendProcessId, $RedisPort -ScriptBlock {
        param($Path, $ProcessId, $RedisPortNumber)

        function Get-RedisInfo([int]$Port) {
            $client = [System.Net.Sockets.TcpClient]::new()
            try {
                $client.ReceiveTimeout = 2000
                $client.SendTimeout = 2000
                $client.Connect('127.0.0.1', $Port)
                $stream = $client.GetStream()
                $writer = [System.IO.StreamWriter]::new($stream, [System.Text.Encoding]::ASCII, 1024, $true)
                $writer.NewLine = "`r`n"
                $writer.WriteLine('INFO')
                $writer.Flush()

                $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::ASCII, $false, 4096, $true)
                $header = $reader.ReadLine()
                if (-not $header.StartsWith('$')) { throw "Unexpected Redis INFO response: $header" }
                $length = [int]$header.Substring(1)
                $buffer = [char[]]::new($length)
                $read = 0
                while ($read -lt $length) {
                    $count = $reader.Read($buffer, $read, $length - $read)
                    if ($count -le 0) { break }
                    $read += $count
                }
                return -join $buffer[0..($read - 1)]
            } finally {
                $client.Dispose()
            }
        }

        while ($true) {
            $timestamp = [DateTimeOffset]::Now.ToString('o')
            $backendCpu = $null
            $backendWorkingSetMb = $null
            try {
                $process = Get-Process -Id $ProcessId -ErrorAction Stop
                $backendCpu = [math]::Round($process.CPU, 3)
                $backendWorkingSetMb = [math]::Round($process.WorkingSet64 / 1MB, 2)
            } catch {}

            $redisCpuSeconds = $null
            $redisMemoryMb = $null
            $redisOps = $null
            $redisClients = $null
            if ($RedisPortNumber) {
                try {
                    $info = Get-RedisInfo -Port $RedisPortNumber
                    $infoLines = $info -split "`r?`n"
                    $redisOps = (($infoLines | Select-String '^instantaneous_ops_per_sec:').Line -replace '^.*:', '').Trim()
                    $redisClients = (($infoLines | Select-String '^connected_clients:').Line -replace '^.*:', '').Trim()
                    $usedMemory = (($infoLines | Select-String '^used_memory:').Line -replace '^.*:', '').Trim()
                    $cpuSystem = (($infoLines | Select-String '^used_cpu_sys:').Line -replace '^.*:', '').Trim()
                    $cpuUser = (($infoLines | Select-String '^used_cpu_user:').Line -replace '^.*:', '').Trim()
                    if ($usedMemory) { $redisMemoryMb = [math]::Round(([double]$usedMemory) / 1MB, 2) }
                    if ($cpuSystem -and $cpuUser) {
                        $redisCpuSeconds = [math]::Round(([double]$cpuSystem) + ([double]$cpuUser), 3)
                    }
                } catch {}
            }

            [pscustomobject]@{
                timestamp = $timestamp
                backendCpuSeconds = $backendCpu
                backendWorkingSetMb = $backendWorkingSetMb
                redisCpuSeconds = $redisCpuSeconds
                redisUsedMemoryMb = $redisMemoryMb
                redisOpsPerSec = $redisOps
                redisClients = $redisClients
            } | Export-Csv -LiteralPath $Path -NoTypeInformation -Append
            Start-Sleep -Seconds 5
        }
    }
}

function Invoke-K6Run([string]$Kind, [int]$Target, [string]$RunDirectory, [int]$BackendProcessId, [int]$RedisPort) {
    $script = if ($Kind -eq 'rest') { 'presence-preflight.js' } else { 'presence-websocket-load.js' }
    $summaryPath = Join-Path $RunDirectory "$Kind-$Target-summary.json"
    $logPath = Join-Path $RunDirectory "$Kind-$Target.log"
    $metricsPath = Join-Path $RunDirectory "$Kind-$Target-runtime.csv"
    $arguments = @(
        'run',
        '--summary-export', $summaryPath,
        '--summary-trend-stats', 'avg,min,med,max,p(90),p(95),p(99)'
    )
    $arguments += @('-e', "TARGET_VUS=$Target", '-e', "PRESENCE_FIXTURES=$FixturePath")
    if ($Smoke) { $arguments += @('-e', 'SMOKE=true') }
    if ($DirectRamp) { $arguments += @('-e', 'DIRECT_RAMP=true') }
    if ($Kind -eq 'rest') {
        $arguments += @('-e', "BASE_URL=$BaseUrl")
    } else {
        $arguments += @('-e', "WS_URL=$WsUrl")
    }
    $arguments += (Join-Path $PSScriptRoot $script)

    Write-Host "[$Kind] target=$Target"
    $backendUri = [uri]$BaseUrl
    $monitor = Start-D17Monitor -OutputPath $metricsPath -BackendProcessId $BackendProcessId -RedisPort $RedisPort
    $exitCode = 0
    try {
        & k6 @arguments 2>&1 | Tee-Object -FilePath $logPath | ForEach-Object { Write-Host $_ }
        $exitCode = $LASTEXITCODE
    } finally {
        Stop-Job -Job $monitor -ErrorAction SilentlyContinue
        Receive-Job -Job $monitor -ErrorAction SilentlyContinue | Out-Null
        Remove-Job -Job $monitor -Force -ErrorAction SilentlyContinue
    }
    if ($exitCode -ne 0) {
        $message = "k6 $Kind target $Target failed with exit code $exitCode"
        if (-not $ContinueOnFailure) { throw $message }
        Write-Warning $message
        return $false
    }
    return $true
}

if (-not (Get-Command k6 -ErrorAction SilentlyContinue)) {
    throw 'k6 is not available on PATH.'
}

try {
    $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 5
    if ($health.status -ne 'UP') { throw "Backend health is $($health.status)" }
} catch {
    throw "Backend health check failed: $($_.Exception.Message)"
}

$effectiveTargets = if ($Smoke) { @(2) } else { @($Targets | Sort-Object -Unique) }
$requiredFixtures = ($effectiveTargets | Measure-Object -Maximum).Maximum
if ((-not (Test-Path -LiteralPath $FixturePath)) -or (@(Get-Content -Raw -LiteralPath $FixturePath | ConvertFrom-Json).Count -lt $requiredFixtures)) {
    if (-not $GenerateMissingFixtures) {
        throw "Need $requiredFixtures distinct fixtures. Run New-PresenceFixtures.ps1 or add -GenerateMissingFixtures."
    }
    & (Join-Path $PSScriptRoot 'New-PresenceFixtures.ps1') -Count $requiredFixtures -BaseUrl $BaseUrl -OutputPath $FixturePath
}

$fixtureCount = @(Get-Content -Raw -LiteralPath $FixturePath | ConvertFrom-Json).Count
if ($fixtureCount -lt $requiredFixtures) { throw "Need $requiredFixtures fixtures; found $fixtureCount." }
$FixturePath = (Resolve-Path -LiteralPath $FixturePath).Path

if (-not $WsUrl) { $WsUrl = ($BaseUrl -replace '^http', 'ws').TrimEnd('/') + '/ws' }
$stamp = [DateTimeOffset]::Now.ToString('yyyyMMdd-HHmmss')
$runDirectory = Join-Path $OutputRoot $stamp
New-Item -ItemType Directory -Force -Path $runDirectory | Out-Null
$backendProcessId = Get-ListenerProcessId -Port ([uri]$BaseUrl).Port
$redisPort = Get-DotEnvValue 'REDIS_PORT'
if (-not $redisPort) { $redisPort = 16379 }

[ordered]@{
    startedAt = [DateTimeOffset]::Now.ToString('o')
    mode = $Mode
    smoke = [bool]$Smoke
    directRamp = [bool]$DirectRamp
    continueOnFailure = [bool]$ContinueOnFailure
    targets = $effectiveTargets
    fixtureCount = $fixtureCount
    baseUrl = $BaseUrl
    wsUrl = $WsUrl
    k6Version = (& k6 version)
} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $runDirectory 'manifest.json') -Encoding utf8

$failedStages = [System.Collections.Generic.List[string]]::new()
foreach ($target in $effectiveTargets) {
    if ($Mode -in @('rest', 'all')) {
        if (-not (Invoke-K6Run -Kind 'rest' -Target $target -RunDirectory $runDirectory -BackendProcessId $backendProcessId -RedisPort $redisPort)) {
            $failedStages.Add("rest-$target")
        }
    }
    if ($Mode -in @('websocket', 'all')) {
        if (-not (Invoke-K6Run -Kind 'websocket' -Target $target -RunDirectory $runDirectory -BackendProcessId $backendProcessId -RedisPort $redisPort)) {
            $failedStages.Add("websocket-$target")
        }
    }
}

[ordered]@{
    completedAt = [DateTimeOffset]::Now.ToString('o')
    failedStages = $failedStages
    passed = $failedStages.Count -eq 0
} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $runDirectory 'result.json') -Encoding utf8

Write-Host "D17 load test completed: $runDirectory"
if ($failedStages.Count -gt 0) { Write-Warning "Failed stages: $($failedStages -join ', ')" }
