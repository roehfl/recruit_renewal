# 관리자 대시보드 집계 API 설계

- 날짜: 2026-08-11
- 대상 화면: `AdminHomeView` (`/admin`) — 시안 2a "퍼널 중심 · 공고 단위 스코프"
- 화면 요청서: `docs/design/관리자 대시보드 시안 요청서.md`
- 범위: **백엔드 집계 API만.** 프론트 구현은 후속 슬라이스.

---

## 1. 출발점 — 기존 퍼널 API가 이미 대부분을 커버한다

`FunnelStatisticsService`는 컨트롤러 javadoc이 시사하는 것보다 훨씬 많은 값을 반환한다. v2 시안 위젯과의 대응은 다음과 같다.

| 시안 위젯 | 기존 API | 상태 |
|---|---|---|
| 단계 퍼널 (인원·전환율) | `stages[].funnelPassedCount` · `stepConversionRate` · `cumulativeRate` | 있음 |
| 전체 통과율 | 마지막 단계 `cumulativeRate` | 있음 |
| 최대 이탈 구간 | `stepConversionRate` 최솟값 (프론트 계산) | 있음 |
| 단계별 결과 구성 | `stages[].distribution` (7버킷) | 있음 |
| 분야별 퍼널 비교 | `dimension=POSITION` | 있음 |
| 학교별 top 5 | `dimension=SCHOOL&topN=5` | 있음 |
| 자격별 top 5 | `dimension=CERTIFICATE&topN=5` | 있음 |
| 공고 선택 드롭다운 | `GET /admin/job-postings` | 있음 |
| **평균 체류일** | — | **신규** |
| **일자별 지원 접수 추이** | — | **신규** |

따라서 이 설계의 실제 작업은 **확장 1건 + 신규 2건**이다. 새 집계 엔진을 만드는 일이 아니다.

### 소비자 현황

- 프론트는 퍼널 API를 **전혀 소비하지 않는다**(`recruit_front`에 funnel/statistics 참조 0건).
- 유일한 소비자는 `AdminStatisticsControllerTest` 17건이며, 응답을 **역직렬화해서 검증**할 뿐 응답 record를 직접 생성하지 않는다. → **필드 추가는 테스트를 깨지 않는다.** 필드 제거·의미 변경은 깬다.

---

## 2. 변경 1 — 퍼널 `dimension` 다중 허용 (확장, 하위호환)

### 문제

시안 화면 하나를 그리려면 퍼널 API를 4번 호출해야 한다(overall / POSITION / SCHOOL / CERTIFICATE). `dimension`이 단일 값이기 때문이다. 매 호출마다 코호트 전체와 단계 결과 전체를 **다시 읽고 다시 집계**한다. 지원자 4,000명·5단계 규모면 한 화면 로드에 약 8만 행을 읽는다.

### 설계

```
GET /admin/job-postings/{jobPostingId}/statistics/funnel
      ?dimension=POSITION,SCHOOL,CERTIFICATE
      &topN=5
```

- `dimension`을 **콤마 구분 목록**으로 받는다. 파싱: split → trim → 대문자 → 중복 제거(입력 순서 유지).
- 값 하나라도 `FunnelDimension`에 없으면 **400**(기존 동작 유지).
- 미지정이면 overall만 반환(기존과 동일).
- `topN`은 기존 규칙 그대로 — SCHOOL·CERTIFICATE에 **공통** 적용, POSITION에서는 무시. 기본 10, 상한 100.

### 응답 구조

기존 필드를 건드리지 않고 새 필드를 **추가**한다.

```
FunnelResponse {
  jobPostingId, jobPostingTitle,
  population, stages[],              // 변경 없음

  dimensionGroups: [                 // ← 신규. 항상 채운다(미지정이면 빈 배열)
    { dimension: "POSITION",    groups: [DimensionFunnelResponse, ...] },
    { dimension: "SCHOOL",      groups: [...] },
    { dimension: "CERTIFICATE", groups: [...] }
  ],

  dimension,                         // @Deprecated — 단일 요청일 때만 채움
  dimensions[]                       //              다중 요청이면 null / []
}
```

**하위호환 규칙**

- 단일 dimension 요청 → `dimension`·`dimensions`는 기존과 **완전히 동일하게** 채우고, `dimensionGroups`에도 같은 내용을 1개 원소로 담는다. → 기존 테스트 17건 무수정 통과.
- 다중 dimension 요청 → `dimension = null`, `dimensions = []`, `dimensionGroups`에 전부 담는다.
- 신규 소비자(대시보드 프론트)는 **`dimensionGroups`만 사용한다.** `dimension`·`dimensions`는 javadoc에 `@deprecated`로 표시하고, 소비자가 사라지면 별도 슬라이스에서 제거한다.

두 표현이 공존하는 건 중복이다. 그럼에도 이 길을 택한 이유는 응답 구조를 바꾸면 테스트 17건을 함께 고쳐야 하는데, 그 churn이 이 슬라이스의 실제 목적(대시보드에 데이터 공급)과 무관하기 때문이다. 제거 시점을 명시해 부채로 남지 않게 한다.

### 구현 노트

`computeCohort`가 이미 코호트를 인자로 받는 순수 계산 함수라, 축을 늘리는 확장이 자연스럽다. **코호트·단계결과 로드는 축 개수와 무관하게 1회**로 유지한다(현재 `getFunnel` 구조 그대로). 축별 분할만 반복한다.

`switch (dimension)`의 exhaustiveness 강제는 유지한다 — `FunnelDimension`에 값이 추가되면 컴파일 에러로 누락을 잡는 기존 장치다.

---

## 3. 변경 2 — 단계별 평균 체류일 (신규 필드)

### 정의 (2026-08-11 확정)

**단계 간 평균 소요일.** 지원자가 각 단계의 결과를 기다리는 기간이다.

```
기준시각(baseline) = 첫 단계면 JobApplication.submittedAt
                     그 외에는 직전 단계의 StageResult.decidedAt

체류일 = 해당 단계 StageResult.decidedAt − 기준시각
```

- 그 단계의 `decidedAt`이 없는 건(미확정)은 표본에서 제외한다.
- 직전 단계의 `decidedAt`이 없는 건도 제외한다(기준시각을 만들 수 없다).
- 표본이 0이면 `null`을 반환한다. **0.0으로 채우지 않는다** — "즉시 처리"와 "표본 없음"은 다르다.
- 단위는 일(day), 소수 1자리. 음수가 나오면(데이터 오류) 해당 건을 제외한다.

### 응답

```
StageFunnelResponse {
  ... 기존 필드 ...
  averageDwellDays: Double | null     // ← 신규
}
```

단계별로 주면 시안의 단일 값("평균 체류 6.2일")은 프론트가 평균 내어 표시할 수 있고, 덤으로 **"어느 단계가 오래 걸리는가"**까지 볼 수 있다. 요약값만 주는 것보다 정보량이 크고 비용은 같다.

### 필요한 프로젝션 변경

두 record에 필드를 추가하고 JPQL을 함께 고친다.

| record | 추가 | 이유 |
|---|---|---|
| `FunnelStageResultRow` | `decidedAt` | 단계 확정 시각 |
| `FunnelCohortRow` | `submittedAt` | 첫 단계의 기준시각 |

두 record 모두 **JPQL `new` 프로젝션이라 생성자 시그니처가 바뀐다.** 쿼리와 record를 반드시 함께 수정한다(둘 중 하나만 고치면 런타임에 터진다).

---

## 4. 변경 3 — 일자별 지원 접수 추이 (신규 엔드포인트)

```
GET /admin/job-postings/{jobPostingId}/statistics/applications-daily
```

### 응답

```
{
  jobPostingId, jobPostingTitle,
  from, to,                      // 실제 집계 구간 (LocalDate)
  totalSubmitted,
  days: [ { date, submittedCount, cumulativeCount }, ... ]
}
```

### 규칙

- 기준은 `JobApplication.submittedAt`의 **날짜**다.
- `WITHDRAWN`도 **포함**한다 — 그날 제출한 사실은 있었고, 이 차트는 "접수 추이"이지 "현재 유효 지원자"가 아니다.
- `DRAFT`는 제외된다(`submittedAt is null`). 기존 퍼널 코호트와 같은 기준이라 두 위젯의 총계가 어긋나지 않는다.
- 구간은 공고 `receptionStartDateTime`의 날짜부터 `min(receptionEndDateTime, 오늘)`까지.
- **제출이 0건인 날짜도 0으로 채운다.** 비우면 라인 차트가 끊기고 x축 간격이 왜곡된다.
- 구간 밖 `submittedAt`(데이터 이상)은 양 끝으로 clamp하지 않고 **제외**하며, 제외 건수는 로그로 남긴다.

### 배치

`FunnelStatisticsService`에 넣지 않는다. 퍼널은 코호트×단계 교차 집계이고 이건 시계열이라 관심사가 다르다. 새 서비스 `ApplicationTrendStatisticsService`를 두고 컨트롤러는 기존 `AdminStatisticsController`를 재사용한다.

집계는 **DB `GROUP BY` 한 번**으로 끝낸다. 퍼널처럼 전체를 메모리에 올리지 않는다.

---

## 5. 시안과 API의 불일치 — 프론트가 처리할 것

구현 시 반드시 지켜야 하는 두 가지다. 백엔드는 바꾸지 않는다.

### 5.1 `distribution`의 분모는 P(공고 전체)다

API의 `distribution` 7버킷 합계는 항상 **P**(공고 전체 지원자)다. 반면 시안의 스택 막대는 단계마다 분모가 다르다 — 서류 행 합계는 4,182(P)이지만 1차 행 합계는 1,320(서류 통과자)이다.

시안이 읽히길 원하는 건 **"그 단계 대상자 중 결과 구성"**이므로 옳은 선택이다. 프론트는 **`noResult`를 제외한 나머지 6버킷의 합을 분모로 정규화**해야 한다. 정규화하지 않으면 1차 이후 막대가 전부 쪼그라든다.

```
분모 = passed + failed + absent + hold + pending + withdrawn
     = P − noResult
```

### 5.2 `pending`은 6번째 세그먼트로 그린다 (2026-08-11 확정)

시안 범례는 합격/탈락/불참/취소/보류 5개인데 API는 **미확정(`PENDING`)**을 별도 버킷으로 준다. 진행 중인 공고를 보는 대시보드이므로 pending이 오히려 다수일 수 있다(1차 면접이 절반 진행됐으면 절반이 pending). 빠뜨리면 비율이 왜곡된다.

→ 스택 막대에 **미확정을 6번째 세그먼트로 추가**한다. 카테고리 6슬롯이며, 검증기를 통과한 팔레트 6슬롯과 개수가 맞는다.

색 배정에 주의한다. 팔레트 슬롯 6은 녹색(`#008300`)인데 "미확정"에 녹색을 쓰면 합격으로 오독된다. **미확정은 결과가 아니라 결과의 부재**이므로, 시안이 '기타'에 쓴 중립 회색 계열로 빼고 카테고리는 5슬롯만 쓰는 편이 의미상 맞다. 다만 중립색은 채도 하한 때문에 카테고리 검증 대상이 아니므로, 흰 배경 대비만 별도로 확인한다. **프론트 구현 슬라이스에서 확정한다.**

---

## 6. 보안 · 감사

- 두 엔드포인트 모두 경로가 `/admin/...` → `/api/admin/**` 매처에 걸려 `ROLE_ADMIN` + `ROLE_RECRUIT_ADMIN`이다. 대시보드는 두 권한 모두 볼 수 있다. **SecurityConfig 변경 없음.**
- statistics는 집계값만 노출하므로 **audit를 남기지 않는다**(기존 규칙 유지).
- 개인 식별 데이터를 반환하지 않는다. 학교·자격 dimension은 topN + '기타'로 접혀 소수 그룹이 개인을 특정하지 않게 한다(기존 장치).

---

## 7. 범위 밖

- 시안 D(진행 상태·일정)·E(처리 대기)·F(지원자 구성) 위젯. 시안 2a에 포함되지 않았다.
- 전사 통합 퍼널(공고 횡단). 현재 집계는 공고 단위이며 통합값은 별도 설계가 필요하다.
- 경쟁률. `JobPosition`에 모집인원 필드가 없다.
- 캐싱. 먼저 실측하고 필요하면 별도 슬라이스에서 다룬다.

---

## 8. 검증 계획

```powershell
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.AdminStatisticsControllerTest" --tests "com.shinyoung.recruit.service.*Statistics*" --no-daemon
```

기존 17건은 **무수정 통과**해야 한다(하위호환 확인). 여기에 신규 테스트를 더한다.

| 대상 | 검증 |
|---|---|
| dimension 다중 | 3축 동시 요청 시 `dimensionGroups` 3개, 각 그룹 내용이 단일 요청 결과와 일치 |
| dimension 다중 | 다중 요청 시 `dimension = null`, `dimensions = []` |
| dimension 파싱 | 공백·소문자·중복 입력 정규화, 잘못된 값 1개 섞이면 400 |
| 평균 체류 | 알려진 `decidedAt` 배치에서 기대 일수 산출 |
| 평균 체류 | 미확정 건·직전 단계 없는 건 제외, 표본 0이면 `null` |
| 일자별 추이 | 제출 없는 날 0으로 채움, 누적값 단조 증가 |
| 일자별 추이 | `WITHDRAWN` 포함 / `DRAFT` 제외, 총계가 퍼널 `population.p`와 일치 |
| 권한 | 지원자·비인증 차단(기존 패턴) |
