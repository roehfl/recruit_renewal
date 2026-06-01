# Phase 07d - Stage Result Excel Upload (preview/commit)

## 1. Phase 요약

- Date: 2026-06-02
- Work type: implementation (Phase 07 네 번째 슬라이스, Phase 07의 **유일한 쓰기 경로**).
- Goal: 운영자가 `upload-template`으로 받은 xlsx로 `StageResult`를 bulk 변경한다. stateless preview/commit, all-or-nothing, 3중 교차검증, 낙관적 동시성, 기존 `StageResultService.bulkUpdateResults` 위임.
- 새 entity/table/migration 없음. commit은 기존 명령에 위임해 기존 불변식(Stage `IN_PROGRESS` guard, PENDING 금지, comment ≤ 2000, actor 필수, 정정 이력/audit)을 그대로 상속한다.

## 2. 구현 범위 (Implemented)

- `GET /api/admin/stages/{stageId}/results/upload-template` — 현재값 prefill + 동시성 토큰 포함 xlsx 템플릿(유일한 upload 소스).
- `POST /api/admin/stages/{stageId}/results/upload/preview` — 행별 검증/diff(영속 변경 없음).
- `POST /api/admin/stages/{stageId}/results/upload/commit` — 재검증 후 all-or-nothing 적용.
- 파일 레벨 방어: `.xlsx`만 허용, 파일 크기/행수 한도, 첫 sheet, header signature 검증, formula 셀 거부, 중복 `stageResultId` 거부, 빈 행 skip, 셀 string 판독(로케일 의존 제거).
- 행 레벨 검증: 3중 교차검증(`stageResultId` 존재 + `applicationId` 일치 + path `stageId` 소속), resultStatus 허용값(PENDING/NO_RESULT 불가, blank=오류), score blank=null clear, comment blank=null clear/2000자.
- 낙관적 동시성: 변경 대상 행의 `stageResultUpdatedAt` 토큰을 현재 DB `StageResult.updatedAt`과 비교, 불일치 시 `STALE`로 전체 거부(409).
- upload commit SLF4J 구조적 audit(주체/요청 메타 + outcome + 카운트 + sourceFileName/Size/contentHash; PII 값 미기록).

## 3. Out of scope (Deferred)

- `InterviewEvaluation` Excel upload — Phase 06 경계(배정 면접관만 평가 작성)상 영구 제외.
- staging 영속화(batchId 모델) — stateless 채택으로 제외.
- HMAC opaque 토큰(`stageResultVersionToken`) — Open Q#7, 원문 ISO-8601 토큰 채택.
- 발표(`RESULT_ANNOUNCED`) 후 정정(`correct` 경로) — upload 범위 밖.
- 영속 `ActivityLog` 이관 — SLF4J audit 유지.
- 07e(Application PDF).

## 4. 변경 파일

### Created (main)

- `enumeration/StageResultUploadRowStatus.java`
- `enumeration/StageResultUploadCommitOutcome.java`
- `exception/InvalidStageResultUploadException.java`
- `config/UploadProperties.java`
- `dto/request/StageResultUploadRowRequest.java`
- `dto/response/StageResultUploadDiff.java`
- `dto/response/StageResultUploadRowResult.java`
- `dto/response/StageResultUploadPreviewResponse.java`
- `dto/response/StageResultUploadCommitResponse.java`
- `dto/response/StageResultUploadTemplateRow.java`
- `service/StageResultUploadParser.java`
- `service/StageResultUploadService.java`
- `service/UploadAuditLogger.java`
- `controller/StageResultUploadController.java`

### Modified (main)

- `dto/response/ApiResponse.java` — `fail(message, data)` 오버로드 추가(거부 응답에 행 detail body 동봉용).
- `common/hash/HashUtil.java` — `sha256Bytes(byte[])` 추가(파일 contentHash용; `sha256(String)`과 오버로드 모호성 회피 위해 별도 이름).
- `exception/GlobalExceptionHandler.java` — `InvalidStageResultUploadException` → 400.
- `src/main/resources/application.yaml` — `recruit.upload.max-rows`(기본 10,000), `recruit.upload.max-file-size`(기본 5MB).

### Created (test)

- `controller/StageResultUploadControllerTest.java`

## 5. 신규 클래스

- `StageResultUploadRowStatus` (Enum): CHANGED / UNCHANGED / ERROR / STALE.
- `StageResultUploadCommitOutcome` (Enum): APPLIED / REJECTED_VALIDATION / REJECTED_STALE.
- `InvalidStageResultUploadException` (Exception): 파일 레벨 검증 실패(400).
- `UploadProperties` (Config): `maxRows`, `maxFileSize`(DataSize).
- `StageResultUploadRowRequest` (Request DTO, parsed): 행 raw 모델(echo 필드 + formula/token-cell flag).
- `StageResultUploadDiff` (Response DTO): 변경 전/후 비교.
- `StageResultUploadRowResult` (Response DTO): 행 단위 검증/적용 결과.
- `StageResultUploadPreviewResponse` (Response DTO): preview 집계 + rows.
- `StageResultUploadCommitResponse` (Response DTO): commit outcome + 카운트 + failedRows.
- `StageResultUploadTemplateRow` (Response DTO): 템플릿 행(현재값 prefill + 토큰).
- `StageResultUploadParser` (Service): 파일 레벨 방어 + 행 파싱.
- `StageResultUploadService` (Service): 3중 교차검증/빈칸 정책/diff/낙관적 동시성/위임.
- `UploadAuditLogger` (Service): commit audit.
- `StageResultUploadController` (Controller): 3개 엔드포인트.

## 6. 수정 클래스

- `ApiResponse` — `fail(String, T)` 오버로드. 기존 `fail(String)`/`success(T)` 불변.
- `HashUtil` — `sha256Bytes(byte[])`.
- `GlobalExceptionHandler` — 핸들러 1개 추가.

## 7. 클래스별 설명 (핵심)

### `StageResultUploadParser` (service)

- 책임: upload-template xlsx의 파일 레벨 방어 + 행 파싱. 형식/허용값/교차검증은 하지 않는다(service 담당).
- 핵심: `parse(MultipartFile)` → `List<StageResultUploadRowRequest>`.
  - 확장자 `.xlsx`만(.xls/.csv/.xlsm 거부), 크기 한도, 첫 sheet, header signature를 `HEADERS`와 정확히 대조.
  - 셀을 모두 문자열로 읽어 로케일 의존 numeric/date parse 제거. formula 셀은 `formulaCellPresent`, 토큰 셀(col 3) numeric/date는 `tokenCellNotString` flag로 표시.
  - 빈 행 skip, `maxRows` 초과 시 파일 전체 거부.
- 관련: `UploadProperties`, `StageResultUploadRowRequest`, `InvalidStageResultUploadException`.

### `StageResultUploadService` (service)

- 책임: 템플릿 생성 / preview / commit. 3중 교차검증 + 빈칸 정책 + 변경 판정 + 낙관적 동시성 + all-or-nothing.
- 핵심 메서드:
  - `generateTemplate(stageId)`: `findByStageIdForAdminList`로 현재값 prefill + 토큰(현재 updatedAt ISO-8601) → `ExcelExportService.generate`.
  - `preview(stageId, file)`: 행별 검증/ diff(STALE 미판정). 영속 변경 없음.
  - `commit(stageId, file, actor)` (`@Transactional`): 재검증 → ERROR 있으면 REJECTED_VALIDATION, STALE 있으면 REJECTED_STALE(0건 적용), 전부 통과 시 변경 행만 `StageResultBulkUpdateRequest`로 매핑해 `bulkUpdateResults`에 단일 트랜잭션 위임.
- 검증 규칙:
  - 3중 교차검증: `stageResultId` 존재(해당 stage), `applicationId` 일치(stage 소속은 stage-scoped 조회로 보장).
  - resultStatus 허용값 = {PASSED, FAILED, ABSENT, HOLD, WITHDRAWN}; blank=오류, PENDING/미허용=오류.
  - score blank → null clear, 형식 오류 → 행 오류. comment blank → null clear, 2000자 초과 → 오류.
  - 변경 없는 행 = UNCHANGED → commit 제외(stale check도 변경 행에만).
  - 낙관적 동시성: `formatToken(updatedAt)` ISO_LOCAL_DATE_TIME, 변경 행 토큰 불일치 → STALE.
- 관련: `StageResultUploadParser`, `StageResultService`, `ExcelExportService`, `StageResultRepository`, `StageRepository`.

### `StageResultUploadController` (controller)

- 책임: 엔드포인트 3개 + audit. commit outcome → HTTP status 매핑(APPLIED=200, REJECTED_VALIDATION=400, REJECTED_STALE=409). 거부 응답도 body에 행 detail 포함.
- 관련: `StageResultUploadService`, `ExcelExportResponseFactory`, `ExportAuditLogger`(template), `UploadAuditLogger`(commit), `CurrentEmployeeService`.

## 8. API 목록

| Method | Path | Purpose | Request | Response |
| --- | --- | --- | --- | --- |
| GET | `/api/admin/stages/{stageId}/results/upload-template` | upload용 템플릿 xlsx(현재값 prefill + 토큰) | — | xlsx(stream) |
| POST | `/api/admin/stages/{stageId}/results/upload/preview` | 업로드 검증·diff(미적용) | multipart `file` | `ApiResponse<StageResultUploadPreviewResponse>` |
| POST | `/api/admin/stages/{stageId}/results/upload/commit` | 업로드 적용(all-or-nothing) | multipart `file` | `ApiResponse<StageResultUploadCommitResponse>` (200/400/409) |

업로드 컬럼: `stageResultId`, `applicationId`, `applicantName`, `stageResultUpdatedAt`(read-only 토큰), `resultStatus`, `score`, `comment`. `stageId`는 path로만 판단(컬럼 없음).

## 9. Entity 관계 요약

- 신규 entity 없음. `StageResult`(기존)만 변경 대상. commit은 `StageResultService.bulkUpdateResults(stageId, request, actor)`로 위임.
- `ci`/`ciHash`/`password`/`phoneNumber`/`email`는 템플릿/응답 어디에도 노출하지 않는다(`applicantName`만 echo).

## 10. 비즈니스 규칙

- upload 소스는 `upload-template`만. applications/stage results export는 소스 아님.
- 3중 교차검증을 모두 만족해야 유효 행.
- 빈칸: resultStatus blank=행 오류, score/comment blank=null clear. 변경 없는 행은 commit 제외.
- all-or-nothing: ERROR/STALE이 하나라도 있으면 0건 적용. ERROR=400(REJECTED_VALIDATION), STALE=409(REJECTED_STALE).
- formula 셀/토큰 셀 비-문자열/중복 `stageResultId`는 행 오류.
- commit은 Stage `IN_PROGRESS`에서만 적용 가능(기존 `bulkUpdateResults` guard 상속). PENDING 금지 상속.

## 11. 테스트 커버리지

- `StageResultUploadControllerTest`:
  - template: prefill + 토큰 + PII 컬럼 부재, unknown stage 404.
  - preview: changed/unchanged/error 집계, blank/PENDING 오류, applicationId 불일치, 중복 id, formula 셀 오류, wrong header 400.
  - commit: changed 적용 + unchanged 제외, score/comment blank clear, all-or-nothing 거부(400, 미적용 확인), STALE 409(미적용 확인), 비-xlsx 확장자 400.
  - 인가: applicant 403 / anonymous 401(template GET, commit POST).
- HashUtil 회귀(`sha256(null)` 모호성 해소 확인).

## 12. 테스트 결과

- 명령: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*StageResultUpload*" --tests "*HashUtil*" --no-daemon`
- 결과: BUILD SUCCESSFUL — `StageResultUploadControllerTest` 14건 + `HashUtilTest` 회귀 통과.
- 비고: 부분 실행(upload + HashUtil). statistics/export 슬라이스처럼 엔티티를 repository로 직접 영속화해 클럭 의존(접수기간) 없이 안정적. 전체 스위트는 본 슬라이스 범위상 미실행(`Infra 01` 기록의 날짜 의존 사전-실패 8건은 별도 과제).

## 13. Known limitations

- 토큰은 원문 ISO-8601 string(HMAC opaque 토큰 미적용) — 사용자가 토큰을 임의로 정확히 위조하면 stale check 우회 가능(Open Q#7, 후속 하드닝 후보).
- `StageResult.updatedAt` precision은 DB/JPA 매핑에 의존 — 같은 소스(DB 조회값)에서 양측 비교하므로 round-trip 일관성 유지.
- audit는 SLF4J 구조적 로그(영속 ActivityLog 미도입).

## 14. Next phase considerations

- Phase 07e: Application PDF(Thymeleaf + openhtmltopdf, CJK 폰트 임베드, admin 전용).
- Phase 07f: Stabilization / Test Hardening(row cap·upload 경계 회귀, PII 부재 검증).
