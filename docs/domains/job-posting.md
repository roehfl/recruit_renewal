# 공고·직무·근무지·이미지·첨부 요건 (`job-posting`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 관련 카드: [application-form](application-form.md) · [question](question.md) · [application](application.md) · [attachment](attachment.md) · [master-data](master-data.md) · [stage-result](stage-result.md) · [statistics](statistics.md) · [admin-application](admin-application.md) · [interview](interview.md) · [privacy-audit](privacy-audit.md) · [board](board.md) · [role-menu](role-menu.md)

## 요약

- 관리자가 채용공고(모집분야·후보 근무지·본문 이미지·첨부 요건)를 등록·수정·발행·마감하고, 지원자가 공개 공고 목록·상세를 본다.
- 상태: `DRAFT`(생성) → `PUBLISHED`(발행) → `CLOSED`(마감). 되돌리는 API 없음.
- 공고 본문은 **이미지 목록**(`JobPostingImage`)이다. `contentHtml`은 스키마 호환용으로만 남아 있고 화면은 읽지도 쓰지도 않는다.
- 공고유형은 화면상 **신입/경력 2종**(신입=`PUBLIC_RECRUITMENT`, 경력=`EXPERIENCED_RECRUITMENT`).
- 경계: 공고 질문 question, 지원서 양식 application-form, 지원 시작·기지원·학기별 성적 application, 첨부 저장 attachment, 전형 stage-result, 통계 statistics, 근무지 코드 master-data.

## 용어

| 용어 | 코드 | 설명 |
|---|---|---|
| 공고 | `JobPosting` | 제목·유형·요약·접수기간·노출기간·`visible`·`pinned`·`displayOrder`·상태 |
| 모집분야 | `JobPosition` | 공고당 1..N. `positionName`(지원서 스냅샷 원천)·`applicationType`·`jobTitle`·`employmentType`·`sortOrder` |
| 후보 근무지 | `JobPositionWorkLocation` | 모집분야당 0..N. CommonCode `WORK_LOCATION`의 `code` + 저장 시점 `displayName` 스냅샷(`name`) |
| 공고유형 | `JobPostingType` | 신입=`PUBLIC_RECRUITMENT`, 경력=`EXPERIENCED_RECRUITMENT`. `INTERN_RECRUITMENT`·`ROLLING_RECRUITMENT`는 레거시 |
| 접수 상태 | `ReceptionStatus` | 저장 안 함, 응답에서 파생: `UPCOMING`·`ACCEPTING`·`CLOSED`(접수기간 종료). `JobPostingStatus.CLOSED`(운영 마감)와 다름 |
| `accepting` | 응답 필드 | 관리자: `status==PUBLISHED && ACCEPTING`. 공개: `ACCEPTING` |
| 공개 조건 | 쿼리 조건 | `status=PUBLISHED` + `visible=true` + 노출기간 안(null은 무제한) |

## 파일 지도

### 백엔드

| 레이어 | 파일 | 역할 |
|---|---|---|
| controller | `{BE}/controller/JobPostingController.java` | 관리자 공고 CRUD·발행·마감 |
| controller | `{BE}/controller/JobPostingPublicController.java` | 공개 목록·상세·이미지 서빙 |
| controller | `{BE}/controller/JobPostingImageController.java` | 관리자 이미지 API. `toImageResponse`(서빙 헤더) 공용 |
| controller | `{BE}/controller/JobPostingAttachmentRequirementController.java` | 첨부 요건 조회·교체 |
| service | `{BE}/service/JobPostingService.java` | 관리자 공고 검증·상태 전이·모집분야 sync·근무지 검증 |
| service | `{BE}/service/JobPostingPublicService.java` | 공개 목록·상세 조립 |
| service | `{BE}/service/JobPostingImage*` | `JobPostingImageService`(검증·저장·삭제·순서·로드), `JobPostingImageStorageService`(파일 저장·root 탈출 차단) |
| service | `{BE}/service/ImageSignatureValidator.java` | 매직바이트 검증 |
| service | `{BE}/service/PostingImageResource.java` | 서빙 record |
| service | `{BE}/service/StoredPostingImageFile.java` | 저장 record |
| service | `{BE}/service/JobPostingAttachmentRequirementService.java` | 요건 조회·교체·행 검증 |
| entity | `{BE}/domain/entity/JobPosting.java` | 공고·상태 전이 |
| entity | `{BE}/domain/entity/JobPosition.java` | 모집분야 |
| entity | `{BE}/domain/entity/JobPositionWorkLocation.java` | 후보 근무지 `@Embeddable`(`job_position_work_location`) |
| entity | `{BE}/domain/entity/JobPostingImage.java` | 이미지 메타 |
| entity | `{BE}/domain/entity/JobPostingAttachmentRequirement.java` | 첨부 요건 |
| repository | `{BE}/domain/repository/JobPostingRepository.java` | 관리자 조회, 공개 목록/상세/exists(공개 조건·정렬) |
| repository | `{BE}/domain/repository/JobPositionRepository.java` | 모집분야 수·일괄 조회 |
| repository | `{BE}/domain/repository/JobPostingImageRepository.java` | 이미지 |
| repository | `{BE}/domain/repository/JobPostingAttachmentRequirementRepository.java` | 요건·정책 집계 |
| repository | `{BE}/domain/repository/JobPositionCountProjection.java` | 모집분야 수 projection |
| repository | `{BE}/domain/repository/JobPostingPublicListProjection.java` | 공개 목록 projection |
| repository | `{BE}/domain/repository/JobPostingAttachmentRequirementPolicyCount.java` | 요건 수·필수 수 |
| dto | `{BE}/dto/request/JobPostingCreateRequest.java` | 생성 |
| dto | `{BE}/dto/request/JobPostingUpdateRequest.java` | 수정(양식 필드 없음) |
| dto | `{BE}/dto/request/JobPositionRequest.java` | 모집분야(`workLocationCodes`, 수정용 `id`) |
| dto | `{BE}/dto/request/JobPostingImage*` | 이미지 메타·altText·순서 요청 |
| dto | `{BE}/dto/request/AttachmentRequirement*` | 요건 교체 래퍼·행 |
| dto | `{BE}/dto/response/JobPostingListResponse.java` | 관리자 목록 행 |
| dto | `{BE}/dto/response/JobPostingDetailResponse.java` | 관리자 상세 |
| dto | `{BE}/dto/response/JobPostingPublic*` | 공개 목록 행·상세 |
| dto | `{BE}/dto/response/JobPosition*` | 모집분야(관리자·공개) |
| dto | `{BE}/dto/response/WorkLocationResponse.java` | `{ code, name }` |
| dto | `{BE}/dto/response/JobPostingImageResponse.java` | 이미지 메타 |
| dto | `{BE}/dto/response/JobPostingAttachmentRequirementResponse.java` | 관리자 요건 |
| dto | `{BE}/dto/response/AttachmentRequirementPublicResponse.java` | 공개 요건 |
| enum | `{BE}/enumeration/JobPostingStatus.java` | 공고 상태 |
| enum | `{BE}/enumeration/JobPostingType.java` | 공고유형 |
| enum | `{BE}/enumeration/JobPositionApplicationType.java` | 지원구분 |
| enum | `{BE}/enumeration/EmploymentType.java` | 고용형태. 경력 섹션도 공유 — 값 변경 시 [application-sections](application-sections.md) 영향 |
| enum | `{BE}/enumeration/ReceptionStatus.java` | 접수 상태 파생 |
| exception | `{BE}/exception/InvalidJobPostingException.java` | 400 |
| exception | `{BE}/exception/JobPostingNotFoundException.java` | 404 |
| config | `{BE}/config/JobPostingImageProperties.java` | `recruit.posting-image.*` 바인딩 |
| test | `{BT}/controller/JobPostingControllerTest.java` | 관리자 공고 + 첨부 요건 API |
| test | `{BT}/controller/JobPostingImageControllerTest.java` | 이미지 API·draft 404 |
| test | `{BT}/controller/JobPostingPublicControllerTest.java` | 공개 API JSON·숨김 404 |
| test | `{BT}/service/JobPostingServiceTest.java` | 검증·전이·sync·근무지 |
| test | `{BT}/service/JobPostingPublicServiceTest.java` | 공개 조건·정렬·집계 |
| test | `{BT}/service/JobPostingImage*` | 이미지 서비스·저장소 |
| test | `{BT}/service/ImageSignatureValidatorTest.java` | 매직바이트 |
| test | `{BT}/service/JobPostingAttachmentRequirementServiceTest.java` | 요건 규칙 |
| test | `{BT}/support/JobPostingImageTestSupport.java` | 발행용 이미지 행 시드(실파일 없음, 타 도메인 픽스처 공용) |

### 프론트

| 구분 | 파일 | 역할 |
|---|---|---|
| route | `{FE}/routes/adminRoutes.ts` | `AdminJobPostingList`·`AdminJobPostingCreate`(`/new`)·`AdminJobPostingDetail`(`/:id`)·`AdminJobPostingEdit`(`/:id/edit`) 항목 |
| route | `{FE}/routes/applicantRoutes.ts` | `ApplicantRecruits`(`/applicant/recruits`)·`ApplicationDetail`(`/applicant/:jobPostingId/detail`) 항목, 둘 다 public |
| view | `{FE}/views/admin/jobPosting/AdminJobPostingListView.vue` | 관리자 목록 |
| view | `{FE}/views/admin/jobPosting/AdminJobPostingFormView.vue` | 등록(multipart)·수정(JSON + 이미지 diff)·근무지 후보 선택 |
| view | `{FE}/views/admin/jobPosting/AdminJobPostingDetailView.vue` | 미리보기·발행·마감 |
| view | `{FE}/views/applicant/ApplicantRecruit.vue` | 지원자 공고 목록(키워드·구분 필터) |
| view | `{FE}/views/applicant/ApplicantRecruitList.vue` | 지원자 홈 위젯(상위 4건) |
| view | `{FE}/views/applicant/ApplicationDetailView.vue` | 공개 상세·지원하기 |
| component | `{FE}/components/jobPosting/JobPostingImageStack.vue` | blob→objectURL 이미지 나열(관리자·지원자 공용) |
| api | `{FE}/api/adminJobPostingApi.ts` | 공고·이미지 함수 + `getAllJobPostings`. 같은 파일의 질문 함수는 [question](question.md) |
| api | `{FE}/api/boardApi.ts` | `fetchJobPostings`·`fetchJobPostingDetail`·`fetchJobPostingImageBlob`. 공지 함수는 [board](board.md) |
| types | `{FE}/types/jobPosting.ts` | 공개·관리자 공고 타입, `WorkLocationOption` |
| types | `{FE}/types/admin/jobPosting.ts` | `AdminJobPosition`(다른 카드 화면의 모집분야 드롭다운) |
| types | `{FE}/types/duty.ts` | 직무소개 정적 페이지 콘텐츠 타입(공고 API 무관) |

## API 계약

| 상태 | 메서드 | 경로 | 요청 요약 | 응답 요약 | 권한 |
|---|---|---|---|---|---|
| 🟢 | GET | /admin/job-postings | query `page`(0)·`size`(10, 1..100) | `PageResponse<JobPostingListResponse>` createdAt desc | `ADMIN`·`RECRUIT_ADMIN` |
| 🟢 | GET | /admin/job-postings/{id} | - | `JobPostingDetailResponse` | `ADMIN`·`RECRUIT_ADMIN` |
| 🟢 | POST | /admin/job-postings | JSON `JobPostingCreateRequest` | `Long`(id), DRAFT | `ADMIN`·`RECRUIT_ADMIN` |
| 🟢 | POST | /admin/job-postings | multipart `request`·`imageMetas?`·`imageFiles?` | `Long`(id), DRAFT | `ADMIN`·`RECRUIT_ADMIN` |
| 🟢 | POST | /admin/job-postings/{id} | JSON `JobPostingUpdateRequest` | `Long` | `ADMIN`·`RECRUIT_ADMIN` |
| 🟢 | POST | /admin/job-postings/{id}/publish | - | `Long` | `ADMIN`·`RECRUIT_ADMIN` |
| 🟢 | POST | /admin/job-postings/{id}/close | - | `Long` | `ADMIN`·`RECRUIT_ADMIN` |
| 🟢 | GET | /job-postings | query `page`(0)·`size`(10, 1..100) | `PageResponse<JobPostingPublicListResponse>` | 공개 |
| 🟢 | GET | /job-postings/{id} | - | `JobPostingPublicDetailResponse` | 공개 |
| 🟢 | GET | /job-postings/{id}/images/{imageId}/file | - | 이미지 바이너리(inline) | 공개 |
| 🟢 | POST | /admin/job-postings/{jobPostingId}/images | multipart `file` + query `altText`·`sortOrder?` | `Long`(imageId) | `ADMIN`·`RECRUIT_ADMIN` |
| 🟢 | POST | /admin/job-postings/{jobPostingId}/images/{imageId} | `{ altText }` | `Long`(imageId) | `ADMIN`·`RECRUIT_ADMIN` |
| 🟢 | POST | /admin/job-postings/{jobPostingId}/images/{imageId}/delete | - | `Long`(imageId) | `ADMIN`·`RECRUIT_ADMIN` |
| 🟢 | POST | /admin/job-postings/{jobPostingId}/images/order | `{ imageIds }` | `Long`(jobPostingId) | `ADMIN`·`RECRUIT_ADMIN` |
| 🟢 | GET | /admin/job-postings/{jobPostingId}/images/{imageId}/file | - | 이미지 바이너리(draft 포함) | `ADMIN`·`RECRUIT_ADMIN` |
| 🟢 | GET | /admin/job-postings/{jobPostingId}/attachment-requirements | - | 요건 배열 | `ADMIN`·`RECRUIT_ADMIN` |
| 🟢 | POST | /admin/job-postings/{jobPostingId}/attachment-requirements | `{ requirements }`(생략 가능) | 요건 배열 | `ADMIN`·`RECRUIT_ADMIN` |

- 바이너리 외 응답은 `ApiResponse<...>`. 검증 400(`InvalidJobPostingException`), 없음·숨김 404(`JobPostingNotFoundException`). 권한은 `{BE}/config/SecurityConfig.java`(`/api/admin/**`, 공개는 `GET /api/job-postings/**` permitAll).

### 엔드포인트 상세

**관리자 공고**
- 목록 행: `{ id, title, postingType, summary, status, visible, pinned, displayOrder, display*/reception*DateTime, receptionStatus, accepting, publishedAt, closedAt, createdAt, updatedAt, positionCount }`. 다른 카드 화면은 `getAllJobPostings()`(size 100 반복)로 전부 받는다.
- 상세: 목록 필드 + `contentHtml` + `jobPositions: [{ id, positionName, applicationType, jobTitle, workLocations: [{ code, name }], employmentType, sortOrder }]` + `applicationFormConfig`(응답만, 편집은 [application-form](application-form.md)) + `images: [{ id, altText, sortOrder, contentType, fileSize }]`(sortOrder·id asc). 다른 카드 화면도 호출.
- 생성 JSON: **FE 미사용**.
- 생성 multipart(2026-08-12 🟢): part `request`(application/json), `imageMetas`(application/json `[{ altText, sortOrder }]`), `imageFiles`(file[]). 메타·파일 개수와 index 짝 일치(불일치 400, 파일 없이 메타만 400), 메타 `sortOrder` 필수·중복 금지(FE는 index). 공고+이미지 단일 트랜잭션, 파일 전체 선검증 후 저장.
- 생성 공통(2026-09-02 🟢): `applicationFormConfig` 선택. 생략하면 기본값(전 섹션 사용, 학력·경력·병역만 필수, 첨부 미사용). 보낼 때는 `use*` boolean 8개(useAttachment 포함)를 모두 보낸다(Jackson 3 null→primitive 거부). `contentHtml` 선택(null→`""`). FE는 둘 다 보내지 않는다. `jobPositions[].id`는 무시.
- `jobPositions[]`(2026-08-31 🟢): `{ id?, positionName, applicationType?, jobTitle?, workLocationCodes: string[], employmentType?, sortOrder }`. `workLocationCodes`는 `WORK_LOCATION` code 목록이고 순서가 노출 순서다. 빈 배열 허용, 중복·blank 400, 활성 코드가 아니면 400 "등록되지 않은 근무지 코드입니다. code=…".
- 수정: `applicationFormConfig` 없음(보내도 무시). `CLOSED` 400 "마감된 공고는 수정할 수 없습니다.". FE가 `contentHtml`을 보내지 않으므로 `""`로 저장된다.
- 모집분야 제자리 수정(2026-09-18 🟢): `id` 있음=수정, 없음=추가, 빠진 기존 행=삭제. 다른 공고·없는 id 400 "공고에 없는 모집분야입니다.", 중복 id 400, 지원서가 있는 모집분야 삭제 400 "지원서가 있는 모집분야는 삭제할 수 없습니다. 모집분야=<이름>".
- 발행: `CLOSED` 400 "마감된 공고는 다시 게시할 수 없습니다.", `PUBLISHED` 400 "이미 게시된 공고입니다.". 이어서 접수기간 → 모집분야 → 레이아웃(400 "레이아웃 검증 실패: …") → 이미지(400 "공고 본문 이미지가 최소 1장 필요합니다.", 2026-09-19 🟢 — `contentHtml`만으로는 불가) 순서로 검사한다. FE는 발행 전 `GET /admin/job-postings/{id}/application-form-layout`의 `layoutStored`로 확인 문구를 바꾼다.
- 마감: `PUBLISHED`가 아니면 400 "게시 상태의 공고만 마감할 수 있습니다.".

**공개 공고**
- 목록 행: `{ id, title, postingType, summary, receptionStart/EndDateTime, receptionStatus, accepting, pinned, positions, applicationFormRequiredPolicy }`. 공개 조건만 포함한다(접수기간이 끝난 `PUBLISHED`도 노출기간 안이면 포함). 정렬: pinned desc → 접수중·예정·마감 → displayOrder asc → receptionEnd asc → publishedAt desc → id desc. page/size 위반 400(영문 메시지).
- 상세: 공개 조건 불충족 404. `{ id, title, postingType, summary, contentHtml, reception*, receptionStatus, accepting, pinned, jobPositions(관리자와 동일 모양), applicationFormConfig, applicationFormRequiredPolicy, attachmentRequirements(관리자 행에서 id 2개 제외), images }`.
- `workLocations`(2026-08-31 🟢): 저장 시점 활성 코드 스냅샷, 등록 순서. 모집분야·근무지 드롭다운은 이 응답 하나로 만든다(추가 API 없음). [application](application.md)의 지원 시작·지원분야 변경 화면도 쓴다.
- 공개 이미지: 공개 조건을 만족하는 공고만(`existsPublicById`), 아니면 404. draft 유출 차단의 2차 방어선이다. 헤더 `Cache-Control: private, max-age=3600`·`Content-Disposition: inline`·`X-Content-Type-Options: nosniff`.
- 기지원 여부 `GET /job-postings/{jobPostingId}/application`은 `ApplicationController` 소유 → [application](application.md). `ApplicationDetailView`가 `skipClientEventLog`로 호출하고 404를 "미지원"으로 처리한다.

**이미지 단위 API(수정 화면 diff용, 2026-08-12 🟢)**
- 추가: `sortOrder` 생략 시 맨 뒤. altText 수정·삭제·순서 변경·추가 모두 `CLOSED`면 400. 추가는 장수 상한 초과 시 400.
- 삭제: 행 삭제 후 파일은 best-effort로 지운다(실패는 로그만). `PUBLISHED` 공고의 마지막 1장은 400 "게시 중인 공고의 마지막 본문 이미지는 삭제할 수 없습니다."(2026-09-19 🟢).
- 순서: `imageIds`가 공고 이미지 전체와 개수·집합이 같아야 한다(빈 배열 400). index가 새 sortOrder다.
- FE 저장 순서(`AdminJobPostingFormView` save): 본문 수정 → 삭제(1건 보류) → 신규 추가 → altText → 보류분 삭제(10장 상한 직전이면 먼저) → 전체 order. 성공한 삭제는 즉시 목록에서 뺀다(재시도 404 방지).

**첨부 요건**
- 두 API 모두 **의도적 FE 미사용**. 관리자 요건 설정 화면은 **만들지 않는다** — 인사팀이 경력기술서·사진 외 첨부는 쓰지 않기로 결정했다(2026-09-20). 미구현 잔여 작업이 아니다. 요건은 공개 상세, [application](application.md) 제출 검증·완성도, [application-form](application-form.md) 레이아웃·현황판이 읽는다.
- 응답 행: `{ requirementId, jobPostingId, attachmentType, sectionType, required, minCount, sortOrder, displayName, description }`(sortOrder·id asc). 조회는 공고가 없으면 404.
- 교체: 전체 삭제 후 재생성(body 없으면 전부 삭제). 허용 밖 섹션은 400 "첨부 요구사항은 지원자가 파일을 올릴 수 있는 섹션(기본정보 BASIC_INFO, 경력 CAREER)에만 등록할 수 있습니다. sectionType=…"(2026-09-19 🟢, `ATTACHMENT` 제외는 523ab48, `BASIC_INFO`·`CAREER` 허용 목록은 300e792에서 도입). 나머지 메시지는 영문.

## 규칙·불변식

**상태·전이**
- 새 공고는 항상 `DRAFT`다. ({BE}/domain/entity/JobPosting.java — 생성자)
- 발행은 `DRAFT`에서만, 마감은 `PUBLISHED`에서만 한다. ({BE}/service/JobPostingService.java — publish, close)
- 발행 조건: 접수 종료 > 시작, 모집분야 ≥1, 레이아웃 검증 통과, 본문 이미지 ≥1장. `contentHtml`은 조건이 아니다. ({BE}/service/JobPostingService.java — publish, validateContentForPublish)
- `CLOSED` 공고는 기본정보·모집분야·이미지를 바꿀 수 없다. `PUBLISHED`는 바꿀 수 있다. ({BE}/service/JobPostingService.java — update / {BE}/service/JobPostingImageService.java — rejectClosed)
- 게시 중 공고의 마지막 이미지는 지울 수 없다. ({BE}/service/JobPostingImageService.java — rejectDeletingLastPublishedContent)
- 첨부 요건은 `DRAFT`에서만 교체한다. ({BE}/service/JobPostingAttachmentRequirementService.java — replaceRequirements)
- `hiringEndedAt`(보존 기산점)은 `closedAt`과 별개이고 [privacy-audit](privacy-audit.md)의 수동 anchor 명령으로만 설정한다. ({BE}/domain/entity/JobPosting.java — fixHiringEndedAt)

**입력 검증·기본값**
- `title` 필수, `summary` ≤500자·HTML 태그 금지, `displayOrder` ≥0, 접수 시작·종료 필수(종료 > 시작), 노출 시작·종료가 둘 다 있으면 시작 ≤ 종료. ({BE}/service/JobPostingService.java — validateRequest)
- 모집분야 ≥1, `positionName` 필수 ≤100, `jobTitle` ≤100, `sortOrder` 필수 ≥0, 요청 안 sortOrder 중복 금지. ({BE}/service/JobPostingService.java — validateJobPosition, validateJobPositionSortOrders)
- null 기본값: `postingType`→`PUBLIC_RECRUITMENT`, `visible`→true, `pinned`→false, `displayOrder`→0, `applicationType`→`NEW_GRADUATE_OR_EXPERIENCED`, `employmentType`→`FULL_TIME`. ({BE}/service/JobPostingService.java — default*)
- `contentHtml` null은 `""`로 저장한다. `ddl-auto:update`가 기존 NOT NULL을 풀지 못해서다. ({BE}/domain/entity/JobPosting.java — defaultContentHtml)

**모집분야·근무지**
- 근무지는 저장 1회당 `WORK_LOCATION` 활성 코드를 한 번 조회해 검증하고, `code`+`displayName` 스냅샷으로 저장한다. 표시명 변경·비활성화는 공고를 다시 저장해야 반영된다. 비활성 코드가 남은 공고를 다시 저장하면 400이므로 후보에서 빼야 한다. ({BE}/service/JobPostingService.java — loadWorkLocationNames, toWorkLocations)
- 후보 근무지 개수가 지원자 분기다: 0개=선택 없음(null 저장), 1개=고정(FE 자동 선택), N개=선택. 지원 시 검증은 [application](application.md). ({BE}/domain/entity/JobPosition.java — workLocations)
- 수정은 모집분야 id 기준 sync다. 전부 지우고 새로 만들면 `job_application` FK에 걸린다. `replaceJobPositions`는 생성에서만 쓴다. ({BE}/service/JobPostingService.java — syncJobPositions)

**공개 노출**
- 공개 조건은 `findPublicList`·`findPublicDetailById`·`existsPublicById` 세 쿼리에 중복돼 있으니 함께 바꾼다. ({BE}/domain/repository/JobPostingRepository.java)
- 지원자 공고 목록은 마감 공고를 보여 주지 않는다. `JobPostingStatus.CLOSED`는 서버가 빼고, `receptionStatus`가 `CLOSED`·`UPCOMING`인 공고는 프론트가 뺀다(`ACCEPTING`만 표시). ({FE}/views/applicant/ApplicantRecruit.vue — loadJobPostings / {FE}/views/applicant/ApplicantRecruitList.vue — loadJobPostings)
- 공개 상세는 접수기간이 끝나도 노출기간 안이면 열리고, "지원하기"는 `ACCEPTING`일 때만 보인다. 숨김 공고 지원 차단은 [application](application.md). ({FE}/views/applicant/ApplicationDetailView.vue — isAccepting)

**공고유형(신입/경력)**
- 선택지는 신입=`PUBLIC_RECRUITMENT`, 경력=`EXPERIENCED_RECRUITMENT` 두 개뿐이다. enum은 바꾸지 않고 기존 값을 재사용한다. ({FE}/views/admin/jobPosting/AdminJobPostingFormView.vue — postingTypeOptions)
- `INTERN_RECRUITMENT`·`ROLLING_RECRUITMENT`는 레거시다(enum에는 남음, 등록 선택지에서 제거). 관리자 목록·상세만 기존 데이터 식별용으로 '인턴채용'·'수시채용' 라벨을 유지하고, 지원자 화면은 매핑이 없으면 배지를 숨긴다. ({FE}/views/admin/jobPosting/AdminJobPostingListView.vue — postingTypeLabelMap)
- 새 화면에서 '공개채용'·'수시채용' 라벨을 되살리지 않는다. 라벨은 신입/경력이다.
- 서버는 `postingType` 값을 제한하지 않는다(API로는 레거시 값도 저장된다). ({BE}/service/JobPostingService.java — defaultPostingType)
- 학기별 성적은 신입 공고만 받는다 → [application-sections](application-sections.md), [admin-application](admin-application.md).

**이미지**
- Content-Type 허용목록(`image/jpg`는 `image/jpeg`로 정규화해 저장), 확장자 허용목록, 앞 12바이트 매직바이트로 3중 검증한다. 장당 10MB, 공고당 10장, `altText` 필수(trim, ≤200), `sortOrder` ≥0. ({BE}/service/JobPostingImageService.java — validateFile, validateAltText, validateTotalCount / {BE}/service/ImageSignatureValidator.java — matches)
- 저장 경로는 root 기준 `job-postings/{postingId}/{UUID}.{ext}`. root 밖·절대경로는 404, `storagePath`는 응답 비노출. ({BE}/service/JobPostingImageStorageService.java — resolveUnderRoot)
- 이미지 root는 첨부 root와 반드시 분리([attachment](attachment.md) 헬스스캔 충돌). 설정 `{BR}/application.yaml` `recruit.posting-image.*`(env `RECRUIT_POSTING_IMAGE_*`, 기본 `posting-images`). ({BE}/config/JobPostingImageProperties.java)
- FE는 `<img src>` 대신 blob→objectURL(관리자 이미지는 세션 쿠키 필요), 세대 카운터로 stale URL revoke. ({FE}/components/jobPosting/JobPostingImageStack.vue — loadImages)
- FE 사전 검증 상수(`MAX_IMAGES`·`MAX_IMAGE_SIZE`·`ALLOWED_IMAGE_TYPES`)는 서버 설정과 수동 동기화. ({FE}/views/admin/jobPosting/AdminJobPostingFormView.vue)

**첨부 요건**
- `attachmentType`·`sectionType`은 필수이고 두 값의 쌍은 중복될 수 없다. `sectionType`은 `acceptsApplicantAttachment()`(=`BASIC_INFO`·`CAREER`)만 허용한다(`ATTACHMENT` 제외는 523ab48, 허용 목록은 300e792에서 도입). 필수 요건 `minCount` ≥1(null→1), 선택 요건 ≥0(null→0), `displayName` 필수 trim ≤100, `description` trim ≤500(blank→null), `sortOrder` null→index(≥0). ({BE}/service/JobPostingAttachmentRequirementService.java — toRequirement, validateNoDuplicates)

## 변경 레시피

### 공고·모집분야 필드 추가
1. 엔티티(`JobPosting`/`JobPosition`)에 추가. 기존 행이 있는 NOT NULL 컬럼은 `ddl-auto:update`로 안 된다(기본값 흡수 또는 수동 DDL).
2. `JobPostingCreateRequest`·`JobPostingUpdateRequest`·`JobPositionRequest`에 추가(테스트용 축약 생성자 유지).
3. `JobPostingService` create·update·`syncJobPositions`·`validateRequest`.
4. 응답 DTO 4종. 공개 목록은 `JobPostingPublicListProjection`·`findPublicList` select도.
5. `JobPostingServiceTest`·`JobPostingControllerTest`·`JobPostingPublicServiceTest` 보강 후 백엔드 검증.
6. FE `{FE}/types/jobPosting.ts`, `AdminJobPostingFormView`(`buildSaveRequest`·`loadForEdit`), 상세 화면 → `npm run type-check`.
7. 카드 API 계약·규칙 갱신, `node tools/check-docs.mjs`.

### 공고유형 라벨·필터 변경
1. `JobPostingType` enum은 바꾸지 않는다.
2. 이 카드: `AdminJobPostingFormView`(`postingTypeOptions`), `AdminJobPostingListView`·`AdminJobPostingDetailView`(`postingTypeLabelMap`), `ApplicantRecruit`(`postingTypeMap`·`divisionOptions`), `ApplicationDetailView`(`postingTypeMap`).
3. 다른 카드의 분기도 확인한다: [application-form](application-form.md) 현황판 라벨, [application-sections](application-sections.md) 학력 섹션, [admin-application](admin-application.md) 상세·PDF의 학기별 성적.
4. `npm run type-check`, 카드 갱신, `node tools/check-docs.mjs`.

### 이미지 제한 변경
1. `{BR}/application.yaml` `recruit.posting-image.*` 또는 `JobPostingImageProperties` 기본값. 전역 multipart 한도(25MB/105MB)도 확인.
2. 새 형식이면 `ImageSignatureValidator` 시그니처 + 테스트.
3. FE 상수(`AdminJobPostingFormView`)와 타임아웃(`UPLOAD_TIMEOUT_MS`·`IMAGE_TIMEOUT_MS`) 동기화.
4. `JobPostingImage*` 테스트, 카드 갱신, `node tools/check-docs.mjs`.

### 발행 조건 추가·변경
1. `JobPostingService.publish`에 검증 추가. 게시 뒤에도 지켜야 하면 삭제·수정 쪽 가드도(`rejectDeletingLastPublishedContent` 패턴).
2. 다른 도메인 테스트 픽스처도 발행한다 → `JobPostingImageTestSupport` 같은 헬퍼로 보강하고 `grep -rl "publish(" recruit_back/recruit_backend/src/test/java` 대상도 실행.
3. FE `AdminJobPostingDetailView` 발행 모달 확인.
4. 카드 갱신, `node tools/check-docs.mjs`.

## 검증

백엔드(`recruit_back/recruit_backend/`에서):

```bash
# Windows PowerShell
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.JobPostingControllerTest" --tests "com.shinyoung.recruit.controller.JobPostingImageControllerTest" --tests "com.shinyoung.recruit.controller.JobPostingPublicControllerTest" --tests "com.shinyoung.recruit.service.JobPostingServiceTest" --tests "com.shinyoung.recruit.service.JobPostingPublicServiceTest" --tests "com.shinyoung.recruit.service.JobPostingImage*" --tests "com.shinyoung.recruit.service.ImageSignatureValidatorTest" --tests "com.shinyoung.recruit.service.JobPostingAttachmentRequirementServiceTest" --no-daemon
# Linux
AES_SECRET_KEY='<로컬 예시 키>' ./gradlew test --tests "com.shinyoung.recruit.controller.JobPostingControllerTest" --tests "com.shinyoung.recruit.controller.JobPostingImageControllerTest" --tests "com.shinyoung.recruit.controller.JobPostingPublicControllerTest" --tests "com.shinyoung.recruit.service.JobPostingServiceTest" --tests "com.shinyoung.recruit.service.JobPostingPublicServiceTest" --tests "com.shinyoung.recruit.service.JobPostingImage*" --tests "com.shinyoung.recruit.service.ImageSignatureValidatorTest" --tests "com.shinyoung.recruit.service.JobPostingAttachmentRequirementServiceTest" --no-daemon
```

- `JobPosting*Test`는 [question](question.md) 테스트도 잡는다.
- 발행 조건·`JobPostingService` 시그니처 변경은 픽스처로 쓰는 다른 도메인 테스트에도 영향 → `grep -rl "JobPostingService\|JobPostingImageTestSupport" recruit_back/recruit_backend/src/test/java` 대상도 실행.

프론트(`recruit_front/`에서). 관련 vitest spec은 없다.

```bash
npm run type-check
```

## 함정·결정

- `8d7485d` 모집분야 전체 재생성이 지원서 FK로 500 → id 기준 제자리 수정. FE는 `jobPositions[].id`를 반드시 되돌려 보낸다. 같은 커밋에서 이미지 전체 교체 시 삭제 1건 보류(마지막 이미지 400 회피).
- `523ab48` `contentHtml`만으로 발행하던 경로와 마지막 이미지 삭제 예외 제거. `contentHtml`을 발행·삭제 조건에 되살리지 않는다.
- `86e40c3` 공고 상세의 `contentHtml` `v-html` 대체 렌더(정제 없는 XSS 경로) 제거. 다시 그리지 않는다(이미지 없는 옛 공고가 없다는 전제).
- `523ab48` 폐지된 `ATTACHMENT` 섹션 필수 요건이 제출을 영구히 막아 등록을 거부했다. `BASIC_INFO`·`CAREER` 허용 목록(`acceptsApplicantAttachment()`)은 300e792에서 도입.
- `2710beb` 중복 API 모듈을 `{FE}/api/adminJobPostingApi.ts` 하나로 통합. 새 공고 API 함수는 여기에만.
- `be56561` 공고유형 신입/경력 전환(enum 유지, 레거시 라벨은 기존 데이터 표시용).
- 보안: `GET /api/job-postings/**` permitAll이라 이 접두의 새 GET은 기본 공개다. 지원자 전용 GET(`/api/job-postings/{jobPostingId}/application`)은 permitAll보다 먼저 선언.
- `recruit_back/recruit_backend/docs/adr/0003-commoncode-additive-no-enum-migration.md`: CommonCode는 추가형 lookup, enum 전환 금지(`EmploymentType`·`JobPositionApplicationType` 유지). `WORK_LOCATION` 백엔드 검증은 예외.
- `ddl-auto:update`는 컬럼을 안 지운다. 옛 `job_position.work_location`·`job_group`은 수동 DDL로 정리, `jobGroup` 부활 금지.
- 공개 목록 size 상한 100. 지원자 화면은 첫 페이지만 받아 100건 초과 시 접수중 공고가 빠질 수 있다.
- 운영: 메뉴 "공고 관리" 아래 "공고 목록"(`/admin/job-postings`)·"공고 등록"(`/admin/job-postings/new`) → [role-menu](role-menu.md).
