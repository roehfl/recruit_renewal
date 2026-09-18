# Phase 07c - 공고 단위 전형 Funnel 통계

## Review 반영 (instruction.md, 7 findings)

- **(Medium 1) POSITION dimension 정렬/표시명**: 그룹 정렬을 `jobPositionId`에서 공고 등록 순서
  `JobPosition.sortOrder`(동률 id)로 변경하고, 표시명을 지원 당시 snapshot이 아니라 현재
  `JobPosition.positionName`으로 변경. `FunnelCohortRow`에 `jobPositionSortOrder` 추가 + 조회 쿼리 수정.
- **(Medium 2) raw passed ≠ funnelPassedCount 테스트 추가**: 보정 데이터(이전 단계 미통과인데 후속 PASSED)로
  raw `distribution.passed=3` vs `funnelPassedCount=1`, `stepConversionRate=1.0`, `cumulativeRate=1/3` 검증.
- **(Medium 3) PENDING/ABSENT/HOLD/NO_RESULT 버킷 테스트 추가**: PENDING(row 있음)과 NO_RESULT(row 없음) 구분 고정, 합=|P|.
- **(Medium 4) withdrawn 분리 테스트 추가**: 철회 지원서는 stage 결과 PASSED, 제출 지원서는 stage 결과 WITHDRAWN으로
  `population.withdrawnCount`(app status)와 `distribution.withdrawn`(stage result status)가 서로 다른 지원서를 가리킴을 검증.
- **(Medium 5) DRAFT 제외 테스트 추가**: DRAFT(submittedAt null)가 P/분포에서 제외(`p=2`)됨을 고정.
- **(Low) topN**: POSITION에서 무시됨을 controller javadoc/주석에 명시(안2 유지).
- **(Low) 0명 모집분야**: 정책 A 유지(P에 존재하는 분야만 응답) — Known limitations에 명시.

## Phase summary

공고 단위 전형 funnel 통계 조회 API를 추가한다. 모집단 P(제출 이력 보유 지원서)를 기준으로 stage별
raw 7-bucket 분포(+synthetic NO_RESULT)와 순차 통과 집합(funnelPassedCount)·두 비율(누적/직전 전환)을
산출하고, overall과 분야별(POSITION) dimension breakdown을 제공한다. read-only이며 집계값만 노출(audit 없음).

## Purpose

운영자가 공고의 전형 단계별 통과/탈락 추이와 전환율을 한눈에 볼 수 있게 한다. 화면별 개인정보가 아니라
집계값만 노출한다.

## Scope

- `GET /admin/job-postings/{jobPostingId}/statistics/funnel` (overall + `dimension=POSITION` breakdown)
- 모집단 P = `submittedAt != null` 코호트(현재 status 무관, 재현 가능).
- stage별 7-bucket 분포: PASSED/FAILED/ABSENT/HOLD/PENDING/WITHDRAWN + 응답 전용 synthetic NO_RESULT (합=|P|).
- 순차 통과 집합 `S_k = S_(k-1) ∩ {stage k PASSED}` → `funnelPassedCount`, `cumulativeRate=|S_k|/|P|`, `stepConversionRate=|S_k|/|S_(k-1)|`.
- dimension=POSITION: 분야별(FK) 그룹 funnel(application 단위 distinct).

## Out of scope

- 학교별(SCHOOL)/자격별(CERTIFICATE) dimension — free-text 부정확성으로 미확정. 호출 시 **400 미지원** 응답.
- Excel upload(07d), Application PDF(07e).
- 산술평균/가중치/자동 합불 판정, 비동기, 캐싱.
- audit(집계값만이므로 미기록).

## Changed files

### New (main)

- `enumeration/FunnelDimension.java` (POSITION/SCHOOL/CERTIFICATE; 07c는 POSITION만 활성)
- `exception/InvalidStatisticsRequestException.java` (400)
- `dto/response/FunnelCohortRow.java`, `dto/response/FunnelStageResultRow.java` (내부 projection)
- `dto/response/FunnelPopulationResponse.java`, `StageDistributionResponse.java`, `StageFunnelResponse.java`,
  `DimensionFunnelResponse.java`, `FunnelResponse.java`
- `service/FunnelStatisticsService.java`
- `controller/AdminStatisticsController.java`

### Modified (main)

- `domain/repository/JobApplicationRepository.java` — `findFunnelCohort` (projection: id/status/positionId/현재 positionName/positionSortOrder).
- `domain/repository/StageResultRepository.java` — `findFunnelStageResults` (projection: appId/stageId/resultStatus).
- `exception/GlobalExceptionHandler.java` — `InvalidStatisticsRequestException`(400) 핸들러.

### New (test)

- `controller/AdminStatisticsControllerTest.java` (5)

## Class-by-class explanation

### FunnelStatisticsService (Service)

- package: `com.shinyoung.recruit.service`
- responsibility: P 코호트·stage 결과를 읽어 funnel을 in-memory 산출.
- key method: `getFunnel(jobPostingId, dimensionParam, topN)` (`@Transactional(readOnly = true)`).
- 흐름: 공고 존재검증(404) → dimension 파싱(POSITION만 허용, 그 외 400) → stages(stageOrder asc) +
  cohort projection + stage-result projection 로드 → `(stageId→(appId→resultStatus))` 인덱스 →
  `computeCohort`로 overall 산출 → POSITION이면 positionId로 그룹핑하고 `JobPosition.sortOrder`(동률 id) 순으로
  정렬해 그룹별 `computeCohort`(표시명은 현재 `JobPosition.positionName`).
- `computeCohort`: distribution은 코호트 전체에 대한 raw 분포(합=|P|), `funnelPassedCount`는 순차 통과 집합 기준.
  비율 분모: cumulative=|P|, step=직전 stage의 생존수(S0=P). |P|=0 또는 직전 생존수 0이면 비율 0.
- note: NO_RESULT = 결과 row 없는 P 멤버(map 미존재). PENDING(row 있음)과 구분.

### AdminStatisticsController (Controller)

- `GET /admin/job-postings/{jobPostingId}/statistics/funnel?dimension=&topN=` → `ApiResponse<FunnelResponse>`.
- `topN`은 free-text 축 활성화 대비 파라미터로 수용하되 POSITION에는 미적용(전체 분야 반환).

### Response DTOs

- `FunnelResponse(jobPostingId, jobPostingTitle, dimension, population, stages, dimensions)` — 최상위는 항상 overall, dimension 지정 시 `dimensions`에 그룹별 추가.
- `FunnelPopulationResponse(p, currentlySubmittedCount, withdrawnCount)`.
- `StageDistributionResponse(passed, failed, absent, hold, pending, withdrawn, noResult)` — 합=|P|.
- `StageFunnelResponse(stageOrder, stageId, stageName, stageType, distribution, funnelPassedCount, cumulativeRate, stepConversionRate)`.
- `DimensionFunnelResponse(groupId, groupName, population, stages)`.
- `FunnelCohortRow`/`FunnelStageResultRow` — JPA 생성자 표현식 projection(내부 집계 입력).

## API list

> 경로는 `WebMvcConfig`로 `/api` prefix 적용. 권한 `/api/admin/**`.

| Method | Path | Purpose | 파라미터 | Response |
| --- | --- | --- | --- | --- |
| GET | `/api/admin/job-postings/{jobPostingId}/statistics/funnel` | 공고 전형 funnel | `dimension?`(POSITION), `topN?` | `ApiResponse<FunnelResponse>` |

응답 예시(overall):
```json
{
  "jobPostingId": 1, "jobPostingTitle": "2026 상반기 신입", "dimension": null,
  "population": { "p": 5, "currentlySubmittedCount": 4, "withdrawnCount": 1 },
  "stages": [
    { "stageOrder": 1, "stageId": 10, "stageName": "Document", "stageType": "DOCUMENT",
      "distribution": { "passed": 2, "failed": 1, "absent": 0, "hold": 0, "pending": 0, "withdrawn": 1, "noResult": 1 },
      "funnelPassedCount": 2, "cumulativeRate": 0.4, "stepConversionRate": 0.4 }
  ],
  "dimensions": []
}
```

## Entity relationship summary

새 entity/table/migration 없음. 모두 read-only. `JobApplication`(P 코호트), `StageResult`(stage 결과), `Stage`(순서) projection만 조회.

## Business rules

| 규칙 | 설명 |
| --- | --- |
| 모집단 P | `submittedAt != null` 코호트(현재 status 무관). |
| 분포 합 | 각 stage raw 7-bucket 합 = |P|. |
| NO_RESULT | 결과 row 없는 P 멤버. 응답 전용 synthetic(enum/입력값 아님), PENDING과 구분. |
| 비율 기준 | `funnelPassedCount`(순차 통과 집합) 기준. raw `distribution.passed`와 다를 수 있음. |
| withdrawn 분리 | `distribution.withdrawn`(stage result status) ≠ `population.withdrawnCount`(application status). |
| dimension | POSITION만 활성(application 단위 distinct). 그룹 정렬 = `JobPosition.sortOrder`(동률 id), 표시명 = 현재 `JobPosition.positionName`. SCHOOL/CERTIFICATE·잘못된 값 → `400`. |
| 0명 분야 | 정책 A: P에 존재하는 분야만 dimension에 응답(제출자 0명 분야는 미포함). |
| topN | free-text 축 대비 파라미터. POSITION에서는 무시(SCHOOL/CERTIFICATE 활성화 전까지 미동작). |
| 404 | 존재하지 않는 jobPostingId. |
| Audit | 없음(집계값만). |

## Test coverage

- `AdminStatisticsControllerTest`(9): MockMvc 응답을 `FunnelResponse`로 역직렬화해 정밀 검증.
  - overall: population(p/submitted/withdrawn), stage1·stage2 distribution(passed/failed/withdrawn/noResult, 합=|P|),
    funnelPassedCount, cumulative/step 비율(소수 isCloseTo).
  - POSITION dimension: 그룹 2개(Backend/Frontend) 각 population·distribution·funnelPassedCount·비율.
  - **raw passed ≠ funnelPassedCount**(보정 데이터로 distribution.passed=3, funnelPassedCount=1, step=1.0, cumulative=1/3).
  - **PENDING/ABSENT/HOLD/NO_RESULT 구분**(PENDING row 있음 vs NO_RESULT row 없음, 합=|P|).
  - **withdrawn 분리**(population.withdrawnCount(app status) ≠ distribution.withdrawn(stage result status), 서로 다른 지원서).
  - **DRAFT 제외**(submittedAt null → P/분포에서 제외, p=2).
  - dimension=SCHOOL → 400, 잘못된/없는 공고 → 404, applicant 403 / anonymous 401.
- 테스트 명령:
  ```powershell
  $env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "AdminStatisticsControllerTest"
  ```
- 결과: 9 tests passed (리뷰 반영으로 +4: raw≠sequential, 버킷 구분, withdrawn 분리, DRAFT 제외).
- 전체 회귀: **881 tests, 873 passed, 8 failed** (`./gradlew.bat test`). 직전(877) 대비 +4 신규(전부 통과),
  **신규 회귀 0건**. 실패 8건은 변동 없이 동일한 클럭 의존 사전-실패(`StageControllerTest` 2 + `StageServiceTest` 6, 접수기간 검증)로 본 슬라이스와 무관하다.

## Known limitations

- in-memory 산출: 한 공고의 P 코호트와 stage 결과를 메모리에 올려 계산한다(공고 단위 bounded). 매우 큰 공고에서
  결과 row 수가 커지면 GROUP BY 집계로 전환 여지.
- `topN`은 free-text 축 대비 파라미터만 수용(POSITION 미적용).
- SCHOOL/CERTIFICATE는 미지원(400). School master(Phase 08) 이후 학교 축 활성화 예정.

## Next phase considerations

- Phase 07d: Excel upload(StageResult) preview/commit.
- Phase 08(School master) 후 SCHOOL dimension 활성화, 이후 CERTIFICATE(free-text topN+'기타').
