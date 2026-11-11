# assets/audio/ — 사전 제작 안내 오디오(E4)

이 폴더는 TTS 합성 없이 재생하는 **사전 제작 오디오 파일**을 두는 곳이다.
`AssetAudioNarrator`(fallback/AssetAudioNarrator.kt)가 이 폴더를 읽는다.

## 파일 규격

- 형식: OGG(Vorbis) 권장. WAV 도 허용한다(파일이 커지므로 OGG 권장).
- 채널: 모노, 샘플레이트 44.1kHz(또는 22.05kHz), 음량은 차량 스피커 기준 과하지 않게.
- 길이: 안내 1문장 기준 10초 내외(재생 대기 타임아웃 30초).
- 압축 주의: assets 는 무압축으로 저장되어야 MediaPlayer.setDataSource(fd) 로 열린다.
  (OGG·WAV 는 기본적으로 무압축 저장된다.)

## 명명 규칙(키 = 파일명, 확장자 제외)

| 파일명 | 용도 | 매핑 대상 문장 |
| --- | --- | --- |
| `arrival_<장소id>.ogg` | 도착 안내 | "{장소명}에 도착했어요. 스탬프를 찍었어요." |
| `narration_<장소id>.ogg` | '다시 듣기'(장소 해설) | 시드 place.narration 전체 |

- `<장소id>` 는 시드(seed/seed.json)의 장소 id 를 그대로 쓴다. 예:
  - `arrival_gj-museum.ogg` — 국립경주박물관 도착 안내
  - `arrival_gj-daereungwon.ogg` — 대릉원(천마총)
  - `arrival_gj-cheomseongdae.ogg` — 첨성대
  - `narration_gj-museum.ogg` — 국립경주박물관 '다시 듣기' 해설
- 도착 문장에 함께 온 인원이 붙는 변형("오늘은 N명이 함께 왔어요.")은 앱이 기본 문장으로
  정규화해 같은 `arrival_<장소id>` 파일 하나로 재생한다.
- 파일이 없는 키는 재생 대신 실패로 처리된다(가짜 재생 금지). 폴더가 비어 있으면
  AssetAudioNarrator 는 선택되지 않는다(현재 저장소 상태 — 오디오 파일은 발주자 준비(Q14)).

## 음성 제작 규칙(00문서 11-2)

> **합성 음성 또는 발주자 직접 녹음만 사용한다. 고인의 목소리를 AI로 재생성해 넣지 않는다.**
> (예시 음성 편지 voice_letter_demo 도 같은 규칙을 따른다.)

- TTS로 만들더라도 일반 음성 합성만 허용한다. 실제 인물의 녹음을 학습·변환해 목소리를
  재현하지 않는다.
- 파일 안 문구에 개인 식별 정보(전화번호·가족 코드 등)를 넣지 않는다.
