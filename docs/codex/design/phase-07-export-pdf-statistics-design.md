# Phase 07 - Export, PDF, Statistics Design

## 1. Phase Summary

- Phase name: Phase 07 - Export, PDF, Statistics
- Work type: documentation-only design phase
- Date: 2026-05-29
- Purpose: 운영자가 admin 조회 데이터를 Excel(xlsx)로 내려받고(Export), Excel로 `StageResult`를 bulk 변경하며(Upload), 지원자 1명의 지원서를 PDF로 출력하고, 공고 단위 전형 funnel 통계를 조회하는 백엔드를 설계한다.
- Status: design completed, Java/source/test implementation not started.

이 문서는 도메인/용어 정의(`CONTEXT.md`)를 전제로 하며, Phase 07의 범위·API·검증 규칙·슬라이스 분할을 확정한다. Java source, test, DB migration, 프론트엔드는 구현하지 않는다.

## 2. Purpose

기존 단계까지 채용 공고/지원서/전형 결과/면접 일정/면접 평가가 구현되어 있다. Phase 07은 그 데이터를 **운영자가 대량으로 내보내고/들여오고/출력하고/집계**하는 reporting 계층을 추가한다.

책임 경계:

- **Export(읽기)**: admin 조회 결과를 xlsx로 내려받는다. 도메인 상태를 변경하지 않는다.
- **Statistics(읽기)**: 공고 단위 funnel 집계값만 노출한다. 개별 개인정보를 노출하지 않는다.
- **Application PDF(읽기)**: 지원자 1명의 지원서를 PDF로 렌더한다. 본질적으로 개인정보를 포함하므로 admin 전용이며 `ci`/`ciHash`/`password`를 절대 포함하지 않는다.
- **Excel Upload(쓰기)**: Phase 07에서 **유일한 쓰기 지점**. 관리자가 xlsx로 `StageResult`만 bulk 변경한다. 기존 `StageResultService`를 경유해 기존 불변식을 그대로 상속한다.

`InterviewEvaluation`은 Phase 06 경계(평가는 배정 면접관 본인만 작성, 평가 독립성)상 **Excel upload 대상에서 제외**한다. 평가 데이터 export(읽기)는 가능하나 평가 변경(쓰기)은 하지 않는다.

## 3. Scope

이 문서가 설계하는 범위:

- Excel export 공통 인프라: POI SXSSF streaming writer, row cap policy, audit 로깅, byte/stream 응답.
- 4개 dataset의 list-parity Excel download: applications / stage results / interviews / interview evaluations.
- applications export 의 연락처 컬럼(`name`/`phoneNumber`/`email`) 확장 정책.
- 공고 단위 전형 funnel Statistics: 모집단 P 코호트, stage별 7-bucket 분포, 두 비율, dimension(전체/분야별 우선).
- Excel upload(StageResult) preview/commit: stateless, all-or-nothing, 3중 교차검증, 기존 `bulkUpdateResults` 재사용.
- Application PDF: Thymeleaf + openhtmltopdf(PDFBox) + CJK 폰트 임베드, admin 전용.
- 신규 의존성과 라이선스 정책.
- 보안/PII/audit 정책.
- 슬라이스 분할(07a~07f)과 슬라이스별 컴포넌트(Service/Controller/DTO) 후보.

## 4. Out Of Scope

Phase 07에 포함하지 않는 항목:

- Java source / test / DB migration / DDL.
- 프론트엔드, 정적 리소스.
- **`InterviewEvaluation` 의 Excel upload(쓰기)** — Phase 06 경계 위반이므로 영구 제외.
- 학교별 통계 dimension — `School` master(Phase 08) 부재로 정확도 미확보, P08 이후로 연기.
- 자격별 통계 dimension — free-text 한계. 07 내 후속 슬라이스 또는 P08 이후(아래 Open Questions 참조, **07c 확정 산출물 아님**).
- 지원자 본인 PDF 다운로드 — admin 전용으로 한정, deferred.
- batch/zip 다건 PDF — 1 지원자 = 1 PDF.
- 비동기 export job / 다운로드 큐 — row cap이 동기 처리 시간을 bound하므로 미도입.
- Excel upload 의 staging 영속화(batchId 모델) — stateless 채택으로 제외.
- 개인정보 파기/보관주기/접근감사 도메인(영속 `ActivityLog`) — backlog.
- 통계의 산술평균/가중치/자동 합불 판정.
- `StageResult` reflect/sync 커맨드.
- 메시지 발송 연동.

## 5. Changed Files

이 phase는 documentation-only 설계 단계이므로 Java/source/test 파일을 변경하지 않는다. 변경/생성하는 문서 산출물:

| File | Change |
| --- | --- |
| `docs/codex/design/phase-07-export-pdf-statistics-design.md` | Phase 07 설계 source of truth 생성. |
| `docs/codex/reports/phase-07-export-pdf-statistics-design.html` | self-contained 인간용 설계 리포트 생성. |
| `docs/codex/06-implementation-roadmap.md` | Phase 07 섹션을 슬라이스 분할/설계 산출물로 갱신. |
| `docs/codex/07-implementation-history.md` | Phase 07 설계 history 엔트리 추가. |
| `CONTEXT.md` | Export/Reporting 용어 정리(Excel upload 범위 축소, 모집단 P 코호트 정의, NO_RESULT/ funnel 단계 분포 추가, password 제외 보강) — grilling 중 인라인 갱신. |
| `docs/adr/0001-application-pdf-openhtmltopdf-avoid-itext-agpl.md` | PDF 렌더 스택 + AGPL(iText) 회피 결정 기록. |
| `docs/adr/0002-phase07-export-readonly-upload-stageresult-only.md` | export 비변경 / upload는 StageResult only / InterviewEvaluation 제외 경계 기록. |

구현 슬라이스(07a~07f)에서 생성될 Java/test 파일은 §11 Component Summary에 후보로 정리한다.

## 6. Slice Plan

| Slice | 범위 |
| --- | --- |
| **07a** | Excel export 공통 인프라(POI SXSSF, `ExcelExportWriter` 추상화, row cap, export audit) + applications download(목록 컬럼 + 연락처) |
| **07b** | 나머지 dataset download — stage results / interviews / interview evaluations (list-parity) |
| **07c** | Statistics funnel — 전체 + 분야별(FK), stage별 7-bucket 분포 + 두 비율, P 코호트, NO_RESULT |
| **07d** | Excel upload(StageResult) preview/commit — stateless, all-or-nothing, 3중 교차검증 |
| **07e** | Application PDF — admin 전용, Thymeleaf + openhtmltopdf(PDFBox) + CJK 폰트 |
| **07f** | Stabilization / Test Hardening — 회귀, PII 부재 검증, row cap·upload 경계 회귀 |

각 슬라이스는 기존 프로젝트 관례대로 targeted test와 함께 추가하고, 구현 후 implementation 문서(`docs/codex/implementation/phase-07x-*.md`)와 HTML 리포트를 함께 갱신한다.

## 7. Excel Export Design (07a / 07b)

### 7.1 라이브러리 및 메모리 모델

- **Apache POI** `poi-ooxml`로 xlsx 생성. (Apache-2.0)
- **SXSSF**(streaming workbook)로 행을 디스크로 흘려 heap을 bound. 대량 export에서 OOM 방지.
- POI 정확 버전은 의존성 추가 시 확정(현행 5.x 계열). Spring Boot 4 BOM이 POI를 관리하지 않으므로 버전 명시 필요.

### 7.2 Row Cap Policy

- `application.yaml` 설정값으로 `maxRows` 노출, 기본 **50,000**.
- export 실행 **전**에 동일 필터 기준 `count` 선검증한다.
- `count > maxRows`이면 workbook을 생성하지 않고 `400 EXPORT_ROW_LIMIT_EXCEEDED`를 반환한다.
- 조용한 truncation을 금지한다(`CONTEXT.md`의 "no silent caps").

### 7.3 공통 Export Writer

- `ExcelExportWriter`(또는 동등 추상화): 컬럼 정의(header + value extractor 목록) + row iterator를 받아 SXSSF로 시트를 작성.
- 각 dataset은 "컬럼 정의 + row mapper"만 선언 → 07b dataset 추가 비용을 낮춘다.
- 모든 export는 **page/size를 무시**하고 동일 list 필터로 조회된 전체 행을 내보낸다(`CONTEXT.md` Export 규칙).

### 7.4 응답 메커니즘 및 트랜잭션 경계

- 기존 `AttachmentDownloadResponseFactory`와 동일한 헤더 규약을 따른다: `Content-Type=application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `Content-Disposition: attachment; filename=...; filename*=UTF-8'...`, `X-Content-Type-Options: nosniff`, `Cache-Control: no-store`.
- **(채택) temp file 선생성 방식**: service는 **read-only 트랜잭션 안에서 projection DTO를 page 단위로 읽어** SXSSF temp xlsx **생성까지만** 담당한다. controller/response layer가 그 파일을 `ResponseEntity<Resource>`로 전송하고, **전송 완료 후 `finally`에서 temp file을 삭제**한다(성공·실패·예외 모두). **service 내부 `finally`에서 먼저 삭제하지 않는다** — Resource를 내려주는데 service에서 지우면 response body write 전에 파일이 사라질 수 있다.
- **대안(StreamingResponseBody 내부 page fetch)**: response body 내부에서 `TransactionTemplate(readOnly=true)`로 page 단위 fetch를 반복한다.
- 두 방식 모두 **JPA entity/lazy collection을 writer에 절대 넘기지 않는다** — `ExcelExportWriter`의 value extractor는 entity가 아니라 **export row projection DTO** 기준으로 작성한다.
- 이유: streaming 응답은 controller 반환 이후 별도 실행 흐름에서 body가 써질 수 있어, lazy association을 그대로 쓰면 `LazyInitializationException`·connection 장시간 점유·N+1·SXSSF temp file 누수가 발생할 수 있다. projection + 명확한 tx 경계로 차단한다.

### 7.5 Dataset 및 컬럼 (전부 list-parity)

1:N 평탄화 없음. 1행 = 1엔티티. 깊은 지원서 상세는 PDF가 담당.

#### applications export

기존 list 화면(`AdminApplicationSummaryResponse`) 컬럼 + **연락처 확장**. 주의: export는 `AdminApplicationSummaryResponse`를 그대로 직렬화하지 않는다. `phoneNumber`/`email`은 그 응답에 **없으므로**, export 전용 projection/row에서 `Applicant.phoneNumber`/`email`을 추가 조회해 채운다.

| 컬럼 | 출처 | 비고 |
| --- | --- | --- |
| applicationId | `JobApplication.id` | |
| applicantName | `applicantNameSnapshot` | |
| **phoneNumber** | `Applicant.phoneNumber` | export 전용 추가 조회(목록 응답엔 없음) |
| **email** | `Applicant.email` | export 전용 추가 조회(목록 응답엔 없음) |
| jobPostingTitle | `jobPostingTitleSnapshot` | |
| jobPositionName | `jobPositionNameSnapshot` | |
| status | `JobApplicationStatus` | |
| submittedAt / withdrawnAt | | |
| createdAt / updatedAt | | |

- `phoneNumber`/`email`은 현재 어떤 admin 응답에도 없으므로, **export가 admin이 연락처를 보는 최초 surface**다. 운영 연락·발송 목적상 평문 export하되 audit 로그를 남긴다.
- `ci`/`ciHash`/`password`는 **절대 컬럼에 포함하지 않는다**.

#### stage results export

기존 `AdminStageResultResponse` 그대로: stageResultId, stageId, applicationId, applicantName, jobPositionName, applicationStatus, resultStatus, score, comment, submittedAt, decidedAt. (연락처 없음 — Open Question, 수요 확인 시 추가)

#### interviews export

기존 `AdminInterviewSummaryResponse` 그대로: interviewId, jobPostingTitle, stageName, stageType, groupName, startDateTime, endDateTime, method, locationName, roomName, onlineMeetingUrl, status, candidateCount, interviewerCount.

#### interview evaluations export

평가 행 1개 = 1행으로 평탄화(stage 레벨): interviewId, groupName, applicantName, positionName, interviewerName, status, grade, recommendation, comment, submittedAt. (읽기 전용 export — Phase 06 경계 유지, 평가 변경 아님)

### 7.6 Excel cell 보안 (formula injection 방어)

free-text(이름/연락처/comment 등)가 `=`, `+`, `-`, `@`, tab, CR/LF로 시작하면 spreadsheet가 formula/hyperlink로 해석할 위험이 있다.

- export writer는 모든 free-text cell을 **명시적 string cell**로 작성한다.
- 위험 prefix로 시작하는 값은 apostrophe(`'`) prefix 또는 escaping 처리한다.
- (upload 측 formula 방어는 §9.6 참조.)

## 8. Statistics / Funnel Design (07c)

### 8.1 모집단 P (코호트)

- `P = { app | app.submittedAt != null }` — 한 번이라도 제출한 지원서, 현재 status 무관.
- `JobApplication.withdraw()`가 `submittedAt`을 지우지 않으므로 제출 후 철회 지원서도 P에 포함된다(코드 확인됨).
- 코호트를 제출이력으로 고정 → 조회 시점이 달라도 funnel 재현 가능.
- 부가 카운트:
  - `currentlySubmittedCount = |P ∩ status==SUBMITTED|`
  - `withdrawnCount = |P ∩ status==WITHDRAWN|`(제출 후 철회)

### 8.2 단계별 7-bucket 분포

funnel 단계 = step0 접수(P) → 공고의 `stageOrder` 순 각 Stage. DOCUMENT 단계도 면접 단계와 동일하게 하나의 step으로 취급. 동적 stage 구조에 자동 적응.

각 stage k에서 P 멤버를 7개 버킷으로 분류, **합은 항상 `|P|`**:

| 버킷 | 의미 |
| --- | --- |
| PASSED / FAILED / ABSENT / HOLD / PENDING / WITHDRAWN | 해당 stage의 `StageResult.resultStatus` (지원서 distinct) |
| **NO_RESULT** | 그 stage에 `StageResult` row 자체가 없는 P 멤버(미초기화/미도달) |

- `PENDING`(row 있음·결정 전)과 `NO_RESULT`(row 없음)는 명확히 구분한다.
- **`NO_RESULT`는 응답 전용 synthetic 버킷**이다. DB `StageResultStatus` enum 값이 아니며, Excel upload 허용 입력값도 아니다.
- **`distribution.withdrawn`(stage result status) ≠ `population.withdrawnCount`(application status)**: 전자는 그 stage의 `StageResult.resultStatus == WITHDRAWN` 수이고, 후자는 application-level 철회 수(§8.1)다. 둘을 혼동/덮어쓰지 않는다. distribution은 stage result status의 raw 집계만 담는다.

### 8.3 raw 분포와 순차 통과 집합의 분리

비율은 **순차 통과 집합** 기준으로 계산해 "접수 → stageOrder 순서대로 각 stage PASSED" funnel 정의에 맞춘다. 데이터 보정/수동 수정 때문에 이전 stage를 통과하지 않은 지원서가 후속 stage에서 PASSED로 잡힐 수 있으므로, raw `distribution.passed`를 비율 분모/분자에 직접 쓰면 전환율이 100%를 넘는 등 funnel 의미가 깨질 수 있다.

- **`distribution`**: 각 stage에서 P 전체를 7-bucket으로 분류한 raw 분포(합 = `|P|`). `distribution.passed` 유지(현황 파악용).
- **`funnelPassedCount` (순차 통과 집합 `|S_k|`)**:
  - `S0 = P`
  - `S_k = S_(k-1) ∩ { app | stage k 결과 == PASSED }`
- **비율은 `funnelPassedCount` 기준**:
  - 누적 비율 `cumulativeRate = |S_k| / |P|`
  - 직전 단계 전환율 `stepConversionRate = |S_k| / |S_(k-1)|` (S0 분모는 `|P|`)

즉 raw 분포(현황)와 순차 통과 수(funnel 비율)를 응답에서 이름으로 분리한다.

### 8.4 Dimension (집계 축)

| Dimension | API 값 | 기준 필드 | 정확도 | Phase 07 포함 |
| --- | --- | --- | --- | --- |
| 전체 | (none) | 모든 P | 정확 | **포함(07c)** |
| 분야별 | `POSITION` | `JobApplication.jobPosition` | FK, 정확 | **포함(07c)** |
| 학교별 | `SCHOOL` | `ApplicationEducation.schoolName`(최종학력 1교) | free-text, 부정확 | **제외** → Phase 08 School master 이후 |
| 자격별 | `CERTIFICATE` | `ApplicationCertificate.certificateName`(보유 distinct) | free-text, 부정확 | **미확정** — 07 내 후속 슬라이스 또는 P08(Open Q#2) |

- API 값(`POSITION`/`SCHOOL`/`CERTIFICATE`)은 CONTEXT.md Dimension 용어(분야별/학교별/자격별)에 매핑된다. `SCHOOL`/`CERTIFICATE`는 07c 미확정 축이며, 활성화 전까지 호출 시 명시적 미지원/연기 응답으로 처리한다.
- 모든 dimension은 **지원서(application) 단위 distinct**로 센다.
- 학교별은 **최종학력(가장 높은 `EducationLevel`: HIGH_SCHOOL < COLLEGE < UNIVERSITY < MASTER < DOCTOR) 1교만**, 자격별은 **자격명별 보유 지원서 distinct**.
- free-text 축(학교/자격)은 **topN(기본 10) + '기타' 버킷**으로 cardinality를 제한한다. 응답 메타에 free-text 부정확성을 표기한다.

### 8.5 API 모양

```
GET /admin/job-postings/{jobPostingId}/statistics/funnel                 (overall)
GET /admin/job-postings/{jobPostingId}/statistics/funnel?dimension=POSITION
```

- dimension 미지정 → overall funnel만 반환.
- dimension 지정 → 한 호출당 한 dimension breakdown(자식 그룹별 funnel). 07c 확정 값은 `POSITION`만. free-text 축(`SCHOOL`/`CERTIFICATE`)은 활성화 시 `topN`(기본 10) 적용.
- 응답 예시(overall):

```json
{
  "jobPostingId": 1,
  "jobPostingTitle": "2026 상반기 신입",
  "population": { "p": 1200, "currentlySubmittedCount": 1180, "withdrawnCount": 20 },
  "stages": [
    {
      "stageOrder": 1, "stageName": "서류", "stageType": "DOCUMENT",
      "distribution": { "passed": 300, "failed": 800, "absent": 0, "hold": 0, "pending": 0, "withdrawn": 20, "noResult": 80 },
      "funnelPassedCount": 300, "cumulativeRate": 0.25, "stepConversionRate": 0.25
    }
  ]
}
```

- `distribution` 7개 필드 합 = `population.p`(raw 현황).
- `cumulativeRate`/`stepConversionRate`는 `funnelPassedCount`(순차 통과 집합) 기준이며, raw `distribution.passed`와 값이 다를 수 있다.
- statistics는 집계값만 노출하므로 audit 로그를 남기지 않는다.

## 9. Excel Upload Design (07d)

### 9.1 대상 및 상태 모델

- 대상은 **`StageResult`만**. (`InterviewEvaluation` 제외 — Phase 06 경계)
- **Stateless** preview/commit. preview/commit 모두 multipart 파일을 받는다. 새 entity/table/migration 없음.
- preview는 검증·diff만 반환(영속 없음), commit은 재검증 후 적용.

### 9.2 엔드포인트

```
GET  /admin/stages/{stageId}/results/upload-template  (xlsx, 유일한 upload 소스)
POST /admin/stages/{stageId}/results/upload/preview   (multipart: file)
POST /admin/stages/{stageId}/results/upload/commit    (multipart: file)
```

### 9.3 업로드 소스, 행 매칭(3중 교차검증), 동시성 토큰

**업로드 소스는 `GET /admin/stages/{stageId}/results/upload-template`로 받은 템플릿 sheet만 허용한다.** applications export와 stage results export는 **upload source가 아니다** — 목록/연락용 export와 시스템이 검증 가능한 upload sheet는 목적이 다르므로 묶지 않는다. upload-template은 stage-scoped 엔드포인트라 `stageId`는 path로만 판단하고 row에 반복하지 않는다.

업로드 row 모델 `StageResultUploadRowRequest`:

| 컬럼 | 역할 |
| --- | --- |
| `stageResultId` | read-only echo, 매칭 키 |
| `applicationId` | read-only echo, 교차검증 |
| `applicantName` | read-only echo, 사람 눈 확인용(매칭 미사용) |
| `stageResultUpdatedAt` | read-only 동시성 토큰(아래 포맷 규칙) |
| `resultStatus` / `score` / `comment` | 편집 대상(template에 현재값 prefill) |

행은 다음 3가지를 **모두** 만족해야 유효:

1. `stageResultId`가 실제 `StageResult`로 존재.
2. 그 `StageResult.jobApplication.id`가 행의 `applicationId`와 일치.
3. 그 `StageResult.stage.id`가 path `{stageId}`와 일치.

- 3중 교차검증은 **upload service**가 `StageResult`를 조회해 수행한다. **row에 `stageId` 컬럼을 두지 않는다**(path로 판단). 기존 `StageResultBulkUpdateItemRequest`(stageResultId/resultStatus/score/comment)는 **변경하지 않는다** — echo/토큰 필드는 upload row DTO에만 존재한다.

**`stageResultUpdatedAt` 동시성 토큰 포맷:**

- Excel **string cell**로 export(date/numeric cell 아님). ISO-8601 고정(예: `2026-05-29T16:30:12.123456+09:00`).
- commit 시 DB `StageResult.updatedAt`을 **같은 precision으로 normalize 후 비교**.
- 해당 셀이 date/numeric/**formula**이면 row error. header/comment에 read-only token임을 표시.
- (선택 하드닝) 원문 대신 opaque `stageResultVersionToken = HMAC(stageResultId|applicationId|stageId|updatedAt)`로 두면 사용자 임의 수정에 의한 stale check 우회도 막을 수 있다(Open Q#7).

### 9.4 편집 가능 컬럼, 허용 값, 빈칸 의미

| 컬럼 | 편집 | 허용값 / 빈칸 의미 |
| --- | --- | --- |
| resultStatus | O | 허용값 = `StageResultStatus` − `PENDING` = {PASSED, FAILED, ABSENT, HOLD, WITHDRAWN}. `NO_RESULT`/synthetic 불가. **blank → row error(필수)**. |
| score | O | `BigDecimal`, 기존 규칙. **blank → null clear**. |
| comment | O | 최대 2000자. **blank → null clear**(고정). |
| stageResultId / applicationId / applicantName / stageResultUpdatedAt | X(read-only echo/token) | 매칭·검증·동시성 토큰 |

빈칸 정책 원칙: template에 **현재값을 prefill**하고, 사용자가 지우면 clear로 해석한다("빈칸 = 기존값 유지"는 실수·오해 유발이라 채택하지 않는다).

- **변경 없는 row**: diff에 `unchanged`로 표시하고 **commit 적용 대상에서 제외**한다.
- **stale check 범위**: commit 적용 대상(변경 row)에만 적용한다. unchanged row는 적용하지 않으므로 덮어쓰기 위험이 없다.

### 9.5 검증 및 commit 의미 (낙관적 동시성 포함)

- **preview**: 모든 행을 검증해 행별 결과(유효/오류 사유)와 변경 diff를 반환. 영속 변경 없음.
- **commit**:
  1. commit 직전 전체 row 선행 재검증: 3중 매칭 + 허용값 + comment 길이 + Stage `IN_PROGRESS` guard + **낙관적 동시성 검사**.
  2. **낙관적 동시성**: 각 **변경 대상 행**의 `stageResultUpdatedAt`(ISO-8601 string, §9.3)을 현재 DB `StageResult.updatedAt`과 **같은 precision으로 normalize 후 비교**해, 다르면 그 행은 `STALE_ROW` 오류. preview 이후 다른 관리자가 같은 `StageResult`를 변경했으면 덮어쓰지 않는다.
  3. 검증 실패(`STALE_ROW` 포함)가 **하나라도** 있으면 update 0건(부분 성공 없음). 응답은 `409`(또는 row-level `STALE_ROW` 목록 포함)로 전체 거부.
  4. 전 행 통과 시 upload service가 검증된 행을 기존 `StageResultBulkUpdateRequest`(items: stageResultId/resultStatus/score/comment)로 매핑해 **단일 transaction**에서 `StageResultService.bulkUpdateResults(stageId, request, actor)`에 위임한다.
- 기존 서비스 경유로 다음을 그대로 상속: Stage `IN_PROGRESS` guard, `resultStatus != PENDING` guard, comment ≤ 2000, actor 필수, 정정 이력/audit.
- `stageResultUpdatedAt` 토큰으로 lost update를 막으므로 새 staging table 없이 stateless를 유지한다.
- 발표(`RESULT_ANNOUNCED`) 후 정정은 별도 `correct` 경로이며 upload 범위 밖이다.

### 9.6 업로드 파일 레벨 방어

upload는 write path이므로 export보다 보수적으로 막는다.

- `maxUploadRows`, `maxUploadFileSize`를 `application.yaml` 설정으로 둔다.
- **`.xlsx`만 허용**. `.xls`, `.csv`, macro-enabled(`.xlsm`)는 거부.
- **첫 sheet만** 처리(또는 sheet name 고정). header signature/version 검증.
- **모든 column에서 `CellType.FORMULA` 거부** — echo column(comment/name 등)이 formula cell이면 preview 단계에서 row error.
- duplicate `stageResultId` row 거부.
- 빈 행/숨김 행/필터링된 행 처리 규칙 명시(기본: 빈 행 skip, 숨김/필터 행도 데이터로 취급).
- 날짜/숫자 format locale 의존성 제거(셀을 string으로 읽어 명시적 parse).

## 10. Application PDF Design (07e)

### 10.1 렌더링 스택

- **Thymeleaf**(Apache-2.0) 서버 템플릿으로 XHTML 작성 → **openhtmltopdf**(PDFBox 백엔드)로 PDF 렌더.
- **CJK 폰트 임베드**: Noto Sans KR 또는 나눔고딕(둘 다 SIL OFL 1.1, 임베드 허용)을 리소스로 번들. 컨테이너에 시스템 CJK 폰트가 없어도 한글이 정상 출력되도록 폰트를 문서에 임베드.
- **라이선스**: iText 7(AGPL)은 사내 폐쇄소스에 부적합하므로 **사용하지 않는다**. openhtmltopdf(LGPL 계열, AGPL 아님) + PDFBox(Apache-2.0)로 간다. 정확한 라이선스/버전은 의존성 추가 시 재확인(Open Q#1).

### 10.2 대상 및 단위

- **admin 전용**. 지원자 본인 PDF 다운로드는 deferred.
- 지원자 1명 = PDF 1개. batch/zip 미지원.
- 엔드포인트: `GET /admin/applications/{applicationId}/pdf` → `application/pdf`, `Content-Disposition: attachment`.

### 10.3 내용

지원서 양식 섹션을 그대로 미러:

- 기본정보: `name`, `phoneNumber`, `email`, 지원 공고/분야, status, submittedAt.
- 학력 / 경력 / 자격 / 어학 / 병역 / 수상 / 공백기간 / 질문답변(각 sortOrder 순).
- **`ci`/`ciHash`/`password`는 절대 포함하지 않는다.**
- 상태/제출일시 스탬프 포함.

### 10.4 PDF Audit

- PDF는 Phase 07에서 **가장 집중된 PII surface**(식별된 1인의 전체 지원서)다. 따라서 생성 시 **반드시 SLF4J 구조적 audit 로그**를 남긴다(필드 표준은 §14.1): `actor`, `applicationId`, `jobPostingId`, `jobPositionId`, `timestamp`.
- 영속 `ActivityLog` 도메인 생성 시 그쪽으로 이관(backlog).

### 10.5 PDF 템플릿 보안 (untrusted text)

지원자 free-text(질문답변/경력설명 등)를 Thymeleaf에 넣을 때:

- applicant-provided text는 **`th:text`만 사용**, **`th:utext` 금지**(HTML injection 차단).
- 줄바꿈은 HTML 변환이 아니라 CSS `white-space: pre-wrap`로 처리.
- **외부 URL resource(image/font) load 금지** — 폰트는 번들 local만 사용.
- attachment binary embed는 **Phase 07 범위 밖**.
- PDF 응답에도 export와 동일하게 `Cache-Control: no-store`, `Pragma: no-cache`, `X-Content-Type-Options: nosniff` 적용.

## 11. Component Summary (Service / Controller / DTO) 및 Data Ownership

### 11.1 Data Ownership / No New Entities

- Phase 07은 **새 entity/table/migration이 없다**.
- export / statistics / PDF 는 모두 **read-only**.
- 유일한 쓰기 경로는 Excel upload → `StageResultService.bulkUpdateResults` 로, 기존 `StageResult` 불변식(IN_PROGRESS guard, PENDING 금지, 정정 이력)을 그대로 상속한다. upload는 독립 도메인 상태를 갖지 않는다(stateless).

### 11.2 슬라이스별 컴포넌트 후보

후보 명칭이며 구현 시 확정. 패키지는 프로젝트 구조(`controller`/`service`/`dto.request`/`dto.response`) 관례를 따른다.

| Slice | 컴포넌트 후보 | type | 책임 |
| --- | --- | --- | --- |
| 07a | `ExcelExportWriter` | Service(infra) | SXSSF 시트 작성(컬럼 정의 + row iterator), row cap 적용 |
| 07a | `ExportColumn` / `ExcelExportSpec` | 값 객체 | dataset별 header + value extractor 정의 |
| 07a | `AdminExportController` | Controller | export 엔드포인트(applications 등) |
| 07a | `ExportAuditLogger` | Service | export SLF4J 구조적 audit |
| 07b | (07a 인프라 재사용) | — | stage results / interviews / evaluations 컬럼 spec 추가 |
| 07c | `AdminStatisticsController` | Controller | funnel 조회 엔드포인트 |
| 07c | `FunnelStatisticsService` | Service | P 코호트·7-bucket 분포·두 비율·dimension 집계 |
| 07c | `FunnelResponse` / `StageFunnelResponse` / `StageDistribution` / `DimensionFunnelResponse` | Response DTO | funnel 응답 구조 |
| 07d | `StageResultUploadController` | Controller | preview/commit 엔드포인트 |
| 07d | `StageResultUploadService` | Service | xlsx 파싱·3중 교차검증·all-or-nothing·bulkUpdateResults 위임 |
| 07d | `StageResultUploadRowRequest` | Request DTO(parsed) | 행 모델(echo 필드 포함) |
| 07d | `StageResultUploadPreviewResponse` / `StageResultUploadRowResult` / `StageResultUploadCommitResponse` | Response DTO | 행별 검증/ diff/ commit 결과 |
| 07e | `ApplicationPdfController` | Controller | `GET .../{applicationId}/pdf` |
| 07e | `ApplicationPdfService` | Service | 지원서 조회 → Thymeleaf XHTML → openhtmltopdf 렌더, PDF audit |
| 07e | `application-pdf.html` | Thymeleaf template | PDF용 XHTML, CJK 폰트 @font-face |

## 12. API List

모든 Phase 07 엔드포인트는 `/admin/**` 권한이다. interviewer/applicant 경로는 없다.

### 12.1 Export (admin, read-only)

| Method | Path | Purpose | Request(필터) | Response |
| --- | --- | --- | --- | --- |
| GET | `/admin/applications/export` | 전체 지원서 목록 xlsx | jobPostingId, jobPositionId, status (page 무시) | xlsx (StreamingResponseBody) |
| GET | `/admin/job-postings/{jobPostingId}/applications/export` | 공고별 지원서 목록 xlsx | jobPositionId, status | xlsx |
| GET | `/admin/stages/{stageId}/results/export` | 전형 결과 목록 xlsx | — | xlsx |
| GET | `/admin/job-postings/{jobPostingId}/interviews/export` | 면접 일정 목록 xlsx | stageId, status, from, to | xlsx |
| GET | `/admin/stages/{stageId}/interview-evaluations/export` | 면접 평가 목록 xlsx(읽기) | — | xlsx |

### 12.2 Statistics (admin, read-only)

| Method | Path | Purpose | Request | Response |
| --- | --- | --- | --- | --- |
| GET | `/admin/job-postings/{jobPostingId}/statistics/funnel` | 공고 전형 funnel(overall/dimension) | dimension?(POSITION), topN? | `ApiResponse<FunnelResponse>` |

### 12.3 Excel Upload (admin, write — StageResult only)

| Method | Path | Purpose | Request | Response |
| --- | --- | --- | --- | --- |
| GET | `/admin/stages/{stageId}/results/upload-template` | upload용 템플릿 xlsx(echo 컬럼 포함) | — | xlsx |
| POST | `/admin/stages/{stageId}/results/upload/preview` | 업로드 검증·diff(미적용) | multipart file | `ApiResponse<StageResultUploadPreviewResponse>` |
| POST | `/admin/stages/{stageId}/results/upload/commit` | 업로드 적용(all-or-nothing) | multipart file | `ApiResponse<StageResultUploadCommitResponse>` |

### 12.4 Application PDF (admin, read-only)

| Method | Path | Purpose | Request | Response |
| --- | --- | --- | --- | --- |
| GET | `/admin/applications/{applicationId}/pdf` | 지원서 1건 PDF | — | application/pdf (byte[]) |

## 13. Validation and Business Rules

### 13.1 Export

| 규칙 | 설명 |
| --- | --- |
| 필터 재사용 | 대응 list 엔드포인트와 동일 필터, page/size 무시. |
| Row cap | 생성 전 count 선검증, `count > maxRows`(기본 50,000) → `400 EXPORT_ROW_LIMIT_EXCEEDED`. |
| PII | applications export만 `name`/`phoneNumber`/`email` 포함. `ci`/`ciHash`/`password` 전 dataset 금지. |
| Audit | 전 export에 SLF4J 구조적 로그(actor, datasetType, filters, rowCount, timestamp). |

### 13.2 Statistics

| 규칙 | 설명 |
| --- | --- |
| 모집단 P | `submittedAt != null` 코호트. |
| 분포 합 | 각 stage raw 7-bucket 합 = `|P|`. |
| NO_RESULT | 응답 전용 synthetic. enum/입력값 아님. PENDING(row 있음)과 구분. |
| 비율 기준 | `cumulativeRate`/`stepConversionRate`는 `funnelPassedCount`(순차 통과 집합) 기준, raw `distribution.passed`와 분리. |
| withdrawn 분리 | `distribution.withdrawn`(stage status) ≠ `population.withdrawnCount`(application status), 덮어쓰기 금지. |
| Dimension distinct | application 단위 distinct. 학교=최종학력 1교, 자격=자격명별 distinct. |
| free-text | topN(기본 10) + '기타', 부정확성 메타 표기. |
| Audit | 없음(집계값만). |

### 13.3 Excel Upload

| 규칙 | 설명 |
| --- | --- |
| 대상 | `StageResult`만. |
| 업로드 소스 | `upload-template`만. applications/stage results export는 소스 아님. row에 `stageId` 컬럼 없음(path 판단). |
| 행 매칭 | `stageResultId` + `applicationId` + path `{stageId}` 3중 교차검증(service에서 StageResult 조회). |
| 낙관적 동시성 | `stageResultUpdatedAt`(ISO-8601 string) normalize 후 현재 `StageResult.updatedAt`와 비교, 불일치 → `STALE_ROW`, 전체 거부(409). 토큰 셀이 date/numeric/formula면 row error. |
| 파일 방어 | `.xlsx`만(.xls/.csv/.xlsm 거부), 첫 sheet, header signature/version 검증, formula cell 거부, duplicate `stageResultId` 거부, `maxUploadRows`/`maxUploadFileSize`. |
| 허용 resultStatus | `StageResultStatus` − `PENDING`. `NO_RESULT` 불가. |
| 빈칸 정책 | resultStatus blank → row error; score blank → null clear; comment blank → null clear. 변경 없는 row는 commit 제외(stale check도 변경 row에만). template은 현재값 prefill. |
| comment | ≤ 2000자. |
| Stage 상태 | `IN_PROGRESS`에서만(기존 guard 상속). |
| 부분 성공 | 없음. 하나라도 실패 시 update 0건. |
| 적용 경로 | 단일 transaction, 기존 `StageResultService.bulkUpdateResults` 경유. 기존 공유 DTO 불변. |
| actor | 인증 admin에서 추출, 정정 이력/audit에 기록. |

### 13.4 Application PDF

| 규칙 | 설명 |
| --- | --- |
| 권한 | admin 전용. |
| 단위 | 1 지원자 = 1 PDF. |
| PII | `name`/`phoneNumber`/`email` 포함, `ci`/`ciHash`/`password` 금지. |
| 폰트 | CJK 폰트 임베드(OFL), 외부 URL resource load 금지. |
| 템플릿 보안 | applicant text `th:text`만, `th:utext` 금지, 줄바꿈 CSS `pre-wrap`, attachment embed 범위 밖. |
| 응답 헤더 | `Cache-Control: no-store`, `Pragma: no-cache`, `X-Content-Type-Options: nosniff`. |
| Audit | SLF4J 구조적 로그(actor, applicationId, jobPostingId, jobPositionId, timestamp) 필수. |

## 14. Security / PII / Audit Policy

- **`ci`/`ciHash`/`password`**: 어떤 export/PDF/statistics에도 절대 포함하지 않는다.
- **`name`/`phoneNumber`/`email`**: applications export와 PDF에서 평문 노출(운영 연락 목적). 노출 시 audit 로그.
- **Statistics**: 집계값만. 개별 식별 데이터 없음. audit 없음.
- **Audit 저장소**: 현재는 SLF4J 구조적 로그(export, PDF, upload commit). 영속 `ActivityLog` 도메인 생성 시 그쪽으로 이관(backlog).
- **내부 경로/스토리지 경로/시크릿/암호화키**: 응답이나 PDF에 노출 금지.
- **Excel formula injection**: export는 free-text를 string cell로 + 위험 prefix escaping(§7.6); upload는 모든 column에서 formula cell 거부(§9.6).
- **권한 세분화 여지**: 현재 전 endpoint `/admin/**`. 단 applications export/PDF는 PII 평문 노출이므로 controller/service 레벨에 `EXPORT_APPLICATION_PII` 같은 **정책 상수**를 남겨, 향후 role matrix에서 "목록 조회 admin"과 "PII export admin"을 분리하기 쉽게 한다(지금 `SecurityConfig` 세분화는 하지 않음).

### 14.1 Audit schema (구체화)

PII surface이므로 구현자가 필드를 빠뜨리지 않도록 표준화한다.

- 공통: `eventType`, `actorId`, `actorLoginId`, `authority`, `requestId`, `clientIp`, `userAgent`, `timestamp`
- export: `datasetType`, `filtersHash`, `filtersSafeJson`, `rowCount`, `fileName`
- PDF: `applicationId`, `jobPostingId`, `jobPositionId`
- upload commit: `stageId`, `rowCount`, `changedCount`, `failed`, `sourceFileName`, `sourceFileSize`, `contentHash`
- **금지**: 이름/전화번호/이메일/CI/password/raw comment 등 PII·민감값을 audit log에 직접 남기지 않는다(식별이 필요하면 id/hash만).
- `filtersSafeJson`은 **allowlist 기반**으로 구성한다. raw request map을 그대로 넣지 않으며, `applicantName`/`email`/`phoneNumber`/`comment` 등 PII성 필터가 생기면 마스킹/제외한다.

## 15. Dependencies (신규)

| 의존성 | 용도 | 라이선스 | 비고 |
| --- | --- | --- | --- |
| `org.apache.poi:poi-ooxml` | xlsx 생성(SXSSF) | Apache-2.0 | 버전 명시 필요(5.x 계열) |
| `com.openhtmltopdf:openhtmltopdf-pdfbox` | HTML→PDF | LGPL 계열(AGPL 아님) | 정확 버전/라이선스 재확인 |
| `spring-boot-starter-thymeleaf` | PDF용 XHTML 템플릿 | Apache-2.0 | |
| CJK 폰트(Noto Sans KR / 나눔고딕) | 한글 임베드 | SIL OFL 1.1 | 리소스 번들 |

- iText 7(AGPL)은 도입하지 않는다.
- 새 entity/table/migration은 없다(전부 read-only 또는 기존 명령 재사용).

## 16. Test Strategy and Test Results

### 16.1 Test Results

이 문서는 **documentation-only 설계 단계**이므로 Java/test를 구현하거나 실행하지 않았다. 아래 명령/전략은 구현 슬라이스(07a~07f)용 계획이다. (status: §1 "Java/source/test implementation not started.")

### 16.2 Test Strategy

슬라이스별 targeted test(전체 스위트는 PC 성능 이슈로 부분 실행, 미실행 사유 보고).

- **Export(POI read-back)**: 생성 xlsx를 POI로 다시 읽어 header/행수(=필터 count) 단언; applications export `phoneNumber`/`email` 존재 + `ci`/`ciHash`/`password` 컬럼 부재; **formula injection 위험 prefix(`=`,`+`,`-`,`@`) 값이 string/escaped로 기록**됨; **no-store/nosniff/Content-Disposition 헤더** 단언.
- **Row cap**: count > maxRows → `400 EXPORT_ROW_LIMIT_EXCEEDED`, **workbook 실제 미생성** 단언.
- **Statistics**: 제출+제출후철회 코호트 → 각 stage raw 분포 합 = `|P|`, `NO_RESULT`가 StageResult 없는 멤버를 정확히 셈, `funnelPassedCount`/두 비율 검증, `distribution.withdrawn` ≠ `population.withdrawnCount`, 분야별 distinct, (자격별 도입 시) topN+'기타'.
- **Upload**: preview 검출(잘못된 stageResultId / 불일치 applicationId / stage mismatch / `PENDING`·`NO_RESULT` / 길이초과 / **blank resultStatus → row error** / **blank score·comment → clear**); **`STALE_ROW`(stageResultUpdatedAt 불일치 → update 0)**; duplicate `stageResultId` 거부; formula cell 거부; `.xls`/`.csv`/`.xlsm` 거부; header signature/version 불일치 거부; `maxUploadRows`/`maxUploadFileSize` 초과 거부; commit all-or-nothing; `IN_PROGRESS` guard; rollback. (**STALE_ROW 테스트는 07d 핵심** — 빠지면 lost update 방어가 문서에만 존재)
- **PDF(PDFBox text extraction)**: 한글 `name` 포함 + `ci` 문자열 부재 + 섹션 헤더; **`th:utext` 미사용 정적/convention 검사**; **외부 URL resource load 차단**; no-store/nosniff 헤더; audit 호출 단언.

테스트 명령(예, 구현 슬라이스용):

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*Export*" --tests "*Statistics*" --tests "*Upload*" --tests "*Pdf*" --no-daemon
```

## 17. Open Questions

| # | 질문 | 현재 입장 | 상태 |
| --- | --- | --- | --- |
| 1 | POI / openhtmltopdf 정확 버전·라이선스 | 5.x / LGPL계열, 추가 시 재확인 | Open(구현 시) |
| 2 | 자격별 dimension을 07 내 후속 슬라이스로 낼지 P08로 미룰지 | free-text 한계 명시 후 07 내 후속 가능 | Open |
| 3 | stage results / interviews export에 연락처 추가 여부 | 일단 list-parity, 수요 확인 후 | Open |
| 4 | 매우 큰 dataset의 비동기 export | row cap이 동기 bound, async 미도입 | Decided: 동기 |
| 5 | evaluation export 레벨(stage만 vs interview/application도) | stage 레벨 1행=1평가 | Open(07b) |
| 6 | applications export route(global + per-posting 둘 다 vs per-posting만) | 둘 다 제공(list와 동일) | Decided: 둘 다 |
| 7 | stageResultUpdatedAt 토큰을 원문 대신 HMAC opaque token으로 강화할지 | 운영 리스크 줄이려면 고려, 필수 아님 | Open |

## 18. Decision Log

| # | Decision | Rationale | Date |
| --- | --- | --- | --- |
| 1 | 4개 기둥 모두 Phase 07, 읽기(export/stats) 먼저·쓰기(upload)·PDF 나중. | 리스크 낮은 read-only로 기반을 먼저 다지고 위험한 쓰기와 무거운 PDF 의존성을 뒤로. | 2026-05-29 |
| 2 | Excel = POI + SXSSF(streaming) + 하드 row cap. | heap bound + 대량 안전. 조용한 truncation 금지. | 2026-05-29 |
| 3 | download 전부 list-parity, 1:N 평탄화 없음. | 단순/필터 재사용. 깊은 상세는 PDF가 담당. | 2026-05-29 |
| 4 | applications export = 목록 컬럼 + name/phone/email, ci/ciHash/password 제외, 전 export audit. | export는 운영 연락 목적이 있어 목록보다 컬럼이 많은 게 정상. 민감식별자는 절대 제외. | 2026-05-29 |
| 5 | funnel = stage별 resultStatus 전체 분포 + 두 비율(누적/전환). | 운영 분석에 가장 풍부. headline은 PASSED 추이. | 2026-05-29 |
| 6 | dimension = 전체+분야별(FK) 먼저, 학교별 P08 이후, 자격별 free-text 후속. | FK는 정확, free-text는 부정확. 정확한 축부터. | 2026-05-29 |
| 7 | Excel upload = StageResult만, InterviewEvaluation 제외. | 평가는 Phase 06상 배정 면접관만 작성·평가 독립성. admin 엑셀 대량 변경은 경계 위반. | 2026-05-29 |
| 8 | upload = stateless preview/commit, 새 테이블 없음. | migration framework 부재. 기존 서비스 재검증으로 충분. | 2026-05-29 |
| 9 | upload = all-or-nothing, 3중 교차검증, 기존 bulkUpdateResults 경유, 공유 DTO 불변. | 금융권 안전성. 기존 불변식·정정이력·audit 상속. echo 필드는 upload row DTO에만. | 2026-05-29 |
| 10 | PDF = Thymeleaf + openhtmltopdf(PDFBox) + CJK 임베드, iText(AGPL) 회피. | 다단 한글 문서 유지보수 용이 + 라이선스 안전. | 2026-05-29 |
| 11 | PDF = admin 전용, 1 지원자 1 PDF, 생성 시 audit. | 범위 최소. 집중 PII surface라 접근 추적 필수. | 2026-05-29 |
| 12 | 모집단 P = submittedAt != null 코호트. | "고정·재현가능" intent 부합. withdraw가 submittedAt 보존. | 2026-05-29 |
| 13 | funnel 분포 = 7-bucket(+NO_RESULT), 합 = |P|. NO_RESULT는 응답 전용 synthetic. | 분포 수학이 닫힘. PENDING(row 있음)과 NO_RESULT(row 없음) 구분. enum/입력값 아님. | 2026-05-29 |
| 14 | row cap = 생성 전 count 선검증, 초과 시 400 EXPORT_ROW_LIMIT_EXCEEDED. | 무의미한 대량 생성 방지, 명시적 실패. | 2026-05-29 |
| 15 | upload 소스 = `upload-template`만(stage results export·applications export 모두 제외). | 목록/연락 export와 검증 가능한 upload sheet는 목적이 달라 분리. 컬럼 계약 충돌 제거. | 2026-05-29 (review2) |
| 16 | upload 낙관적 동시성: `stageResultUpdatedAt` 토큰, 불일치 시 `STALE_ROW` 전체 거부(409). | stateless 유지하며 lost update 방어(다른 admin 변경 덮어쓰기 차단). | 2026-05-29 (review) |
| 17 | export = temp file 선생성(read-only tx + projection DTO), entity/lazy를 writer에 넘기지 않음. | streaming tx 경계 모호로 인한 LazyInit/connection 점유/N+1/temp 누수 차단. | 2026-05-29 (review) |
| 18 | funnel 비율은 순차 통과 집합(`funnelPassedCount`) 기준, raw distribution과 분리. | 비순차 PASSED로 전환율이 깨지는 것 방지, funnel 정의와 일치. | 2026-05-29 (review) |
| 19 | `distribution.withdrawn`(stage result status) ≠ `population.withdrawnCount`(application status), 덮어쓰기 금지. | 두 의미 혼동 시 통계 왜곡. raw stage 분포 유지. | 2026-05-29 (review) |
| 20 | Excel formula injection 방어 + upload 파일 레벨 방어(.xlsx만/formula 거부/중복 거부 등) + PDF `th:utext` 금지·외부 resource 차단 + audit schema 표준화 + `EXPORT_APPLICATION_PII` 권한 상수. | export/upload/PDF 입력·출력 보안 표면 정리, 구현자 누락 방지. | 2026-05-29 (review) |
| 21 | upload 빈칸 정책: resultStatus blank=row error, score/comment blank=null clear, 변경 없는 row는 commit 제외(stale check도 변경 row에만), template은 현재값 prefill. | "빈칸=기존값 유지"의 실수 위험 제거, 명확성. | 2026-05-29 (review2) |
| 22 | `stageResultUpdatedAt` = ISO-8601 **string cell**, normalize 후 비교, date/numeric/formula면 row error. (옵션) HMAC opaque token. | Excel 날짜셀 precision/timezone 깨짐·토큰 변조 방지. | 2026-05-29 (review2) |
| 23 | upload row에 `stageId` 컬럼 없음 — stageId는 path로만 판단. | `stageResultId`+`applicationId`+path stageId면 충분, row 중복 컬럼 제거. | 2026-05-29 (review2) |
| 24 | export 응답 = `ResponseEntity<Resource>`, temp 삭제는 controller가 전송 후 finally(service finally 금지). | service finally 삭제 시 body write 전 파일 소멸 위험. | 2026-05-29 (review2) |
| 25 | `filtersSafeJson`은 allowlist 기반, PII성 필터는 마스킹/제외. | audit log가 PII 로그가 되는 것 방지. | 2026-05-29 (review2) |

## 19. Next Phase Considerations

- 07a 구현부터: POI SXSSF writer + row cap + applications export + export audit.
- Phase 08 - CommonCode And School Master: `School` master 도입 후 학교별 dimension 정확도 확보 → funnel 학교 축 활성화.
- Backlog - Privacy/Audit/Retention: SLF4J export/PDF/upload audit를 영속 `ActivityLog`로 이관.
- Future: stage results/interviews export 연락처 확장(운영 수요 확인 시), 비동기 대량 export, 자격별 dimension 정규화.
