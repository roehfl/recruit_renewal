# 첨부 저장소 (`attachment`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 관련 카드: [application-sections](application-sections.md)(첨부를 올리는 섹션 화면) · [application](application.md)(제출 검증·완성도) · [job-posting](job-posting.md)(공고별 첨부 요건) · [application-form](application-form.md)(`ApplicationSectionType`·`useAttachment`) · [admin-application](admin-application.md)(관리자 첨부 목록·경력기술서 URL·PDF 사진) · [privacy-audit](privacy-audit.md)(파기 saga·감사 로그) · [auth-account](auth-account.md)(`SecurityConfig`·CORS)

## 요약

- 첨부 **바이너리는 로컬 파일시스템**, **메타데이터·상태는 DB `application_attachment`**(`ApplicationAttachment`, 상태 `PhysicalFileStatus`). 저장소 구현은 `LocalAttachmentStorageService` 하나.
- 지원자: 목록·업로드·다운로드·삭제. 관리자: 다운로드(감사 fail-close)·사유 필수 삭제·저장소 점검(dry-run).
- **소유 화면 없음.** FE 사용처는 [application-sections](application-sections.md) 소유 섹션 두 곳이다.
  - 증명사진 = `BASIC_INFO` + `ETC` — `{FE}/views/applicant/application/sections/BasicInfoSection.vue`
  - 경력기술서 = `CAREER` + `CAREER_DESCRIPTION` — `{FE}/views/applicant/application/sections/CareerSection.vue`
- 독립 첨부 섹션(`ATTACHMENT`)은 2026-09-01 **프론트에서 폐지(⛔)**. 백엔드 엔드포인트·enum·요건 테이블은 유지.
- 삭제는 DB soft delete 먼저, 물리 파일 삭제는 **커밋 후**. 물리 삭제 실패는 로그만 남고 저장소 점검이 잔존 파일을 찾는다.

## 용어

| 용어 | 뜻 |
|---|---|
| 저장소 루트 | `recruit.attachment.storage-root`(env `RECRUIT_ATTACHMENT_STORAGE_ROOT`, 기본 `attachments` — 상대 경로면 실행 디렉터리 기준) |
| storage key(`storagePath`) | 루트 기준 `applications/{applicationId}/{yyyy}/{MM}/{dd}/{UUID}.{ext}`. 물리 파일명 `storedFileName` = `{UUID}.{ext}`. 둘 다 응답·로그 노출 금지 |
| `originalFileName` | 정제된 원본 파일명(표시·다운로드용). 파기 시 `__PURGED__` |
| `PhysicalFileStatus` | `METADATA_ONLY`(파일 없음) · `STORED` · `MISSING`(파일 없음 표시 — 쓰는 코드 없음) · `DELETED`(legacy) · `SOFT_DELETED` · `BINARY_DELETE_PENDING` · `BINARY_DELETED` · `BINARY_DELETE_FAILED`(뒤 3개 파기 saga 전용) |
| 활성 첨부 | `HIDDEN_FROM_LISTING`(soft-deleted 2종 + `BINARY_DELETE_*` 3종) 밖 = `METADATA_ONLY`·`STORED`·`MISSING` |
| 작성 가능 | 지원서 `WITHDRAWN` 아님 + 공고 `PUBLISHED` + 접수기간 안. `SUBMITTED`도 접수기간이면 가능 |
| managed key | 점검 관리 대상 key 형식: 정확히 6세그먼트 `applications`/양수 id/4자리 연/01~12/01~31/파일명. 응답에는 SHA-256(`fileKeyHash`)만 |

## 파일 지도

### 백엔드

| 레이어 | 파일 | 역할 |
|---|---|---|
| controller | `{BE}/controller/ApplicationAttachmentController.java` | 지원자 5종, 업로드 금지 파라미터 검사 |
| controller | `{BE}/controller/AdminApplicationAttachmentController.java` | 관리자 다운로드(감사)·삭제 |
| controller | `{BE}/controller/AdminAttachmentStorageHealthController.java` | 저장소 점검 |
| controller | `{BE}/controller/AttachmentDownloadResponseFactory.java` | 다운로드 헤더 조립(지원자·관리자 공용) |
| service | `{BE}/service/ApplicationAttachmentService.java` | 목록, ⛔ 메타데이터 교체 |
| service | `{BE}/service/ApplicationAttachmentFileService.java` | 업로드·한도·롤백 시 파일 정리 |
| service | `{BE}/service/ApplicationAttachmentDeleteService.java` | soft delete + 커밋 후 물리 삭제, 관리자 삭제 감사 |
| service | `{BE}/service/ApplicationAttachmentDownloadService.java` | `STORED` 행 → 파일 로드 |
| service | `{BE}/service/AttachmentFilePolicy.java` | 크기·파일명·확장자·content type 검증 |
| service | `{BE}/service/AttachmentStorageService.java` | 저장소 인터페이스 |
| service | `{BE}/service/LocalAttachmentStorageService.java` | 로컬 구현, 루트 밖 경로 차단 |
| service | `{BE}/service/AttachmentStorageHealthScanService.java` | 파일↔행 대조 점검 |
| service | `{BE}/service/StoredAttachmentFile.java` | 저장 결과 |
| service | `{BE}/service/AttachmentStorageResource.java` | 로드 결과 |
| service | `{BE}/service/AttachmentStorageDeleteResult.java` | 물리 삭제 결과 |
| service | `{BE}/service/AttachmentDownloadResource.java` | 다운로드 재료 |
| service | `{BE}/service/AttachmentAdminMetadata.java` | 삭제 감사 metadata |
| entity | `{BE}/domain/entity/ApplicationAttachment.java` | 첨부 행 |
| repository | `{BE}/domain/repository/ApplicationAttachmentRepository.java` | 상태별 조회·한도 집계 |
| dto | `{BE}/dto/request/AttachmentRequest.java` | ⛔ 교체 행 |
| dto | `{BE}/dto/request/AttachmentReplaceRequest.java` | ⛔ 교체 래퍼 |
| dto | `{BE}/dto/request/AttachmentAdminDeleteRequest.java` | `{ reason }` |
| dto | `{BE}/dto/response/AttachmentResponse.java` | 메타데이터 |
| dto | `{BE}/dto/response/AttachmentDeleteResponse.java` | 삭제 결과 |
| dto | `{BE}/dto/response/AttachmentStorageHealthScanResponse.java` | 점검 요약 |
| dto | `{BE}/dto/response/AttachmentStorageHealthIssueResponse.java` | 점검 이슈 |
| enum | `{BE}/enumeration/AttachmentType.java` | 첨부 종류 |
| enum | `{BE}/enumeration/PhysicalFileStatus.java` | 파일 상태·집합 상수 |
| enum | `{BE}/enumeration/AttachmentDeleteActorType.java` | 삭제 주체 |
| enum | `{BE}/enumeration/AttachmentStorageHealthIssueType.java` | 점검 이슈 유형 |
| exception | `{BE}/exception/StorageHealthScanException.java` | 점검 실패 500 |
| config | `{BE}/config/AttachmentProperties.java` | `recruit.attachment.*` |
| test | `{BT}/controller/ApplicationAttachmentControllerTest.java` | 목록·교체·업로드 API |
| test | `{BT}/controller/ApplicationAttachmentDownloadControllerTest.java` | 다운로드 헤더, 삭제, 권한 |
| test | `{BT}/controller/AdminAttachmentStorageHealthControllerTest.java` | 점검 API |
| test | `{BT}/service/ApplicationAttachmentServiceTest.java` | 교체 |
| test | `{BT}/service/ApplicationAttachmentFileServiceTest.java` | 업로드·한도·정책 |
| test | `{BT}/service/ApplicationAttachmentDeleteServiceTest.java` | 삭제 |
| test | `{BT}/service/ApplicationAttachmentDownloadServiceTest.java` | 다운로드 |
| test | `{BT}/service/AttachmentStorageHealthScanServiceTest.java` | 점검 |
| test | `{BT}/service/LocalAttachmentStorageServiceTest.java` | 경로 차단·삭제 결과 |

### 프론트

| 구분 | 파일 | 역할 |
|---|---|---|
| api | `{FE}/api/application/sections/attachmentApi.ts` | 목록·업로드·삭제·blob 다운로드, 파일 타임아웃 120초 |
| types | `{FE}/types/application/sections/attachment.ts` | `AttachmentDeleteResponse` + 재수출 |
| types | `{FE}/types/application/sections/basicInfo.ts` | `attachmentType`·`sectionType` 유니언, `AttachmentFileRequest`, `AttachmentResponse` 원본(공유 — [application-sections](application-sections.md)) |

## API 계약

JSON 응답은 `ApiResponse<T>`. 다운로드 성공은 바이너리, 실패는 JSON. 권한 "지원자" = `/api/applications/**` `ROLE_APPLICANT` + `CurrentApplicantService`(세션 없음 401·지원자 아님 403) + 본인 지원서(아니면 404). "관리자" = `/api/admin/**` `ROLE_ADMIN`·`ROLE_RECRUIT_ADMIN`.

| 상태 | 메서드 | 경로 | 요청 요약 | 응답 요약 | 권한 |
|---|---|---|---|---|---|
| 🟢 | GET | /applications/{applicationId}/attachments | 없음 | `AttachmentResponse[]` 활성 행 전부(sortOrder·id asc) | 지원자 |
| ⛔ | POST | /applications/{applicationId}/attachments | `{ attachments: [{ attachmentType, sectionType, sectionRecordId?, originalFileName, contentType, fileSize, sortOrder }] }` | `AttachmentResponse[]` | 지원자(작성 가능) |
| 🟢 | POST | /applications/{applicationId}/attachments/files | multipart `file` + query `attachmentType`, `sectionType`, `sectionRecordId?` | `AttachmentResponse` | 지원자(작성 가능) |
| 🟢 | GET | /applications/{applicationId}/attachments/{attachmentId}/download | 없음 | 바이너리 | 지원자 |
| 🟢 | POST | /applications/{applicationId}/attachments/{attachmentId}/delete | 없음 | `AttachmentDeleteResponse` | 지원자(작성 가능) |
| 🟢 | GET | /admin/applications/{applicationId}/attachments/{attachmentId}/download | 없음 | 바이너리 | 관리자(임직원 세션) |
| 🟢 | POST | /admin/applications/{applicationId}/attachments/{attachmentId}/delete | `{ reason }` | `AttachmentDeleteResponse` | 관리자(임직원 세션) |
| 🟢 | POST | /admin/attachments/storage-health/scan | 없음 | `AttachmentStorageHealthScanResponse` | 관리자 |

### 엔드포인트 상세

**공통**
- `AttachmentResponse`: `{ attachmentId, attachmentType, sectionType, sectionRecordId, originalFileName, contentType, fileSize, sortOrder }`. `storedFileName`·`storagePath`·`physicalFileStatus`·URL은 어떤 응답에도 넣지 않는다(테스트가 부재 검사).
- `AttachmentDeleteResponse`: `{ applicationId, attachmentId, deleted(항상 true), physicalDeleteRequested, message }`. `physicalDeleteRequested` = 삭제 직전 `STORED`였음(실제 삭제 성공 여부 아님).
- 오류: 400 `InvalidJobApplicationException`(영문 메시지), 404 `JobApplicationNotFoundException`("Application was not found." / "Attachment was not found." / "Attachment file was not found.").
- 매핑: FE `attachmentApi.getApplicationAttachments()`/`postApplicationAttachmentsFile()`/`downloadApplicationAttachment()`/`deleteApplicationAttachments()` ↔ `ApplicationAttachmentController.getAttachments()`/`uploadAttachmentFile()`/`downloadAttachmentFile()`/`deleteAttachment()`. 관리자 다운로드는 `{FE}/api/admin/adminApplicationApi.ts`의 `downloadApplicationAttachment()`([admin-application](admin-application.md)).

**⛔ 독립 첨부 섹션 폐지 (2026-09-01, 프론트 전용 / 백엔드 무변경)**
- `AttachmentSection.vue` 삭제. `{FE}/views/applicant/ApplicationFormView.vue`가 `ATTACHMENT` 항목과 그 때문에 빈 페이지를 걸러낸다.
- 관리자 공고 화면의 "첨부파일" 체크박스 제거, 신규 공고는 `useAttachment=false` 전송. 기존 `useAttachment=true` 공고는 수정 저장해도 값이 유지돼 백엔드 레이아웃에 ATTACHMENT 페이지가 남는다(FE가 숨김 — [application-form](application-form.md)).
- 유지: `ApplicationSectionType.ATTACHMENT`, `useAttachment`, `JobPostingAttachmentRequirement`, 첨부 엔드포인트 전부. 대체: 경력기술서 → 경력 섹션(경력 1건 이상일 때만, `sectionRecordId` 미전송, 경력 0건이면 첨부도 삭제), 증명사진 → 기본정보.
- 폐지 전 계약 중 유효: `AttachmentType`에 `CAREER_DESCRIPTION`·`EMPLOYMENT_CERTIFICATE` 추가 🟢, `CAREER_CERTIFICATE`·`RESUME`·`TRANSCRIPT`는 데이터 보존용 유지. 라벨은 FE 상수 맵이 출처(`ATTACHMENT_TYPE` 공통코드 없음, 드롭다운·라벨 맵은 섹션과 함께 삭제됨). 노출 규칙 `enabled = useAttachment || 요건 행 존재` 무변경, `requireAttachment` 컬럼 금지(요건 행이 필수의 단일 출처).
- 🟢(2026-09-19) 요건은 업로드 경로가 있는 섹션(`BASIC_INFO`·`CAREER`)에만 — 300e792에서 도입. 규칙 절 참고.

**GET 목록 — 🟢**
- 필터가 없어 활성 행이 전부 온다. FE는 (sectionType, attachmentType) 쌍으로 거른다(사진은 마지막 행). 안 거르고 삭제하면 다른 섹션 파일이 지워진다. 철회·마감 지원서도 조회된다.

**POST 업로드 — 🟢**
- part `file` 없으면 400 "Attachment file is required.". `attachmentType`·`sectionType` 누락·enum 밖 값은 400.
- `sortOrder`·`displayName`·`originalFileName`·`storedFileName`·`storagePath`를 파라미터나 part 이름으로 보내면 400 "`<이름>` cannot be supplied by client.". FE가 query로 함께 보내는 `applicationId`는 무시된다.
- 같은 (attachmentType, sectionType) 중복 허용(unique 없음). FE는 "새 파일 업로드 → 기존 삭제"로 1건을 유지한다.
- 기본 한도: 파일당 20MB, 지원서당 `STORED` 20개·합계 100MB(사진·경력기술서 공유), 확장자 `pdf,jpg,jpeg,png,doc,docx,xls,xlsx,hwp,hwpx`. 서블릿 multipart 상한(25MB/요청 105MB) 초과는 400 "Attachment file size exceeds the allowed limit.".
- `sectionType` 제한 없음(`ATTACHMENT`도 받음).

**GET 다운로드(지원자·관리자 공통) — 🟢**
- `STORED`만 200. 그 밖 상태·지원서 불일치 → 404. 행은 `STORED`인데 파일이 없으면 404 "Attachment file was not found."(행 상태 변경 없음).
- 헤더: `Content-Type`(비면 `application/octet-stream`), `Content-Length`(디스크 실제 크기), `Content-Disposition: attachment; filename="<ASCII 대체명>"; filename*=UTF-8''<퍼센트 인코딩>`, `X-Content-Type-Options: nosniff`, `Cache-Control: no-store`, `Pragma: no-cache`. `Content-Disposition`은 CORS 노출 헤더에 있어야 JS가 읽는다(`SecurityConfig` — [auth-account](auth-account.md)).
- 지원자: 본인이면 지원서 상태 무관. FE는 증명사진 미리보기(blob → object URL)에 쓴다.
- 관리자: FE는 지원서 상세 사진(`{FE}/views/admin/application/Application.vue`)과 지원현황 경력기술서 버튼(`careerDescriptionDownloadUrl` — `{FE}/views/admin/application/ApplicationStatus.vue`)에 쓴다.

**POST 지원자 삭제 — 🟢**
- 활성 행이 아니면(이미 삭제 포함) 404. 사유는 고정값 `APPLICANT_SELF_DELETE`, 감사 기록 없음.
- 옛 🔴 "`basicInfoApi.deleteApplicationAttachments` 반환 타입 오선언"은 해소됐다(해당 함수 삭제, 모든 호출이 `attachmentApi`).

**POST 관리자 삭제 — 🟢, FE 미사용**
- 관리자 화면에 버튼이 없다. `reason` 필수(`@NotBlank`, ≤1000). 지원서 상태·접수기간 무관. `deletedBy`=로그인 ID, `deletedByType=EMPLOYEE`.

**POST 저장소 점검 — 🟢, FE 미사용**
- 운영자가 직접 호출. 항상 `dryRun=true`, DB·파일 무변경, 이력·스케줄러·복구 없음.
- 응답: `{ dryRun, scannedAt, scanned·managed·ignoredPhysicalFileCount, stored·deleted·missing·pendingBinaryDeleteRowCount, 이슈 유형별 5개(storedMissingPhysicalFile·deletedPhysicalFileRemaining·orphanPhysicalFile·purgedPhysicalFilePresent·invalidStoragePath)Count, issues[] }`. 이슈 `{ category, applicationId, attachmentId, rowStatus, fileKeyHash, physicalFileSize, message }` — 경로·물리 파일명 미노출. `MISSING_ROW_PHYSICAL_FILE_PRESENT`·`IGNORED_UNMANAGED_FILE`은 전용 카운트 필드가 없다.
- 루트가 없으면 파일 0건 정상. 루트가 디렉터리가 아니거나 순회 IO 오류면 500 "Attachment storage health scan failed.". `ROLE_PRIVACY_ADMIN`만 있으면 403.

**⛔ POST 메타데이터 교체(`replaceAttachments`) — FE 미사용, 쓰지 말 것**
- `METADATA_ONLY` 행을 전부 hard delete 후 요청대로 다시 만든다(빈 목록 허용). 파일이 없어 다운로드 불가 — 파일은 업로드/삭제로 다룬다.
- `storedFileName`·`storagePath` 전송, 요청 안 `sortOrder` 중복, 기존 `STORED` 행 `sortOrder`와 충돌은 400. `sectionRecordId` 규칙은 업로드와 같다. (`ApplicationAttachmentService` — validateRequest)

## 규칙·불변식

**저장 경로·파일명**
- key `applications/{applicationId}/{yyyy}/{MM}/{dd}/{UUID}.{ext}`, 원본 파일명은 물리 경로에 쓰지 않는다. ({BE}/service/LocalAttachmentStorageService.java — store)
- 파일 접근은 루트 아래만: 빈 key·절대 경로·정규화 후 루트 밖 → load 404, 삭제는 `INVALID_STORAGE_PATH` 결과. ({BE}/service/LocalAttachmentStorageService.java — resolveUnderRoot)
- 저장 IO 실패 → 400 "Attachment file could not be stored."(디스크 장애가 입력 오류처럼 보인다). ({BE}/service/LocalAttachmentStorageService.java — store)
- 업로드 순서: 본인 → 작성 가능 → 메타 → 파일 정책 → 한도 → 물리 저장 → 롤백 정리 등록 → 행 `saveAndFlush`. 행 저장 실패·롤백이면 방금 저장한 파일을 지운다(정리 실패는 warn 로그). ({BE}/service/ApplicationAttachmentFileService.java — upload, registerRollbackCleanup)
- `APPLICATION` 섹션은 `sectionRecordId` null, 그 외 null 또는 >0. 레코드 존재·소유는 검사하지 않는다. ({BE}/service/ApplicationAttachmentFileService.java — validateMetadata)

**파일 정책**
- 파일 null·빈 파일·`maxFileSize` 초과 → 400. ({BE}/service/AttachmentFilePolicy.java — validate)
- 원본 파일명: 공백·`/`·`\`·제어문자 거부, trim + 연속 공백 1칸, ≤255자, 기본명이 Windows 예약어(`CON`·`NUL`·`COM1`~`9`·`LPT1`~`9` 등)면 거부. ({BE}/service/AttachmentFilePolicy.java — sanitizeOriginalFileName)
- 확장자 = 마지막 `.` 뒤(없으면 거부), 소문자로 허용 목록 비교. ({BE}/service/AttachmentFilePolicy.java — extractExtension, validateExtension)
- content type = 클라이언트 multipart 값, 필수, 허용 목록(대소문자 무시: pdf·jpeg·png·msword·docx·xls·xlsx MIME, `application/x-hwp`, `application/haansofthwp`, `application/vnd.hancom.hwpx`). **확장자와 짝 검사·매직넘버·백신 검사 없음.** ({BE}/service/AttachmentFilePolicy.java — validateContentType)
- 설정은 기동 시 검증(크기 >0, 개수 ≥1, 목록 비지 않음). 기본값은 `{BR}/application.yaml`과 클래스 초기값이 같다. ({BE}/config/AttachmentProperties.java)

**한도·정렬**
- 한도는 `STORED` 행만 센다: 개수 + 1 ≤ 20, 합계 + 새 파일 ≤ 100MB. ({BE}/service/ApplicationAttachmentFileService.java — validateStoredLimits)
- `sortOrder` = 모든 행(삭제 포함) 최댓값 + 1(없으면 0). 번호 재사용 없음. ({BE}/service/ApplicationAttachmentFileService.java — nextSortOrder)

**권한·작성 가능 기간**
- 지원자 경로는 본인 지원서가 아니면 404. ({BE}/service/ApplicationSectionAccessService.java — findOwnedApplication, [application](application.md) 소유)
- 업로드·지원자 삭제·교체는 작성 가능 검사(`WITHDRAWN` 거부, 공고 `PUBLISHED`, 접수 시작 ≤ 지금 ≤ 종료). `SUBMITTED`도 통과. ({BE}/service/ApplicationSectionAccessService.java — validateWritable)
- 목록·지원자 다운로드는 작성 가능 검사 없음. ({BE}/service/ApplicationAttachmentDownloadService.java — downloadForApplicant)
- 관리자 다운로드·삭제: 지원서 없음 404, 첨부가 그 지원서 소속 아님 404, 상태 무관, 임직원 principal 필수(지원자 세션 403). ({BE}/service/ApplicationAttachmentDeleteService.java — deleteForAdmin, {BE}/service/CurrentEmployeeService.java — getCurrentEmployeeActor)

**삭제 — 논리 먼저, 물리는 커밋 후**
1. 활성 행(지원서 소속) 조회, 없으면 404. ({BE}/service/ApplicationAttachmentDeleteService.java — findActiveAttachment)
2. `markDeleted`: `SOFT_DELETED` + `deletedAt`(주입 `Clock`)·`deletedBy`·`deletedByType`·`deletionReason`. 행은 지우지 않는다. ({BE}/domain/entity/ApplicationAttachment.java — markDeleted)
3. 직전 `STORED`이고 `storagePath`가 있을 때만 물리 삭제를 `afterCommit`에 등록(동기화 없으면 즉시). 롤백이면 물리 삭제 없음. ({BE}/service/ApplicationAttachmentDeleteService.java — registerAfterCommitPhysicalDelete)
4. 물리 삭제 실패는 **warn 로그만**. DB는 `SOFT_DELETED` 확정, 재시도 없음, 응답은 성공. 파일이 이미 없으면 정상. 남은 파일은 점검의 `DELETED_PHYSICAL_FILE_REMAINING`. ({BE}/service/ApplicationAttachmentDeleteService.java — deletePhysicalFile)
- `METADATA_ONLY`·`MISSING` 행도 삭제 가능(물리 삭제 없음).
- 관리자 사유: trim 후 비면 400, 1000자 초과 400. ({BE}/service/ApplicationAttachmentDeleteService.java — validateReason)
- 감사: 관리자 삭제 `ATTACHMENT_ADMIN_DELETE` in-tx(실패 시 삭제 롤백, 사유·파일명 미기록). 관리자 다운로드 `ATTACHMENT_ADMIN_DOWNLOAD` REQUIRES_NEW **fail-close**(감사 커밋 실패면 바이너리 안 나감). 지원자 행위는 기록 안 함. ({BE}/service/ApplicationAttachmentDeleteService.java — deleteForAdmin, {BE}/controller/AdminApplicationAttachmentController.java — downloadAttachmentFile)

**다운로드**
- `STORED` 행만. `Content-Length`는 실제 파일 크기(DB `fileSize`와 다르면 warn 로그). ({BE}/service/ApplicationAttachmentDownloadService.java — findStoredAttachment, toDownloadResource)
- 파일명: `/`·`\`·제어문자 → `_`, 비면 `download`. ASCII 대체명은 비ASCII·`"` → `_`, 남는 게 `_`·`.`뿐이면 `download.{ext}`. `filename*`은 UTF-8 퍼센트 인코딩. 헤더는 이 팩토리에서만 만든다. ({BE}/controller/AttachmentDownloadResponseFactory.java — toResponse, contentDisposition)

**상태**
- 목록·삭제 대상 = 활성 행. 다운로드·한도·요건 충족 = `STORED`만. ({BE}/enumeration/PhysicalFileStatus.java)
- 새 soft delete는 항상 `SOFT_DELETED`. legacy `DELETED`는 같은 것으로 취급(운영 이관 SQL은 주석 처리된 채 미실행, enum 제거 미실행).
- `purgeMetadataPii`·`markBinaryDelete*`·`markBinaryDeleted`(`storagePath` null)는 파기 saga 전용이다 — 이 카드 서비스는 호출하지 않는다([privacy-audit](privacy-audit.md)). ({BE}/domain/entity/ApplicationAttachment.java)

**저장소 점검이 찾는 이상 유형** ({BE}/service/AttachmentStorageHealthScanService.java — scanDryRun)
- 파일: 루트 아래 순회, 심볼릭 링크는 따라가지 않는다. 행: `STORED`·`DELETED`·`SOFT_DELETED`·`MISSING`·`BINARY_DELETE_PENDING`·`BINARY_DELETE_FAILED`(`METADATA_ONLY`·`BINARY_DELETED` 제외). `BINARY_DELETE_*` 행은 이슈가 아니라 `pendingBinaryDeleteRowCount`만 세고 key를 orphan 제외용으로 등록한다. (scanPhysicalFiles, scanRows)

| 이슈 유형 | 조건 |
|---|---|
| `PURGED_PHYSICAL_FILE_PRESENT` | 파기 완료(`PURGED`) 지원서 경로에 파일 잔존 — 치명. 먼저 분류하고 그 key는 아래 판정에서 뺀다 |
| `STORED_MISSING_PHYSICAL_FILE` | `STORED` 행인데 파일 없음 |
| `DELETED_PHYSICAL_FILE_REMAINING` | soft-deleted 행인데 파일 잔존(물리 삭제 실패 흔적) |
| `MISSING_ROW_PHYSICAL_FILE_PRESENT` | `MISSING` 행인데 파일 있음 |
| `ORPHAN_PHYSICAL_FILE` | managed 파일인데 어떤 점검 행·파기 진행 key도 가리키지 않음 |
| `INVALID_STORAGE_PATH` | 행 `storagePath`가 빈 값·절대 경로·루트 밖·managed 형식 아님 |
| `IGNORED_UNMANAGED_FILE` | managed 형식 밖 파일 또는 심볼릭 링크 |

**폐지된 ATTACHMENT 섹션 요건 처리** — `ATTACHMENT` 제외는 523ab48, 아래 `BASIC_INFO`·`CAREER` 허용 목록은 300e792에서 도입.
- 단일 기준 `ApplicationSectionType.acceptsApplicantAttachment()` = `BASIC_INFO`·`CAREER`. ({BE}/enumeration/ApplicationSectionType.java — [application-form](application-form.md) 소유)
- 요건 등록: 그 밖 섹션은 400 "첨부 요구사항은 지원자가 파일을 올릴 수 있는 섹션(기본정보 BASIC_INFO, 경력 CAREER)에만 등록할 수 있습니다. sectionType={값}". ({BE}/service/JobPostingAttachmentRequirementService.java — [job-posting](job-posting.md))
- 제출 검증·완성도: 허용 섹션 요건만 필수로 보고 레거시 요건 행은 무시. 충족 = `STORED`·`deletedAt` null·(attachmentType, sectionType) 일치 수 ≥ `minCount`. ({BE}/service/ApplicationSubmitValidator.java — validateAttachmentRequirements, {BE}/service/ApplicationCompletionReadChecker.java — [application](application.md))
- 업로드 API는 무변경.

## 변경 레시피

### 허용 확장자·크기·개수 한도 변경
1. 운영 값은 env `RECRUIT_ATTACHMENT_*`로 바꾼다. 기본값은 `{BR}/application.yaml`과 `{BE}/config/AttachmentProperties.java` 초기값을 같이 바꾼다.
2. 파일당 한도를 올리면 `spring.servlet.multipart.*`(env `RECRUIT_ATTACHMENT_MULTIPART_*`)도 올린다 — 서블릿 한도가 먼저 걸린다.
3. 확장자를 추가하면 content type도 추가한다. PDF 사진은 jpeg/png ≤5MB만 쓴다(`ApplicationPhotoLoader` — [admin-application](admin-application.md)).
4. `{BT}/service/ApplicationAttachmentFileServiceTest.java` 보강 → 검증 → 카드 갱신 → `node tools/check-docs.mjs`.

### 새 섹션에서 첨부 받기
1. 업로드 UI는 [application-sections](application-sections.md) 소유 섹션 화면에 두고 `attachmentApi`를 재사용한다. (sectionType, attachmentType) 쌍을 정해 목록을 그 쌍으로 거르고, 교체는 "업로드 성공 → 기존 삭제" 순서로 한다.
2. 필수 요건을 걸게 하려면 `{BE}/enumeration/ApplicationSectionType.java` `APPLICANT_ATTACHMENT_SECTION_TYPES`에 섹션 추가(요건 등록·제출 검증·완성도가 따라온다). 테스트 `JobPostingAttachmentRequirementServiceTest`·`ApplicationSubmitValidatorTest`·`ApplicationDashboardServiceTest`, [application-form](application-form.md)·[job-posting](job-posting.md) 카드 갱신.
3. 새 `AttachmentType`이면 enum + FE 유니언 두 곳(`{FE}/types/application/sections/basicInfo.ts`, `{FE}/types/admin/applicationSections.ts`). DDL 불필요.
4. 카드 요약의 사용처 쌍 갱신 → `npm run type-check` → `node tools/check-docs.mjs`.

### 다운로드 헤더·응답 필드 변경
1. 헤더는 `{BE}/controller/AttachmentDownloadResponseFactory.java` 한 곳. `no-store`·`nosniff`는 빼지 않는다. JS가 읽을 새 헤더는 `{BE}/config/SecurityConfig.java` `setExposedHeaders`에 추가([auth-account](auth-account.md)).
2. `AttachmentResponse` 필드 추가 시 저장 내부값 금지, FE `AttachmentResponse`(basicInfo.ts) 동기화.
3. `{BT}/controller/ApplicationAttachmentDownloadControllerTest.java` 보강 → 검증 → 카드 갱신 → `node tools/check-docs.mjs`.

### 삭제·정리 정책 변경(재시도·정리 명령 등)
1. "DB 상태 먼저 → 커밋 후 물리 삭제" 순서를 유지한다(반대면 롤백 시 파일 없는 `STORED` 행이 생긴다).
2. 새 명령은 `POST /admin/attachments/...`, 도메인 변경이면 감사 in-tx, 응답은 `fileKeyHash`만. `BINARY_DELETE_*`·PURGED 파일은 [privacy-audit](privacy-audit.md) 소관.
3. 컬럼 변경 시 운영 SQL을 `recruit_back/recruit_backend/docs/ops/`에 추가.
4. `{BT}/service/ApplicationAttachmentDeleteServiceTest.java`·`{BT}/service/AttachmentStorageHealthScanServiceTest.java` 보강 → 검증 → 카드 갱신 → `node tools/check-docs.mjs`.

## 검증

백엔드(`recruit_back/recruit_backend/`에서, 이 카드 테스트 9개):

```powershell
# Windows PowerShell
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.ApplicationAttachment*" --tests "com.shinyoung.recruit.controller.AdminAttachmentStorageHealthControllerTest" --tests "com.shinyoung.recruit.service.ApplicationAttachment*" --tests "com.shinyoung.recruit.service.AttachmentStorageHealthScanServiceTest" --tests "com.shinyoung.recruit.service.LocalAttachmentStorageServiceTest" --no-daemon
```

```bash
# Linux
AES_SECRET_KEY='<로컬 예시 키>' ./gradlew test --tests "com.shinyoung.recruit.controller.ApplicationAttachment*" --tests "com.shinyoung.recruit.controller.AdminAttachmentStorageHealthControllerTest" --tests "com.shinyoung.recruit.service.ApplicationAttachment*" --tests "com.shinyoung.recruit.service.AttachmentStorageHealthScanServiceTest" --tests "com.shinyoung.recruit.service.LocalAttachmentStorageServiceTest" --no-daemon
```

상태·요건·파기 연동을 바꿨으면 `service` 패키지의 `ApplicationSubmitValidatorTest`·`ApplicationDashboardServiceTest`([application](application.md)), `ApplicationPhotoLoaderTest`·`AdminApplicationSectionServiceTest`([admin-application](admin-application.md)), `PurgeExecutionServiceTest`·`PurgeReconciliationServiceTest`([privacy-audit](privacy-audit.md))도 돌린다.

프론트(`recruit_front/`에서, 관련 spec 없음):

```bash
npm run type-check
```

## 함정·결정

- `8d7485d` FE 첨부 저장을 "업로드 성공 → 기존 삭제" 순서로 바꾸고 파일 타임아웃을 120초로 늘렸다. 순서를 되돌리면 업로드 실패 시 기존 파일이 사라진다.
- `3ff110b` `Content-Disposition` CORS 노출 추가(없으면 크로스 오리진에서 파일명 파싱 `TypeError`). FE는 `{FE}/common/fileDownload.ts` `saveBlobResponse`로 헤더 부재를 처리한다.
- `523ab48` 폐지된 `ATTACHMENT` 섹션 필수 요건이 제출을 막던 결함 수정. 요건은 FE가 올리는 쌍과 맞아야 충족된다(사진 `BASIC_INFO`+`ETC`, 경력기술서 `CAREER`+`CAREER_DESCRIPTION`).
- 사진 전용 타입이 없다. "`BASIC_INFO`의 `ETC` 중 마지막 행 = 사진"이 FE·관리자 상세·PDF 공통 규약이다.
- 사진 안내 "JPG/JPEG, 1MB 미만"은 강제되지 않는다(FE 검사 없음, 서버는 10종·20MB). PDF에는 jpeg/png ≤5MB만 들어간다.
- 제출 후에도 접수기간이면 업로드·삭제된다(옛 설계의 DRAFT 전용 아님). 삭제 시 요건 재검사는 없다.
- 물리 삭제 재시도·정리·복구 명령이 없고 `MISSING`을 기록하는 코드도 없다. 잔존 파일 정리는 수작업이다.
- 날짜 폴더는 `LocalDate.now()`(시스템 시간대, 주입 `Clock` 아님) — 백엔드 AGENTS.md "인자 없는 now() 금지"의 예외 상태.
- 루트 기본값은 상대 경로다. 운영은 절대 경로 env를 주고 공고 이미지 루트와 분리한다([job-posting](job-posting.md)).
- 점검은 전체 파일·행을 동기 처리한다.
- `recruit_back/recruit_backend/docs/adr/0005-retention-purge-mode-tombstone-anonymization-binary-deletion.md` — 파기 = 바이너리 물리 삭제 + DB tombstone. 그래서 행을 hard delete 하지 않는다.
- `recruit_back/recruit_backend/docs/adr/0006-audit-transaction-policy.md` — 반출은 fail-close REQUIRES_NEW, 커밋 변경은 in-tx. afterCommit 감사로 바꾸지 않는다.
- 운영 DDL: `recruit_back/recruit_backend/docs/ops/phase-09d-2-attachment-saga-ddl.sql`(saga 컬럼, 주석 처리된 `DELETED`→`SOFT_DELETED` 이관), `recruit_back/recruit_backend/docs/ops/phase-09e-reconciliation-ddl.sql`(`binary_delete_failure_code`).
