# 관리자 대시보드·통계 (`statistics`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 관련 카드: [job-posting](job-posting.md) (공고 목록 `GET /admin/job-postings`·`getAllJobPostings`·접수 기간) · [stage-result](stage-result.md) (전형 `Stage`·전형결과 `StageResult`·결과 상태) · [application](application.md) (지원서 상태·제출·학력/자격 섹션과 그 리포지토리) · [admin-application](admin-application.md) (지원현황 검색·엑셀) · [privacy-audit](privacy-audit.md) (파기 치환값) · [master-data](master-data.md) (학교 코드)

## 요약

- 관리자 첫 화면(`/admin`, 라우트 `AdminHome`)이 대시보드다. 상단에서 **공고 1건**을 고르면 모든 위젯이 그 공고의 집계로 다시 그려진다. 공고를 가로지르는 전사 집계는 없다.
- 백엔드 API는 2개이고 둘 다 읽기 전용이다: **전형 퍼널**(`/statistics/funnel`)과 **일자별 접수 추이**(`/statistics/applications-daily`).
- 두 API의 모집단은 같다: 한 번이라도 제출한 지원서(`submittedAt != null`). `DRAFT`는 빠지고, 제출 후 철회(`WITHDRAWN`)는 들어간다. 그래서 추이 총계와 퍼널 `population.p`가 같다.
- 퍼널은 공고 전형을 `stageOrder` 순으로 보고, 단계마다 결과 분포(7버킷)와 **순차 통과** 인원·비율·평균 체류일을 낸다. 분야(`POSITION`)·학교(`SCHOOL`)·자격(`CERTIFICATE`)별로 나눈 퍼널도 같은 호출로 받는다.
- 집계값만 돌려주며 개인 식별값·감사 로그(audit)는 없다.

## 용어

| 용어 | 뜻 |
|---|---|
| 모집단 P | 이 공고의 `JobApplication` 중 `submittedAt != null`인 것. 현재 status와 무관. 제출 후 철회 포함, `DRAFT` 제외 |
| `population` | `{ p, currentlySubmittedCount(status=SUBMITTED), withdrawnCount(status=WITHDRAWN) }` |
| 단계 분포 (`distribution`) | 한 단계에서 코호트 전원을 7버킷으로 나눈 수: `passed`/`failed`/`absent`/`hold`/`pending`/`withdrawn` + `noResult`. 합 = 코호트 크기 |
| `noResult` | 그 단계에 `StageResult` 행 자체가 없는 인원(아직 도달·초기화 전). 응답 전용 가상 버킷이다. DB enum 값이나 업로드 입력값이 아니다 |
| `pending` | `StageResult` 행은 있지만 결과가 아직 정해지지 않은 상태(`StageResultStatus.PENDING`). `noResult`와 다르다 |
| 순차 통과 집합 S_k | S0 = P, S_k = S_(k-1) ∩ {k단계 결과 `PASSED`}. `funnelPassedCount` = \|S_k\| |
| `cumulativeRate` | \|S_k\| / \|P\| (P 대비 누적 통과율) |
| `stepConversionRate` | \|S_k\| / \|S_(k-1)\| (직전 단계 대비 전환율) |
| `averageDwellDays` | 단계 평균 체류일. 기준 시각은 첫 단계면 `submittedAt`, 그 뒤는 직전 단계의 `decidedAt`. 표본이 없으면 `null` |
| dimension (집계 축) | `FunnelDimension` = `POSITION`(분야) / `SCHOOL`(최종학력 학교) / `CERTIFICATE`(자격명). 모두 지원서 단위 distinct로 센다 |
| `topN` · '기타' | 학교·자격 축에서 상위 N개 그룹만 따로 보여 주고 나머지는 이름이 `기타`인 그룹 하나로 합친다 |
| 대상자 수 (FE) | 한 단계에서 `P − noResult`(6버킷 합). 결과 구성 막대의 분모 |

## 파일 지도

### 백엔드

| 레이어 | 파일 | 역할 |
|---|---|---|
| controller | `{BE}/controller/AdminStatisticsController.java` | 통계 GET 2개. 클래스 `@RequestMapping` 없음 |
| service | `{BE}/service/FunnelStatisticsService.java` | 퍼널: P 코호트·분포·순차 통과·체류일·dimension |
| service | `{BE}/service/ApplicationTrendStatisticsService.java` | 일자별 제출 수: 구간·0 채우기·누적 |
| enum | `{BE}/enumeration/FunnelDimension.java` | `POSITION`·`SCHOOL`·`CERTIFICATE` |
| exception | `{BE}/exception/InvalidStatisticsRequestException.java` | 잘못된 `dimension` → 400 |
| dto | `{BE}/dto/response/FunnelResponse.java` | 퍼널 응답 최상위 |
| dto | `{BE}/dto/response/DimensionGroupResponse.java` | 축 하나의 묶음 |
| dto | `{BE}/dto/response/DimensionFunnelResponse.java` | 그룹 하나의 퍼널 |
| dto | `{BE}/dto/response/FunnelPopulationResponse.java` | 모집단 요약 |
| dto | `{BE}/dto/response/StageFunnelResponse.java` | 단계 하나의 퍼널 결과 |
| dto | `{BE}/dto/response/StageDistributionResponse.java` | 7버킷 분포 |
| dto | `{BE}/dto/response/ApplicationDailyResponse.java` | 추이 응답 |
| dto | `{BE}/dto/response/ApplicationDailyPointResponse.java` | 추이 한 점 |
| dto | `{BE}/dto/response/FunnelCohortRow.java` | 집계 입력(JPQL `new`): P 한 건 |
| dto | `{BE}/dto/response/FunnelStageResultRow.java` | 집계 입력(JPQL `new`): 지원서×단계 결과 |
| dto | `{BE}/dto/response/FunnelSchoolEducationRow.java` | 집계 입력(JPQL `new`): 학력 한 건 |
| dto | `{BE}/dto/response/FunnelCertificateRow.java` | 집계 입력(JPQL `new`): 자격 한 건 |
| dto | `{BE}/dto/response/ApplicationDailyCountRow.java` | 집계 입력(JPQL `new`): 날짜별 건수 |
| test | `{BT}/controller/AdminStatisticsControllerTest.java` | `@SpringBootTest` 29건(분포·비율·체류일·축 3종·파싱·추이·권한·404) |

집계 입력 쿼리가 있는 리포지토리는 다른 카드 소유다: `JobApplicationRepository`(`findFunnelCohort`·`findDailySubmittedCounts`)·`ApplicationEducationRepository`(`findFunnelSchoolEducations`)·`ApplicationCertificateRepository`(`findFunnelCertificates`) → [application](application.md), `StageResultRepository`(`findFunnelStageResults`)·`StageRepository`(`findByJobPostingIdOrderByStageOrderAscIdAsc`) → [stage-result](stage-result.md). 모두 `{BE}/domain/repository/`에 있다.

### 프론트

| 구분 | 파일 | 역할 |
|---|---|---|
| route | `{FE}/routes/adminRoutes.ts` | (공유) `AdminHome`(path `''` → `/admin`). 부모 meta `requiresAuth`, `roles: ADMIN_ROLES` |
| view | `{FE}/views/admin/AdminHomeView.vue` | 대시보드 본체: 공고 선택·새로고침, 통계 2콜 병렬, 응답 경합 가드, 위젯 배치 |
| component | `{FE}/views/admin/dashboard/DashboardCard.vue` | 카드 껍데기(제목·부제·액션 슬롯) |
| component | `{FE}/views/admin/dashboard/TableToggleButton.vue` | 차트↔표 전환(접근성 요구사항) |
| component | `{FE}/views/admin/dashboard/StageFunnelCard.vue` | "단계 퍼널": 단계별 통과 인원 막대, 전체 통과율·최대 이탈 구간·평균 체류 |
| component | `{FE}/views/admin/dashboard/StageResultCompositionCard.vue` | "단계별 결과 구성": 6세그먼트 스택 막대(분모 = P − noResult) |
| component | `{FE}/views/admin/dashboard/PositionFunnelCard.vue` | "분야별 퍼널 비교": 분야마다 작은 퍼널(스몰 멀티플) |
| component | `{FE}/views/admin/dashboard/TopGroupCard.vue` | "학교별 지원자"·"자격별 보유" 상위 N 가로 막대(재사용) |
| component | `{FE}/views/admin/dashboard/DailyTrendCard.vue` | "일자별 지원 접수 추이": 인라인 SVG 선 그래프 |
| component | `{FE}/common/chartPalette.ts` | 차트 색 상수(`CHART_SERIES_COLORS`·`CHART_ORDINAL_COLORS`·`CHART_NEUTRAL_COLOR`·`LOW_CONTRAST_SERIES_COLORS`)·`ordinalColorAt`. 대시보드 전용 |
| api | `{FE}/api/statisticsApi.ts` | `statisticsApi.getFunnel(jobPostingId, dimensions[], topN?)`, `getApplicationsDaily(jobPostingId)` |
| api | `{FE}/api/adminJobPostingApi.ts` | (공유, [job-posting](job-posting.md) 소유) 여기서는 `getAllJobPostings`만 쓴다 |
| types | `{FE}/types/statistics.ts` | `FunnelDimension`·`StageDistribution`·`StageFunnel`·`FunnelPopulation`·`DimensionFunnel`·`DimensionGroup`·`FunnelResult`·`ApplicationDaily(Point)` |

## API 계약

권한: 두 경로 모두 `/api/admin/**` 매처 → `ADMIN`·`RECRUIT_ADMIN` (`{BE}/config/SecurityConfig.java`). 지원자 403, 비인증 401. 응답은 `ApiResponse<...>` 래핑. 계약 확정 2026-08-12.

| 상태 | 메서드 | 경로 | 요청 요약 | 응답 요약 | 권한 |
|---|---|---|---|---|---|
| 🟢 | GET | /admin/job-postings/{jobPostingId}/statistics/funnel | query `dimension?`(콤마 구분 여러 값: `POSITION`,`SCHOOL`,`CERTIFICATE`), `topN?`(기본 10, 상한 100) | `FunnelResponse` `{ jobPostingId, jobPostingTitle, population, stages[], dimensionGroups[], dimension(deprecated), dimensions(deprecated) }` | ADMIN·RECRUIT_ADMIN |
| 🟢 | GET | /admin/job-postings/{jobPostingId}/statistics/applications-daily | — | `ApplicationDailyResponse` `{ jobPostingId, jobPostingTitle, from, to, totalSubmitted, days: [{ date, submittedCount, cumulativeCount }] }` | ADMIN·RECRUIT_ADMIN |

### 엔드포인트 상세

**응답 모양 (퍼널)**

- `population`: `{ p, currentlySubmittedCount, withdrawnCount }`
- `stages[]`(`StageFunnelResponse`): `{ stageOrder, stageId, stageName, stageType, distribution: { passed, failed, absent, hold, pending, withdrawn, noResult }, funnelPassedCount, cumulativeRate, stepConversionRate, averageDwellDays: number | null }`
- `dimensionGroups[]`: `{ dimension, groups: [{ groupId, groupName, population, stages[] }] }` — 항상 들어 있다. `dimension`을 안 보내면 `[]`.
- `dimension`/`dimensions`: **한 축만** 요청했을 때만 그 축 값과 그 축 groups로 채운다. 여러 축이거나 축이 없으면 `null`/`[]`. `@Deprecated` 필드이므로 새 소비자는 `dimensionGroups`만 쓴다. FE 타입(`FunnelResult`)에는 이 두 필드가 없다.

**GET funnel** — 🟢 확정(하위호환 확장, front-back 반영 완료)

- 화면 범위는 공고 1건이다. `dimension`은 콤마로 여러 값을 받는다(대시보드 4콜 → 1콜). 파싱·`topN` 규칙은 규칙 12·19.
- `averageDwellDays`(2026-08-11 확정): 규칙 11. 표본이 0이면 `null`(0.0으로 채우지 않는다 — "즉시 처리"와 "표본 없음"은 다르다). 단위는 일, 소수 1자리.
- 구현 주의: `FunnelStageResultRow.decidedAt`, `FunnelCohortRow.submittedAt`은 JPQL `new` 프로젝션이다. **record와 쿼리를 반드시 함께** 고친다.
- 오류: 400(dimension), 404(공고 없음).

**GET applications-daily** — 🟢 확정(신규, front-back 반영 완료)

- 기준은 `submittedAt`의 날짜다. `WITHDRAWN` 포함(그날 제출한 사실은 있다), `DRAFT` 제외 → `totalSubmitted` = 퍼널 `population.p`. 구간·0 채우기는 규칙 21~24.
- 퍼널과 관심사가 달라 서비스를 분리했다. 집계는 DB `GROUP BY` 한 번이다.
- 🔴 DB 호환성 미검증(계약 원문 유지): 날짜 그룹에 JPQL `cast(application.submittedAt as LocalDate)`를 쓴다. H2(테스트)에서만 검증했고 운영 후보 MariaDB에서는 확인하지 않았다. 번역이 실패하면 날짜 그룹을 Java 계층으로 옮긴다.
- 오류: 404(공고 없음).

**대시보드 위젯 ↔ 데이터** (`AdminHomeView.vue` 한 번 로드 = 공고 목록 1회 + 통계 2콜 병렬)

| 위젯(컴포넌트) | 제목 | 쓰는 API·필드 |
|---|---|---|
| 공고 선택 | — | `getAllJobPostings()` → `GET /admin/job-postings`를 size 100으로 마지막 페이지까지 ([job-posting](job-posting.md)) |
| `StageFunnelCard` | 단계 퍼널 | funnel `population.p`, `stages[].funnelPassedCount`·`cumulativeRate`·`stepConversionRate`·`averageDwellDays` |
| `StageResultCompositionCard` | 단계별 결과 구성 | funnel `stages[].distribution` |
| `PositionFunnelCard` | 분야별 퍼널 비교 | funnel `dimensionGroups[POSITION].groups[]` |
| `TopGroupCard` ×2 | 학교별 지원자 / 자격별 보유 | funnel `dimensionGroups[SCHOOL]`·`[CERTIFICATE]`의 `groups[].population.p` |
| `DailyTrendCard` | 일자별 지원 접수 추이 | applications-daily 전체 |

- 통계 호출은 `getFunnel(id, ['POSITION','SCHOOL','CERTIFICATE'], 5)` → `?dimension=POSITION,SCHOOL,CERTIFICATE&topN=5`, 그리고 `getApplicationsDaily(id)`.
- 프론트가 처리하는 불일치(백엔드는 바꾸지 않음): (1) `distribution` 분모는 P(공고 전체)다. 결과 구성 막대는 "그 단계 대상자" 기준이라 `noResult`를 뺀 6버킷 합으로 정규화한다. 안 하면 뒤 단계 막대가 전부 쪼그라든다. (2) `pending`을 6번째 세그먼트로 그리고 중립 회색을 쓴다(녹색 슬롯 6은 합격으로 오독된다).
- 범위 밖: 진행 상태·일정, 처리 대기, 지원자 구성 위젯, 전사 통합 퍼널(공고 횡단), 경쟁률(`JobPosition`에 모집 인원 필드 없음), 캐싱.

## 규칙·불변식

**공통**

1. 두 API 모두 공고가 없으면 404 `JobPostingNotFoundException` (`{BE}/service/FunnelStatisticsService.java` — `getFunnel`; `{BE}/service/ApplicationTrendStatisticsService.java` — `getDailySubmissions`).
2. 읽기 전용(`@Transactional(readOnly = true)`)이다. 감사 로그를 남기지 않고 집계값만 반환한다. 개인 식별 필드를 응답에 넣지 않는다 (`{BE}/service/FunnelStatisticsService.java`, `{BE}/service/ApplicationTrendStatisticsService.java` — 클래스 선언).
3. 예외 → HTTP: `InvalidStatisticsRequestException` 400 `ApiResponse.fail(message)` (`{BE}/exception/GlobalExceptionHandler.java` — `handleInvalidStatisticsRequest`).

**퍼널 집계 기준**

4. 모집단 P = 공고의 지원서 중 `submittedAt is not null`. 상태 전이는 `DRAFT → SUBMITTED`(제출 시 `submittedAt` 설정) → `WITHDRAWN`(`submittedAt` 유지)뿐이고 `DRAFT`로 돌아가는 길이 없다. 그래서 `DRAFT`는 항상 빠지고 제출 후 철회는 항상 들어간다 (`{BE}/domain/repository/JobApplicationRepository.java` — `findFunnelCohort`; `{BE}/domain/entity/JobApplication.java` — `submit`·`withdraw`).
5. `population.currentlySubmittedCount` = P 중 현재 `SUBMITTED`, `withdrawnCount` = P 중 현재 `WITHDRAWN` (`{BE}/service/FunnelStatisticsService.java` — `computeCohort`).
6. 단계 = 그 공고의 모든 `Stage`를 `stageOrder ASC, id ASC`로. 서류·면접을 구분하지 않고 모두 한 단계로 본다. 단계가 없으면 `stages = []` (`{BE}/domain/repository/StageRepository.java` — `findByJobPostingIdOrderByStageOrderAscIdAsc`).
7. 단계 결과는 P 멤버의 `StageResult`만 읽는다(`submittedAt is not null` 조건) (`{BE}/domain/repository/StageResultRepository.java` — `findFunnelStageResults`).
8. `distribution`: 코호트 전원을 그 단계 결과로 센다. 행 없음 → `noResult`, 나머지는 `StageResultStatus` 6종 그대로다. 7버킷 합은 항상 코호트 크기다 (`{BE}/service/FunnelStatisticsService.java` — `distribution`).
9. 비율은 raw `distribution.passed`가 아니라 순차 통과 집합으로 계산한다. 보정·수동 수정 때문에 앞 단계를 통과하지 않은 지원서가 뒤 단계에서 `PASSED`일 수 있기 때문이다(raw로 계산하면 전환율이 100%를 넘을 수 있다). 분모가 0이면 비율은 0.0 (`{BE}/service/FunnelStatisticsService.java` — `computeCohort`).
10. `distribution.withdrawn`(단계 결과 `WITHDRAWN`)과 `population.withdrawnCount`(지원서 `WITHDRAWN`)는 서로 다른 값이다. 합치거나 덮어쓰지 않는다 (`{BE}/dto/response/StageDistributionResponse.java`, `{BE}/dto/response/FunnelPopulationResponse.java`).
11. 평균 체류일 표본: 이 단계 `decidedAt`이 있고 기준 시각도 있는 건만 쓴다. 음수는 뺀다. 다음 단계 기준 시각은 이 단계에서 `decidedAt`이 있는 **모든** 코호트 멤버의 것이다(통과자만이 아니다). 결과는 `Math.round(x*10)/10`, 표본이 0이면 `null` (`{BE}/service/FunnelStatisticsService.java` — `averageDwellDays`).

**dimension (`FunnelDimension`)별 동작**

12. 파싱: null·공백이면 축 없음. 콤마로 나눠 trim, 대문자, `LinkedHashSet`으로 중복 제거(순서 유지)한다. 모르는 값이 있거나 유효 토큰이 0개면 400 `지원하지 않는 dimension 값입니다.` (`{BE}/service/FunnelStatisticsService.java` — `parseSupportedDimensions`).
13. `dimensionGroups`는 요청 순서대로 들어간다. 한 축만 요청했으면 deprecated `dimension`·`dimensions`도 채운다 (`{BE}/service/FunnelStatisticsService.java` — `getFunnel`).
14. 축 분기는 `switch` 식이라 enum 값을 추가하면 컴파일 오류로 누락이 드러난다 (`{BE}/service/FunnelStatisticsService.java` — `computeDimension`).
15. 그룹마다 그룹 코호트를 P로 두고 규칙 8·9·11을 그대로 적용한다. 그룹의 `population.p`는 그룹 인원이다 (`{BE}/service/FunnelStatisticsService.java` — `computeCohort`).
16. `POSITION`: 지원서의 `jobPosition`으로 P를 나눈다(분할). 지원자가 있는 분야만 그룹이 생긴다(0명 분야는 없음). 정렬은 `JobPosition.sortOrder` 오름차순(null은 뒤), 같으면 id. `groupId` = `jobPositionId`, `groupName` = **현재** `JobPosition.positionName`(지원 당시 스냅샷 아님). `topN` 무시 (`{BE}/service/FunnelStatisticsService.java` — `computePositionDimension`).
17. `SCHOOL`: 지원자마다 최종학력 1건을 고른다 — `educationLevel`이 가장 높은 것(`HIGH_SCHOOL < COLLEGE < UNIVERSITY < MASTER < DOCTOR`, enum 순서, null은 최하). 같으면 `schoolCode`가 있는 쪽. 그 `schoolCode`로 묶는다. `schoolCode`가 없거나(직접 입력) 학력이 없으면 '기타'. 인원 내림차순·`schoolCode` 오름차순 상위 `topN`만 개별 그룹이고, 나머지 학교와 미매칭은 '기타' 하나로 합친다(비면 생략). P의 분할이다. `groupId`는 항상 null, `groupName`은 그 코드를 가진 첫 코호트 지원자의 최종학력 `schoolName` (`{BE}/service/FunnelStatisticsService.java` — `computeSchoolDimension`·`pickFinalEducation`).
18. `CERTIFICATE`: 자격명을 trim하고 연속 공백을 한 칸으로 줄인다(대소문자는 그대로). 빈 이름은 뺀다. "자격명별 보유 지원자 distinct"로 센다. 분할이 아니다 — 한 사람이 여러 그룹에 들어갈 수 있고, 자격이 없는 사람은 어느 그룹에도 없다. 보유자 수 내림차순·이름 오름차순 상위 `topN`만 개별 그룹이고, 나머지 자격 보유자의 합집합(distinct)이 '기타'다(상위 그룹과 겹칠 수 있음). `groupId`는 항상 null (`{BE}/service/FunnelStatisticsService.java` — `computeCertificateDimension`·`normalizeCertificateName`).
19. `topN`이 null이거나 0 이하면 10, 100을 넘으면 100이다. 400을 내지 않는다 (`{BE}/service/FunnelStatisticsService.java` — `DEFAULT_DIMENSION_TOP_N`·`MAX_DIMENSION_TOP_N`).
20. 코호트·단계 결과는 요청당 1회 읽는다. `SCHOOL`이면 학력, `CERTIFICATE`면 자격 쿼리를 한 번씩 더 읽는다 (`{BE}/service/FunnelStatisticsService.java` — `getFunnel`; `{BE}/domain/repository/ApplicationEducationRepository.java` — `findFunnelSchoolEducations`; `{BE}/domain/repository/ApplicationCertificateRepository.java` — `findFunnelCertificates`).

**일자별 추이**

21. 모집단은 퍼널 P와 같다(`submittedAt is not null`). 날짜는 DB `cast(submittedAt as LocalDate)`로 `GROUP BY`한다. 쿼리는 구간을 거르지 않고 전체를 돌려준다 (`{BE}/domain/repository/JobApplicationRepository.java` — `findDailySubmittedCounts`).
22. 구간: `from` = `receptionStartDateTime.toLocalDate()`, `to` = min(`receptionEndDateTime.toLocalDate()`, `LocalDate.now()`). `to < from`이면(접수 시작 전) `to = from`이라 0인 점 하나를 준다 (`{BE}/service/ApplicationTrendStatisticsService.java` — `resolveRangeEnd`).
23. 구간 밖 제출은 양 끝으로 당겨 넣지 않고 **제외**하며 건수만 WARN 로그로 남긴다. 이때는 `totalSubmitted < population.p`가 된다 (`{BE}/service/ApplicationTrendStatisticsService.java` — `getDailySubmissions`).
24. `days`는 `from`~`to`의 모든 날짜다(0건인 날 포함). `cumulativeCount`는 누적이고 `totalSubmitted` = 마지막 누적 (`{BE}/service/ApplicationTrendStatisticsService.java` — `getDailySubmissions`).

**프론트**

25. 공고를 바꾸면 funnel과 daily를 `Promise.all`로 함께 호출한다. 하나라도 실패하면 "통계를 불러오지 못했습니다." 오류를 띄운다 (`{FE}/views/admin/AdminHomeView.vue` — `loadStatistics`·`changeJobPosting`).
26. 응답 경합: `statisticsRequestSeq`로 가장 최근 요청의 응답만 반영한다. 다른 공고로 바꾸면 이전 통계를 먼저 비운다. 같은 공고 새로고침이면 이전 화면을 흐리게(`is-refreshing`) 남겨 둔다 (`{FE}/views/admin/AdminHomeView.vue` — `changeJobPosting`).
27. 공고 목록은 전체 페이지를 받는다(`DRAFT` 포함). 기본 선택은 `accepting`인 첫 공고, 없으면 목록 첫 공고다 (`{FE}/views/admin/AdminHomeView.vue` — `onMounted`·`pickDefaultJobPosting`).
28. FE는 `dimensionGroups`만 읽는다(`groupsOf(dimension)`) (`{FE}/views/admin/AdminHomeView.vue` — `groupsOf`).
29. 단계 퍼널: 첫 행은 P('지원'), 막대 길이는 P 대비 비율이다. 전체 통과율 = 마지막 단계 `cumulativeRate`. 최대 이탈 구간 = `stepConversionRate`가 가장 낮은 단계(같으면 앞 단계)를 "직전 → 그 단계"로 표시한다. 평균 체류 = null이 아닌 단계 값의 평균(소수 1자리)이고, 전부 null이면 '—'(0일로 표시하지 않음). P = 0이면 표의 비율도 '—' (`{FE}/views/admin/dashboard/StageFunnelCard.vue` — `rows`·`overallPassRate`·`worstStepLabel`·`averageDwellDays`).
30. 결과 구성: 분모 = 6버킷 합(P − `noResult`), 0이면 "대상자 없음". 세그먼트 순서·색은 고정이다(합격·탈락·불참·취소·보류·미확정). 미확정은 `CHART_NEUTRAL_COLOR`. 점유율 12% 미만이면 라벨을 숨긴다 (`{FE}/views/admin/dashboard/StageResultCompositionCard.vue` — `SEGMENTS`·`rows`).
31. 상위 N 막대: 길이는 P 대비가 아니라 최댓값 대비다. `groupName === '기타'`이면 회색이다(서버 `OTHER_GROUP_NAME` 문자열과 같아야 함) (`{FE}/views/admin/dashboard/TopGroupCard.vue` — `rows`).
32. 추이 그래프는 0건인 날도 그린다. 표 보기는 0건인 날을 뺀다. 최다 접수일을 표시한다 (`{FE}/views/admin/dashboard/DailyTrendCard.vue` — `points`·`tableRows`·`peakDay`).
33. 차트 색은 `chartPalette.ts` 값만 쓴다(검증된 값이라 눈대중 변경 금지, 브랜드 녹색은 시리즈 색으로 쓰지 않음). 저대비 색(`LOW_CONTRAST_SERIES_COLORS`)을 쓰는 차트는 표 보기를 함께 제공한다 (`{FE}/common/chartPalette.ts`; `{FE}/views/admin/dashboard/TableToggleButton.vue`).

## 변경 레시피

### 퍼널 dimension 축 추가 (예: 어학)

1. `{BE}/enumeration/FunnelDimension.java`에 값을 추가한다. 그러면 `computeDimension` `switch`가 컴파일 오류를 낸다. `{BE}/service/FunnelStatisticsService.java`에 `computeXxxDimension`을 구현한다(분할인지 중복 가능인지, `topN`·'기타' 적용 여부를 정한다).
2. 입력 projection record `{BE}/dto/response/Funnel*Row.java`를 새로 만들고, 해당 섹션 리포지토리에 JPQL `new` 쿼리를 추가한다(조건: `jobApplication.jobPosting.id = :jobPostingId and jobApplication.submittedAt is not null`). 리포지토리는 [application](application.md) 소유이므로 그 카드 규칙도 확인한다.
3. `{BT}/controller/AdminStatisticsControllerTest.java`에 그룹 분할·정렬·'기타'·다중 축 케이스를 추가한다.
4. FE: `{FE}/types/statistics.ts` `FunnelDimension` 유니온 → `{FE}/views/admin/AdminHomeView.vue` `DASHBOARD_DIMENSIONS`·위젯 배치 → 필요하면 `TopGroupCard.vue` 재사용.
5. 이 카드 `## 용어`·`## API 계약`·규칙 12~19 갱신 → `node tools/check-docs.mjs`.

### 퍼널 응답에 단계 지표 추가

1. 새 입력 값이 필요하면 projection record와 JPQL을 **같이** 고친다: `FunnelCohortRow` ↔ `JobApplicationRepository.findFunnelCohort`, `FunnelStageResultRow` ↔ `StageResultRepository.findFunnelStageResults`. 한쪽만 고치면 생성자 시그니처가 어긋나 런타임에 실패한다.
2. `{BE}/dto/response/StageFunnelResponse.java`(또는 `FunnelPopulationResponse`)에 필드를 추가하고 `computeCohort`에서 계산한다. "값 없음"은 0이 아니라 `null`로 구분할지 정한다.
3. 테스트는 응답을 역직렬화해 검증하므로 필드 추가는 기존 테스트를 깨지 않는다. 새 케이스를 추가한다.
4. FE `{FE}/types/statistics.ts` → 해당 카드 컴포넌트.
5. 카드 `## API 계약` 갱신 → `node tools/check-docs.mjs`.

### 일자별 추이 기준 변경 (구간·시간대·Clock)

1. `{BE}/service/ApplicationTrendStatisticsService.java` — `resolveRangeEnd`. 오늘 기준을 테스트에서 고정하려면 `{BE}/config/TimeConfig.java`의 `Clock` 빈을 주입하고 `LocalDate.now(clock)`으로 바꾼다(다른 서비스와 같은 방식).
2. 날짜 그룹 방식을 바꾸면(예: MariaDB 호환 때문에 Java 그룹으로) `JobApplicationRepository.findDailySubmittedCounts`와 `{BE}/dto/response/ApplicationDailyCountRow.java`를 함께 고친다.
3. 테스트: `applications_daily_*` 4건. 퍼널 P와 총계가 같은지 확인하는 케이스를 유지한다.
4. FE `{FE}/views/admin/dashboard/DailyTrendCard.vue`(부제 `from ~ to`).
5. 카드 규칙 21~24 갱신 → `node tools/check-docs.mjs`.

### 대시보드 위젯 추가·변경 (API 무변경)

1. `{FE}/views/admin/dashboard/`에 카드를 만든다. `DashboardCard.vue` 껍데기와 `TableToggleButton.vue`를 쓰고, 색은 `{FE}/common/chartPalette.ts`에서만 가져온다.
2. `{FE}/views/admin/AdminHomeView.vue`에 배치한다. 데이터는 이미 받은 `funnel`/`daily`에서 파생한다(호출 추가를 피한다).
3. `npm run type-check`.
4. 새 `.vue`는 이 카드 `## 파일 지도`에 등록한다 → `node tools/check-docs.mjs`. 계약 영향이 없는지 확인한다.

## 검증

백엔드(`recruit_back/recruit_backend/`에서):

```powershell
# Windows PowerShell
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.AdminStatisticsControllerTest" --no-daemon
```

```bash
# Linux
AES_SECRET_KEY='<로컬 예시 키>' ./gradlew test --tests "com.shinyoung.recruit.controller.AdminStatisticsControllerTest" --no-daemon
```

집계 입력 쿼리(`findFunnelCohort`·`findFunnelStageResults`·`findFunnelSchoolEducations`·`findFunnelCertificates`·`findDailySubmittedCounts`)가 있는 리포지토리를 고쳤다면 그 리포지토리 소유 카드([application](application.md), [stage-result](stage-result.md))의 검증 절도 돌린다.

프론트(`recruit_front/`에서). 이 도메인의 vitest spec은 없다.

```bash
npm run type-check
```

문서(레포 루트에서): `node tools/check-docs.mjs`

## 함정·결정

- **시간대·오늘 기준**: `ApplicationTrendStatisticsService.resolveRangeEnd`(약 89행)는 `Clock` 없이 `LocalDate.now()`를 쓴다. JVM 기본 시간대를 따르고(`TimeConfig`의 `Clock.systemDefaultZone()`과 같은 시간대) 테스트에서 고정할 수 없다. 테스트 픽스처 접수 기간(2026-05-01~2026-12-31)은 실제 오늘 날짜에 기대어 통과한다. "오늘로 잘리는" 분기는 검증되지 않는다. `submittedAt`은 `LocalDateTime`(시간대 없음)이라 서버 시간대를 바꾸면 날짜 경계가 달라진다.
- 🔴 MariaDB에서 `cast(... as LocalDate)` 번역은 미검증이다(H2만 검증). 운영 전환 때 먼저 확인한다.
- 재제출하면 `JobApplication.submit`이 `submittedAt`을 덮어쓴다. 그래서 추이는 **마지막 제출일**로 잡히고, 첫 단계 체류일 기준 시각도 마지막 제출 시각이다. P 소속은 변하지 않는다.
- 파기 잠재 문제(코드 확인, 테스트 없음): `{BE}/domain/repository/ApplicationPiiPurgeRepository.java`가 `schoolName`·`certificateName`을 `__PURGED__`로 바꾸고 `schoolCode`는 남긴다([privacy-audit](privacy-audit.md)). 퍼널은 파기 여부를 거르지 않는다. 그래서 파기된 공고의 `CERTIFICATE` 축에 `__PURGED__` 그룹이 생기고, `SCHOOL` 그룹 이름이 `__PURGED__`가 될 수 있다.
- '기타'는 문자열 약속이다. 서버 `OTHER_GROUP_NAME`과 FE `TopGroupCard.vue`의 `OTHER_GROUP_NAME`이 같아야 한다. 실제 자격명이 "기타"면 FE에서 잔여 묶음처럼 회색으로 보인다.
- `8d7485d` 대시보드 응답 경합 가드(늦게 온 이전 공고 응답 무시). 공고 목록이 첫 페이지 50건만 받던 문제를 `getAllJobPostings`로 수정. P = 0일 때 막대·비율을 '—'로 표시. 재발 주의(규칙 26·27·29).
- `34ed07c` 학력 학교 식별자를 `schoolId` → `schoolCode`/`schoolSource`로 교체했다. `SCHOOL` 축은 `School` 마스터 조회 없이 학력 행의 `schoolCode`·`schoolName`만 쓴다.
- 오래된 설명(수정 승인 전까지 코드가 기준): `FunnelDimension` javadoc의 `schoolId`(현재 `schoolCode`), `DimensionFunnelResponse` javadoc의 "분야명 snapshot"(실제는 현재 `positionName`), 테스트 이름 `school_dimension_tie_break_prefers_education_with_school_id_at_same_level`.
- `{BE}/domain/repository/JobPositionCountProjection.java`는 이름과 달리 통계와 무관하다. `{BE}/service/JobPostingService.java`의 공고 목록 `positionCount` 전용이며 [job-posting](job-posting.md) 소유다.
- FE `StageFunnel.stageType`은 `string`으로 선언돼 있다(백엔드는 `StageType` enum).
- `recruit_back/recruit_backend/docs/adr/0002-phase07-export-readonly-upload-stageresult-only.md` — export·statistics·PDF는 도메인 상태를 바꾸지 않는 읽기 전용이다.
- `recruit_back/recruit_backend/docs/adr/0004-school-optional-application-level-link.md` — 학교 매칭은 선택적인 지원서 수준 참조다. 미매칭(직접 입력)은 '기타'로 가고 과거 데이터는 소급 매칭하지 않는다(ADR 본문의 `schoolId`는 현재 `schoolCode`).
- `recruit_back/recruit_backend/docs/adr/0005-retention-purge-mode-tombstone-anonymization-binary-deletion.md` — 파기는 행을 지우지 않고 비식별 치환하므로 P·인원 distinct는 파기 뒤에도 재현된다(자유 입력 축은 예외, 위 파기 항목 참고).
