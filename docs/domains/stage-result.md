# 전형·전형결과 (`stage-result`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 관련 카드: [job-posting](job-posting.md) · [admin-application](admin-application.md) · [interview](interview.md) · [statistics](statistics.md) · [application](application.md) · [auth-account](auth-account.md) · [privacy-audit](privacy-audit.md) · [application-form](application-form.md) · [role-menu](role-menu.md) · [master-data](master-data.md)

## 요약

- 관리자가 공고별 전형 단계(`Stage`)를 설정하고, 단계마다 대상자(`StageResult`)를 불러와 판정 → 발표 → 마감한다. 발표 후 변경은 사유 필수 **정정**만. 지원자는 **발표된** 결과만 본다.
- 판정(단계 `IN_PROGRESS`만): 화면 일괄 저장(`bulk`), 단건(FE 미사용), 엑셀 왕복(템플릿 → 미리보기 → 적용, 내부적으로 `bulk`).
- 화면 `AdminStageResult`(`/admin/stage-results`): 공고 셀렉트 → 단계 스텝퍼 → 배너 → 카운트 카드 → 그리드, 단계 설정은 드로어.
- 경계: 결과 xlsx export(`GET /admin/stages/{stageId}/results/export`)·그리드 파생 필드(`AdminStageResultEnricher`)·지원서 상세 전형 타임라인 → [admin-application](admin-application.md). 면접 → [interview](interview.md). 퍼널 → [statistics](statistics.md). 내 지원 현황 결과 요약 → [application](application.md). 마이페이지 화면·actor 해석 → [auth-account](auth-account.md). 감사 로그·파기 판정 → [privacy-audit](privacy-audit.md).

## 용어

| 용어 | 코드 | 설명 |
|---|---|---|
| 전형 단계 | `Stage` | 공고별 0..N. `stageOrder`는 공고 내 유일 |
| 단계 유형 | `StageType` | `DOCUMENT` 서류·`FIRST_INTERVIEW`·`SECOND_INTERVIEW`·`FINAL_INTERVIEW`·`ETC` |
| 단계 상태 | `StageStatus` | `READY`→`IN_PROGRESS`→`RESULT_ANNOUNCED`→`CLOSED` 한 방향(FE 라벨 대기·진행중·발표완료·마감) |
| 최종 단계 | `finalStage` | 서버는 공고당 최대 1개. 정확히 1개가 아니면 파기 판정 불가 |
| 전형 결과 | `StageResult` | (단계, 지원서)당 1행. `score`·`comment`·`decidedAt`·`decidedBy`·`version` |
| 결과 상태 | `StageResultStatus` | `PENDING` 대기(생성 시, 되돌리기 불가)·`PASSED` 합격·`FAILED` 불합격·`HOLD` 보류·`ABSENT` 결시·`WITHDRAWN` 철회 |
| 직전 단계 | - | 같은 공고에서 `stageOrder`가 현재보다 작은 단계 중 최대 |
| 발표 / 마감 | `announce` / `close` | 발표 = 지원자 공개 시점. 마감의 감사 액션명은 `STAGE_RESULT_CONFIRM` |
| 정정 | `correctResult` | 발표·마감 후 사유 필수 변경, 전후값 이력 append |
| 발표일시 | `resultAnnouncementDateTime` | 관리자 입력 예정 일시. **표시용**이며 노출 게이트가 아니다 |

## 파일 지도

### 백엔드

| 레이어 | 파일 | 역할 |
|---|---|---|
| controller | `{BE}/controller/StageController.java` | 단계 CRUD·순서·시작·발표·마감·삭제 |
| controller | `{BE}/controller/StageResultController.java` | 결과 목록·불러오기·판정·정정·이력 |
| controller | `{BE}/controller/StageResultUploadController.java` | 템플릿·preview·commit, outcome→HTTP |
| controller | `{BE}/controller/ApplicationStageResultController.java` | 지원자 결과 조회 |
| service | `{BE}/service/StageService.java` | 단계 규칙·전이·공고 가드·발표/마감 감사 |
| service | `{BE}/service/StageResultService.java` | initialize·판정·bulk·판정 감사 |
| service | `{BE}/service/StageResultCorrectionService.java` | 정정·이력 |
| service | `{BE}/service/StageResultUploadService.java` | 템플릿 생성·preview·commit |
| service | `{BE}/service/StageResultUploadParser.java` | xlsx 파일 레벨 검증·파싱 |
| service | `{BE}/service/StageResultStatusLabels.java` | 결과 한글 라벨·파싱 |
| service | `{BE}/service/StageResultChangeMetadata.java` | 판정 감사 metadata |
| service | `{BE}/service/Upload*` | `UploadAuditLogger`·`UploadMetadata`·`UploadConflictMetadata`(업로드 감사) |
| service | `{BE}/service/ApplicationStageResultService.java` | 지원자 결과 조회 |
| entity | `{BE}/domain/entity/Stage.java` | 단계 |
| entity | `{BE}/domain/entity/StageResult.java` | 결과(`@Version`) |
| entity | `{BE}/domain/entity/StageResultCorrectionHistory.java` | 정정 이력 |
| repository | `{BE}/domain/repository/StageRepository.java` | 단계 조회·중복 검사 |
| repository | `{BE}/domain/repository/StageResultRepository.java` | 결과 조회(지원자 공개 쿼리 포함) + 다른 카드용 배치 조회 |
| repository | `{BE}/domain/repository/StageResultCorrectionHistoryRepository.java` | 이력 조회 |
| dto | `{BE}/dto/request/Stage*` | 요청 9종 |
| dto | `{BE}/dto/response/StageResult*` | bulk·initialize·이력·업로드 응답 8종 |
| dto | `{BE}/dto/response/StageListResponse.java` | 단계 목록 행 |
| dto | `{BE}/dto/response/StageDetailResponse.java` | 단계 상세 |
| dto | `{BE}/dto/response/AdminStageResultResponse.java` | 관리자 결과 행 |
| dto | `{BE}/dto/response/ApplicantStageResultResponse.java` | 지원자 결과 행 |
| enum | `{BE}/enumeration/Stage*` | 단계 상태·유형, 결과 상태, 업로드 행 상태·commit 결과 |
| exception | `{BE}/exception/*Stage*` | `InvalidStage*` 400 3종, `Stage*NotFoundException` 404 2종 |
| config | `{BE}/config/UploadProperties.java` | `recruit.upload.*`(10000행·5MB). [interview](interview.md)·[master-data](master-data.md) 공유 |
| test | `{BT}/controller/Stage*` | 단계·결과·업로드 컨트롤러 테스트 3종 |
| test | `{BT}/controller/ApplicationStageResultControllerTest.java` | 지원자 조회 |
| test | `{BT}/service/Stage*` | 서비스·라벨·파서·감사 계측 테스트 6종 |
| test | `{BT}/service/ApplicationStageResultServiceTest.java` | 지원자 노출 조건 |

### 프론트

| 구분 | 파일 | 역할 |
|---|---|---|
| route | `{FE}/routes/adminRoutes.ts` | `AdminStageResult`(`/admin/stage-results`) |
| view | `{FE}/views/admin/stageResult/AdminStageResultView.vue` | 본체: 선택·배너·편집 버퍼·저장 |
| view | `{FE}/views/admin/stageResult/useStageLifecycle.ts` | 불러오기·시작·발표·마감 명령 |
| component | `{FE}/views/admin/stageResult/StageStepper.vue` | 단계 스텝퍼 |
| component | `{FE}/views/admin/stageResult/StageResultCounts.vue` | 카운트 카드(필터 토글) |
| component | `{FE}/views/admin/stageResult/StageResultGrid.vue` | 결과 그리드 |
| component | `{FE}/views/admin/stageResult/StageUploadPreviewModal.vue` | 엑셀 업로드 모달 |
| component | `{FE}/views/admin/stageResult/StageResultCorrectModal.vue` | 정정 모달 + 이력 |
| component | `{FE}/views/admin/stageResult/StageConfigDrawer.vue` | 단계 설정 드로어 |
| api | `{FE}/api/admin/adminStageApi.ts` | 이 카드 관리자 API(+ [admin-application](admin-application.md) API 2개). `getStages`는 [interview](interview.md)도 사용 |
| api | `{FE}/api/applicationApi.ts` | (공유) `getStageResults`만 이 카드 |
| types | `{FE}/types/admin/stage.ts` | 타입·라벨·옵션 상수 |
| types | `{FE}/types/application.ts` | (공유) `ApplicantStageResult` |

- store·spec 없음. xlsx 저장은 공통 `{FE}/common/fileDownload.ts`. 진입: 메뉴, [job-posting](job-posting.md) 공고 상세·[application-form](application-form.md) 현황판 버튼(쿼리 `jobPostingId`).

## API 계약

권한 표기: 관리자 = `ADMIN`·`RECRUIT_ADMIN`(`/api/admin/**` 매처), ★ = 임직원 actor 필수(`CurrentEmployeeService.getCurrentEmployeeActor`, 비인증 401·비임직원 403 → [auth-account](auth-account.md)).

| 상태 | 메서드 | 경로 | 요청 요약 | 응답 요약 | 권한 |
|---|---|---|---|---|---|
| 🟢 | GET | /admin/job-postings/{jobPostingId}/stages | - | `StageListResponse[]` | 관리자 |
| 🟢 | GET | /admin/job-postings/{jobPostingId}/stages/{stageId} | - | `StageDetailResponse` | 관리자 |
| 🟢 | POST | /admin/job-postings/{jobPostingId}/stages | `{ stageName, stageType, stageOrder, resultAnnouncementDateTime?, finalStage }` | `Long`(id), READY | 관리자 |
| 🟢 | POST | /admin/job-postings/{jobPostingId}/stages/{stageId} | 생성과 같은 모양 | `Long` | 관리자 |
| 🟢 | POST | /admin/job-postings/{jobPostingId}/stages/reorder | `{ items: [{ stageId, stageOrder }] }` | `StageListResponse[]` | 관리자 |
| 🟢 | POST | /admin/job-postings/{jobPostingId}/stages/{stageId}/start | - | `Long` | 관리자 |
| 🟢 | POST | /admin/job-postings/{jobPostingId}/stages/{stageId}/announce | - | `Long` | 관리자 ★ |
| 🟢 | POST | /admin/job-postings/{jobPostingId}/stages/{stageId}/close | - | `Long` | 관리자 ★ |
| 🟢 | POST | /admin/job-postings/{jobPostingId}/stages/{stageId}/delete | - | `Long` | 관리자 |
| 🟢 | GET | /admin/stages/{stageId}/results | - | `AdminStageResultResponse[]` | 관리자 |
| 🟢 | POST | /admin/stages/{stageId}/results/initialize | - | `{ stageId, createdCount, existingCount, skippedCount, removedCount, results[] }` | 관리자 |
| 🟢 | POST | /admin/stages/{stageId}/results/{resultId} | `{ resultStatus, score?, comment? }` | `AdminStageResultResponse` | 관리자 ★ |
| 🟢 | POST | /admin/stages/{stageId}/results/bulk | `{ results: [{ stageResultId, resultStatus, score?, comment? }] }` | `{ stageId, updatedCount, results[] }` | 관리자 ★ |
| 🟢 | POST | /admin/stages/{stageId}/results/{resultId}/correct | `{ resultStatus, score?, comment?, reason }` | `AdminStageResultResponse` | 관리자 ★ |
| 🟢 | GET | /admin/stages/{stageId}/results/{resultId}/histories | - | `StageResultCorrectionHistoryResponse[]` 최신순 | 관리자 |
| 🟢 | GET | /admin/stages/{stageId}/results/upload-template | - | xlsx 스트림 | 관리자 ★ |
| 🟢 | POST | /admin/stages/{stageId}/results/upload/preview | multipart `file` | `StageResultUploadPreviewResponse` | 관리자 |
| 🟢 | POST | /admin/stages/{stageId}/results/upload/commit | multipart `file` | `StageResultUploadCommitResponse`(200/400/409) | 관리자 ★ |
| 🟢 | GET | /applications/{applicationId}/stage-results | - | `ApplicantStageResultResponse[]`(발표분만) | `APPLICANT` 본인 |

- 응답은 `ApiResponse<...>` 래핑(템플릿 제외). 400 메시지는 영문(업로드·지원자 API만 한글), 화면 문구는 FE가 만든다.
- 공통 오류: 공고·단계·결과 없음(또는 경로 소속 불일치) 404, 동시 트랜잭션 충돌 409("다른 사용자가 먼저 변경하여 처리하지 못했습니다. 최신 상태를 다시 확인해 주세요.").

### 엔드포인트 상세

**단계**
- 목록 행 `{ id, jobPostingId, stageName, stageType, stageOrder, status, resultAnnouncementDateTime, finalStage }`(stageOrder asc). 상세는 + `createdAt`·`updatedAt`, **FE 미사용**.
- 생성·수정 요청(FE `StageSaveRequest`): `stageName` `@NotBlank`, `stageType`·`stageOrder`(≥0) 필수, `resultAnnouncementDateTime` null = 미정. 오류 `Closed JobPosting cannot be changed.`·`Stage order already exists.`·`Final stage already exists.`.
- 수정(옛 계약 "변경 2", 2026-09-04 🟢): 공고 `CLOSED` 400 → 단계 `IN_PROGRESS`면 `resultAnnouncementDateTime`만 반영(나머지 4개 필드가 현재 값과 다르면 400 `In progress stage allows changing resultAnnouncementDateTime only.`) → `RESULT_ANNOUNCED`·`CLOSED` 400 `Only READY stage can be changed.` → READY 전체 수정. **FE는 진행 중 단계의 잠긴 4개 필드에 원본 값을 그대로 보낸다**(순서도 원본 값).
- `reorder`: 공고의 **모든** 단계 + **전부 READY**, stageOrder ≥0·중복 금지, stageId 중복 금지(다른 공고 id 404).
- `start`·`announce`·`close`: 공고 `PUBLISHED`만(`Stage status command is allowed only for PUBLISHED JobPosting.`), 각각 READY·IN_PROGRESS·RESULT_ANNOUNCED에서만. `announce`는 결과 0건 `StageResult must be initialized before announce.`, PENDING 잔여 `StageResult has pending results.`.
- `delete`: READY만. 확정·취소 면접이 있으면 400 `Stage with confirmed or cancelled interview cannot be deleted.`([interview](interview.md)에서 면접 취소 → 삭제 후 재시도). 그 단계의 `StageResult`(전부 PENDING)·`DRAFT` 면접은 함께 지운다.

**결과**
- 결과 행(옛 계약 "변경 1", 2026-09-04 🟢): `{ stageResultId, stageId, applicationId, applicantName, jobPositionId, jobPositionName, applicationStatus, resultStatus, score, comment, submittedAt, decidedAt, decidedBy, workLocation, applicationType, finalEducationLevel, finalSchoolName, previousStageResultStatus }`. 목록은 submittedAt desc, **비페이징**. `initialize`·`bulk`의 `results`도 단계 전체.
  - `decidedBy` 판정자 loginId(관리자 전용). `workLocation` 근무지 스냅샷. `finalEducationLevel`·`finalSchoolName` = 최고 `EducationLevel` 행(동률 id 큰 행) — 지원현황 조회와 같은 규칙. `previousStageResultStatus` = 직전 단계의 이 지원서 결과(직전 단계 상태는 안 봄). 값이 없으면 모두 null.
- `initialize`(옛 계약 "변경 4", 2026-09-18 🟢): 단계 READY·IN_PROGRESS만. 첫 단계 대상 = 공고의 `SUBMITTED` 전부. 2단계부터: 직전 단계가 `RESULT_ANNOUNCED`·`CLOSED`가 아니면 400 `Previous stage results must be announced before initializing.`, 대상 = `SUBMITTED` + 직전 단계 `PASSED`, 비대상의 `PENDING` 행 삭제(판정 행 보존). `existingCount` 대상 중 기존 행, `skippedCount` 비대상 지원서(미제출·철회·직전 비합격), `removedCount` 삭제 행(첫 단계 0).
- 단건 판정 **FE 미사용**. `bulk`: 빈 배열·id 중복 400, 하나라도 다른 단계·없음이면 404(전체 거부). `score`·`comment`는 보낸 값으로 **교체**(null = 비움).
- `correct`: 단계 RESULT_ANNOUNCED·CLOSED만, `reason` 필수 ≤1000, `comment` ≤2000, PENDING 불가, decidedAt=정정 시각. `histories` 행 `{ historyId, stageResultId, correctedAt, correctedBy, reason, previous/newStatus, previous/newScore, previous/newComment, previous/newDecidedAt }`.

**엑셀 업로드(옛 계약 "변경 3", 2026-09-04 🟢)**
- 템플릿 헤더 7열(정확 일치): `시스템ID(수정금지)`(stageResultId)·`수험번호(수정금지)`(applicationId)·`이름(수정금지)`·`수정토큰(수정금지)`·`결과`·`점수`·`코멘트`. 앞 4열 회색 음영, 헤더 틀고정, 결과는 한글 라벨 프리필, formula escape 끔.
- 결과 열 드롭다운 `합격 / 불합격 / 보류 / 결시 / 철회`(**대기 없음**), 2행~(데이터 행 수+1)행, 빈칸 불허(STOP).
- 라벨 `PENDING=대기`·`PASSED=합격`·`FAILED=불합격`·`HOLD=보류`·`ABSENT=결시`·`WITHDRAWN=철회`. 파싱은 라벨 또는 enum 이름(대소문자 무시). ⚠️ JSON은 enum 이름이고 라벨 API는 없다 → FE 맵이 글자까지 같아야 한다.
- 파일 레벨 거부 = 400 **data 없음**: 빈 파일·`.xlsx` 아님·크기·헤더 불일치(`업로드 템플릿 헤더가 올바르지 않습니다. 엑셀 템플릿 다운로드 파일을 사용하세요.`)·행 수 초과·판독 불가. 빈 행은 건너뛴다.
- 부분 판정: 파일 `대기` + DB PENDING → `UNCHANGED`. 판정된 행을 `대기`로, DB PENDING인 채 점수·코멘트만 → 오류. 빈 점수·코멘트 = null로 비움.
- 행 오류 문구: `결과는 필수입니다.` / `허용되지 않는 결과입니다: <입력> (합격/불합격/보류/결시/철회)` / `점수 형식이 올바르지 않습니다: <입력>` / `판정된 결과를 대기로 되돌릴 수 없습니다. 다른 관리자가 이미 판정했다면 템플릿을 다시 받으세요.` / `대기 상태에서는 점수·코멘트를 입력할 수 없습니다. 결과를 먼저 판정하세요.` / `코멘트는 2000자 이하여야 합니다.` / `시스템ID는 필수이며 숫자여야 합니다.` / `수험번호는 필수이며 숫자여야 합니다.` / `수험번호가 시스템ID와 일치하지 않습니다.` / `이 단계의 대상자가 아니거나 존재하지 않습니다.` / `시스템ID가 파일 내에서 중복되었습니다.` / `수식(formula) 셀은 허용되지 않습니다.` / `수정토큰은 문자열 셀이어야 합니다.` / `STALE_ROW: 다른 사용자가 변경했습니다. 현재 토큰=<토큰>`. 입력 되비춤은 50자까지.
- preview `{ stageId, totalRows, changedCount, unchangedCount, errorCount, committable, rows[] }`, 행 `{ rowNumber, stageResultId, applicationId, applicantName, status, errors[], diff }`(`diff`는 CHANGED·STALE만, 문자열·enum 이름). 저장·STALE 판정 안 함.
- commit `{ stageId, outcome, totalRows, changedCount, unchangedCount, errorCount, staleCount, failedRows[] }`: `APPLIED` 200 / `REJECTED_VALIDATION` 400 + data / `REJECTED_STALE` 409 + data / `@Version` 충돌 409 **data 없음**.
- 템플릿·preview는 단계 존재만 검사. commit은 IN_PROGRESS + actor를 변경 0건이어도 검사.

**지원자 조회(2026-09-18 🟢)**
- 본인 지원서만(없음·타인 404), `DRAFT` 400 "임시저장 지원서는 전형 결과를 조회할 수 없습니다.", `WITHDRAWN` 가능. 행 `{ stageName, stageType, stageOrder, resultStatus, resultAnnouncementDateTime, decidedAt }`(stageOrder asc).
- FE: [auth-account](auth-account.md) `ApplicantProfile` 지원 목록 "확인" 버튼(DRAFT 행엔 없음) → 모달, 비면 "발표된 전형 결과가 없습니다.".

## 규칙·불변식

**단계·공고 상태**
- 각 명령은 바로 앞 상태에서만. 시작·발표·마감은 공고 `PUBLISHED`만, 생성·수정·순서·삭제는 공고 `CLOSED`만 막는다. ({BE}/service/StageService.java — start, announce, close, validateJobPostingPublishedForCommand, validateJobPostingEditable)
- 불러오기·판정·정정은 공고 상태를 보지 않는다(단계 상태만). ({BE}/service/StageResultService.java — validateInitializable, validateEditable / {BE}/service/StageResultCorrectionService.java — validateCorrectable)
- 발표 조건: 결과 행 ≥1 + PENDING 0건. 시작은 결과 행 수를 안 본다(FE만 막음). 발표·마감 시각은 기록하지 않는다. ({BE}/service/StageService.java — validateStageResultsReadyForAnnounce)

**단계 설정**
- `stageOrder` 공고 내 유일·≥0, `finalStage=true` 최대 1개(0개 허용). ({BE}/service/StageService.java — validateStageOrderForCreate, validateFinalStageForCreate 외)
- 최종 단계가 정확히 1개가 아니면 파기 판정이 `INVALID_STAGE_CONFIGURATION` → 그 공고 지원서 전부가 파기 대상에서 빠진다([privacy-audit](privacy-audit.md)). FE 드로어는 경고·확인만. ({BE}/service/RetentionEligibilityService.java — evaluate / {FE}/views/admin/stageResult/StageConfigDrawer.vue — finalStageCount)
- 삭제는 READY만. READY 단계 결과 행은 항상 PENDING placeholder(판정은 IN_PROGRESS만)라는 전제로 함께 지운다. ({BE}/service/StageService.java — delete)
- 시작된 단계가 있으면 `reorder` 불가 → FE는 READY 행 "맨 뒤로"(max+10, `update`)만, 신규도 max+10. 드로어 저장 순서: 삭제 → 수정(최종단계를 내려놓는 행 먼저) → 생성 → 순서, 부분 실패 가능. ({FE}/views/admin/stageResult/StageConfigDrawer.vue — save, moveToEnd)

**대상자·판정·정정**
- 결과 행은 (단계, 지원서)당 1개, 같은 공고, 생성 시 PENDING. ({BE}/domain/entity/StageResult.java — 생성자)
- 불러오기 대상·정리 규칙은 상세 `initialize` 참고. 첫 단계는 행을 지우지 않으므로 불러온 뒤 철회한 지원서의 PENDING 행은 `WITHDRAWN`으로 판정해야 발표 가능. ({BE}/service/StageResultService.java — initialize, findPreviousStagePassedApplicationIds)
- 판정: IN_PROGRESS만, 결과 필수·PENDING 금지·comment ≤2000·actor 필수, decidedAt(서버 시각)·decidedBy 덮어씀. **판정(단건·bulk·업로드)은 전후값 이력을 남기지 않는다.** ({BE}/service/StageResultService.java — validateEditable, validateResultStatus / {BE}/domain/entity/StageResult.java — updateResult)
- 정정: 현재 값 `previous*`·요청 값 `new*`로 이력 append 후 교체. 이력 수정·삭제 API 없음, ActivityLog 없음. ({BE}/service/StageResultCorrectionService.java — correctResult)

**낙관적 락·업로드 충돌**
- `StageResult.version`(`@Version`)이 모든 결과 쓰기의 **동시 트랜잭션** lost update를 막는다(→ 409). ({BE}/domain/entity/StageResult.java — version / {BE}/exception/GlobalExceptionHandler.java — handleOptimisticLockingFailure)
- 기존 행이 있는 영속 DB(운영 MariaDB 등)는 수동 DDL 1회 + 재기동: `recruit_back/recruit_backend/docs/ops/phase-07d-stage-result-version-column.sql`(`ADD COLUMN version BIGINT NOT NULL DEFAULT 0`). 새 H2는 자동 생성.
- 단건·bulk·정정 요청엔 버전·토큰이 없다 → 화면 조회 후 다른 관리자가 저장했으면 409 없이 마지막 저장이 이긴다. 행 단위 stale 검출은 업로드 수정토큰뿐. ({BE}/dto/request/StageResultBulkUpdateItemRequest.java)
- commit: 1차 검증(ERROR → `REJECTED_VALIDATION`) → CHANGED 행만 id 오름차순 `PESSIMISTIC_WRITE` refresh(데드락 방지) → 2차 검증(토큰 불일치 = STALE) → ERROR/STALE 있으면 0건 적용 → CHANGED 행만 `bulkUpdateResults(…, recordAudit=false)`(우회 금지). 토큰은 템플릿과 commit이 같은 `formatToken`(마이크로초 절삭 ISO). ({BE}/service/StageResultUploadService.java — commit, validate, formatToken)

**지원자 노출**
- 단계 `RESULT_ANNOUNCED`·`CLOSED` 결과만 보인다. **발표 명령이 곧 공개 시점**(`resultAnnouncementDateTime` 도래는 안 봄). 이 상태 목록은 `findVisibleByJobApplicationIdForApplicant`와 `findVisibleByJobApplicationIdsForApplicantSummary`([application](application.md)) 두 default 메서드에 따로 있다 — 둘 다 바꾼다. ({BE}/domain/repository/StageResultRepository.java)
- 지원자 응답은 전용 DTO(점수·코멘트·판정자 비노출), 본인 지원서만, DRAFT 거부. ({BE}/service/ApplicationStageResultService.java — getApplicantStageResults)

**감사([privacy-audit](privacy-audit.md))**
- 발표 `STAGE_RESULT_ANNOUNCE`, 마감 `STAGE_RESULT_CONFIRM`, 단건·bulk 판정 `STAGE_RESULT_CORRECT`(metadata `stageId`·`changedCount`만), 업로드 commit `STAGE_RESULT_UPLOAD`(bulk 감사 끔). 성공은 in-tx, 업로드 거부·충돌은 REQUIRES_NEW. 업로드 원본 파일명 저장 금지(해시·확장자만). 템플릿 다운로드는 export 감사 실패 시 응답하지 않는다. ({BE}/service/StageService.java — recordStageAudit / {BE}/service/UploadAuditLogger.java / {BE}/controller/StageResultUploadController.java — uploadTemplate)

**화면(FE)**
- `STAGE_RESULT_STATUS_LABELS`는 백엔드 `StageResultStatusLabels`와 글자까지 같아야 한다. ({FE}/types/admin/stage.ts)
- 편집 버퍼 `pendingEdits: Map<stageResultId, { resultStatus, score, comment }>`(원본과 같아지면 삭제) → "변경사항 저장" = `bulk` 1회. PENDING 편집은 전송 안 함. 409면 재조회 + 버퍼 유지. 공고·단계 전환, 불러오기, 발표, 업로드·드로어 열기, 이탈 전에 미저장 변경을 확인 후 버린다. ({FE}/views/admin/stageResult/AdminStageResultView.vue — writeEdit, saveEdits, confirmDiscardIfDirty)
- **카운트 카드는 저장 전 판정을 반영한다**: 버퍼 `resultStatus`를 덮어쓴 `resultsWithPendingEdits`를 `StageResultCounts`에 넘기고, 그리드 필터·표시도 `effectiveStatus`(버퍼 우선)라 카드 숫자와 필터 결과가 같다. 반면 **발표 차단 사유(`pendingCount`)·발표 확인 요약은 저장된 `results` 기준**. ({FE}/views/admin/stageResult/AdminStageResultView.vue — resultsWithPendingEdits, pendingCount / {FE}/views/admin/stageResult/StageResultGrid.vue — effectiveStatus)
- 카드에 철회 없음(필터로만). 일괄 적용은 철회 제외·필터로 가려진 선택 행 제외. PENDING 옵션은 원본 PENDING 행에만. ({FE}/types/admin/stage.ts — BULK_APPLY_STATUSES)
- 버튼 가드는 백엔드 가드를 미러링, 명령 실패 시 재조회. 업로드 거부 응답 data의 `failedRows`를 표로 그린다. 모달이 제출 시점 `props.stageId`를 읽으므로 마스크를 끄지 않는다. ({FE}/views/admin/stageResult/useStageLifecycle.ts — runCommand / {FE}/views/admin/stageResult/StageUploadPreviewModal.vue — commit(props.stageId 읽기)·extractCommitPayload(failedRows 파싱))

## 변경 레시피

### 결과 그리드 열(`AdminStageResultResponse`) 추가
1. `{BE}/dto/response/AdminStageResultResponse.java`에 필드 추가. 파생값은 `{BE}/service/AdminStageResultEnricher.java`([admin-application](admin-application.md))에 배치 조회로(N+1 금지).
2. `StageResultControllerTest`·`StageResultServiceTest` 보강 → 백엔드 검증.
3. FE `{FE}/types/admin/stage.ts`·`StageResultGrid.vue`(열·`BASE_SCROLL_X`) → `npm run type-check`.
4. 카드 API 상세 갱신 → `node tools/check-docs.mjs`.

### 결과 상태 값·라벨 변경
1. `StageResultStatus` + `StageResultStatusLabels`(`LABELS`·`UPLOAD_CHOICES`, 누락 시 템플릿 생성 예외).
2. 다른 카드: `PASSED` 기준 면접 확정([interview](interview.md)), 퍼널([statistics](statistics.md)), `PENDING` 기준 파기([privacy-audit](privacy-audit.md)), 내 지원 현황([application](application.md)).
3. FE `{FE}/types/admin/stage.ts` 상수들, `StageResultCounts.vue`, `{FE}/types/application.ts`·`ApplicantProfile` 라벨([auth-account](auth-account.md)).
4. `StageResultStatusLabelsTest`·`StageResultUploadControllerTest`, `npm run type-check`, 카드 갱신, `node tools/check-docs.mjs`.

### 엑셀 업로드 템플릿 열·검증 변경
1. 함께 바꾼다: `StageResultUploadParser`(`HEADERS`·`COLUMN_COUNT`·`TOKEN_COLUMN`), `StageResultUploadService`(`TEMPLATE_SPEC`·`RESULT_COLUMN`·`decorateTemplateSheet`·`validate`), `StageResultUploadTemplateRow`·`StageResultUploadRowRequest`. 헤더가 바뀌면 이전 템플릿은 거부된다.
2. `StageResultUploadParserTest`·`StageResultUploadControllerTest`. 템플릿은 SXSSF inline string이라 테스트에서 `setCellValue`로 고치면 무시된다 → `removeCell` 후 `createCell`.
3. FE `StageUploadPreviewModal.vue`, 카드 문구 목록 갱신 → `node tools/check-docs.mjs`.

### 단계 상태 가드 변경(예: 마감 공고에서도 발표 허용)
1. `StageService`(`validateJobPostingPublishedForCommand`·`validateJobPostingEditable`·`validateStageStatus`), `StageResultService.validateEditable`·`validateInitializable`, `StageResultCorrectionService.validateCorrectable`.
2. 단계 상태를 읽는 다른 코드: 공개 쿼리 2종, `InterviewService.validatePreviousStagePassed`([interview](interview.md)), 파기 판정([privacy-audit](privacy-audit.md)).
3. FE 미러: `AdminStageResultView.vue`(`jobPostingPublished`·`announceBlockedReason`·`initializeBlockedReason`), `StageConfigDrawer.vue`(`fieldsEditable`·`deletable`·`reorderable`).
4. `StageServiceTest`·`StageControllerTest`, 카드 갱신, `node tools/check-docs.mjs`.

## 검증

백엔드(`recruit_back/recruit_backend/`에서):

```bash
# Windows PowerShell
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.Stage*" --tests "com.shinyoung.recruit.controller.ApplicationStageResultControllerTest" --tests "com.shinyoung.recruit.service.Stage*" --tests "com.shinyoung.recruit.service.ApplicationStageResultServiceTest" --no-daemon
# Linux
AES_SECRET_KEY='<로컬 예시 키>' ./gradlew test --tests "com.shinyoung.recruit.controller.Stage*" --tests "com.shinyoung.recruit.controller.ApplicationStageResultControllerTest" --tests "com.shinyoung.recruit.service.Stage*" --tests "com.shinyoung.recruit.service.ApplicationStageResultServiceTest" --no-daemon
```

- `Stage*`는 이 카드 테스트만 잡는다. 업로드 서비스는 `StageResultUploadControllerTest`가 덮는다.
- 규칙 변경 시 다른 카드 테스트: `grep -rlE "StageResultRepository|StageRepository|StageService|StageResultService" recruit_back/recruit_backend/src/test/java`.

프론트(`recruit_front/`에서, vitest spec 없음):

```bash
npm run type-check
```

문서(레포 루트에서): `node tools/check-docs.mjs`

## 함정·결정

- `102bd3c` 대상자를 불러온 READY 단계 삭제가 `StageResult` FK로 500 → PENDING 행을 함께 삭제. 판정 경로에 READY를 허용하면 판정 데이터가 지워진다.
- `140736d` 면접 FK도 같은 문제 → `DRAFT` 면접은 함께 삭제, 확정·취소 면접이 있으면 400(면접 취소 → 면접 삭제 → 단계 삭제). 평가가 붙은 면접은 못 지우므로 그 단계도 삭제 불가(의도).
- `2fa2b05` 2단계 이후 대상자를 직전 단계 합격자로 제한. "직전 단계" 정의를 `InterviewService`·`AdminStageResultEnricher`와 같게 유지한다.
- `8d7485d` 빠른 공고·단계·파일 전환 시 늦은 응답이 섞였다 → 요청 시점 id와 현재 선택을 비교해 버린다.
- `86d12c9` 카운트 카드가 저장값만 세서 그리드와 달랐다 → `resultsWithPendingEdits`. 이력 조회 경합은 `historiesRequestSeq`로.
- `recruit_back/recruit_backend/docs/adr/0002-phase07-export-readonly-upload-stageresult-only.md` — 엑셀 쓰기는 StageResult만(`bulkUpdateResults` 경유), 면접 평가 엑셀 업로드는 영구 제외.
- `recruit_back/recruit_backend/docs/adr/0006-audit-transaction-policy.md` — 성공 감사 in-tx, 거부·충돌 REQUIRES_NEW, 전후값·PII 금지.
- 마감 공고 교착(미해결): 공고 `CLOSED`는 종착인데 발표·마감은 `PUBLISHED`만 → 진행 중 단계가 판정만 되고 끝낼 수 없다(FE는 툴팁 안내만). 공고 마감 전에 단계를 발표·마감한다.
- `previousStageResultStatus`는 조회 시 계산돼, 발표된 단계 앞에 단계를 넣으면 소급해 바뀐다 → FE는 신규·이동을 맨 뒤로만.
- 결과 export(영문 12열)는 업로드 소스가 아니다. FE만 발표·마감 단계로 다운로드를 제한한다.
- 결과 목록 비페이징 전제(enricher·그리드·카운트). 페이징 도입 시 셋을 함께 바꾼다.
- 드로어가 이름을 `trim()`해 보내므로 앞뒤 공백 있는 진행 중 단계는 수정이 400.
- 운영: 메뉴 관리에 `/admin/stage-results`를 등록해야 사이드바에 뜬다 → [role-menu](role-menu.md).
