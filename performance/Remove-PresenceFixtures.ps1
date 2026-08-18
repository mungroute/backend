param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$FixturePath = (Join-Path $PSScriptRoot 'presence-fixtures.json'),
    [switch]$KeepAccounts
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $FixturePath)) {
    throw "Fixture file not found: $FixturePath"
}

$fixtures = @(Get-Content -Raw -LiteralPath $FixturePath | ConvertFrom-Json)
$ended = 0
$deactivated = 0
$failed = 0

foreach ($fixture in $fixtures) {
    $headers = @{ Authorization = "Bearer $($fixture.accessToken)" }
    try {
        Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/walks/$($fixture.sessionId)/end" -Headers $headers | Out-Null
        $ended += 1
    } catch {
        # 이미 종료됐거나 만료된 세션은 계정 정리를 계속 시도한다.
    }

    if (-not $KeepAccounts) {
        try {
            Invoke-RestMethod -Method Delete -Uri "$BaseUrl/api/users/me" -Headers $headers | Out-Null
            $deactivated += 1
        } catch {
            $failed += 1
        }
    }
}

[ordered]@{
    fixtureCount = $fixtures.Count
    endedSessions = $ended
    deactivatedAccounts = $deactivated
    failedAccountCleanup = $failed
} | ConvertTo-Json

if ($failed -gt 0) {
    throw "$failed fixture accounts could not be deactivated."
}
