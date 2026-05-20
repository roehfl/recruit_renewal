Phase 03i-4-2 - Attachment Soft Delete Command Implementation

목표:
Phase 03i-4 설계에 따라 attachment delete command를 구현한다.
이번 Phase는 delete command만 구현한다.
orphan scan, cleanup execution, admin repair, full audit history table은 구현하지 않는다.

구현 대상:
1. PhysicalFileStatus.DELETED 추가
2. ApplicationAttachment soft delete lifecycle 필드/메서드 추가
3. applicant attachment delete command 추가
4. admin attachment delete command 추가
5. normal metadata list에서 DELETED row 제외
6. download에서 DELETED row 404 유지
7. DB state를 먼저 DELETED로 변경
8. transaction afterCommit에서 physical file delete
9. physical delete 실패는 rollback하지 않고 log만 남김
10. 테스트/문서 갱신

참고 문서:
- docs/codex/design/phase-03i-4-attachment-delete-cleanup-repair-design.md
- docs/codex/design/phase-03i-attachment-file-upload-download-design.md
- docs/codex/implementation/phase-03i-2-attachment-file-upload.md
- docs/codex/implementation/phase-03i-3-attachment-file-download.md
- docs/codex/design/phase-03c-application-detail-design.md
- docs/codex/design/phase-03-application-design.md
- docs/codex/07-implementation-history.md

절대 구현하지 말 것:
- orphan scan dry-run
- orphan cleanup execution
- admin repair command
- mark-missing command
- includeDeleted metadata read
- full audit/history table
- upload API 변경
- download API 변경
- metadata replace API 구조 변경
- SecurityConfig 변경
- dashboard readiness 변경
- submit validator 변경
- attachment required policy
- S3/NAS/object storage 전환
- virus scan/DLP 연동
- downloadAvailable 필드 추가
- HTTP DELETE method 사용

API:
1. Applicant delete
   POST /applications/{applicationId}/attachments/{attachmentId}/delete

2. Admin delete
   POST /admin/applications/{applicationId}/attachments/{attachmentId}/delete

HTTP method 정책:
- DELETE method를 쓰지 않는다.
- 현재 프로젝트 command API 관례에 맞춰 POST command를 사용한다.

1. PhysicalFileStatus 수정

기존:
- METADATA_ONLY
- STORED
- MISSING

추가:
- DELETED

정책:
- DELETED row는 normal metadata list에서 제외한다.
- DELETED row는 download 대상이 아니다.
- DELETED row delete command 재호출은 404로 처리한다.
- DELETED는 orphan physical file을 뜻하지 않는다.
- orphan physical file은 DB row 없는 storage artifact이며 후속 cleanup phase에서 다룬다.

2. ApplicationAttachment entity 수정

ApplicationAttachment에 최소 삭제 추적 필드를 추가한다.

권장 필드:
- deletedAt: LocalDateTime
- deletedBy: String
- deletedByType: String 또는 enum
- deletionReason: String

권장:
- deletedByType은 enum으로 분리 가능하면 분리한다.
  - AttachmentDeleteActorType
    - APPLICANT
    - EMPLOYEE
- deletionReason length는 1000자 후보.
- deletedBy length는 100~255자 후보.
- deletedAt은 Clock 기준으로 세팅한다.

주의:
- full audit history table은 이번 Phase에서 만들지 않는다.
- 하지만 admin delete reason을 입력받고도 DB에 전혀 남기지 않는 것은 운영상 약하다.
- 따라서 이번 Phase에서는 ApplicationAttachment row에 최소 삭제 정보만 남긴다.
- 별도 ApplicationAttachmentDeletionHistory는 후속 audit phase 후보로 문서에 남긴다.

Entity method 후보:
- markDeleted(String deletedBy, AttachmentDeleteActorType deletedByType, String deletionReason, LocalDateTime deletedAt)

markDeleted 정책:
- 이미 physicalFileStatus == DELETED 이면 실패시키거나 service에서 사전에 404 처리한다.
- physicalFileStatus를 DELETED로 변경한다.
- deletedAt/deletedBy/deletedByType/deletionReason을 세팅한다.
- storedFileName/storagePath/originalFileName/contentType/fileSize는 유지한다.
  - 이유: 삭제 후 audit/debug/cleanup 후보 판단에 필요하다.
- 응답에는 storage internals를 노출하지 않는다.

3. Repository 수정

ApplicationAttachmentRepository에 active/deleted 필터용 메서드를 추가/수정한다.

현재 normal metadata list는 DELETED를 제외해야 한다.

기존 메서드가 있다면:
- findByJobApplicationIdOrderBySortOrderAscIdAsc(Long applicationId)

다음 중 하나로 변경한다:
- findByJobApplicationIdAndPhysicalFileStatusNotOrderBySortOrderAscIdAsc(applicationId, DELETED)
또는
- @Query로 active row만 조회

권장 active metadata list:
- physicalFileStatus != DELETED

주의:
- METADATA_ONLY, STORED, MISSING은 일단 active list 후보에 남길 수 있다.
- 다만 MISSING은 현재 runtime에서 적극 사용하지 않는다.
- DELETED만 normal list에서 제외한다.

Delete command lookup 후보:
- Optional<ApplicationAttachment> findByIdAndJobApplicationIdAndPhysicalFileStatusNot(
    Long attachmentId,
    Long jobApplicationId,
    PhysicalFileStatus excludedStatus
  )

또는 명시 query:
- attachmentId + applicationId + physicalFileStatus <> DELETED

Admin/applicant delete 모두 이미 DELETED인 row는 조회되지 않아야 하며 404 처리한다.

Download:
- 기존 download는 findByIdAndJobApplicationIdAndPhysicalFileStatus(..., STORED)를 쓰므로 DELETED는 자동 404다.
- download service는 구조 변경하지 않는다.

Upload sortOrder:
- 기존 findMaxSortOrderByJobApplicationId는 전체 row 기준을 유지한다.
- DELETED row도 max sortOrder 계산에 포함한다.
- 이유:
  - 삭제 후 같은 sortOrder 재사용을 막고 append-only 이력을 유지하기 위함.
- 이 메서드는 DELETED 제외로 바꾸지 않는다.

Metadata replace sortOrder conflict:
- metadata replace는 active preserved row 기준으로만 충돌을 검사한다.
- DELETED row의 sortOrder는 충돌 대상으로 보지 않는다.
- 기존 STORED conflict 검사는 유지한다.
- MISSING을 active preserved row로 볼 경우 MISSING sortOrder도 충돌 대상으로 포함해도 된다.
- 최소 구현은 현재 STORED conflict 유지 + DELETED list 제외만으로 충분하다.
- 단, DELETED row 때문에 metadata replace가 실패하면 안 된다.

4. DTO 추가

Applicant delete:
- request body 없음.
- response: ApiResponse<AttachmentDeleteResponse>

Admin delete:
- request DTO 추가:
  - AttachmentAdminDeleteRequest
  - field:
    - reason: String
- validation:
  - @NotBlank
  - @Size(max = 1000)

Response DTO 추가:
- AttachmentDeleteResponse

후보 필드:
- Long applicationId
- Long attachmentId
- boolean deleted
- boolean physicalDeleteRequested
- String message

권장:
- physicalDeleteCompleted는 넣지 않는다.
  - afterCommit 삭제라 service return 시점에 완료 여부를 정직하게 알기 어렵다.
- storagePath, storedFileName, storageRoot, absolute path는 절대 넣지 않는다.
- physicalFileStatus도 응답에 넣지 않는다.

응답 예:
- METADATA_ONLY delete:
  - deleted=true
  - physicalDeleteRequested=false
  - message="Attachment was deleted."
- STORED delete:
  - deleted=true
  - physicalDeleteRequested=true
  - message="Attachment was deleted."

5. Service 추가

후보:
- ApplicationAttachmentDeleteService

역할:
- applicant delete orchestration
- admin delete orchestration
- ownership/state validation
- attachment/application scope validation
- already DELETED hidden 404
- DB soft delete
- afterCommit physical delete registration
- response creation

의존성 후보:
- ApplicationSectionAccessService
- ApplicationAttachmentRepository
- JobApplicationRepository
- AttachmentStorageService
- Clock
- CurrentEmployeeService는 controller에서 actor resolve 후 service에 넘기거나 service가 직접 받아도 된다.

권장 service method:
- AttachmentDeleteResponse deleteForApplicant(Long applicantId, Long applicationId, Long attachmentId)
- AttachmentDeleteResponse deleteForAdmin(Long applicationId, Long attachmentId, String adminLoginId, String reason)

Applicant delete 정책:
- current applicant own application만 허용
- application.status == DRAFT
- JobPosting.status == PUBLISHED
- now inside reception period
- attachmentId가 applicationId에 속해야 함
- physicalFileStatus != DELETED
- SUBMITTED/WITHDRAWN 삭제 불가
- other applicant 접근은 기존 hidden 404 유지
- attachment/application mismatch는 404

구현:
- sectionAccessService.findOwnedApplication(applicantId, applicationId)
- sectionAccessService.validateWritable(application)
  - 기존 validateWritable이 DRAFT + PUBLISHED + accepting을 검증한다면 재사용
- active attachment lookup
- markDeleted(...)
- afterCommit physical delete 등록

Applicant reason:
- deletionReason은 "APPLICANT_SELF_DELETE" 같은 내부 상수 사용
- deletedByType = APPLICANT
- deletedBy = applicantId 문자열 또는 가능하면 applicant loginId
- loginId 접근이 과도하면 applicantId 문자열로 시작해도 된다.

Admin delete 정책:
- /admin/** security는 기존 SecurityConfig에 맡긴다.
- SecurityConfig 변경 금지.
- applicationId 존재 확인
- attachmentId가 applicationId에 속해야 함
- physicalFileStatus != DELETED
- DRAFT/SUBMITTED/WITHDRAWN 모두 허용
- reason 필수
- reason max 1000자
- applicant principal은 보안 레이어에서 403
- anonymous는 401

구현:
- jobApplicationRepository.existsById(applicationId) 또는 findById
- active attachment lookup
- markDeleted(adminLoginId, EMPLOYEE, reason, now)
- afterCommit physical delete 등록

Admin actor:
- Admin controller에서 @AuthenticationPrincipal CustomUserDetails를 받는다.
- 기존 CurrentEmployeeService가 있다면 사용해서 employee loginId를 resolve한다.
- 없거나 사용이 맞지 않으면 CustomUserDetails.getUsername()을 사용하되 userType/blank 검증을 명확히 한다.
- applicant가 admin path에 들어오는 것은 security에서 403이어야 한다.

6. Physical delete after commit

원칙:
- DB 상태를 DELETED로 변경하는 transaction이 먼저 성공해야 한다.
- physical file delete는 transaction afterCommit에서 수행한다.
- physical delete 실패는 DB rollback을 유발하지 않는다.
- physical delete 실패는 warn/error log로 남기고 후속 orphan cleanup 대상으로 남긴다.

대상:
- previous physicalFileStatus == STORED이면 physical delete requested.
- previous physicalFileStatus == MISSING이면 deleteIfExists를 호출해도 되고 생략해도 된다.
- previous physicalFileStatus == METADATA_ONLY이면 physical delete 없음.
- previous physicalFileStatus == DELETED이면 delete command는 404.

구현 후보:
- service에서 delete 전 상태와 storagePath를 변수로 보관
- markDeleted 후 TransactionSynchronizationManager.registerSynchronization(...)
- afterCommit에서 storageService.deleteIfExists(storagePath)

주의:
- afterCommit 등록은 storagePath가 null/blank가 아닌 STORED row에만 수행한다.
- deleteIfExists는 이미 idempotent하므로 missing file이어도 실패하지 않아야 한다.
- storage internals는 API 응답에 노출하지 않는다.
- log에는 attachmentId/applicationId 중심으로 남긴다.
- 가능하면 storagePath 전체를 info/warn log에 노출하지 않는다.
  - 필요하면 debug level에서만 제한적으로 남긴다.

7. Metadata list 변경

Applicant metadata list:
- GET /applications/{applicationId}/attachments
- DELETED row 제외

Admin metadata list:
- GET /admin/applications/{applicationId}/attachments
- DELETED row 제외

주의:
- AttachmentResponse/AdminAttachmentResponse에 physicalFileStatus를 추가하지 않는다.
- downloadAvailable 추가하지 않는다.
- delete endpoint 추가 후에도 기존 metadata response shape는 유지한다.

구현 포인트:
- ApplicationAttachmentService.getAttachmentResponses(...)에서 DELETED 제외 repository method 사용
- AdminApplicationSectionService attachment 조회도 DELETED 제외 method를 사용하도록 수정
- 만약 여러 곳에서 findByJobApplicationIdOrderBySortOrderAscIdAsc를 사용한다면 DELETED 제외가 필요한 곳과 전체 row가 필요한 곳을 구분한다.
- upload append max sortOrder는 전체 row 기준이므로 기존 max query는 유지한다.

8. Controller 추가

Applicant:
- ApplicationAttachmentController에 추가

Endpoint:
- POST /applications/{applicationId}/attachments/{attachmentId}/delete

Request:
- body 없음

Flow:
- @AuthenticationPrincipal CustomUserDetails
- CurrentApplicantService로 applicantId resolve
- applicationAttachmentDeleteService.deleteForApplicant(...)
- ApiResponse.success(response)

Admin:
- AdminApplicationAttachmentController에 추가

Endpoint:
- POST /admin/applications/{applicationId}/attachments/{attachmentId}/delete

Request:
- @Valid @RequestBody AttachmentAdminDeleteRequest

Flow:
- @AuthenticationPrincipal CustomUserDetails
- admin/employee actor resolve
- applicationAttachmentDeleteService.deleteForAdmin(...)
- ApiResponse.success(response)

주의:
- admin download controller가 이미 AdminApplicationAttachmentController에 있다면 같은 controller에 delete command를 추가해도 된다.
- response에는 storagePath/storedFileName 없음.

9. Exception policy

- already DELETED: 404
- attachment/application mismatch: 404
- application not found: 404
- other applicant application: 404
- applicant SUBMITTED/WITHDRAWN delete: 400
- applicant outside accepting: 400
- admin reason blank: 400 validation error
- anonymous: 401 existing handler
- wrong role: 403 existing handler

예외 클래스:
- 기존 JobApplicationNotFoundException / InvalidJobApplicationException 재사용 가능.
- 새 AttachmentNotFoundException을 만들 필요는 없다.
- 메시지는 storage internals를 포함하지 않는다.

10. 테스트 추가/수정

Service test:
- ApplicationAttachmentDeleteServiceTest 추가

Applicant delete:
- applicant_owner_draft_stored_delete_success
- applicant_owner_draft_metadata_only_delete_success
- applicant_delete_marks_row_deleted
- applicant_delete_excludes_deleted_row_from_metadata_list
- applicant_delete_stored_row_requests_after_commit_physical_delete
- applicant_delete_metadata_only_does_not_request_physical_delete
- applicant_delete_submitted_fails
- applicant_delete_withdrawn_fails
- applicant_delete_outside_reception_period_fails
- applicant_delete_unpublished_posting_fails
- applicant_delete_other_applicant_hidden_404
- applicant_delete_attachment_application_mismatch_404
- applicant_delete_already_deleted_404
- applicant_delete_response_does_not_expose_storage_fields

Admin delete:
- admin_delete_draft_success
- admin_delete_submitted_success_with_reason
- admin_delete_withdrawn_success_with_reason
- admin_delete_blank_reason_fails
- admin_delete_reason_too_long_fails
- admin_delete_attachment_application_mismatch_404
- admin_delete_already_deleted_404
- admin_delete_marks_deleted_by_employee
- admin_delete_response_does_not_expose_storage_fields

Physical delete after commit:
- For STORED row, physical file exists before commit and is deleted after commit.
- For METADATA_ONLY row, no physical delete is attempted.
- For missing physical file, delete command still succeeds and row becomes DELETED.
- If using @Transactional test, afterCommit will not run until test transaction commits.
- For afterCommit verification, use one of:
  - no @Transactional on the specific test class/method
  - TestTransaction.flagForCommit(); TestTransaction.end()
  - TransactionTemplate with explicit commit
- Do not fake success without verifying afterCommit behavior.

Repository/list regression:
- normal applicant metadata list excludes DELETED
- admin metadata list excludes DELETED
- download DELETED row returns 404
- upload append sortOrder still uses full max including DELETED
- metadata replace does not fail due to DELETED sortOrder
- metadata replace still fails on active STORED sortOrder conflict
- metadata replace does not resurrect DELETED rows

Controller test:
- applicant POST delete success
- applicant POST delete anonymous 401
- applicant POST delete employee/admin 403
- applicant other applicant hidden 404
- applicant submitted delete 400
- admin POST delete success as ROLE_ADMIN
- admin POST delete success as ROLE_RECRUIT_ADMIN
- admin POST delete applicant principal 403
- admin POST delete anonymous 401
- admin blank reason 400
- response shape is ApiResponse<AttachmentDeleteResponse>
- response has no storedFileName/storagePath/physicalFileStatus/downloadAvailable

Response exposure reflection test:
- AttachmentDeleteResponse record components do not contain:
  - storedFileName
  - storagePath
  - storageRoot
  - physicalFileStatus
  - absolutePath
  - downloadAvailable

11. 기존 테스트 회귀 수정

수정될 가능성이 있는 테스트:
- ApplicationAttachmentServiceTest
- ApplicationAttachmentControllerTest
- AdminApplicationSectionServiceTest
- AdminApplicationSectionControllerTest
- ApplicationAttachmentDownloadServiceTest
- ApplicationAttachmentDownloadControllerTest
- ApplicationAttachmentFileServiceTest

주의:
- DELETED row가 normal list에서 제외되도록 기대값 수정
- download는 STORED만 허용하므로 DELETED 404 테스트 추가
- upload append sortOrder가 DELETED 포함 전체 max + 1인지 확인

12. 문서 갱신

새 문서:
- docs/codex/implementation/phase-03i-4-2-attachment-delete-command.md
- docs/codex/reports/phase-03i-4-2-attachment-delete-command.html

갱신:
- docs/codex/design/phase-03i-4-attachment-delete-cleanup-repair-design.md
- docs/codex/design/phase-03i-attachment-file-upload-download-design.md
- docs/codex/design/phase-03c-application-detail-design.md
- docs/codex/design/phase-03-application-design.md
- docs/codex/07-implementation-history.md

문서에 반드시 기록:
- 구현 API:
  - POST /applications/{applicationId}/attachments/{attachmentId}/delete
  - POST /admin/applications/{applicationId}/attachments/{attachmentId}/delete
- PhysicalFileStatus.DELETED 추가
- hard delete가 아니라 soft lifecycle delete
- applicant delete는 DRAFT + accepting only
- admin delete는 DRAFT/SUBMITTED/WITHDRAWN + reason required
- DELETED row는 normal metadata list에서 제외
- DELETED row는 download 404
- DB state update 후 afterCommit physical delete
- physical delete 실패는 rollback하지 않고 log + future cleanup 대상
- audit history table은 미구현
- orphan scan/cleanup/admin repair 미구현
- storage internals 비노출
- 테스트 결과

HTML report:
- self-contained HTML
- 외부 CDN/JS/CSS 금지
- 사람이 볼 수 있게 요약
- 포함 섹션:
  - Executive Summary
  - Implemented Scope
  - API List
  - Lifecycle Policy
  - Applicant Delete
  - Admin Delete
  - Transaction / Physical Delete Strategy
  - Response Exposure
  - Tests
  - Deferred Items
  - Next Phase

13. 검증 명령

우선 targeted:
- .\gradlew.bat test --tests "*ApplicationAttachmentDelete*" --no-daemon
- .\gradlew.bat test --tests "*ApplicationAttachment*" --no-daemon
- .\gradlew.bat test --tests "*AdminApplicationSection*" --no-daemon

최종:
- .\gradlew.bat clean test --no-daemon

완료 기준:
- applicant DRAFT delete 성공
- applicant SUBMITTED/WITHDRAWN delete 실패
- admin delete reason 필수
- DELETED row가 metadata list에서 제외
- DELETED row download 404
- STORED delete 후 afterCommit physical delete 동작 검증
- physical delete 실패가 DB soft delete를 rollback하지 않음
- already DELETED delete 404
- upload append sortOrder는 DELETED 포함 전체 max + 1
- metadata replace는 DELETED sortOrder 때문에 실패하지 않음
- storage internals 비노출
- targeted tests 통과
- full clean test 통과