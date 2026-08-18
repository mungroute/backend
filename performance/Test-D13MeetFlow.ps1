param([string]$BaseUrl = 'http://localhost:8080')

$ErrorActionPreference = 'Stop'
$run = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()
$tail = $run.Substring($run.Length - 8)

function New-TestUser([string]$label, [string]$phonePrefix) {
    $body = @{
        email = "d13-meet-$label-$run@example.com"
        password = 'MeetFlow1234'
        nickname = "만남테스트-$label-$tail"
        phoneNumber = "$phonePrefix$tail"
        termsAgreed = $true
    } | ConvertTo-Json
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/signup" -ContentType 'application/json' -Body $body
}

$first = New-TestUser 'a' '019'
$second = New-TestUser 'b' '018'
$firstHeaders = @{ Authorization = "Bearer $($first.accessToken)" }
$secondHeaders = @{ Authorization = "Bearer $($second.accessToken)" }
$firstWalk = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/walks/start" -Headers $firstHeaders -ContentType 'application/json' -Body '{"mode":"meet"}'
$secondWalk = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/walks/start" -Headers $secondHeaders -ContentType 'application/json' -Body '{"mode":"meet"}'

try {
    Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/meet/profile" -Headers $firstHeaders -ContentType 'application/json' -Body '{"dogName":"망고","breed":"리트리버","ageYears":4,"temperamentTags":["차분해요"]}' | Out-Null
    Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/meet/profile" -Headers $secondHeaders -ContentType 'application/json' -Body '{"dogName":"쿠키","breed":"푸들","ageYears":2,"temperamentTags":["사람을 좋아해요"]}' | Out-Null
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/presence/consent" -Headers $firstHeaders -ContentType 'application/json' -Body (@{ sessionId = $firstWalk.sessionId } | ConvertTo-Json) | Out-Null
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/presence/consent" -Headers $secondHeaders -ContentType 'application/json' -Body (@{ sessionId = $secondWalk.sessionId } | ConvertTo-Json) | Out-Null

    $secondPosition = @{ sessionId=$secondWalk.sessionId; measuredAt=[DateTimeOffset]::Now.ToString('o'); lon=126.9781; lat=37.5666; accuracy=7; heading=90; stationary=$false; radiusM=100 } | ConvertTo-Json
    Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/meet/presence" -Headers $secondHeaders -ContentType 'application/json' -Body $secondPosition | Out-Null
    $firstPosition = @{ sessionId=$firstWalk.sessionId; measuredAt=[DateTimeOffset]::Now.ToString('o'); lon=126.9780; lat=37.5665; accuracy=7; heading=90; stationary=$false; radiusM=100 } | ConvertTo-Json
    $candidateResult = Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/meet/presence" -Headers $firstHeaders -ContentType 'application/json' -Body $firstPosition
    $candidate = $candidateResult.candidates[0]
    if (-not $candidate -or $candidate.PSObject.Properties.Name -contains 'lat' -or $candidate.PSObject.Properties.Name -contains 'userId') { throw '수락 전 후보 개인정보 계약 위반' }

    $request = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/meet/requests" -Headers $firstHeaders -ContentType 'application/json' -Body (@{ sessionId=$firstWalk.sessionId; candidateRef=$candidate.candidateRef } | ConvertTo-Json)
    $incoming = (Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/meet/requests?sessionId=$($secondWalk.sessionId)" -Headers $secondHeaders)[0]
    if ($incoming.profile -ne $null) { throw '상호 수락 전 프로필이 노출됨' }
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/meet/requests/$($request.requestId)/accept" -Headers $secondHeaders | Out-Null

    $connected = Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/meet/presence" -Headers $firstHeaders -ContentType 'application/json' -Body (@{ sessionId=$firstWalk.sessionId; measuredAt=[DateTimeOffset]::Now.ToString('o'); lon=126.9780; lat=37.5665; accuracy=7; heading=90; stationary=$false; radiusM=100 } | ConvertTo-Json)
    if (-not $connected.connection -or $connected.connection.profile.dogName -ne '쿠키' -or $null -eq $connected.connection.lat) { throw '수락 후 제한 프로필·위치 공개 실패' }
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/meet/requests/$($request.requestId)/block" -Headers $firstHeaders | Out-Null
    Write-Host 'D13 meet flow passed: pre-accept privacy, accept, limited profile/location, block'
}
finally {
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/walks/$($firstWalk.sessionId)/end" -Headers $firstHeaders -ErrorAction SilentlyContinue | Out-Null
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/walks/$($secondWalk.sessionId)/end" -Headers $secondHeaders -ErrorAction SilentlyContinue | Out-Null
}
