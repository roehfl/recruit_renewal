# Phase 07a - Excel Export 공통 인프라 + Applications Download

## Review 반영 (instruction.md, 8 findings)

instruction.md 코드 리뷰를 반영했다.

- **(Major 1) SXSSF temp file 누수**: `ExcelExportWriter`에서 `workbook.dispose()`/`close()`를
  `finally`로 옮겨 예외 경로에서도 SXSSF 내부 temp file이 정리되도록 했다.
- **(Major 2) Stream → page fetch**: `streamExportApplications`(JPA `Stream`)를
  `findExportApplications(..., Pageable)`로 교체하고, writer가 `ExportRowSource`로 page(1,000) 단위로
  fetch하도록 바꿔 DB/JPA 메모리·connection 점유를 bound했다.
- **(Major 3) 정렬 parity**: export 정렬을 admin list와 동일한
  `order by createdAt desc, id desc`로 맞췄다(기존 `order by id`).
- **(Medium 1) audit schema 확장**: `ExportAuditContext`(actorLoginId/authority/clientIp/userAgent/requestId)
  도입 + `timestamp`/`filtersHash` 추가. controller가 `HttpServletRequest`에서 메타를 구성한다.
- **(Medium 2) 예외 정리**: writer 호출 try에서 `IOException`뿐 아니라 `RuntimeException`도
  `ExportGenerationException`으로 감싼다(검증 예외는 try 밖이라 미감쌈).
- **(Medium 3) row cap 단위 테스트**: `ApplicationExportServiceTest`로 row cap 초과 시
  `writeToTempFile`이 호출되지 않음(workbook 미생성)을 mock으로 검증.
- **(Medium 4) jobPositionId 필터 테스트**: 한 공고에 Backend/Frontend position을 두고
  `jobPositionId` 필터가 해당 분야 행만 내보내는지 검증.
- **(Low 1)** Content-Disposition 파일명 sanitize에 `"` → `_` 추가.
- **(Low 2)** 공유 `CurrentEmployeeService.getCurrentEmployeeActor` 오류 문구를 StageResult 전용에서
  일반 admin 문구로 일반화.

## Phase summary

Phase 07의 첫 슬라이스. POI SXSSF 기반 Excel export 공통 인프라(`ExcelExportWriter`,
컬럼 spec 추상화, row cap, export audit)를 구축하고, 그 위에 **applications 목록 xlsx
download**(연락처 컬럼 포함)를 구현한다. read-only이며 도메인 상태를 변경하지 않는다.

## Purpose

운영자가 admin 지원서 목록을 Excel로 대량 내려받게 한다. 동시에 07b~ 이후 dataset이
"컬럼 정의 + row mapper"만 선언하면 재사용할 수 있는 export 인프라를 함께 만든다.

## Scope

- Apache POI(`poi-ooxml`) 의존성 추가.
- SXSSF streaming export writer(`ExcelExportWriter`) + 컬럼 spec 값 객체(`ExcelExportSpec`/`ExportColumn`).
- Row cap 정책: 생성 전 count 선검증, 초과 시 `400 EXPORT_ROW_LIMIT_EXCEEDED`(workbook 미생성).
- temp file 선생성 + controller 스트리밍 후 finally 삭제(`ExcelExportResponseFactory`, `StreamingResponseBody`).
- projection DTO(`ApplicationExportRow`)를 JPA 생성자 표현식 + `Stream`으로 조회(entity/lazy를 writer에 미전달).
- applications export 2개 엔드포인트(global / per-posting), 목록 컬럼 + 연락처(phoneNumber/email).
- Excel formula injection 방어(string cell + 위험 prefix escaping).
- export SLF4J 구조적 audit(`ExportAuditLogger`).
- `EXPORT_APPLICATION_PII` 정책 상수(향후 role 분리용 marker).

## Out of scope

- stage results / interviews / interview evaluations export (07b).
- Statistics funnel (07c), Excel upload (07d), Application PDF (07e).
- `ci`/`ciHash`/`password` 노출(전 dataset 영구 금지).
- 비동기 export job, 영속 `ActivityLog` audit, 파일명 timestamp.

## Changed files

### New (main)

- `config/ExportProperties.java`
- `exception/ExportRowLimitExceededException.java`
- `exception/ExportGenerationException.java`
- `dto/response/ApplicationExportRow.java`
- `service/ExportColumn.java`
- `service/ExcelExportSpec.java`
- `service/ExcelExportFile.java`
- `service/ExportRowSource.java`
- `service/ExcelExportWriter.java`
- `service/ExportAuditContext.java`
- `service/ExportAuditLogger.java`
- `service/ApplicationExportService.java`
- `controller/ExcelExportResponseFactory.java`
- `controller/AdminExportController.java`

### Modified (main)

- `build.gradle` — `implementation 'org.apache.poi:poi-ooxml:5.3.0'` 추가.
- `src/main/resources/application.yaml` — `recruit.export.max-rows` 추가(기본 50,000).
- `domain/repository/JobApplicationRepository.java` — `countExportApplications`, `findExportApplications(..., Pageable)` 추가(정렬 `createdAt desc, id desc`).
- `service/CurrentEmployeeService.java` — `getCurrentEmployeeActor` 오류 문구 일반화(공유 admin 메서드).
- `exception/GlobalExceptionHandler.java` — `ExportRowLimitExceededException`(400), `ExportGenerationException`(500) 핸들러 추가.

### New (test)

- `controller/AdminExportControllerTest.java`
- `controller/AdminExportRowCapTest.java`

## Class-by-class explanation

### ExportProperties (Config)

- package: `com.shinyoung.recruit.config`
- responsibility: export 정책 설정(`recruit.export.max-rows`, 기본 50,000) 바인딩.
- key fields: `long maxRows`(`@Min(1)`).

### ExportRowLimitExceededException / ExportGenerationException (Exception)

- package: `com.shinyoung.recruit.exception`
- responsibility: row cap 초과(400, code `EXPORT_ROW_LIMIT_EXCEEDED`) / 파일 생성 실패(500).
- note: `ApiResponse`에 code 필드가 없어 code는 메시지 prefix로 노출한다.

### ApplicationExportRow (Response DTO / projection)

- package: `com.shinyoung.recruit.dto.response`
- responsibility: applications export 전용 평탄 projection. JPA 생성자 표현식으로 직접 조회.
- key fields: applicationId, applicantName, phoneNumber, email, jobPostingTitle, jobPositionName,
  status, submittedAt, withdrawnAt, createdAt, updatedAt.
- note: 연락처는 `Applicant`에서 조회. `ci`/`ciHash`/`password` 미포함.

### ExportColumn / ExcelExportSpec (값 객체)

- package: `com.shinyoung.recruit.service`
- responsibility: dataset별 header + row→문자열 추출기(`ExportColumn`), 시트 이름 + 컬럼 목록(`ExcelExportSpec`).
- note: dataset 추가 시 spec만 선언하면 writer 재사용.

### ExcelExportFile (값 객체)

- package: `com.shinyoung.recruit.service`
- responsibility: 생성된 temp xlsx 핸들(path, fileName, rowCount) + xlsx content-type 상수.

### ExportRowSource (값 객체, functional interface)

- package: `com.shinyoung.recruit.service`
- responsibility: writer가 page(0-based) + size로 row를 끌어오는 소스. 더 없으면 빈 리스트 반환.
- note: `Stream` 대신 명시적 page fetch로 DB/JPA 메모리·connection을 bound. writer를 Spring Data 타입에 미결합(int page/size).

### ExcelExportWriter (Service infra)

- package: `com.shinyoung.recruit.service`
- responsibility: `ExcelExportSpec` + `ExportRowSource`를 받아 SXSSF로 temp xlsx 생성.
- key methods: `writeToTempFile(spec, rowSource)` → `Path`. 내부에서 page(1,000) 단위로 fetch하며 작성.
- notes: row access window 100으로 workbook heap bound, page fetch로 DB 메모리 bound. 모든 셀 string cell.
  formula injection 방어: `=`,`+`,`-`,`@`, tab, CR/LF로 시작하면 apostrophe escape.
  실패 시 temp file 삭제 후 예외 전파, **`finally`에서 항상 `dispose()`/`close()`**(SXSSF 내부 temp 누수 방지).

### ExportAuditContext (값 객체)

- package: `com.shinyoung.recruit.service`
- responsibility: export audit 공통 요청/주체 메타(actorLoginId, authority, clientIp, userAgent, requestId).
- note: 07b/07d/07e 확장 시 logger 시그니처 재작성을 피하기 위한 묶음. PII 미포함.

### ExportAuditLogger (Service)

- package: `com.shinyoung.recruit.service`
- responsibility: applications export SLF4J 구조적 audit(`recruit.audit.export`).
- 기록: eventType, datasetType, **timestamp**(`Clock`), actorLoginId, **authority, clientIp, userAgent, requestId**,
  **filtersHash**, filtersSafeJson(allowlist: jobPostingId/jobPositionId/status), rowCount, fileName.
- note: 이름/전화/이메일 등 PII 값 자체는 audit에 남기지 않는다.

### ApplicationExportService (Service)

- package: `com.shinyoung.recruit.service`
- responsibility: 필터 검증 → count 선검증(row cap) → projection `Stream` → writer → `ExcelExportFile`.
- key method: `exportApplications(jobPostingId, jobPositionId, status)` (`@Transactional(readOnly = true)`).
- notes: jobPostingId 지정 시 존재 검증(없으면 404). status는 list와 동일 규칙으로 parse.
  count > maxRows면 `ExportRowLimitExceededException`(workbook 미생성). writer에 `ExportRowSource`
  (`page, size → findExportApplications(..., PageRequest)`)를 넘겨 page fetch. writer 호출 try에서
  `IOException`/`RuntimeException`을 `ExportGenerationException`으로 감싸되, 검증 예외는 try 밖이라 미감쌈.
- related: `JobApplicationRepository`, `ExcelExportWriter`, `ExportProperties`.

### ExcelExportResponseFactory (Controller infra)

- package: `com.shinyoung.recruit.controller`
- responsibility: `ExcelExportFile`을 `ResponseEntity<StreamingResponseBody>`로 변환.
- notes: temp 파일을 스트리밍하고 **전송 후 finally에서 삭제**. 헤더: xlsx content-type,
  `Content-Disposition: attachment`(ASCII fallback + `filename*=UTF-8''`), `X-Content-Type-Options: nosniff`,
  `Cache-Control: no-store`, `Pragma: no-cache`.

### AdminExportController (Controller)

- package: `com.shinyoung.recruit.controller`
- responsibility: applications export 엔드포인트 2개. actor 추출 → service → audit → 스트리밍 응답.
- 상수: `EXPORT_APPLICATION_PII`(향후 role matrix 분리 marker).
- related: `ApplicationExportService`, `ExcelExportResponseFactory`, `ExportAuditLogger`, `CurrentEmployeeService`.

## API list

> 모든 컨트롤러 엔드포인트는 `WebMvcConfig`에 의해 `/api` prefix가 적용된다. 아래는 실제 호출 경로.

| Method | Path | Purpose | Request(필터) | Response |
| --- | --- | --- | --- | --- |
| GET | `/api/admin/applications/export` | 전체 지원서 목록 xlsx | jobPostingId?, jobPositionId?, status? (page 무시) | xlsx (StreamingResponseBody) |
| GET | `/api/admin/job-postings/{jobPostingId}/applications/export` | 공고별 지원서 목록 xlsx | jobPositionId?, status? | xlsx |

권한: `/api/admin/**` → `ROLE_ADMIN`/`ROLE_RECRUIT_ADMIN`(기존 SecurityConfig).

### Export 컬럼 (시트 `applications`)

applicationId, applicantName, **phoneNumber**, **email**, jobPostingTitle, jobPositionName,
status, submittedAt, withdrawnAt, createdAt, updatedAt.

## Entity relationship summary

새 entity/table/migration 없음. 모두 read-only. `JobApplication`(+ `Applicant` 연락처) projection만 추가 조회.

## Business rules

| 규칙 | 설명 |
| --- | --- |
| 필터 재사용 | 대응 list와 동일 필터(jobPostingId/jobPositionId/status), page/size 무시(전체 행). |
| Row cap | 생성 전 count 선검증, `count > maxRows`(기본 50,000) → `400 EXPORT_ROW_LIMIT_EXCEEDED`, workbook 미생성. |
| PII | applications export만 `applicantName`/`phoneNumber`/`email` 포함. `ci`/`ciHash`/`password` 절대 금지. |
| Formula injection | 모든 셀 string cell, 위험 prefix(`=`,`+`,`-`,`@`, tab, CR/LF) apostrophe escape. |
| 정렬 parity | export 정렬 = admin list와 동일 `createdAt desc, id desc`. |
| tx 경계 | service가 read-only tx에서 projection을 page(1,000) 단위로 fetch해 temp 파일 생성. entity/lazy를 writer에 미전달. |
| temp 삭제 | controller가 스트리밍 후 finally에서 삭제(service는 삭제하지 않음). writer는 finally에서 `dispose()`/`close()`. |
| Audit | 전 applications export에 SLF4J 구조적 로그(timestamp/actor/authority/clientIp/userAgent/requestId/datasetType/필터 allowlist/filtersHash/rowCount/fileName). PII 값 미기록. |
| 404 | 존재하지 않는 jobPostingId → 404. |

## Test coverage

- `AdminExportControllerTest`(7): xlsx 헤더(content-type/disposition/nosniff/no-store) + POI read-back
  header 일치 + `ci`/`ciHash`/`password` 컬럼 부재 + 연락처 값; status 필터; **jobPositionId 필터**;
  per-posting 분리; 존재하지 않는 posting 404; formula injection escape(`'=cmd...`); applicant 403 / anonymous 401.
- `AdminExportRowCapTest`(1): `recruit.export.max-rows=1` + 2행 → `400 EXPORT_ROW_LIMIT_EXCEEDED`(JSON, workbook 미생성, 통합).
- `ApplicationExportServiceTest`(2, Mockito 단위): row cap 초과 시 `writeToTempFile` **never 호출**(workbook 미생성) + row cap 이내면 writer로 파일 생성.
- 테스트 명령:
  ```powershell
  $env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*AdminExport*" --tests "ApplicationExportServiceTest"
  ```
- 결과: 10 tests passed (7 + 1 + 2).
- 전체 회귀: **858 tests, 850 passed, 8 failed** (`./gradlew.bat test`, 16m 33s).
  직전 슬라이스(848) 대비 +10 신규(전부 통과), **신규 회귀 0건**. 실패 8건은 변동 없이 동일한
  클럭 의존 사전-실패(`StageControllerTest` 2 + `StageServiceTest` 6, 접수기간 검증)로 본 슬라이스와 무관하다.

## Known limitations

- export row를 page(1,000) 단위로 fetch하되 한 export는 단일 시트/단일 트랜잭션이다. 매우 큰 dataset의
  비동기 처리는 도입하지 않았다(row cap이 동기 시간을 bound).
- audit는 SLF4J 로그만. 영속 `ActivityLog` 이관은 backlog.
- 파일명에 timestamp를 넣지 않는다(결정성/단순성). 필요 시 후속.
- 날짜/숫자도 string cell로 기록(locale 비의존, list-parity). 수신측 숫자 서식은 없음.

## Next phase considerations

- 07b: stage results / interviews / interview evaluations export를 동일 인프라로 추가(컬럼 spec만 선언).
- 07c: funnel statistics.
- export audit를 영속 `ActivityLog`로 이관(backlog).
