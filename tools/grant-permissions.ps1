# grant-permissions.ps1 — 에뮬레이터에 dangerous 권한을 직접 부여한다.
# 배경: Pleos 에뮬레이터에서 권한 요청 UI가 크래시하므로(참가팀 보고) adb로 부여한다. --user 10 필수.
# 사용법: ./tools/grant-permissions.ps1 -Package app.arosaegim [-AllowMockLocation]

param(
    [Parameter(Mandatory = $true)]
    [string]$Package,
    [switch]$AllowMockLocation
)

$ErrorActionPreference = 'Stop'
$adb = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adb) {
    Write-Host "[오류] adb 를 찾을 수 없습니다. PATH에 platform-tools 를 추가하세요." -ForegroundColor Red
    exit 1
}

Write-Host "장치 확인..."
& adb devices
& adb shell pm grant --user 10 $Package pleos.car.permission.FUSED_LOCATION
if ($LASTEXITCODE -eq 0) { Write-Host "FUSED_LOCATION 부여 완료" -ForegroundColor Green }
else { Write-Host "FUSED_LOCATION 부여 실패 (장치 연결·패키지 설치 여부 확인)" -ForegroundColor Red }

if ($AllowMockLocation) {
    & adb shell appops set --user current $Package android:mock_location allow
    Write-Host "mock_location 허용 완료" -ForegroundColor Green
}

# 부여 결과 확인 (granted=true 여야 한다)
Write-Host "`n부여 결과 확인:"
& adb shell dumpsys package $Package | Select-String "FUSED_LOCATION"
