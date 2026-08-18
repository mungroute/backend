[CmdletBinding()]
param(
    [string]$FixturePath = (Join-Path $PSScriptRoot 'presence-fixtures.json'),
    [string]$OutputPath = (Join-Path $PSScriptRoot 'presence-fixtures-refreshed.json'),
    [string]$BaseUrl = 'http://localhost:8080'
)

$ErrorActionPreference = 'Stop'
$source = @(Get-Content -Raw -LiteralPath $FixturePath | ConvertFrom-Json)
$refreshed = [System.Collections.Generic.List[object]]::new()

for ($index = 0; $index -lt $source.Count; $index++) {
    $fixture = $source[$index]
    if ($fixture.testEmail -notmatch '^k6-presence-(\d+)-\d+@example\.com$') {
        throw "Fixture $($index + 1) is not a generated k6 presence account."
    }

    $password = "LoadTest$($matches[1])A1"
    $auth = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" -ContentType 'application/json' -Body (@{
        email = $fixture.testEmail
        password = $password
    } | ConvertTo-Json)
    $headers = @{ Authorization = "Bearer $($auth.accessToken)" }
    $session = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/walks/start" -Headers $headers -ContentType 'application/json' -Body '{"mode":"distance"}'
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/presence/consent" -Headers $headers -ContentType 'application/json' -Body (@{
        sessionId = $session.sessionId
    } | ConvertTo-Json) | Out-Null

    $refreshed.Add([ordered]@{
        sessionId = $session.sessionId
        accessToken = $auth.accessToken
        lon = [double]$fixture.lon
        lat = [double]$fixture.lat
        testEmail = $fixture.testEmail
    })
    Write-Progress -Activity 'D17 presence fixture 갱신' -Status "$($index + 1) / $($source.Count)" -PercentComplete ((($index + 1) / $source.Count) * 100)
}

$refreshed | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $OutputPath -Encoding utf8
Write-Progress -Activity 'D17 presence fixture 갱신' -Completed
Write-Host "Refreshed $($refreshed.Count) fixtures: $OutputPath"

