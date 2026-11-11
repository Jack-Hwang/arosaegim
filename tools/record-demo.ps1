# record-demo.ps1 — 에뮬레이터 화면을 녹화해 demo/raw/ 에 저장한다.
# 사용법: ./tools/record-demo.ps1 [-Seconds 170] [-OutDir demo/raw]
# 비고: screenrecord는 1회 최대 3분. 소리는 화면 녹화에 잡히지 않는다(차량 오디오 경로).
#       소리가 필요하면 에뮬레이터를 QEMU_AUDIO_DRV=wav, QEMU_WAV_PATH=<경로> 환경변수와
#       -audio wav 옵션으로 띄워 오디오를 별도 저장한 뒤 편집 시 합친다. [REPORTED]

param(
    [int]$Seconds = 170,
    [string]$OutDir = "demo/raw"
)

$ErrorActionPreference = 'Stop'
$adb = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adb) {
    Write-Host "[오류] adb 를 찾을 수 없습니다. PATH에 platform-tools 를 추가하세요." -ForegroundColor Red
    exit 1
}

if ($Seconds -gt 180) { $Seconds = 180; Write-Host "screenrecord 최대 180초로 제한했습니다." -ForegroundColor Yellow }

if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Path $OutDir | Out-Null }

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$remote = "/sdcard/demo_$stamp.mp4"
$local = Join-Path $OutDir ("{0}.mp4" -f $stamp)

Write-Host "녹화 시작 (${Seconds}초)..."
& adb shell screenrecord --time-limit $Seconds $remote
Write-Host "녹화 종료. 파일을 가져옵니다..."
& adb pull $remote $local
& adb shell rm $remote

if (Test-Path $local) {
    Write-Host ("저장 완료: {0}" -f $local) -ForegroundColor Green
    Write-Host "참고: 앱 안내 음성은 화면 녹화에 포함되지 않습니다. 오디오 별도 저장 절차는 파일 머리 주석 참고."
} else {
    Write-Host "[오류] 녹화 파일을 가져오지 못했습니다." -ForegroundColor Red
    exit 1
}
