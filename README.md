# TrafficGauge

목적지까지 경로를 안내하면서, 전방 교차로를 초록불 안에 통과할 수 있는지 게이지로 보여주는 Android 앱.

## 시작하기

1. Android Studio에서 프로젝트 열기 (최초 Gradle sync에 인터넷 필요)
2. `local.properties`에 본인 Tmap App Key 확인 (`local.properties.example` 참고, 이미 로컬에는 세팅되어 있고 git에는 올라가지 않음)
3. (선택) `DATA_GO_KR_SERVICE_KEY`를 채우면 실제 신호 데이터 사용, 비워두면 자동으로 추정 모델 사용
4. 실행 시 위치 권한을 허용해야 속도/현재 위치가 표시됨

## 구조

- `gauge/` — 통과 가능성 계산 핵심 로직 (`SignalGaugeCalculator`, 단위 테스트 포함)
- `gauge/SignalTimingEstimator.kt` — 실제 데이터가 없을 때의 폴백 추정 모델 (`AssumedCycleSignalTimingSource`)
- `gauge/NationwideSignalTimingSource.kt` — **실제 신호 데이터** 연동 (data.go.kr 전국 통합 데이터, 아래 "다음에 확인할 것" 참고)
- `location/SpeedTracker.kt` — GPS 기반 속도 스트림 (이동평균 스무딩)
- `routing/RouteRepository.kt` — Tmap POI 검색 + 경로 조회, 안내지점(교차로 추정) 파싱
- `routing/IntersectionRepository.kt` — 전국 교차로 목록 캐싱 + GPS로 가장 가까운 교차로 찾기
- `data/` — Tmap / data.go.kr REST API 클라이언트, 좌표·방위각 계산 유틸
- `ui/` — 지도(osmdroid), 목적지 검색바, 신호 게이지 Compose UI
- `MainActivity.kt`, `NavigationViewModel.kt` — 화면 조립 및 상태 관리

## 신호 데이터 소스: data.go.kr 전국 통합 데이터

행정안전부/한국지역정보개발원이 제공하는 전국 교차로 신호제어기 실시간 정보(교차로 정보 + 신호잔여시간 정보)를 사용합니다. 서울시 V2X API와 같은 경찰청 표준 규격(8방향 × 6개 신호종류)을 따르는 것으로 보여, 필드명 규칙(`{방향}{신호종류}Sg{Rmdr|Stat}{Cs|Nm}`)은 서울시 API로 검증된 것을 그대로 적용했습니다.

**아직 실제 응답으로 검증 못한 것 (활용신청 승인 후 확인 필요):**
- `NationwideSignalApiService`의 `INTERSECTION_INFO_PATH` / `SIGNAL_TIMING_PATH`는 자리표시자입니다. data.go.kr Open API 상세 페이지의 "요청 URL"을 그대로 넣어야 합니다.
- 잔여시간 단위가 정말 1/10초(deciseconds)인지, 필드명이 문서와 완전히 같은지 (`IntersectionInfoItem`의 `itstLat`/`itstLot` 등도 추정)
- 현재는 진입방향의 **직진(St)** 신호만 읽습니다 — 좌회전 등 실제 회전에 맞는 신호를 읽으려면 Tmap 경로의 `turnType`을 `SignalMovement`에 매핑하는 로직 추가 필요
- 전국 교차로 목록이 얼마나 큰지, 페이지네이션으로 전부 캐싱하는 게 맞는 접근인지(너무 크면 위치 기반 필터 API가 있는지 확인 필요)

`DATA_GO_KR_SERVICE_KEY`를 비워두면 자동으로 `AssumedCycleSignalTimingSource`(추정치)로 폴백하므로, 위 항목이 아직 안 맞아도 앱 자체는 정상 동작합니다.

## 그 외 알려진 제약

- **Tmap API 응답 필드명 미검증**: `RouteRepository`/`TmapApiService`의 POI·경로 응답 파싱은 문서 기준으로 작성했고, 실제 앱키로 호출해 확인해봐야 합니다. `OkHttp` BASIC 로깅이 켜져 있으니 첫 실행 로그로 확인하세요.
- **교차로 지점 = 경로 안내지점(GP)**: Tmap 경로 API가 신호등 유무를 알려주지 않으므로, 턴/안내가 필요한 지점을 "교차로 후보"로 대체 사용 중입니다.
- **지도는 osmdroid(무료 오픈소스)**: Tmap 네이티브 지도 SDK로 바꾸고 싶다면 Tmap 개발자 콘솔의 Android 연동 가이드에서 정확한 Gradle 좌표를 확인해 `app/build.gradle.kts`에 추가하세요.

## 빌드 검증

이 저장소는 실제 Android SDK(cmdline-tools, platform 34, build-tools 34)로 `assembleDebug` + `testDebugUnitTest`까지 통과를 확인했습니다. Android Studio에서 열어도 문제없이 sync/빌드될 겁니다.
