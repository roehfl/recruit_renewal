# Phase 03c-4 - Application Military

## Phase 이름

Phase 03c-4: Application Military vertical slice

## 목적

`JobApplication` 하위 상세 섹션 중 병역사항을 구현한다. 지원자가 본인 지원서의 병역 단건 record를 조회하고, `DRAFT` 상태에서 upsert 방식으로 저장할 수 있게 한다.

## 구현 범위

- `ApplicationMilitary` Entity 추가
- 병역 관련 enum 추가
- `ApplicationMilitaryRepository` 추가
- 지원자 병역 조회/저장 Request/Response DTO 추가
- `ApplicationMilitaryService` 추가
- `ApplicationMilitaryController` 추가
- `GET /applications/{applicationId}/military`
- `POST /applications/{applicationId}/military`
- `ApplicationFormConfig.useMilitary=false` 저장 차단
- `DRAFT` 상태에서만 저장 허용
- `SUBMITTED`, `WITHDRAWN` 저장 차단
- 타인 지원서 접근 차단
- 병역 유형별 허용 필드 검증
- 복무 시작일/종료일 교차 검증
- invalid enum, validation 실패를 `ApiResponse.fail` 형식으로 반환

## 미구현 범위

- Award, GapPeriod, Attachment
- StageResult
- 자기소개서/질문답변
- 관리자 상세 섹션 API
- `ApplicationSubmitValidator`
- submit 시 `ApplicationMilitary` 1건 필수 검증 연결
- 병역 record clear/delete API
- `PUT`, HTTP `DELETE`
- SecurityConfig 권한 정책 변경
- CommonCode

## 변경 파일 목록

### 코드 변경

- `src/main/java/com/shinyoung/recruit/controller/ApplicationMilitaryController.java`
- `src/main/java/com/shinyoung/recruit/domain/entity/ApplicationMilitary.java`
- `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationMilitaryRepository.java`
- `src/main/java/com/shinyoung/recruit/dto/request/MilitarySaveRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/response/MilitaryResponse.java`
- `src/main/java/com/shinyoung/recruit/enumeration/MilitaryBranch.java`
- `src/main/java/com/shinyoung/recruit/enumeration/MilitaryRank.java`
- `src/main/java/com/shinyoung/recruit/enumeration/MilitaryServiceType.java`
- `src/main/java/com/shinyoung/recruit/enumeration/MilitarySubjectType.java`
- `src/main/java/com/shinyoung/recruit/service/ApplicationMilitaryService.java`

### 테스트 변경

- `src/test/java/com/shinyoung/recruit/controller/ApplicationMilitaryControllerTest.java`
- `src/test/java/com/shinyoung/recruit/service/ApplicationMilitaryServiceTest.java`

### 문서 변경

- `docs/codex/implementation/phase-03c-4-application-military.md`
- `docs/codex/design/phase-03-application-design.md`
- `docs/codex/design/phase-03c-application-detail-design.md`
- `docs/codex/07-implementation-history.md`

## 신규 클래스 목록

- `MilitarySubjectType`
- `MilitaryServiceType`
- `MilitaryBranch`
- `MilitaryRank`
- `ApplicationMilitary`
- `ApplicationMilitaryRepository`
- `MilitarySaveRequest`
- `MilitaryResponse`
- `ApplicationMilitaryService`
- `ApplicationMilitaryController`
- `ApplicationMilitaryServiceTest`
- `ApplicationMilitaryControllerTest`

## 수정 클래스 목록

- 없음

## 클래스별 설명

| 구분 | 패키지 | 클래스 | 역할 | 주요 필드/메서드 | 연관 클래스 | 비고 |
|---|---|---|---|---|---|---|
| Enum | `com.shinyoung.recruit.enumeration` | `MilitarySubjectType` | 병역 대상/비대상/복무완료/면제/해당없음 code | `SUBJECT`, `NOT_SUBJECT`, `COMPLETED`, `EXEMPTED`, `NOT_APPLICABLE` | `ApplicationMilitary` | 화면 표시명은 enum에 묶지 않음 |
| Enum | `com.shinyoung.recruit.enumeration` | `MilitaryServiceType` | 복무 구분 code | `ACTIVE_DUTY`, `SUPPLEMENTARY`, `PUBLIC_SERVICE`, `ETC` 등 | `ApplicationMilitary` | CommonCode 미사용 |
| Enum | `com.shinyoung.recruit.enumeration` | `MilitaryBranch` | 군별 code | `ARMY`, `NAVY`, `AIR_FORCE`, `MARINE`, `ETC` 등 | `ApplicationMilitary` | CommonCode 미사용 |
| Enum | `com.shinyoung.recruit.enumeration` | `MilitaryRank` | 계급 code | `PRIVATE`, `SERGEANT`, `CAPTAIN`, `MAJOR`, `ETC` 등 | `ApplicationMilitary` | CommonCode 미사용 |
| Entity | `com.shinyoung.recruit.domain.entity` | `ApplicationMilitary` | 지원서별 병역 단건 record | `jobApplication`, `militarySubjectType`, 복무/면제 필드, `create`, `update` | `JobApplication` | `job_application_id` unique, cascade/orphanRemoval 미사용 |
| Repository | `com.shinyoung.recruit.domain.repository` | `ApplicationMilitaryRepository` | 병역 단건 조회/삭제 | `findByJobApplicationId`, `deleteByJobApplicationId`, `existsByJobApplicationId` | `ApplicationMilitary` | upsert 조회에 사용 |
| Request DTO | `com.shinyoung.recruit.dto.request` | `MilitarySaveRequest` | 병역 저장 요청 | `militarySubjectType`, `serviceType`, `militaryBranch`, `rank`, `serviceStartDate`, `serviceEndDate`, `exemptionReason` | `ApplicationMilitaryService` | `militarySubjectType` 필수, 면제 사유 1000자 제한 |
| Response DTO | `com.shinyoung.recruit.dto.response` | `MilitaryResponse` | 병역 조회/저장 응답 | `militaryId`, 병역 유형/복무/면제 필드, `from` | `ApplicationMilitary` | 저장 전 조회는 data=null |
| Service | `com.shinyoung.recruit.service` | `ApplicationMilitaryService` | 병역 조회/upsert 저장 | `getMilitary`, `saveMilitary` | `JobApplicationRepository`, `ApplicationMilitaryRepository` | 본인/상태/기간/config/유형별 필드 검증 |
| Controller | `com.shinyoung.recruit.controller` | `ApplicationMilitaryController` | 지원자 병역 API | `getMilitary`, `saveMilitary` | `CurrentApplicantService`, `ApplicationMilitaryService` | 기존 인증 helper 재사용 |
| Test | `com.shinyoung.recruit.service` | `ApplicationMilitaryServiceTest` | Service 규칙 검증 | 저장/조회/upsert/검증 실패 | `ApplicationMilitaryService` | 고정 Clock 사용 |
| Test | `com.shinyoung.recruit.controller` | `ApplicationMilitaryControllerTest` | API 계약 검증 | path/method/응답 포맷 | `ApplicationMilitaryController` | PUT/DELETE 미지원 확인 |

## Entity 관계 요약

- `ApplicationMilitary` 1:1 `JobApplication`
- `ApplicationMilitary`가 `JobApplication`을 `LAZY @OneToOne`으로 참조한다.
- `JobApplication`에는 `ApplicationMilitary` 필드를 추가하지 않았다.
- cascade/orphanRemoval은 사용하지 않았다.
- `application_military.job_application_id`는 unique로 둔다.

## MilitarySubjectType 정책

| MilitarySubjectType | 의미 | DRAFT 저장 정책 | 상세 필드 정책 |
|---|---|---|---|
| `SUBJECT` | 병역 대상 | 저장 가능 | 복무/면제 상세 필드가 있으면 400 |
| `NOT_SUBJECT` | 병역 비대상 | 저장 가능 | 복무/면제 상세 필드가 있으면 400 |
| `NOT_APPLICABLE` | 해당 없음 | 저장 가능 | 복무/면제 상세 필드가 있으면 400 |
| `COMPLETED` | 복무 완료 | 저장 가능 | 복무 상세 필드 허용, `exemptionReason` 있으면 400 |
| `EXEMPTED` | 면제 | 저장 가능 | `exemptionReason` 허용, 복무 상세 필드 있으면 400 |

## API 목록

| Method | Path | 목적 | Request | Response |
|---|---|---|---|---|
| GET | `/applications/{applicationId}/military` | 내 지원서 병역 조회 | 없음 | `ApiResponse<MilitaryResponse>` |
| POST | `/applications/{applicationId}/military` | 내 지원서 병역 upsert 저장 | `MilitarySaveRequest` | `ApiResponse<MilitaryResponse>` |

## Request/Response DTO 구조

`MilitarySaveRequest`

- `militarySubjectType`: null 불가
- `serviceType`
- `militaryBranch`
- `rank`
- `serviceStartDate`
- `serviceEndDate`
- `exemptionReason`: 최대 1000자

`MilitaryResponse`

- `militaryId`
- `militarySubjectType`
- `serviceType`
- `militaryBranch`
- `rank`
- `serviceStartDate`
- `serviceEndDate`
- `exemptionReason`

## upsert 저장 정책

1. `applicationId`와 `applicantId`로 본인 지원서를 조회한다.
2. `DRAFT`, `JobPosting.status=PUBLISHED`, 접수기간 내, `useMilitary=true`를 검증한다.
3. request와 `militarySubjectType` null 여부를 검증한다.
4. 복무 시작일/종료일과 병역 유형별 허용 필드를 검증한다.
5. 기존 `ApplicationMilitary` record가 있으면 `update`로 갱신한다.
6. 기존 record가 없으면 `create` 후 저장한다.
7. 저장된 record를 `MilitaryResponse`로 반환한다.

## useMilitary 연동 정책

- 저장은 `ApplicationFormConfig.useMilitary=true`일 때만 가능하다.
- `useMilitary=false`이면 `InvalidJobApplicationException`으로 400 처리한다.
- 조회는 본인 지원서라면 `DRAFT`, `SUBMITTED`, `WITHDRAWN` 모두 허용한다.
- 저장 전 record가 없으면 조회 응답의 `data`는 null이다.
- submit 시 `useMilitary=true`이면 `ApplicationMilitary` 1건 필수 정책은 Phase 03c-7에서 연결한다.

## 상태별 수정 정책

| JobApplicationStatus | 조회 | 저장 |
|---|---|---|
| `DRAFT` | 가능 | 가능 |
| `SUBMITTED` | 가능 | 불가 |
| `WITHDRAWN` | 가능 | 불가 |

저장은 추가로 `JobPosting.status=PUBLISHED`이고 접수기간 내여야 한다.

## 날짜 검증 정책

- `serviceStartDate`와 `serviceEndDate`가 모두 있으면 `serviceStartDate <= serviceEndDate`여야 한다.
- `COMPLETED`에서 복무기간 필수 여부는 DRAFT 저장에서는 강제하지 않는다.
- `EXEMPTED`에서 면제 사유 필수 여부는 DRAFT 저장에서는 강제하지 않는다.
- 필수 여부는 Phase 03c-7 submit validator에서 재검토한다.

## 개인정보/응답 제한 정책

- 응답에는 CI, ciHash, 전화번호, 이메일, 주소, 비밀번호 등 계정 개인정보를 포함하지 않는다.
- `exemptionReason`은 민감정보 후보로 보고, 관리자 상세 응답에서는 마스킹 또는 암호화를 후속 Phase에서 검토한다.
- 지원자 본인 응답에는 입력값 확인을 위해 `exemptionReason`을 반환한다.

## 테스트 목록

- DRAFT 지원서에 `SUBJECT` 저장 성공
- DRAFT 지원서에 `NOT_SUBJECT` 저장 성공
- DRAFT 지원서에 `NOT_APPLICABLE` 저장 성공
- DRAFT 지원서에 `COMPLETED` 저장 성공
- DRAFT 지원서에 `EXEMPTED` 저장 성공
- 저장 후 조회 성공
- 기존 Military record upsert 갱신 확인
- 저장 전 GET은 data null 정책 확인
- `useMilitary=false` 저장 실패
- `SUBMITTED`, `WITHDRAWN` 상태 저장 실패
- 타인 지원서 저장/조회 실패
- 접수기간 전/후 저장 실패
- PUBLISHED가 아닌 JobPosting 저장 실패
- `militarySubjectType=null` 실패
- `serviceStartDate > serviceEndDate` 실패
- `SUBJECT`, `NOT_SUBJECT`, `NOT_APPLICABLE` 상세 필드 입력 실패
- `COMPLETED`에 `exemptionReason` 입력 실패
- `EXEMPTED`에 복무 상세 필드 입력 실패
- `exemptionReason` 길이 제한 초과 실패
- Controller 성공/validation/invalid enum/error response 검증
- `PUT`, HTTP `DELETE` 미지원 확인

## 테스트 명령

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationMilitaryServiceTest --tests com.shinyoung.recruit.controller.ApplicationMilitaryControllerTest
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationEducationServiceTest --tests com.shinyoung.recruit.controller.ApplicationEducationControllerTest --tests com.shinyoung.recruit.service.ApplicationCareerServiceTest --tests com.shinyoung.recruit.controller.ApplicationCareerControllerTest --tests com.shinyoung.recruit.service.ApplicationCertificateServiceTest --tests com.shinyoung.recruit.controller.ApplicationCertificateControllerTest --tests com.shinyoung.recruit.service.ApplicationLanguageServiceTest --tests com.shinyoung.recruit.controller.ApplicationLanguageControllerTest
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test
```

## 테스트 결과

- `ApplicationMilitaryServiceTest`, `ApplicationMilitaryControllerTest`: 성공
- Education/Career/Certificate/Language 상세 섹션 회귀 테스트: 성공
- `clean test`: 성공

## 남은 이슈

- Education, Career, Certificate, Language, Military에서 반복되던 본인 지원서 조회, DRAFT, PUBLISHED, 접수기간, config enabled 검증은 Phase 03c-4R에서 `ApplicationSectionAccessService`로 최소 추출했다.
- `ApplicationSubmitValidator`는 아직 연결하지 않았다.
- submit 시 `useMilitary=true`이면 `ApplicationMilitary` 1건 필수 검증을 Phase 03c-7에서 구현해야 한다.
- `COMPLETED` 복무기간 필수, `EXEMPTED` 면제 사유 필수 여부는 submit validator에서 확정한다.
- 관리자 상세 섹션 응답의 면제 사유 마스킹/암호화 정책은 후속 관리자 상세 Phase에서 확정한다.

## 다음 Phase 추천

Phase 03c-4R에서 공통 helper를 정리했으므로, 다음 기능 Phase는 Phase 03c-5 Award + GapPeriod vertical slice를 추천한다.
