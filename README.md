# TrafficGauge

목적지까지 경로를 안내하면서, 전방 교차로를 초록불 안에 통과할 수 있는지 게이지로 보여주는 Android 앱.

## 시작하기

1. Android Studio에서 프로젝트 열기 (최초 Gradle sync에 인터넷 필요)
2. `local.properties`에 본인 Tmap App Key 확인 (`local.properties.example` 참고, 이미 로컬에는 세팅되어 있고 git에는 올라가지 않음)
3. 실행 시 위치 권한을 허용해야 속도/현재 위치가 표시됨

## 구조

- `gauge/` — 통과 가능성 계산 핵심 로직 (`SignalGaugeCalculator`, 단위 테스트 포함)
- `gauge/SignalTimingEstimator.kt` — **임시 추정 모델**. 실제 신호 잔여시간 API가 없는 지역을 위한 자리표시자
- `location/SpeedTracker.kt` — GPS 기반 속도 스트림 (이동평균 스무딩)
- `routing/RouteRepository.kt` — Tmap POI 검색 + 경로 조회, 안내지점(교차로 추정) 파싱
- `data/` — Tmap REST API 클라이언트, 좌표 계산 유틸
- `ui/` — 지도(osmdroid), 목적지 검색바, 신호 게이지 Compose UI
- `MainActivity.kt`, `NavigationViewModel.kt` — 화면 조립 및 상태 관리

## 알려진 제약 / 다음에 확인할 것

- **Tmap API 응답 필드명 미검증**: `RouteRepository`/`TmapApiService`의 POI·경로 응답 파싱은 문서 기준으로 작성했고, 이 환경은 네트워크 제약으로 실제 앱키로 호출해보지 못했습니다. 첫 실행 후 로그(`OkHttp` BASIC 레벨 로깅 켜져 있음)로 실제 JSON을 확인하고 필드명이 다르면 맞춰 수정하세요.
- **신호 잔여시간은 추정치**: 한국에 전국 커버하는 무료 실시간 신호 API가 없어 `AssumedCycleSignalTimingSource`가 임의 주기(120초/50% 초록)로 값을 만들어냅니다. 실제 값이 아니므로 실사용 전 반드시 실제 데이터 소스(C-ITS 등)로 교체 필요 — `SignalTimingSource` 인터페이스만 구현하면 교체 가능하도록 분리해뒀습니다.
- **교차로 지점 = 경로 안내지점(GP)**: Tmap 경로 API가 신호등 유무를 알려주지 않으므로, 턴/안내가 필요한 지점을 "교차로 후보"로 대체 사용 중입니다. 실제로는 신호등이 없는 교차로도 포함될 수 있습니다.
- **지도는 osmdroid(무료 오픈소스)**: Tmap 네이티브 지도 SDK로 바꾸고 싶다면 Tmap 개발자 콘솔의 Android 연동 가이드에서 정확한 Gradle 좌표를 확인해 `app/build.gradle.kts`에 추가하세요.
- **빌드 미검증**: 이 개발 환경에는 Android SDK가 없어 실제 컴파일/빌드를 돌려보지 못했습니다. Android Studio에서 첫 sync 시 나오는 에러를 알려주시면 바로 고쳐드리겠습니다.
