# Phase 03c-6 - Application Attachment Metadata

## Phase 이름

Phase 03c-6: Application Attachment metadata vertical slice

## 목적

`JobApplication` 하위 상세 섹션 중 첨부파일 metadata 저장/조회 구조를 구현한다. 실제 파일 업로드, 다운로드, 저장소 연동은 구현하지 않고, 지원자가 본인 지원서의 첨부 metadata 목록을 `DRAFT` 상태에서 replace 저장할 수 있게 한다.

## 구현 범위

- `AttachmentType` enum 추가
- `ApplicationSectionType` enum 추가
- `ApplicationAttachment` Entity 추가
- `ApplicationAttachmentRepository` 추가
- 지원자 첨부 metadata Request/Response DTO 추가
- `ApplicationAttachmentService` 추가
- `ApplicationAttachmentController` 추가
- `GET /applications/{applicationId}/attachments`
- `POST /applications/{applicationId}/attachments`
- `ApplicationSectionAccessService.findOwnedApplication`, `validateWritable` 재사용
- `DRAFT` 상태에서만 저장 허용
- `SUBMITTED`, `WITHDRAWN` 저장 차단
- 타인 지원서 접근 차단
- `storedFileName`, `storagePath` 응답 제외
- `sectionType=APPLICATION`일 때 `sectionRecordId` 금지
- `sectionType!=APPLICATION`일 때 `sectionRecordId` null 허용, 값이 있으면 1 이상 검증

## 미구현 범위

- 실제 multipart 파일 업로드
- 파일 저장, 파일 이동, 바이러스 검사
- S3/NAS/local storage 연동
- 다운로드 API
- 파일 삭제 API
- Attachment 전용 `ApplicationFormConfig` flag
- `sectionRecordId` 실제 섹션 row 존재성 검증
- 관리자 상세 섹션 API
- `ApplicationSubmitValidator`
- StageResult
- 자기소개서/질문답변 도메인
- `PUT`, HTTP `DELETE`

## 변경 파일 목록

### 코드 변경
- `src/main/java/com/shinyoung/recruit/controller/ApplicationAttachmentController.java`
- `src/main/java/com/shinyoung/recruit/domain/entity/ApplicationAttachment.java`
- `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationAttachmentRepository.java`
- `src/main/java/com/shinyoung/recruit/dto/request/AttachmentReplaceRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/request/AttachmentRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/response/AttachmentResponse.java`
- `src/main/java/com/shinyoung/recruit/enumeration/ApplicationSectionType.java`
- `src/main/java/com/shinyoung/recruit/enumeration/AttachmentType.java`
- `src/main/java/com/shinyoung/recruit/service/ApplicationAttachmentService.java`

### 테스트 변경
- `src/test/java/com/shinyoung/recruit/service/ApplicationAttachmentServiceTest.java`
- `src/test/java/com/shinyoung/recruit/controller/ApplicationAttachmentControllerTest.java`

### 문서 변경
- `docs/codex/implementation/phase-03c-6-application-attachment-metadata.md`
- `docs/codex/design/phase-03-application-design.md`
- `docs/codex/design/phase-03c-application-detail-design.md`
- `docs/codex/07-implementation-history.md`

## 신규 클래스 목록

- `AttachmentType`
- `ApplicationSectionType`
- `ApplicationAttachment`
- `ApplicationAttachmentRepository`
- `AttachmentReplaceRequest`
- `AttachmentRequest`
- `AttachmentResponse`
- `ApplicationAttachmentService`
- `ApplicationAttachmentController`
- `ApplicationAttachmentServiceTest`
- `ApplicationAttachmentControllerTest`

## 수정 클래스 목록

- 없음

## 클래스별 설명

| 구분 | 패키지 | 클래스 | 역할 | 주요 필드/메서드 | 연관 클래스 | 비고 |
|---|---|---|---|---|---|---|
| Enum | `com.shinyoung.recruit.enumeration` | `AttachmentType` | 첨부 유형 code | `RESUME`, `TRANSCRIPT`, `GRADUATION_CERTIFICATE`, `CAREER_CERTIFICATE`, `CERTIFICATE_PROOF`, `LANGUAGE_SCORE_REPORT`, `PORTFOLIO`, `ETC` | `ApplicationAttachment` | CommonCode 미사용 |
| Enum | `com.shinyoung.recruit.enumeration` | `ApplicationSectionType` | 첨부 귀속 섹션 code | `APPLICATION`, `EDUCATION`, `CAREER`, `CERTIFICATE`, `LANGUAGE`, `MILITARY`, `AWARD`, `GAP_PERIOD`, `ETC` | `ApplicationAttachment` | access helper enabled 검증에는 미사용 |
| Entity | `com.shinyoung.recruit.domain.entity` | `ApplicationAttachment` | 지원서별 첨부 metadata row | `attachmentType`, `sectionType`, `sectionRecordId`, `originalFileName`, `storedFileName`, `storagePath`, `contentType`, `fileSize`, `sortOrder`, `create` | `JobApplication` | 실제 파일 저장 아님 |
| Repository | `com.shinyoung.recruit.domain.repository` | `ApplicationAttachmentRepository` | 첨부 metadata 조회/삭제 | `findByJobApplicationIdOrderBySortOrderAscIdAsc`, `findByJobApplicationId`, `deleteByJobApplicationId`, `existsByJobApplicationIdAndAttachmentType` | `ApplicationAttachment` | replace 저장에 사용 |
| Request DTO | `com.shinyoung.recruit.dto.request` | `AttachmentReplaceRequest` | 첨부 metadata replace 요청 | `attachments` | `AttachmentRequest` | null이면 400, 빈 목록 허용 |
| Request DTO | `com.shinyoung.recruit.dto.request` | `AttachmentRequest` | 첨부 metadata row 요청 | `attachmentType`, `sectionType`, `sectionRecordId`, `originalFileName`, `storedFileName`, `storagePath`, `contentType`, `fileSize`, `sortOrder` | `ApplicationAttachmentService` | 길이/양수/정렬 검증 |
| Response DTO | `com.shinyoung.recruit.dto.response` | `AttachmentResponse` | 첨부 metadata row 응답 | `attachmentId`, `attachmentType`, `sectionType`, `sectionRecordId`, `originalFileName`, `contentType`, `fileSize`, `sortOrder`, `from` | `ApplicationAttachment` | `storedFileName`, `storagePath` 제외 |
| Service | `com.shinyoung.recruit.service` | `ApplicationAttachmentService` | 첨부 metadata 조회/replace 저장 | `getAttachments`, `replaceAttachments` | `ApplicationSectionAccessService`, `ApplicationAttachmentRepository` | 본인/DRAFT/기간/상태 검증 |
| Controller | `com.shinyoung.recruit.controller` | `ApplicationAttachmentController` | 지원자 첨부 metadata API | `getAttachments`, `replaceAttachments` | `CurrentApplicantService`, `ApplicationAttachmentService` | JSON metadata만 처리 |
| Test | `com.shinyoung.recruit.service` | `ApplicationAttachmentServiceTest` | Service 규칙 검증 | 저장/조회/replace/검증 실패 | `ApplicationAttachmentService` | 고정 Clock 사용 |
| Test | `com.shinyoung.recruit.controller` | `ApplicationAttachmentControllerTest` | API 계약 검증 | path/method/응답 포맷/노출 제한 | `ApplicationAttachmentController` | PUT/DELETE 미지원 확인 |

## Entity 관계 요약

- `ApplicationAttachment` N:1 `JobApplication`
- `JobApplication`에는 Attachment 컬렉션을 추가하지 않았다.
- cascade/orphanRemoval은 사용하지 않는다.
- replace 저장은 `applicationId` 기준 기존 row를 명시 삭제한 뒤 새 row를 저장한다.
- 목록 정렬은 `sortOrder ASC, id ASC`를 사용한다.

## AttachmentType 정책

- `RESUME`: 이력서
- `TRANSCRIPT`: 성적증명서
- `GRADUATION_CERTIFICATE`: 졸업증명서
- `CAREER_CERTIFICATE`: 경력증명서
- `CERTIFICATE_PROOF`: 자격증 증빙
- `LANGUAGE_SCORE_REPORT`: 어학성적 증빙
- `PORTFOLIO`: 포트폴리오
- `ETC`: 기타

enum name은 저장/비즈니스 규칙용 code로만 사용하고 화면 표시명은 별도 계층에서 다룬다.

## ApplicationSectionType 정책

- `APPLICATION`: 지원서 전체 공통 첨부
- `EDUCATION`, `CAREER`, `CERTIFICATE`, `LANGUAGE`, `MILITARY`, `AWARD`, `GAP_PERIOD`: 상세 섹션 첨부 후보
- `ETC`: 기타

`ApplicationSectionType`은 첨부 metadata의 귀속 힌트이며, `ApplicationSectionAccessService`의 config enabled 검증용 enum으로 사용하지 않는다.

## API 목록

| Method | Path | 목적 | Request | Response |
|---|---|---|---|---|
| GET | `/applications/{applicationId}/attachments` | 내 지원서 첨부 metadata 목록 조회 | 없음 | `ApiResponse<List<AttachmentResponse>>` |
| POST | `/applications/{applicationId}/attachments` | 내 지원서 첨부 metadata 목록 replace 저장 | `AttachmentReplaceRequest` | `ApiResponse<List<AttachmentResponse>>` |

## Request/Response DTO 구조

`AttachmentReplaceRequest`

- `attachments`: null 불가, 빈 목록 허용

`AttachmentRequest`

- `attachmentType`: null 불가
- `sectionType`: null 불가
- `sectionRecordId`: nullable
- `originalFileName`: blank 불가, 255자 이하
- `storedFileName`: blank 불가, 255자 이하
- `storagePath`: blank 불가, 1000자 이하
- `contentType`: blank 불가, 100자 이하
- `fileSize`: null 불가, 0보다 큼
- `sortOrder`: null 불가, 0 이상

`AttachmentResponse`

- `attachmentId`
- `attachmentType`
- `sectionType`
- `sectionRecordId`
- `originalFileName`
- `contentType`
- `fileSize`
- `sortOrder`

응답에는 `storedFileName`, `storagePath`를 포함하지 않는다.

## replace 저장 정책

1. `applicationId`와 `applicantId`로 본인 지원서를 조회한다.
2. `DRAFT`, `JobPosting.status=PUBLISHED`, 접수기간 내 조건을 `ApplicationSectionAccessService.validateWritable`로 검증한다.
3. Attachment는 현재 `ApplicationFormConfig` flag가 없으므로 enabled 검증을 하지 않는다.
4. replace request와 목록 null 여부를 검증한다.
5. 각 요청 row의 필수값, 길이, `fileSize`, `sortOrder`, `sectionType/sectionRecordId` 규칙을 검증한다.
6. 기존 row를 `applicationId` 기준 명시 삭제한다.
7. 새 row를 `saveAll`로 저장한다.
8. `sortOrder ASC, id ASC`로 다시 조회해 응답한다.

## Attachment config flag 미사용 정책

- 현재 `ApplicationFormConfig`에는 `useAttachment` 또는 attachment required policy가 없다.
- 따라서 Attachment metadata 저장은 `DRAFT`, PUBLISHED 공고, 접수기간 내 조건만 만족하면 허용한다.
- 추후 첨부 필수 정책이 필요하면 `ApplicationFormConfig` 확장 또는 별도 attachment policy 도입을 검토한다.

## 상태별 수정 정책

- `DRAFT`: 조회/replace 저장 가능
- `SUBMITTED`: 조회 가능, 저장 불가
- `WITHDRAWN`: 조회 가능, 저장 불가
- 타인 지원서는 조회/저장 모두 `JobApplicationNotFoundException`으로 숨김 처리한다.

## storagePath/storedFileName 응답 제외 정책

- `storedFileName`은 내부 저장 파일명이다.
- `storagePath`는 내부 저장 경로다.
- 지원자 응답에는 두 필드를 노출하지 않는다.
- 다운로드 URL 생성도 이번 Phase에서 하지 않는다.

## 실제 파일 업로드/다운로드 미구현 범위

- `MultipartFile`을 받지 않는다.
- multipart/form-data API를 만들지 않았다.
- 실제 파일 존재 여부를 검증하지 않는다.
- 파일 저장소, 파일 이동, 바이러스 검사, 다운로드 권한 검증은 후속 Phase로 분리한다.

## 개인정보/응답 제한 정책

- `originalFileName`은 사용자에게 표시 가능한 값으로 응답에 포함한다.
- `storedFileName`, `storagePath`는 내부 관리 값이므로 응답에서 제외한다.
- 관리자 상세 섹션 API에서는 원본 파일명 마스킹 여부와 다운로드 권한을 별도 정책으로 결정한다.

## 테스트 목록

- `ApplicationAttachmentServiceTest`
  - DRAFT 지원서 첨부 metadata replace 저장 성공
  - 저장 후 조회 성공
  - 정렬 확인
  - replace 시 기존 row 삭제 후 새 데이터만 남는지 확인
  - 빈 목록 replace 저장 허용 및 기존 데이터 삭제 확인
  - SUBMITTED/WITHDRAWN 저장 실패
  - 타인 지원서 조회/저장 실패
  - 접수기간 전/후 저장 실패
  - PUBLISHED가 아닌 JobPosting 저장 실패
  - 필수값 누락 실패
  - `fileSize <= 0` 실패
  - `sortOrder` null/중복 실패
  - 길이 제한 초과 실패
  - `sectionType=APPLICATION`에 `sectionRecordId`가 있으면 실패
  - `sectionRecordId <= 0` 실패
  - 응답 DTO에 내부 저장 필드가 없는지 확인
- `ApplicationAttachmentControllerTest`
  - GET 성공
  - POST 성공
  - validation 실패 400 + `ApiResponse.fail`
  - invalid enum 실패 400 + `ApiResponse.fail`
  - 타인 지원서 접근 404 + `ApiResponse.fail`
  - SUBMITTED 저장 실패 400 + `ApiResponse.fail`
  - PUT/DELETE 미지원
  - 응답 JSON에 `storedFileName`, `storagePath` 없음

## 실행한 테스트 명령

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationAttachmentServiceTest --tests com.shinyoung.recruit.controller.ApplicationAttachmentControllerTest
```

## 테스트 결과

- `ApplicationAttachmentServiceTest` 성공
- `ApplicationAttachmentControllerTest` 성공
- Education/Career/Certificate/Language/Military/Award/GapPeriod 상세 섹션 회귀 테스트 성공
- `./gradlew.bat clean test` 성공

## 남은 이슈

- Attachment submit 필수 정책은 아직 없다.
- `sectionRecordId`의 실제 상세 섹션 row 존재성 검증은 아직 하지 않는다.
- 실제 파일 업로드/다운로드/저장소 연동은 아직 없다.
- 저장 경로 암호화 또는 파일 접근 감사 로그는 후속 보안/파일 Phase에서 결정한다.

## 다음 Phase 추천

다음 Phase는 Phase 03c-7 `ApplicationSubmitValidator` 통합을 추천한다. 일반 상세 섹션과 Attachment metadata 저장/조회 구조가 마련되었으므로, 이제 `ApplicationFormConfig` 기반 최종제출 필수 검증을 `JobApplicationService.submit()`에 연결할 수 있다.
