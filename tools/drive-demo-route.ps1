# drive-demo-route.ps1 — 에뮬레이터 가상 위치로 데모 코스를 "주행"시킨다.
# 사용법:  ./tools/drive-demo-route.ps1 -Route tools/routes/gyeongju-demo.json [-SpeedKmh 40] [-IntervalSec 1] [-DwellSec 8]
# 주의: 위경도 순서는 adb emu geo fix 기준 "경도(lon) 먼저". knot = km/h / 1.852.
# PowerShell 5.1 / 7 호환. Ctrl-C로 안전 종료.

param(
    [Parameter(Mandatory = $true)]
    [string]$Route,                 # route.json 경로 (배열 또는 { _note, points: [...] } 형태)
    [int]$SpeedKmh = 40,            # 순항 속도
    [int]$IntervalSec = 1,          # geo fix 주기(초)
    [int]$DwellSec = 8              # 마지막 지점 정지 시간(초)
)

$ErrorActionPreference = 'Stop'

# --- adb 확인 ---
$adb = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adb) {
    Write-Host "[오류] adb 를 찾을 수 없습니다. Android SDK platform-tools 를 PATH에 추가한 뒤 다시 실행하세요." -ForegroundColor Red
    Write-Host "       예: set PATH=%PATH%;C:\tools\android-sdk\platform-tools"
    exit 1
}

# --- route.json 읽기 (배열 또는 _note+points 객체 모두 지원) ---
if (-not (Test-Path $Route)) {
    Write-Host "[오류] 경로 파일이 없습니다: $Route" -ForegroundColor Red
    exit 1
}
$raw = Get-Content -Raw -Encoding UTF8 $Route | ConvertFrom-Json
$points = @()
if ($raw -is [array]) {
    $points = $raw
} else {
    if ($raw._note) { Write-Host "[주의] $($raw._note)" -ForegroundColor Yellow }
    $points = $raw.points
}
if ($points.Count -lt 2) {
    Write-Host "[오류] 경로 지점이 2개 미만입니다." -ForegroundColor Red
    exit 1
}

# --- Haversine (미터) ---
function Get-DistanceM([double]$lat1, [double]$lon1, [double]$lat2, [double]$lon2) {
    $r = 6371000.0
    $p1 = $lat1 * [math]::PI / 180.0
    $p2 = $lat2 * [math]::PI / 180.0
    $dp = ($lat2 - $lat1) * [math]::PI / 180.0
    $dl = ($lon2 - $lon1) * [math]::PI / 180.0
    $a = [math]::Sin($dp / 2) * [math]::Sin($dp / 2) +
         [math]::Cos($p1) * [math]::Cos($p2) * [math]::Sin($dl / 2) * [math]::Sin($dl / 2)
    2 * $r * [math]::Atan2([math]::Sqrt($a), [math]::Sqrt(1 - $a))
}

$knots = [math]::Round($SpeedKmh / 1.852, 1)
$stepM = $SpeedKmh * (1000.0 / 3600.0) * $IntervalSec   # 간격마다 이동할 거리(속도×간격)
$total = 0.0
for ($i = 0; $i -lt $points.Count - 1; $i++) {
    $total += Get-DistanceM $points[$i].lat $points[$i].lon $points[$i + 1].lat $points[$i + 1].lon
}
Write-Host ("총 거리 약 {0:N0} m / 속도 {1} km/h ({2} knot) / 간격 {3}s / 보간 스텝 {4:N0} m" -f $total, $SpeedKmh, $knots, $IntervalSec, $stepM)
Write-Host "종료는 Ctrl-C. 주행을 시작합니다..." -ForegroundColor Cyan
Start-Sleep -Seconds 2

function Send-Fix([double]$lat, [double]$lon, [double]$speedKmh) {
    # geo fix: <lon> <lat> [고도] [위성수] [속도(knot)] — 순서 주의(경도 먼저)
    $k = [math]::Round($speedKmh / 1.852, 1)
    & adb emu geo fix $lon $lat 30 8 $k | Out-Null
}

$done = $false
try {
    for ($i = 0; $i -lt $points.Count - 1; $i++) {
        $a = $points[$i]; $b = $points[$i + 1]
        $segLen = Get-DistanceM $a.lat $a.lon $b.lat $b.lon
        $nSteps = [math]::Max(1, [math]::Ceiling($segLen / $stepM))
        Write-Host ("구간 {0}: {1} -> {2}  ({3:N0} m, {4} 스텝)" -f ($i + 1), $a.name, $b.name, $segLen, $nSteps)
        for ($s = 0; $s -le $nSteps; $s++) {
            $t = $s / $nSteps
            $lat = $a.lat + ($b.lat - $a.lat) * $t
            $lon = $a.lon + ($b.lon - $a.lon) * $t
            Send-Fix $lat $lon $SpeedKmh
            $traveled = ($total - ($segLen * (1.0 - $t)))
            $remain = $total - $traveled
            $pct = 100.0 * $traveled / $total
            Write-Host ("  진행 {0,5:N1}%  남은 거리 {1,7:N0} m  (lat={2:N6}, lon={3:N6})" -f $pct, $remain, $lat, $lon)
            Start-Sleep -Seconds $IntervalSec
        }
    }
    # 마지막 지점 정지(DwellSec) — 속도 0
    $last = $points[$points.Count - 1]
    Write-Host ("도착: {0} — {1}초 정지 (속도 0)" -f $last.name, $DwellSec) -ForegroundColor Green
    for ($d = 0; $d -lt $DwellSec; $d++) {
        Send-Fix $last.lat $last.lon 0
        Start-Sleep -Seconds 1
    }
    $done = $true
}
finally {
    if (-not $done) { Write-Host "`n[중단] 사용자 요청으로 안전 종료합니다." -ForegroundColor Yellow }
    else { Write-Host "`n[완료] 코스 주행이 끝났습니다." -ForegroundColor Green }
}
