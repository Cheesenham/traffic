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

## 신호 데이터 소스: data.go.kr KLID 전국 통합 데이터 (B551982/rti)

행정안전부/한국지역정보개발원(KLID)이 제공하는 실제 서비스키로 라이브 호출까지 검증했습니다.

- **엔드포인트**: `https://apis.data.go.kr/B551982/rti/{op}` — `crsrd_map_info`(교차로 정보), `tl_drct_info`(신호 잔여시간+상태)
- **요청 파라미터**: `serviceKey`, `type=json`, `stdgCd`(10자리 시/도 표준코드, 유일한 필터), `pageNo`, `numOfRows`
- **응답 구조**: `{header:{resultCode,resultMsg}, body:{totalCount,pageNo,numOfRows,items:{item:[...]}}}` — `resultCode`가 `K3`면 "이 지역은 해당 오퍼레이션 데이터 자체가 없음"(에러 아님, 빈 배열과는 다른 의미)
- **필드명**: `crsrdId`/`crsrdNm`/`mapCtptIntLat`/`mapCtptIntLot`/`lmtSpd`(교차로), `{방향}{신호종류}sgSttsNm`/`{방향}{신호종류}sgRmndCs`(신호) — 방향 8개(`nt/et/st/wt/ne/se/sw/nw`) × 종류 6개(`Bs/Bc/Lt/Pd/St/Ut`)
- **신호 상태값**: 한글이 아니라 SAE J2735 표준 영문 값 (`protected-Movement-Allowed`, `permissive-Movement-Allowed`, `stop-And-Remain`, `stop-Then-Proceed`, `dark`, 빈 문자열)
- **잔여시간 단위**: **밀리초**로 실측 확인 (같은 필드를 4초 간격으로 반복 호출했을 때 값이 ~4000씩 줄어드는 것으로 확인; 필드 접미사 "Cs"가 암시하는 centisecond가 아님)
- **커버리지 (전국 17개 시/도 전수 확인, 2026-09-26 기준)**: 이름은 "전국 통합데이터"지만 실제로 작동하는 지역은 매우 제한적입니다.

  | 오퍼레이션 | 서울 | 인천 | 경기 | 울산 | 그 외 13개 시/도 |
  |---|---|---|---|---|---|
  | `crsrd_map_info` (교차로 정적 정보) | ✅ 2779건 | ❌ NODATA(K3) | ❌ NODATA(K3) | (미확인) | (미확인) |
  | `tl_drct_info` (실시간 신호) | ❌ NODATA(K3) | ❌ NODATA(K3) | ❌ NODATA(K3) | ✅ 399건, 초 단위 실시간 갱신 확인 | ❌ 전부 NODATA(K3) |

  즉 **수도권(서울/인천/경기) 전부 실시간 신호 데이터가 없고**, 서울조차 교차로 위치 정보만 있을 뿐 신호 타이밍은 없습니다. 지금 이 API로 실제 게이지가 동작하는 곳은 사실상 **울산광역시뿐**입니다. (테스트 중 서울에서 딱 한 번 고정 타임스탬프의 1건짜리 응답이 잡힌 적 있으나 재확인 결과 계속 K3라 신뢰할 수 없는 일시적 현상으로 판단.)
- **stdgCd 결정 방법**: 이 API는 위경도/교차로ID로 직접 조회가 안 되고 `stdgCd` 하나로만 필터링되므로, `SidoCodes`에서 GPS와 가장 가까운 17개 시/도 중심점을 찾아 근사치로 결정 (역지오코딩 API 의존 없이 계산만으로 처리)

`CompositeSignalTimingSource`가 실제 데이터(`NationwideSignalTimingSource`)를 먼저 시도하고, 못 받으면 자동으로 `AssumedCycleSignalTimingSource`(추정치)로 폴백하므로 커버리지가 없는 지역에서도 게이지가 빈 화면이 되지 않습니다. `DATA_GO_KR_SERVICE_KEY`를 비워두면 처음부터 추정 모델만 사용합니다.

**아직 남은 것:**
- 현재는 진입방향의 **직진(St)** 신호만 읽습니다 — 좌회전 등 실제 회전에 맞는 신호를 읽으려면 Tmap 경로의 `turnType`을 `SignalMovement`에 매핑하는 로직 추가 필요
- `SidoCodes`는 시/도 단위 근사치라 도 경계 근처에서는 옆 시/도로 판정될 수 있음 (API 필터 자체가 시/도 단위라 큰 문제는 아님)

## 그 외 알려진 제약

- **Tmap API 응답 필드명 미검증**: `RouteRepository`/`TmapApiService`의 POI·경로 응답 파싱은 문서 기준으로 작성했고, 실제 앱키로 호출해 확인해봐야 합니다. `OkHttp` BASIC 로깅이 켜져 있으니 첫 실행 로그로 확인하세요.
- **교차로 지점 = 경로 안내지점(GP)**: Tmap 경로 API가 신호등 유무를 알려주지 않으므로, 턴/안내가 필요한 지점을 "교차로 후보"로 대체 사용 중입니다.
- **지도는 osmdroid(무료 오픈소스)**: Tmap 네이티브 지도 SDK로 바꾸고 싶다면 Tmap 개발자 콘솔의 Android 연동 가이드에서 정확한 Gradle 좌표를 확인해 `app/build.gradle.kts`에 추가하세요.

## 빌드 검증

이 저장소는 실제 Android SDK(cmdline-tools, platform 34, build-tools 34)로 `assembleDebug` + `testDebugUnitTest`까지 통과를 확인했습니다. Android Studio에서 열어도 문제없이 sync/빌드될 겁니다.
