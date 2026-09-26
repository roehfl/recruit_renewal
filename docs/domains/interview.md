# 면접 일정·면접관·면접 평가 (`interview`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 관련 카드: [interview-supplement](interview-supplement.md) (면접 추가사항 — 조 도착시간을 입력 시간 기본값으로 씀) · [stage-result](stage-result.md) (단계·전형결과·단계 삭제) · [admin-application](admin-application.md) (면접·평가 export, 지원서 상세) · [auth-account](auth-account.md) (직원 로그인·`Employee`) · [role-menu](role-menu.md) (`ROLE_INTERVIEWER`) · [job-posting](job-posting.md) (공고) · [privacy-audit](privacy-audit.md) (감사·파기)

## 요약

- **면접(`Interview`)** = 면접 유형 전형 단계 안의 조 1개. 참가자는 지원자(`CANDIDATE`)·면접관(`INTERVIEWER`). 상태 `DRAFT` → `CONFIRMED` → `CANCELLED`.
- 만드는 길: ① 관리자 JSON 명령 API(**FE 미사용**) ② 엑셀 스케줄 업로드(단계 전체 교체 + 즉시 `CONFIRMED`). 화면은 ②만 쓴다.
- `CONFIRMED`·`CANCELLED` 면접만 본인이 배정된 지원자·면접관에게 보인다.
- **평가(`InterviewEvaluation`)**: 관리자 initialize → 지원자×면접관마다 `DRAFT` 행 → 면접관이 본인 평가만 저장·제출(`SUBMITTED`) → 관리자 reopen(`DRAFT`). 평가 화면 없음.
- **경계**: 면접·평가는 `StageResult`를 읽기만 한다. 합격 처리는 [stage-result](stage-result.md)에서 수동으로.
- 화면: `/admin/interview`(`InterviewSchedulingSetting`) — 조회·엑셀 다운로드/업로드만.

## 용어

| 용어 | 뜻 |
|---|---|
| 면접 (`Interview`) | 단계 안 면접 1건 = 조 1개. `startDateTime`(면접시각), `arrivalDateTime`(도착, 선택), `memo`는 관리자 전용. **종료시각 없음** |
| 참가자 (`InterviewParticipant`) | `role` `CANDIDATE`(지원서) / `INTERVIEWER`(임직원 `Employee`), `participantStatus` `ASSIGNED`/`CANCELLED`, `sortOrder`(지원자=면접순서) |
| `InterviewStatus` | `DRAFT`(관리자만) / `CONFIRMED`(노출) / `CANCELLED`(노출 유지, 취소 표시) |
| `InterviewMethod` | `IN_PERSON` / `ONLINE` / `HYBRID` / `OTHER` |
| 면접 유형 단계 | `StageType` `FIRST_INTERVIEW`·`SECOND_INTERVIEW`·`FINAL_INTERVIEW` |
| 직전 단계 | 같은 공고에서 `stageOrder`가 현재보다 작은 단계 중 가장 큰 것 |
| 평가 (`InterviewEvaluation`) | (면접, 지원자 참가자, 면접관 참가자) 1행. `grade`, `recommendation`, `comment`(≤2000), `status`, `submittedAt`. `jobApplication`·`stage`는 생성 시 복사 |
| 평가 enum | `EvaluationGrade` `VG` > `G_PLUS` > `G` > `G_MINUS` > `F`, `EvaluationRecommendation` `STRONG_YES`~`STRONG_NO` 5단계, `EvaluationStatus` `DRAFT`/`SUBMITTED`(취소 상태 없음) |
| 조 · 수험번호 · 면접관 표기 | 엑셀 열. 조 = `groupName`(업로드면 `"1"`,`"2"`…), 수험번호 = 지원서 id, 면접관 = `이름(로그인ID)` 쉼표 구분 |

## 파일 지도

### 백엔드

| 레이어 | 파일 | 역할 |
|---|---|---|
| controller | `{BE}/controller/InterviewAdminController.java` | 면접 조회·명령 8개 |
| controller | `{BE}/controller/InterviewEvaluationAdminController.java` | 평가 initialize·reopen·조회 5개 |
| controller | `{BE}/controller/InterviewScheduleController.java` | 스케줄 조회·export(감사)·템플릿·업로드 4개 |
| controller | `{BE}/controller/InterviewerEvaluationController.java` | 면접관 본인 평가 4개 |
| controller | `{BE}/controller/InterviewerInterviewController.java` | 면접관 본인 면접 2개 |
| controller | `{BE}/controller/ApplicantInterviewController.java` | 지원자 본인 면접 3개 |
| service | `{BE}/service/InterviewService.java` | 면접 명령·관리자 조회·확정 가드 |
| service | `{BE}/service/InterviewScheduleService.java` | 스케줄 조회·xlsx·업로드 검증·교체 |
| service | `{BE}/service/InterviewScheduleUploadParser.java` | 파일 방어·셀 정규화·`HEADERS` |
| service | `{BE}/service/InterviewEvaluationAdminService.java` | initialize·reopen(+ActivityLog)·관리자 평가 조회 |
| service | `{BE}/service/InterviewerEvaluationService.java` | 면접관 평가 조회·저장·제출 |
| service | `{BE}/service/InterviewerInterviewService.java` | 면접관 면접 조회 |
| service | `{BE}/service/ApplicantInterviewService.java` | 지원자 면접 조회 |
| service | `{BE}/service/EvaluationReopenMetadata.java` | reopen 감사 metadata(`AuditMetadata` permits) |
| entity | `{BE}/domain/entity/Interview.java` | 필드 검증·전이·참가자 cascade |
| entity | `{BE}/domain/entity/InterviewParticipant.java` | 역할별 필드·unique |
| entity | `{BE}/domain/entity/InterviewEvaluation.java` | 평가 전이·조합 unique |
| repository | `{BE}/domain/repository/InterviewRepository.java` | 관리자 검색·단계별 조회 |
| repository | `{BE}/domain/repository/InterviewParticipantRepository.java` | 노출(`findVisible*`)·충돌·스케줄 행 쿼리 |
| repository | `{BE}/domain/repository/InterviewEvaluationRepository.java` | 면접관 본인 스코프·관리자 조회·존재 검사 |
| dto | `{BE}/dto/request/Interview*.java` | 요청 8종(면접·참가자·평가 저장·스케줄 조건·업로드 행) |
| dto | `{BE}/dto/response/AdminInterview*.java` | 관리자 면접·참가자·스케줄 행·면접별 평가 |
| dto | `{BE}/dto/response/AdminEvaluation*.java` | 관리자 평가 지원자 묶음·평가 1건·요약 |
| dto | `{BE}/dto/response/AdminApplicationEvaluationResponse.java` | 지원서별 평가(면접별) |
| dto | `{BE}/dto/response/*Distribution.java` | `GradeDistribution`·`RecommendationDistribution` |
| dto | `{BE}/dto/response/InterviewScheduleUpload*.java` | 업로드 결과·행 오류 |
| dto | `{BE}/dto/response/InterviewEvaluationInitializeResponse.java` | initialize 건수 |
| dto | `{BE}/dto/response/Interviewer*.java` | 면접관 면접·평가 응답 |
| dto | `{BE}/dto/response/ApplicantInterview*.java` | 지원자 면접 요약·상세 |
| enum | `{BE}/enumeration/Interview*.java` | `InterviewStatus`·`InterviewMethod`·`InterviewParticipantRole`·`InterviewParticipantStatus` |
| enum | `{BE}/enumeration/Evaluation*.java` | `EvaluationStatus`·`EvaluationGrade`·`EvaluationRecommendation` |
| exception | `{BE}/exception/*Interview*.java` | `Invalid*` 400, `*NotFound*` 404 |
| test | `{BT}/controller/*Interview*ControllerTest.java` | API·권한·본인 것만 |
| test | `{BT}/domain/*/Interview*Test.java` | 엔티티 3·저장소 3 |
| test | `{BT}/service/*Interview*Test.java` | 서비스 6 + Stabilization 2(StageResult 불변) |

### 프론트

| 구분 | 파일 | 역할 |
|---|---|---|
| route | `{FE}/routes/adminRoutes.ts` | (공유) `InterviewSchedulingSetting` — `/admin/interview` |
| view | `{FE}/views/admin/interview/interviewScheduling.vue` | 공고·면접단계·필터 → 조회 표 → 엑셀 다운로드/업로드 |
| api | `{FE}/api/admin/adminInterviewApi.ts` | 스케줄 4함수, 엑셀 timeout 120초 |
| types | `{FE}/types/admin/interview.ts` | 스케줄 조건·행·업로드 결과 타입 |

화면이 쓰는 공유 모듈: `{FE}/api/adminJobPostingApi.ts`(`getAllJobPostings`·`getJobPosting`)·`{FE}/types/admin/jobPosting.ts`(`AdminJobPosition`)·`{FE}/types/jobPosting.ts`(`AdminJobPostingListItem`) ([job-posting](job-posting.md)), `{FE}/api/admin/adminStageApi.ts`(`getStages`)·`{FE}/types/admin/stage.ts`(`StageListItem`·`StageType`·`ApiFailurePayload`) ([stage-result](stage-result.md)), 공통 `{FE}/api/apiError.ts`(`getApiErrorMessage`)·`{FE}/common/dateUtil.ts`(`formatDate`)·`{FE}/common/fileDownload.ts`.

## API 계약

권한 열: 관리자 = `/admin/**` → `ADMIN`·`RECRUIT_ADMIN`, 면접관 = `/interviewer/**` → `EMPLOYEE`·`ADMIN`·`RECRUIT_ADMIN`·`INTERVIEWER` + 직원 세션, `APPLICANT` = `/applicant/**` (`{BE}/config/SecurityConfig.java`). 응답은 `ApiResponse<...>`(export·템플릿은 xlsx). `from`/`to`는 ISO date-time.

| 상태 | 메서드 | 경로 | 요청 요약 | 응답 요약 | 권한 |
|---|---|---|---|---|---|
| 🟢 | GET | /admin/job-postings/{jobPostingId}/interviews | query `stageId?`, `status?`, `from?`, `to?` | `AdminInterviewSummaryResponse[]` | 관리자 |
| 🟢 | GET | /admin/interviews/{interviewId} | — | `AdminInterviewDetailResponse` | 관리자 |
| 🟢 | POST | /admin/job-postings/{jobPostingId}/interviews | `{ stageId, groupName, startDateTime, arrivalDateTime?, method, locationName?, roomName?, onlineMeetingUrl?, memo? }` | `Long` id(`DRAFT`) | 관리자 |
| 🟢 | POST | /admin/interviews/{interviewId} | 생성과 같음(`stageId` 없음) | `Long` | 관리자 |
| 🟢 | POST | /admin/interviews/{interviewId}/participants | `{ candidates: [{ jobApplicationId, sortOrder? }], interviewers: [{ employeeId, sortOrder? }] }` | `Long` | 관리자 |
| 🟢 | POST | /admin/interviews/{interviewId}/confirm | — | `Long` | 관리자 |
| 🟢 | POST | /admin/interviews/{interviewId}/cancel | — | `Long` | 관리자 |
| 🟢 | POST | /admin/interviews/{interviewId}/delete | — | `Long`(물리 삭제) | 관리자 |
| 🟢 | POST | /admin/interviews/{interviewId}/evaluations/initialize | — | `{ interviewId, createdCount, alreadyExistedCount, totalCount }` | 관리자 |
| 🟢 | POST | /admin/interviews/{interviewId}/evaluations/{evaluationId}/reopen | — | 평가 1건(`status=DRAFT`) | 관리자 + 직원 세션 |
| 🟢 | GET | /admin/interviews/{interviewId}/evaluations | — | `AdminInterviewEvaluationResponse` | 관리자 |
| 🟢 | GET | /admin/stages/{stageId}/interview-evaluations | — | `AdminInterviewEvaluationResponse[]`(면접별) | 관리자 |
| 🟢 | GET | /admin/applications/{applicationId}/interview-evaluations | — | `AdminApplicationEvaluationResponse[]`(면접별) | 관리자 |
| 🟢 | GET | /admin/job-postings/{jobPostingId}/interview-schedules | query `stageId`(필수), `applicationType?`, `jobPositionId?`, `workLocation?`, `groupName?` | 스케줄 행[] | 관리자 |
| 🟢 | GET | /admin/job-postings/{jobPostingId}/interview-schedules/export | 조회와 같은 query | xlsx(0건이면 헤더만) | 관리자 + 직원 세션 |
| 🟢 | GET | /admin/job-postings/{jobPostingId}/interview-schedules/upload-template | — | xlsx(헤더만) | 관리자 |
| 🟢 | POST | /admin/job-postings/{jobPostingId}/interview-schedules/upload | query `stageId`, multipart `file`(.xlsx) | 업로드 결과; 행 오류면 400 + 같은 모양 data | 관리자 |
| 🟢 | GET | /interviewer/interviews/{interviewId}/evaluations | — | 본인 평가 목록 | 면접관 |
| 🟢 | GET | /interviewer/interviews/{interviewId}/evaluations/{evaluationId} | — | 평가 상세 | 면접관 |
| 🟢 | POST | /interviewer/interviews/{interviewId}/evaluations/{evaluationId} | `{ grade?, recommendation?, comment? }`(덮어쓰기) | 평가 상세 | 면접관 |
| 🟢 | POST | /interviewer/interviews/{interviewId}/evaluations/{evaluationId}/submit | 저장과 같은 body(선택) | 평가 상세(`SUBMITTED`) | 면접관 |
| 🟢 | GET | /interviewer/interviews | query `status?`(`DRAFT` 불가), `from?`, `to?` | 면접 요약[] | 면접관 |
| 🟢 | GET | /interviewer/interviews/{interviewId} | — | 면접 상세(+지원자) | 면접관 |
| 🟢 | GET | /applicant/interviews | query `status?`(`DRAFT` 불가), `from?`, `to?` | 면접 요약[] | APPLICANT |
| 🟢 | GET | /applicant/applications/{applicationId}/interviews | 같은 query | 면접 요약[] | APPLICANT |
| 🟢 | GET | /applicant/interviews/{interviewId} | — | 면접 상세 | APPLICANT |

### 엔드포인트 상세

- **FE 사용**: 스케줄 4개만. 나머지 22개(면접 명령 8·관리자 평가 5·면접관 6·지원자 3)는 **FE 미사용**(옛 계약 섹션 없음 → 🟢). 스케줄 4개와 "종료시각 제거"는 옛 계약 🟢 확정(2026-09-18). 면접 목록은 모두 `startDateTime`, id 순.

**응답 모양**

- 관리자 면접 요약 `{ interviewId, jobPostingId, jobPostingTitle, stageId, stageName, stageType, groupName, startDateTime, arrivalDateTime, method, locationName, roomName, onlineMeetingUrl, status, candidateCount, interviewerCount }`(카운트는 참가자 상태 무관). 상세 = 요약 − 카운트 + `memo`, `candidates[]`, `interviewers[]`, 참가자 `{ participantId, role, jobApplicationId, applicantId, applicantName, jobPositionId, jobPositionName, employeeId, employeeName, departmentName, participantStatus, sortOrder }`(다른 역할 필드 null).
- 면접관 면접 요약 = 관리자 요약 − `arrivalDateTime`·`interviewerCount` + `cancelled`. 상세 = 그 요약 − `candidateCount` + `candidates: [{ jobApplicationId, applicantId, applicantName, positionId, positionName, sortOrder }]`, `guideMessage`.
- 지원자 면접 요약 = 관리자 요약 − 카운트 + `applicationId`, `positionId`, `positionName`, `cancelled`. 상세 = 요약 + `guideMessage`. 메모·면접관·다른 지원자 없음. `guideMessage`는 취소면 `"Interview schedule has been cancelled."`, 아니면 null.
- 관리자 평가: 면접별 `{ interviewId, interviewGroupName, stageId, stageName, interviewStatus, startDateTime, candidates: [{ candidateParticipantId, applicationId, applicantName, positionId, positionName, summary, evaluations[] }] }`, 지원서별 = 같은 면접 필드 + `summary`, `evaluations[]`. `summary` = `{ submittedCount, totalEvaluatorCount, gradeDistribution: { vg, gPlus, g, gMinus, f }, recommendationDistribution: { strongYes, yes, neutral, no, strongNo } }`, 평가 1건 = `{ evaluationId, interviewerParticipantId, interviewerName, status, grade, recommendation, comment, submittedAt }`.
- 면접관 평가: 목록 `{ interviewId, interviewGroupName, interviewStatus, startDateTime, evaluations[] }`, 행 = 관리자 평가 1건 − 면접관 필드 + `candidateParticipantId, applicationId, candidateName, positionId, positionName`, 상세 = 행 + 면접 4필드.
- 오류: 400 `InvalidInterviewException`/`InvalidInterviewEvaluationException`(상태·단계·필드·가드), 404(면접·평가·공고·단계·지원서 없음, 본인 것 아님), initialize 동시 호출 unique 위반 409, 엔티티 필드 위반 500(함정).

**면접 스케줄링 (옛 계약 🟢 2026-09-18 이관)**

- 엑셀 양식(조회 표 = 템플릿 = 다운로드): 9열 고정 `일자 | 장소 | 도착시간 | 면접시간 | 면접순서 | 조 | 면접관 | 수험번호 | 성명`, 헤더 노란 음영·굵게·틀고정. 한 행 = 지원자 1명.
- 조회 행 `{ interviewId, groupName, candidateOrder, interviewDateTime, arrivalDateTime, locationName, interviewers: [{ employeeId, name, loginId }], applicationId, applicantName }`. `stageId` 없으면 400 `면접단계를 선택하세요.`
- export: 조회 결과 그대로, 수식 방어 접두(`'`) 없음(재업로드 원본). 감사 datasetType `INTERVIEW_SCHEDULES` → `EXPORT_INTERVIEWS`. FE는 `stageId`만 보낸다(필터 파일 재업로드로 다른 조가 삭제되던 결함). 백엔드 필터는 유지.
- upload 파일 거부: 400 `ApiResponse.fail(message)`, data 없음(규칙 14·15). `stageId` 누락은 400 `Invalid request.`
- upload 결과 `{ stageId, interviewCount, candidateCount, replacedInterviewCount, errors: [문자열], rowErrors: [{ rowNumber, messages[] }] }`(`rowNumber`는 헤더 = 1행). 성공 200(오류 빈 배열). 행 오류면 400 `ApiResponse.fail("업로드 검증에 실패하여 반영하지 않았습니다.", data)`에 건수 0.
- **종료시각 제거**: `endDateTime` 삭제, `arrivalDateTime` 추가. 요청·모든 면접/평가 응답에서 `endDateTime` 없음. `arrivalDateTime`은 관리자·지원자 면접 응답에만.
- 다른 카드 소유: 면접 목록 export `GET /admin/job-postings/{jobPostingId}/interviews/export`(도착 열 있음)·평가 export `GET /admin/stages/{stageId}/interview-evaluations/export` → [admin-application](admin-application.md).

## 규칙·불변식

**면접 상태·명령**

1. 전이는 `DRAFT`(생성) → `CONFIRMED`(confirm) → `CANCELLED`(cancel)뿐. 확정 해제·취소 복구 없음 (`{BE}/domain/entity/Interview.java` — `confirm`·`cancel`).
2. 생성·수정·참가자 교체·확정: 면접 유형 단계 + 단계 `READY`/`IN_PROGRESS` + (생성 외) 면접 `DRAFT`. 아니면 400 (`{BE}/service/InterviewService.java` — `validateInterviewStage`·`validateStageMutable`·`validateDraft`).
3. 필드: `groupName`·`startDateTime`·`method` 필수, `arrivalDateTime` ≤ `startDateTime`. `IN_PERSON`/`HYBRID`는 `locationName`, `ONLINE`/`HYBRID`는 `onlineMeetingUrl` 필수. trim, 빈 값 null (`{BE}/domain/entity/Interview.java` — `validate`). 길이(`groupName` ≤100 등)는 DTO `@Size` (`{BE}/dto/request/InterviewCreateRequest.java`·`{BE}/dto/request/InterviewUpdateRequest.java`).
4. 참가자 교체 = 전부 삭제 후 재생성. 두 목록 필수(빈 배열 허용), null 항목·역할 내 id 중복·`sortOrder` 중복 400. 지원서는 같은 공고(아니면 400, 없으면 404), 임직원 없으면 400. 제출·합격은 확정 때 검사 (`{BE}/service/InterviewService.java` — `replaceParticipants`).
5. 확정: `ASSIGNED` 지원자·면접관 각 ≥1. 지원자마다 `SUBMITTED` + 직전 단계 `RESULT_ANNOUNCED`/`CLOSED` + 그 결과 `PASSED`(첫 단계면 확정 불가). 다른 `CONFIRMED` 면접과 **같은 `startDateTime`**인 지원자·면접관이 있으면 400(구간 겹침 아님) (`{BE}/service/InterviewService.java` — `validateConfirmParticipants`·`validatePreviousStagePassed`).
6. 취소: `CONFIRMED` + 단계 `READY`/`IN_PROGRESS`. 상태만 바꾸고 참가자·평가·전형결과 유지 (`{BE}/service/InterviewService.java` — `cancel`).
7. 삭제(물리): `DRAFT`·`CANCELLED`만(`CONFIRMED`는 먼저 cancel — 통보 없이 사라지지 않게), 단계 `READY`/`IN_PROGRESS`, 평가 행(DRAFT 포함) 있으면 400. 참가자는 cascade (`{BE}/service/InterviewService.java` — `delete`).
8. 관리자 조회는 모든 상태. `from ≤ startDateTime < to`, `to ≤ from` 400 (`{BE}/domain/repository/InterviewRepository.java` — `searchAdminInterviews`).
9. 면접 명령·스케줄 업로드는 감사 로그 없음(감사는 reopen·export뿐).

**참가자 역할**

10. `CANDIDATE`는 `jobApplication`만, `INTERVIEWER`는 `employee`만. unique `(interview_id, role, job_application_id)`·`(interview_id, role, employee_id)`. 생성 시 `ASSIGNED` (`{BE}/domain/entity/InterviewParticipant.java` — `validate`).
11. 참가자를 `CANCELLED`로 바꾸는 운영 경로는 없다(`cancel()`은 테스트만). 가드는 `ASSIGNED`만 센다 (`{BE}/domain/repository/InterviewParticipantRepository.java`).

**스케줄 엑셀**

12. 헤더 원천은 파서 `HEADERS` 하나(표·템플릿·다운로드·업로드 대조 공용). 다운로드는 원값 (`{BE}/service/InterviewScheduleService.java` — `SPEC`·`exportSchedules`).
13. 조회 대상 = 단계의 `CANCELLED` 아닌 면접의 `ASSIGNED` 지원자. 필터는 정확히 일치(`applicationType`은 모집분야 유형 — `NEW_GRADUATE_OR_EXPERIENCED` ≠ `EXPERIENCED`, `workLocation`은 지원서 `workLocationCode`). 정렬 조(숫자 아니면 뒤) → 면접순서 → 수험번호 (`{BE}/service/InterviewScheduleService.java` — `matches`·`ROW_ORDER`).
14. 파일 방어: `.xlsx`만, 크기 `recruit.upload.max-file-size`(5MB)·행 `recruit.upload.max-rows`(10000) 한도, 첫 시트, 헤더 정확 대조, 빈 행 건너뜀. 날짜 서식 셀은 `yyyy-MM-dd`/`HH:mm`(분 반올림)로 정규화, 수식 셀은 행 오류 (`{BE}/service/InterviewScheduleUploadParser.java` — `parse`·`numericToString`). 0행 거부는 (`{BE}/service/InterviewScheduleService.java` — `upload`).
15. 단계 거부(파싱 전): 면접 유형 아님, `READY`/`IN_PROGRESS` 아님, **단계에 평가 행 존재**, 직전 단계 없음·미발표 (`{BE}/service/InterviewScheduleService.java` — `validateUploadableStage`·`findPreviousStagePassedApplicationIds`).
16. 행 오류: 모든 칸 필수, 일자 `yyyy-MM-dd`, 시각 `H:mm[:ss]`, 도착 ≤ 면접시간, 장소 ≤200, 면접순서·조 1 이상 정수. 면접관은 `loginId`로 `Employee` 조회·이름 일치·칸 안 중복 불가. 수험번호는 파일 내 유일·이 공고·`SUBMITTED`·직전 단계 `PASSED`. 성명 = `applicantNameSnapshot`(없으면 계정 이름) (`{BE}/service/InterviewScheduleService.java` — `parseRows`·`parseApplication`).
17. 조 검증: 같은 조는 첫 행과 일자·장소·도착·면접시간·면접관(집합) 동일, 면접순서 중복 불가(행 오류). `errors`: 조·조별 면접순서가 1..n 연속, 같은 면접관이 같은 일시에 두 조. 다른 단계·공고 `CONFIRMED` 면접과 같은 시각(면접관 → `errors`, 지원자 → 행 오류)은 **다른 오류가 없을 때만** 검사 (`{BE}/service/InterviewScheduleService.java` — `validateGroupConsistency`·`validateConfirmedCollisions`).
18. all-or-nothing. 통과하면 단계 기존 면접을 **상태 무관 전부** 삭제 후 조마다 생성: `groupName`=조, `startDateTime`=일자+면접시간, `arrivalDateTime`=일자+도착시간, `IN_PERSON`, `locationName`=장소, 지원자 `sortOrder`=면접순서, 면접관 `sortOrder`=칸 안 순서 → 즉시 `CONFIRMED` (`{BE}/service/InterviewScheduleService.java` — `replaceSchedules`).
19. export 감사는 fail-close: 기록 실패 시 temp xlsx 삭제 후 예외 전파 (`{BE}/controller/InterviewScheduleController.java` — `export`).

**평가**

20. initialize: 면접 `CONFIRMED`만. `ASSIGNED` 지원자 × 면접관 조합마다 `DRAFT` 행, 있으면 건너뜀(멱등). 자동 생성 없음 (`{BE}/service/InterviewEvaluationAdminService.java` — `initialize`).
21. 저장·제출 가드: 면접 `CONFIRMED`, 두 참가자 `ASSIGNED`, 평가 `DRAFT`. `comment` ≤2000. 저장은 세 필드를 **덮어씀**(null이면 지움). 제출은 body 있으면 먼저 저장, `grade`·`recommendation` 필수, `submittedAt`=`Clock` 현재 (`{BE}/service/InterviewerEvaluationService.java` — `validateWritable`·`submit`).
22. reopen: `SUBMITTED` + 면접 `CONFIRMED` + 두 참가자 `ASSIGNED`(아니면 재제출 불가). → `DRAFT`, `submittedAt=null`. 평가는 `interviewId`로 스코프(다르면 404). 같은 tx에서 ActivityLog `EVALUATION_REOPEN` + `EvaluationReopenMetadata` 기록, 감사 실패 시 롤백 (`{BE}/service/InterviewEvaluationAdminService.java` — `reopen`).
23. 평가 독립성: 면접관은 본인 평가만 본다(타인 평가·집계 없음, 타인 평가 id 404). 면접관 이름은 관리자 응답에만 (`{BE}/service/InterviewerEvaluationService.java` — `findOwnedEvaluation`).
24. 요약 분포는 `SUBMITTED`만, `totalEvaluatorCount`는 DRAFT 포함. 평가 행 없는 지원자는 안 나옴 (`{BE}/dto/response/AdminEvaluationSummaryResponse.java` — `from`).
25. 평가는 지우지 않는다: 삭제 API 없음, 평가가 있으면 면접 삭제(규칙 7)·스케줄 교체(규칙 15) 불가. 파기는 `comment`만 null ([privacy-audit](privacy-audit.md) `{BE}/domain/repository/ApplicationPiiPurgeRepository.java` — `purgeEvaluationComments`).

**전형결과 경계**

26. 면접·스케줄은 `StageResult`를 **읽기만**(직전 합격 판정), 평가 서비스는 주입조차 않는다. 불변은 테스트가 고정 (`{BE}/service/InterviewService.java` — `validatePreviousStagePassed`; `{BT}/service/InterviewEvaluationStabilizationTest.java`).

**접근 제한**

27. `/interviewer/**`: 직원 세션 아니면 403, `Employee` 행 없으면 400 `Employee user was not found.` 본인이 `ASSIGNED` 면접관인 `CONFIRMED`/`CANCELLED` 면접만, 아니면 404(존재 은닉). `status=DRAFT` 400 (`{BE}/service/InterviewerInterviewService.java`; 직원 확인은 `CurrentEmployeeService`).
28. `/applicant/**`: 본인 지원서가 `ASSIGNED`인 `CONFIRMED`/`CANCELLED` 면접만, `WITHDRAWN` 지원서 제외. 지원서별 목록은 남의 지원서 404·철회 400. 상세는 없음·남의 것·`DRAFT` 모두 404 (`{BE}/service/ApplicantInterviewService.java`).
29. reopen·스케줄 export는 actor(직원 로그인 ID)가 필요해 직원 세션이 아니면 403 (`{BE}/service/CurrentEmployeeService.java` — `getCurrentEmployeeActor`).

**프론트 (`interviewScheduling.vue`)**

30. 공고 전환 경합: `changeJobPosting`은 `jobPostingRequestSeq`로 최신 요청 응답만 반영하고(늦은 이전 공고 응답·오류 무시) 조건·표를 초기화. 조회·업로드·다운로드 중엔 `loading` 스핀이 조작을 막는다.
31. 기본 공고 = 첫 `accepting` 공고, 기본 단계 = `IN_PROGRESS` → `READY` → 첫 면접 단계. `INTERVIEW_STAGE_TYPES`는 백엔드와 중복 정의.
32. 단계를 바꾸면 표를 비운다(업로드는 현재 선택 단계로 감) (`changeStage`).
33. 다운로드: 검색했으면 `{ stageId }`만으로 export, 아니면 템플릿 (`downloadExcel`).
34. 업로드: `accept=".xlsx"`, 교체·즉시 공개 확인 모달 후 전송. 400 `data`가 있으면 오류 모달(50줄까지), 없으면 메시지. 성공 시 재조회 (`beforeUpload`·`uploadExcel`). 검색 전이거나 면접 단계가 없으면 업로드 버튼을 막는다.
35. 조회 표는 페이지 없이 전체를 보이고, 일자·조·수험번호·성명 열로 정렬한다(화면 안 정렬, `columns`의 `sorter`).

## 변경 레시피

### 면접(스케줄) 필드 추가 — 예: 호실을 엑셀 열로

1. `{BE}/domain/entity/Interview.java`(필드·`createDraft`·`updateDraft`·`validate`). 컬럼 삭제·NOT NULL 변경은 `ddl-auto`가 못 하므로 SQL을 `recruit_back/recruit_backend/docs/ops/`에.
2. JSON API면 `InterviewCreateRequest`/`InterviewUpdateRequest`·`InterviewService`(지원자·면접관 응답에 `memo` 금지).
3. 엑셀 열: 파서(`HEADERS`·`COLUMN_COUNT`·`toRow`) → `InterviewScheduleUploadRowRequest` → `InterviewScheduleService`(`SPEC`·`COLUMN_WIDTHS`·`parseRows`·`validateGroupConsistency`·`replaceSchedules`) → `AdminInterviewScheduleRowResponse`. 헤더를 바꾸면 예전 파일은 거부된다.
4. 면접 목록 export 열: `{BE}/service/AdminDatasetExportService.java`([admin-application](admin-application.md)).
5. 테스트 `InterviewTest`·`InterviewServiceTest`·`InterviewScheduleServiceTest`·`InterviewScheduleControllerTest` → 프론트 `{FE}/types/admin/interview.ts`·`interviewScheduling.vue` → 카드 → `node tools/check-docs.mjs`.

### 스케줄 업로드 검증 변경

1. 파일 방어는 파서, 단계·행·조·충돌은 `InterviewScheduleService`(규칙 14~18). 행 오류 `row.messages`, 파일 단위 `errors`, 전체 거부 `InvalidInterviewScheduleUploadException`.
2. 직전 합격·같은 시각 충돌은 `InterviewService.validateConfirmParticipants`와 같은 기준 — 둘 다 바꾼다.
3. 테스트 `InterviewScheduleServiceTest`·`InterviewScheduleControllerTest`(+`InterviewServiceTest`) → 화면 안내 문구 → 카드 규칙 12~18 → `node tools/check-docs.mjs`.

### 평가 항목 변경 (등급·추천 값, 새 필드)

1. enum 값: `EvaluationGrade`/`EvaluationRecommendation` + `GradeDistribution`/`RecommendationDistribution`. DB에 enum 이름으로 저장 — 이름 변경은 데이터 이전 필요. CommonCode로 옮기지 않는다(`recruit_back/recruit_backend/docs/adr/0003-commoncode-additive-no-enum-migration.md`).
2. 새 필드: `InterviewEvaluation`(`updateContent`·`submit`) → `InterviewEvaluationSaveRequest` → `InterviewerEvaluationService` → 평가 응답 DTO → export 행 `{BE}/dto/response/InterviewEvaluationExportRow.java`([admin-application](admin-application.md)). 자유서술이면 파기 쿼리도([privacy-audit](privacy-audit.md)).
3. 테스트 `InterviewEvaluationTest`·`InterviewerEvaluationServiceTest`·`InterviewEvaluationAdminServiceTest`·평가 컨트롤러 2개·`AdminDatasetExportControllerTest` → 카드 갱신 → `node tools/check-docs.mjs`.

### 면접 상태 전이·노출 규칙 변경

1. `Interview.java` 전이, `InterviewService` `validate*`.
2. 노출: `InterviewParticipantRepository` `findVisible*` + 세 조회 서비스의 `VISIBLE_STATUSES`.
3. 평가 가드(`validateWritable`·reopen), 스케줄 교체, 단계 삭제 가드 `{BE}/service/StageService.java`([stage-result](stage-result.md)).
4. 테스트: 검증 절 전체 + `StageServiceTest` → 카드 규칙 1~11·27~28 → `node tools/check-docs.mjs`.

## 검증

백엔드(`recruit_back/recruit_backend/`에서). `*Interview*`는 이 카드 테스트 20개와 [interview-supplement](interview-supplement.md) 테스트 4개(`*InterviewSupplement*`)를 함께 잡는다. 파일 지도의 `Interview*`·`*Interview*` 묶음에서 `InterviewSupplement*` 파일은 그 카드 소유다.

```powershell
# Windows PowerShell
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.*Interview*" --no-daemon
```

```bash
# Linux
AES_SECRET_KEY='<로컬 예시 키>' ./gradlew test --tests "com.shinyoung.recruit.*Interview*" --no-daemon
```

엔티티·저장소·`InterviewService` 변경 시 다른 카드 영향 테스트: `StageServiceTest`, `AdminDatasetExportControllerTest`, `ApplicationPiiPurgeServiceTest`, `AuditMetadataContractTest`, `CurrentEmployeeServiceTest`.

프론트(`recruit_front/`에서). 이 도메인 vitest spec 없음.

```bash
npm run type-check
```

문서(레포 루트): `node tools/check-docs.mjs`

## 함정·결정

- `9baaeef` 결정: 종료시각 제거 — 되살리지 않는다. 운영 DB의 `interview.end_date_time`(NOT NULL)·인덱스 `idx_interview_time_range`는 수동 삭제 대상이며 SQL은 `recruit_back/recruit_backend/docs/ops/`에 없다.
- `86d12c9` 화면: 공고 전환 경합 가드(규칙 30), 업로드 `.xlsx`만, '전체' 옵션 중복 제거.
- `8d7485d` 다운로드를 단계 전체로(필터 파일 재업로드 시 다른 조 삭제 결함). export에 필터를 다시 넣지 않는다.
- `b52b7ee` 결정: 면접 삭제 명령 — 확정·취소 면접이 붙은 `READY` 단계를 못 지우던 문제 해소(cancel → delete → 단계 삭제, [stage-result](stage-result.md)).
- 엔티티 필드 검증(도착 > 면접시각, 방식별 장소·URL 누락)은 `IllegalArgumentException`인데 `{BE}/exception/GlobalExceptionHandler.java`에 핸들러가 없어 **500**. JSON 면접 화면을 만들면 서비스에서 먼저 400으로 검사할 것.
- 업로드는 단계의 `CANCELLED` 면접·JSON API로 만든 면접까지 지운다 — 지원자에게 보이던 "취소됨" 일정도 사라진다.
- 평가 화면이 없어 평가 행이 생기지 않는다. initialize를 쓰면 그 단계는 스케줄 재업로드가 막힌다(규칙 15).
- 면접관은 `Employee` 행이 필요하다(로그인해야 생김, [auth-account](auth-account.md)). 로그인 전 임직원은 업로드 오류.
- 미사용(삭제는 승인 후): `InterviewParticipant.cancel()`, 테스트만 쓰는 저장소 메서드 9개(`InterviewParticipantRepository` 5·`InterviewRepository.findByJobPostingId*` 3·`InterviewEvaluationRepository.findByInterviewId`), FE 타입 `InterviewCreateRequest`·`method`.
- `recruit_back/recruit_backend/docs/adr/0002-phase07-export-readonly-upload-stageresult-only.md` — 평가 엑셀 적재·수정은 영구 제외. 스케줄 업로드는 `Interview`만 쓴다.
- `recruit_back/recruit_backend/docs/adr/0006-audit-transaction-policy.md` — reopen 성공 감사는 비즈니스 tx 안, export는 fail-close. ADR의 "reopen 실패 `REQUIRES_NEW` 기록"은 코드에 없다.
