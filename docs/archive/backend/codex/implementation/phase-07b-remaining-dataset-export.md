# Phase 07b - 나머지 Dataset Export (Stage Results / Interviews / Interview Evaluations)

## Review 반영 (instruction.md, 7 findings)

- **(Medium 1) eval xlsx 생성을 read-only tx 밖으로**: `exportStageEvaluations`가 DB 조회/매핑만
  `TransactionTemplate`(readOnly)으로 감싸고 `excelExportService.generate`(xlsx 생성)는 트랜잭션 밖에서
  수행하도록 변경. self-invocation proxy 문제를 피하려고 `@Transactional` 대신 `TransactionTemplate` 사용.
- **(Medium 2) row cap 메모리 bound 표현 정정**: 07b는 기존 list 쿼리를 materialize한 뒤 size로 cap을
  적용한다. row cap은 **xlsx 생성 메모리**를 bound하지만 **조회 list 메모리는 bound하지 않는다**. 07f 전환
  기준을 Known limitations에 명시.
- **(Medium 3) 설계-코드 응답 타입 정합**: 설계 문서 §7.4/Decision #24를 실제 구현인
  `ResponseEntity<StreamingResponseBody>`로 정정(source of truth를 구현에 맞춤).
- **(Medium 4) interviews 필터 테스트 보강**: stageId 필터, from/to 기간 필터, from>to → 400 테스트 추가.
- **(Medium 5) interviews export N+1**: `getAdminInterviews`의 interview별 participant 조회 N+1을 그대로
  export가 상속(list-parity상 동일). 성능 보강(07f)에서 count projection/bulk query 전환 대상으로 문서화.
- **(Low) header 검증 보강**: interviews/evaluations export 테스트에 nosniff/no-store/xlsx content-type 단언 추가.
- **(Low) 07b formula escaping 회귀**: interviews `groupName="+inject"` → xlsx read-back 시 apostrophe escape 검증 추가.

## Phase summary

Phase 07a에서 만든 Excel export 인프라(`ExcelExportWriter`, 컬럼 spec, row cap, audit, 응답 factory)를
재사용해 나머지 3개 dataset의 list-parity xlsx download를 추가한다: **stage results / interviews /
interview evaluations**. 모두 read-only이며 도메인 상태를 변경하지 않는다. 새 entity/table/migration이 없다.

## Purpose

운영자가 전형 결과·면접 일정·면접 평가 목록을 각각 Excel로 내려받게 한다. 각 dataset은 대응
admin list 엔드포인트와 동일한 필터·정렬·파생값을 유지한다(list-parity).

## Scope

- `GET /admin/stages/{stageId}/results/export` (stage results)
- `GET /admin/job-postings/{jobPostingId}/interviews/export` (interviews, 필터 stageId/status/from/to)
- `GET /admin/stages/{stageId}/interview-evaluations/export` (interview evaluations, 읽기 전용)
- 공용 `ExcelExportService`(materialize된 parity list에 row cap + writer 적용) + `ExportRowSource.ofList`.
- dataset 공통 audit(`ExportAuditLogger.logExport`).

## Out of scope

- Statistics funnel (07c), Excel upload (07d), Application PDF (07e).
- `InterviewEvaluation`의 Excel **upload(쓰기)** — Phase 06 경계로 영구 제외(평가 export는 읽기 전용).
- 연락처(phoneNumber/email) — applications export(07a) 전용. 본 3개 dataset은 list-parity상 연락처 없음.
- `ci`/`ciHash`/`password` — 전 dataset 영구 금지.

## Design decision — list-parity via 기존 쿼리 재사용

07a applications는 unbounded global dataset이라 count 선검증 + page-fetch projection을 썼다. 07b 3개
dataset은 **stage/posting-scoped**이고 대응 admin list 엔드포인트가 이미 전체를 materialize한다. 따라서
각 export는 **기존 list 쿼리/서비스를 그대로 재사용**해 필터·정렬·파생값(면접 인원 카운트)의 parity를
보장하고, materialize된 list를 공용 `ExcelExportService.generate`에 넘겨 row cap + writer를 적용한다.
row 수가 max를 넘으면 writer를 호출하지 않아 workbook이 생성되지 않는다.

| Dataset | 재사용 소스 | 필터 | 정렬(parity) |
| --- | --- | --- | --- |
| stage results | `StageResultService.getResults(stageId)` | stageId | `submittedAt desc, id desc` |
| interviews | `InterviewService.getAdminInterviews(...)` | jobPostingId, stageId, status, from, to | `startDateTime asc, id asc` + 참가자 role 카운트 |
| interview evaluations | `InterviewEvaluationRepository.findByStageIdForAdmin(stageId)` + stage 존재검증 | stageId | interview→candidate→interviewer 다중키 정렬 |

## Changed files

### New (main)

- `service/ExcelExportService.java` — materialize된 list에 row cap + writer 적용(공용).
- `service/AdminDatasetExportService.java` — 3개 dataset export + 컬럼 spec.
- `dto/response/InterviewEvaluationExportRow.java` — 평가 평탄 row + `from(InterviewEvaluation)`.

### Modified (main)

- `service/ExportRowSource.java` — `ofList(List)` 정적 팩토리(materialize된 list를 page 단위로 노출).
- `service/ExportAuditLogger.java` — 공통 `logExport(datasetType, context, filters, file)` 추가, `logApplicationsExport`가 위임.
- `controller/AdminExportController.java` — export 엔드포인트 3개 + dataset audit.

### New (test)

- `controller/AdminDatasetExportControllerTest.java` (8)
- `service/ExcelExportServiceTest.java` (2)

## Class-by-class explanation

### ExcelExportService (Service infra)

- package: `com.shinyoung.recruit.service`
- responsibility: materialize된 row list를 받아 row cap 적용 + writer로 temp xlsx 생성.
- key method: `generate(spec, rows, fileName)` → `ExcelExportFile`. `rows.size() > maxRows`면
  `ExportRowLimitExceededException`(writer 미호출 = workbook 미생성). writer의 `IOException`/`RuntimeException`은
  `ExportGenerationException`으로 감쌈.
- related: `ExcelExportWriter`, `ExportProperties`, `ExportRowSource.ofList`.

### AdminDatasetExportService (Service)

- package: `com.shinyoung.recruit.service`
- responsibility: 3개 dataset의 parity list 구성 + dataset별 `ExcelExportSpec` 정의.
- key methods:
  - `exportStageResults(stageId)` — `stageResultService.getResults`(stage 존재검증 상속) 재사용.
  - `exportInterviews(jobPostingId, stageId, status, from, to)` — `interviewService.getAdminInterviews`(posting 검증·search range·counts 상속) 재사용.
  - `exportStageEvaluations(stageId)` — stage 존재검증 + `findByStageIdForAdmin` flat 엔티티 → `InterviewEvaluationExportRow` 매핑을 `TransactionTemplate`(readOnly)으로 감싸고, xlsx 생성(`generate`)은 트랜잭션 밖에서 수행.
- 컬럼 spec은 static 상수. 셀 포맷은 `text()`(LocalDateTime ISO / Enum name / BigDecimal plain / 기타).

### InterviewEvaluationExportRow (Response DTO / projection)

- package: `com.shinyoung.recruit.dto.response`
- responsibility: 평가 1건 = 1행(stage 레벨) 평탄 row. admin 뷰라 면접관 식별 노출.
- 필드: interviewId, groupName, applicantName, positionName, interviewerName, status, grade, recommendation, comment, submittedAt.
- note: candidate/application/employee는 `findByStageIdForAdmin` fetch join이라 매핑 시 추가 조회 없음.

### ExportRowSource (Modified)

- 추가: `ofList(List<T>)` — materialize된 list를 page 단위로 노출하는 소스(writer는 동일 인터페이스로 동작).

### ExportAuditLogger (Modified)

- 추가: `logExport(datasetType, context, Map filters, file)` 공통 메서드(timestamp/actor/authority/clientIp/userAgent/requestId/filtersHash/filtersSafeJson/rowCount/fileName). `logApplicationsExport`가 이를 위임.
- filters는 allowlist 비-PII 값만. dataset별 filtersSafeJson을 동일 포맷으로 직렬화.

### AdminExportController (Modified)

- 추가 엔드포인트 3개(stage results / interviews / interview evaluations). 각 호출에서 actor 추출 →
  service → `logExport` → 스트리밍 응답(temp 전송 후 삭제는 `ExcelExportResponseFactory`).

## API list

> 모든 경로는 `WebMvcConfig`로 `/api` prefix가 적용된다. 권한 `/api/admin/**`.

| Method | Path | Purpose | 필터 | Response |
| --- | --- | --- | --- | --- |
| GET | `/api/admin/stages/{stageId}/results/export` | 전형 결과 목록 xlsx | — | xlsx (stream) |
| GET | `/api/admin/job-postings/{jobPostingId}/interviews/export` | 면접 일정 목록 xlsx | stageId?, status?, from?, to? | xlsx |
| GET | `/api/admin/stages/{stageId}/interview-evaluations/export` | 면접 평가 목록 xlsx(읽기) | — | xlsx |

### 컬럼

- stage results(시트 `stage-results`): stageResultId, stageId, applicationId, applicantName, jobPositionId, jobPositionName, applicationStatus, resultStatus, score, comment, submittedAt, decidedAt.
- interviews(시트 `interviews`): interviewId, jobPostingTitle, stageName, stageType, groupName, startDateTime, endDateTime, method, locationName, roomName, onlineMeetingUrl, status, candidateCount, interviewerCount.
- interview evaluations(시트 `interview-evaluations`): interviewId, groupName, applicantName, positionName, interviewerName, status, grade, recommendation, comment, submittedAt.

## Entity relationship summary

새 entity/table/migration 없음. 모두 read-only. 평가 export만 `InterviewEvaluation` → candidateParticipant.jobApplication / interviewerParticipant.employee를 fetch join으로 평탄화.

## Business rules

| 규칙 | 설명 |
| --- | --- |
| list-parity | 각 export = 대응 admin list와 동일 필터·정렬·파생값(기존 쿼리/서비스 재사용). |
| Row cap | materialize된 list size > maxRows(기본 50,000) → `400 EXPORT_ROW_LIMIT_EXCEEDED`, workbook 미생성. |
| 검증 | stage results=stage 존재(getResults), interviews=posting 존재 + search range(getAdminInterviews), evaluations=stage 존재. 없으면 404. |
| PII | 본 3개 dataset은 연락처 미포함. `ci`/`ciHash`/`password` 금지. 평가 export는 읽기 전용(Phase 06 경계). |
| Formula injection | 07a writer 상속(전 셀 string cell + 위험 prefix escape). |
| temp 삭제 | controller 스트리밍 후 finally 삭제. writer finally dispose/close. |
| Audit | dataset별 SLF4J 구조적 로그(`logExport`), 필터 allowlist + filtersHash, PII 값 미기록. |

## Test coverage

- `AdminDatasetExportControllerTest`(12): 각 dataset xlsx 헤더(content-type/nosniff/no-store) + POI read-back
  header parity, stage results 민감컬럼 부재 + 행수, interviews 참가자 카운트 컬럼 + **status 필터·stageId 필터·
  from/to 기간 필터·from>to 400·groupName formula escape**, evaluations 평탄 행·면접관 식별·status, 각 dataset
  404(unknown stage/posting), applicant 403 / anonymous 401.
- `ExcelExportServiceTest`(2): row cap 초과 시 writer never 호출(workbook 미생성) + cap 이내면 writer 호출.
- 테스트 명령:
  ```powershell
  $env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*DatasetExport*" --tests "ExcelExportServiceTest"
  ```
- 결과: 14 tests passed (12 + 2).
- 전체 회귀: **872 tests, 864 passed, 8 failed** (`./gradlew.bat test`). 리뷰 반영(필터/header/formula 테스트 +4)으로
  07b 신규 14개 전부 통과, **신규 회귀 0건**. 실패 8건은 변동 없이 동일한 클럭 의존 사전-실패
  (`StageControllerTest` 2 + `StageServiceTest` 6, 접수기간 검증)로 본 슬라이스와 무관하다.

## Known limitations

- **Row cap의 bound 범위(정정)**: 07b는 기존 list 쿼리를 그대로 materialize한 뒤 size로 cap을 적용한다.
  따라서 row cap은 **xlsx 생성(SXSSF) 메모리만 bound**하고, **조회 list를 메모리에 올리는 비용은 bound하지 않는다**
  (07a applications는 count-선검증 + page-fetch로 양쪽 모두 bound). scoped dataset이라 현 위험은 낮다.
  - **07f 전환 기준**: 07b export는 list-parity 우선으로 materialize 후 row cap을 적용한다. 대량 공고/전형에서
    row 수가 커질 가능성이 확인되면 count 선검증 + projection page-fetch로 전환한다. 특히 **stage results**는
    지원자 수와 직결되므로 가장 먼저 page-fetch 전환 대상이다.
- **interviews export N+1**: `getAdminInterviews`가 interview별로 participant를 다시 조회해 인원 카운트를
  계산한다(list-parity상 동일). 전체를 한 번에 내려받는 export에서 N+1 비용이 더 크게 드러나므로, 07f/성능
  보강 시 `interviewId`별 count projection(group by) 또는 bulk participant-count map으로 분리한다.
- audit는 SLF4J 로그만. 영속 `ActivityLog` 이관은 backlog.
- stage results/interviews export에 연락처 미포함(list-parity). 운영 수요 확인 시 후속.

## Next phase considerations

- 07c: 공고 단위 전형 funnel statistics(P 코호트, 7-bucket, 두 비율, dimension).
- 대량 stage results export가 빈번하면 page-fetch projection으로 전환 고려(현재 list 엔드포인트도 materialize).
