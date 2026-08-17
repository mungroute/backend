param(
    [ValidateRange(1, 5000)]
    [int]$Count = 2,
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$OutputPath = (Join-Path $PSScriptRoot 'presence-fixtures.json')
)

$ErrorActionPreference = 'Stop'
$runCode = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() % 100000
$password = "LoadTest${runCode}A1"
$fixtures = [System.Collections.Generic.List[object]]::new()

for ($index = 1; $index -le $Count; $index++) {
    $sequence = (($runCode * 1000) + $index) % 100000000
    $email = "k6-presence-$runCode-$index@example.com"
    $nickname = "부하테스트-$runCode-$index"
    $phoneNumber = '019' + $sequence.ToString('D8')
    $signupBody = @{
        email = $email
        password = $password
        nickname = $nickname
        phoneNumber = $phoneNumber
        termsAgreed = $true
    } | ConvertTo-Json

    $auth = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/signup" -ContentType 'application/json' -Body $signupBody
    $headers = @{ Authorization = "Bearer $($auth.accessToken)" }
    $session = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/walks/start" -Headers $headers -ContentType 'application/json' -Body '{"mode":"distance"}'
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/presence/consent" -Headers $headers -ContentType 'application/json' -Body (@{ sessionId = $session.sessionId } | ConvertTo-Json) | Out-Null

    $column = ($index - 1) % 20
    $row = [Math]::Floor(($index - 1) / 20)
    $fixtures.Add([ordered]@{
        sessionId = $session.sessionId
        accessToken = $auth.accessToken
        lon = 126.978 + ($column * 0.00004)
        lat = 37.5665 + ($row * 0.00004)
        testEmail = $email
    })
    Write-Progress -Activity 'D12 presence fixture 생성' -Status "$index / $Count" -PercentComplete (($index / $Count) * 100)
}

$fixtures | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $OutputPath -Encoding utf8
Write-Progress -Activity 'D12 presence fixture 생성' -Completed
Write-Host "Created $Count fixtures: $OutputPath"
