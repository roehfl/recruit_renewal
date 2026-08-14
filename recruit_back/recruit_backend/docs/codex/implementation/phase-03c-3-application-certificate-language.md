# Phase 03c-3 - Application Certificate + Language

> **변경 주의(2026-06-23)**: 본 문서의 Language `score`/`grade` 서술은 이후 슬라이스에서 단일 `scoreOrGrade`로 통합되고 `conversationalAbility`(LANGUAGE_CONVERSATION 코드)가 추가되었다. 현재 계약/스키마는 `07-implementation-history.md`(2026-06-23 항목)와 `recruit/api-contract.md` 어학 섹션을 따른다.

## Phase 이름

Phase 03c-3: Application Certificate + Language vertical slice

## 목적

`JobApplication` 하위 상세 섹션 중 자격사항과 어학사항을 구현한다. 지원자가 본인 지원서의 자격/어학 목록을 조회하고, `DRAFT` 상태에서 replace 방식으로 저장할 수 있게 한다.

## 구현 범위

- `ApplicationCertificate` Entity 추가
- `ApplicationLanguage` Entity 추가
- Certificate/Language Repository 추가
- 지원자 Certificate/Language 조회/replace Request/Response DTO 추가
- `ApplicationCertificateService` 추가
- `ApplicationLanguageService` 추가
- `ApplicationCertificateController` 추가
- `ApplicationLanguageController` 추가
- `GET /applications/{applicationId}/certificates`
- `POST /applications/{applicationId}/certificates`
- `GET /applications/{applicationId}/languages`
- `POST /applications/{applicationId}/languages`
- `ApplicationFormConfig.useCertificate=false` 저장 차단
- `ApplicationFormConfig.useLanguage=false` 저장 차단
- `DRAFT` 상태에서만 저장 허용
- `SUBMITTED`, `WITHDRAWN` 저장 차단
- 타인 지원서 접근 차단
- 자격 취득일/만료일, 어학 응시일/만료일 교차 검증
- invalid enum, validation 실패를 `ApiResponse.fail` 형식으로 반환

## 구현하지 않은 범위

- Education/Career 동작 변경
- Military, Award, GapPeriod, Attachment
- StageResult
- 자기소개서/질문답변
- 관리자 상세 섹션 API
- `ApplicationSubmitValidator`
- submit 시 Certificate/Language 필수 검증 연결
- 개별 Certificate/Language 삭제 API
- `PUT`, HTTP `DELETE`
- SecurityConfig 권한 정책 변경
- CommonCode

## 변경 파일 목록

### 코드 변경

- `src/main/java/com/shinyoung/recruit/controller/ApplicationCertificateController.java`
- `src/main/java/com/shinyoung/recruit/controller/ApplicationLanguageController.java`
- `src/main/java/com/shinyoung/recruit/domain/entity/ApplicationCertificate.java`
- `src/main/java/com/shinyoung/recruit/domain/entity/ApplicationLanguage.java`
- `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationCertificateRepository.java`
- `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationLanguageRepository.java`
- `src/main/java/com/shinyoung/recruit/dto/request/CertificateReplaceRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/request/CertificateRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/request/LanguageReplaceRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/request/LanguageRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/response/CertificateResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/LanguageResponse.java`
- `src/main/java/com/shinyoung/recruit/service/ApplicationCertificateService.java`
- `src/main/java/com/shinyoung/recruit/service/ApplicationLanguageService.java`

### 테스트 변경

- `src/test/java/com/shinyoung/recruit/service/ApplicationCertificateServiceTest.java`
- `src/test/java/com/shinyoung/recruit/service/ApplicationLanguageServiceTest.java`
- `src/test/java/com/shinyoung/recruit/controller/ApplicationCertificateControllerTest.java`
- `src/test/java/com/shinyoung/recruit/controller/ApplicationLanguageControllerTest.java`

### 문서 변경

- `docs/codex/implementation/phase-03c-3-application-certificate-language.md`
- `docs/codex/design/phase-03-application-design.md`
- `docs/codex/design/phase-03c-application-detail-design.md`
- `docs/codex/07-implementation-history.md`

## 신규 클래스 목록

- `ApplicationCertificate`
- `ApplicationLanguage`
- `ApplicationCertificateRepository`
- `ApplicationLanguageRepository`
- `CertificateReplaceRequest`
- `CertificateRequest`
- `LanguageReplaceRequest`
- `LanguageRequest`
- `CertificateResponse`
- `LanguageResponse`
- `ApplicationCertificateService`
- `ApplicationLanguageService`
- `ApplicationCertificateController`
- `ApplicationLanguageController`
- `ApplicationCertificateServiceTest`
- `ApplicationLanguageServiceTest`
- `ApplicationCertificateControllerTest`
- `ApplicationLanguageControllerTest`

## 수정 클래스 목록

- 없음

## 클래스별 설명

| 구분 | 패키지 | 클래스 | 역할 | 주요 필드/메서드 | 연관 클래스 | 비고 |
|---|---|---|---|---|---|---|
| Entity | `com.shinyoung.recruit.domain.entity` | `ApplicationCertificate` | 지원서별 자격 row | `certificateName`, `issuingOrganization`, `acquiredDate`, `expiredDate`, `sortOrder`, `create` | `JobApplication` | cascade/orphanRemoval 미사용 |
| Entity | `com.shinyoung.recruit.domain.entity` | `ApplicationLanguage` | 지원서별 어학 row | `languageName`, `testName`, `score`, `grade`, `examDate`, `expiredDate`, `sortOrder`, `create` | `JobApplication` | score/grade 둘 다 DRAFT 저장에서는 nullable |
| Repository | `com.shinyoung.recruit.domain.repository` | `ApplicationCertificateRepository` | Certificate row 조회/삭제 | `findByJobApplicationIdOrderBySortOrderAscIdAsc`, `findByJobApplicationId`, `deleteByJobApplicationId` | `ApplicationCertificate` | replace 저장에 사용 |
| Repository | `com.shinyoung.recruit.domain.repository` | `ApplicationLanguageRepository` | Language row 조회/삭제 | `findByJobApplicationIdOrderBySortOrderAscIdAsc`, `findByJobApplicationId`, `deleteByJobApplicationId` | `ApplicationLanguage` | replace 저장에 사용 |
| Request DTO | `com.shinyoung.recruit.dto.request` | `CertificateReplaceRequest` | Certificate replace 요청 | `certificates` | `CertificateRequest` | null이면 400, 빈 목록 허용 |
| Request DTO | `com.shinyoung.recruit.dto.request` | `CertificateRequest` | Certificate row 요청 | `certificateName`, `issuingOrganization`, `acquiredDate`, `expiredDate`, `sortOrder` | `ApplicationCertificateService` | 날짜 교차 검증은 Service |
| Request DTO | `com.shinyoung.recruit.dto.request` | `LanguageReplaceRequest` | Language replace 요청 | `languages` | `LanguageRequest` | null이면 400, 빈 목록 허용 |
| Request DTO | `com.shinyoung.recruit.dto.request` | `LanguageRequest` | Language row 요청 | `languageName`, `testName`, `score`, `grade`, `examDate`, `expiredDate`, `sortOrder` | `ApplicationLanguageService` | 날짜 교차 검증은 Service |
| Response DTO | `com.shinyoung.recruit.dto.response` | `CertificateResponse` | Certificate row 응답 | `certificateId`, 자격 필드, `from` | `ApplicationCertificate` | 민감 계정 개인정보 미포함 |
| Response DTO | `com.shinyoung.recruit.dto.response` | `LanguageResponse` | Language row 응답 | `languageId`, 어학 필드, `from` | `ApplicationLanguage` | 민감 계정 개인정보 미포함 |
| Service | `com.shinyoung.recruit.service` | `ApplicationCertificateService` | Certificate 조회/replace 저장 | `getCertificates`, `replaceCertificates` | `JobApplicationRepository`, `ApplicationCertificateRepository` | 상태/기간/config/소유자 검증 |
| Service | `com.shinyoung.recruit.service` | `ApplicationLanguageService` | Language 조회/replace 저장 | `getLanguages`, `replaceLanguages` | `JobApplicationRepository`, `ApplicationLanguageRepository` | 상태/기간/config/소유자 검증 |
| Controller | `com.shinyoung.recruit.controller` | `ApplicationCertificateController` | 지원자 Certificate API | `getCertificates`, `replaceCertificates` | `CurrentApplicantService`, `ApplicationCertificateService` | 기존 인증 helper 재사용 |
| Controller | `com.shinyoung.recruit.controller` | `ApplicationLanguageController` | 지원자 Language API | `getLanguages`, `replaceLanguages` | `CurrentApplicantService`, `ApplicationLanguageService` | 기존 인증 helper 재사용 |
| Test | `com.shinyoung.recruit.service` | `ApplicationCertificateServiceTest` | Certificate Service 규칙 검증 | 저장/조회/replace/검증 실패 | `ApplicationCertificateService` | 고정 Clock 사용 |
| Test | `com.shinyoung.recruit.service` | `ApplicationLanguageServiceTest` | Language Service 규칙 검증 | 저장/조회/replace/검증 실패 | `ApplicationLanguageService` | 고정 Clock 사용 |
| Test | `com.shinyoung.recruit.controller` | `ApplicationCertificateControllerTest` | Certificate API 계약 검증 | path/method/응답 포맷 | `ApplicationCertificateController` | PUT/DELETE 미지원 확인 |
| Test | `com.shinyoung.recruit.controller` | `ApplicationLanguageControllerTest` | Language API 계약 검증 | path/method/응답 포맷 | `ApplicationLanguageController` | PUT/DELETE 미지원 확인 |

## Entity 관계 요약

- `ApplicationCertificate` N:1 `JobApplication`
- `ApplicationLanguage` N:1 `JobApplication`
- `JobApplication`에는 Certificate/Language 컬렉션을 추가하지 않았다.
- cascade/orphanRemoval은 사용하지 않는다.
- replace 저장은 `applicationId` 기준 명시 삭제 후 새 row를 저장한다.
- 목록 정렬은 `sortOrder ASC, id ASC`를 사용한다.

## API 목록

| Method | Path | 목적 | Request | Response |
|---|---|---|---|---|
| GET | `/applications/{applicationId}/certificates` | 내 지원서 자격 목록 조회 | 없음 | `ApiResponse<List<CertificateResponse>>` |
| POST | `/applications/{applicationId}/certificates` | 내 지원서 자격 목록 replace 저장 | `CertificateReplaceRequest` | `ApiResponse<List<CertificateResponse>>` |
| GET | `/applications/{applicationId}/languages` | 내 지원서 어학 목록 조회 | 없음 | `ApiResponse<List<LanguageResponse>>` |
| POST | `/applications/{applicationId}/languages` | 내 지원서 어학 목록 replace 저장 | `LanguageReplaceRequest` | `ApiResponse<List<LanguageResponse>>` |

## Request/Response DTO 구조

`CertificateReplaceRequest`

- `certificates`: null 불가, 빈 목록 허용

`CertificateRequest`

- `certificateName`: blank 불가
- `issuingOrganization`: blank 불가
- `acquiredDate`: null 불가
- `certificateNumber`
- `expiredDate`
- `scoreOrGrade`
- `sortOrder`: null 불가, 0 이상

`CertificateResponse`

- `certificateId`
- `certificateName`
- `issuingOrganization`
- `acquiredDate`
- `certificateNumber`
- `expiredDate`
- `scoreOrGrade`
- `sortOrder`

`LanguageReplaceRequest`

- `languages`: null 불가, 빈 목록 허용

`LanguageRequest`

- `languageName`: blank 불가
- `testName`: blank 불가
- `score`
- `grade`
- `examDate`: null 불가
- `expiredDate`
- `issuingOrganization`
- `sortOrder`: null 불가, 0 이상

`LanguageResponse`

- `languageId`
- `languageName`
- `testName`
- `score`
- `grade`
- `examDate`
- `expiredDate`
- `issuingOrganization`
- `sortOrder`

## replace 저장 정책

1. `applicationId`와 `applicantId`로 본인 지원서를 조회한다.
2. `DRAFT`, `JobPosting.status=PUBLISHED`, 접수기간 내 조건을 검증한다.
3. Certificate는 `ApplicationFormConfig.useCertificate=true`, Language는 `useLanguage=true`를 검증한다.
4. replace request와 목록 null 여부를 검증한다.
5. 새 요청 row의 필수값, 날짜, `sortOrder` 중복을 검증한다.
6. 기존 row를 `applicationId` 기준 명시 삭제한다.
7. 새 row를 `saveAll`로 저장한다.
8. `sortOrder ASC, id ASC`로 다시 조회해 응답한다.

## useCertificate/useLanguage 연동 정책

- Certificate 저장은 `ApplicationFormConfig.useCertificate=true`일 때만 가능하다.
- Language 저장은 `ApplicationFormConfig.useLanguage=true`일 때만 가능하다.
- disabled 섹션 저장은 `InvalidJobApplicationException`으로 400 처리한다.
- 조회는 본인 지원서이면 모든 상태에서 허용한다.
- submit 시 Certificate/Language 필수 검증은 Phase 03c-7에서 연결한다.

## 상태별 수정 정책

| JobApplicationStatus | 조회 | 저장 |
|---|---|---|
| `DRAFT` | 가능 | 가능 |
| `SUBMITTED` | 가능 | 불가 |
| `WITHDRAWN` | 가능 | 불가 |

저장은 추가로 `JobPosting.status=PUBLISHED`이고 접수기간 내여야 한다.

## 날짜 검증 정책

- Certificate: `expiredDate`가 있으면 `acquiredDate <= expiredDate`여야 한다.
- Language: `expiredDate`가 있으면 `examDate <= expiredDate`여야 한다.
- Language의 `score`, `grade`는 둘 다 비어 있어도 DRAFT 저장에서는 허용한다. submit 필수 여부는 Phase 03c-7에서 재검토한다.

## 개인정보/응답 제한 정책

Certificate/Language 응답에는 CI, ciHash, 전화번호, 이메일, 주소, 비밀번호 등 계정 개인정보를 포함하지 않는다. 자격번호는 현재 지원자 본인 응답에는 포함하지만, 관리자 상세 응답의 마스킹/노출 정책은 후속 관리자 상세 섹션 Phase에서 결정한다.

## 테스트 목록

- DRAFT 지원서에 자격 목록 replace 저장 성공
- 자격 저장 후 조회 성공
- 여러 Certificate 정렬 확인
- replace 저장 시 기존 Certificate 삭제 후 새 데이터만 남는지 확인
- 빈 certificates 목록 replace 저장 허용과 기존 데이터 삭제 확인
- `useCertificate=false` 저장 실패
- `SUBMITTED`, `WITHDRAWN` 저장 실패
- 타인 지원서 저장/조회 실패
- 접수기간 전/후 저장 실패
- `PUBLISHED`가 아닌 JobPosting 저장 실패
- `certificateName`, `issuingOrganization`, `acquiredDate` 누락 실패
- `sortOrder` 중복 실패
- `acquiredDate > expiredDate` 실패
- DRAFT 지원서에 어학 목록 replace 저장 성공
- 어학 저장 후 조회 성공
- 여러 Language 정렬 확인
- replace 저장 시 기존 Language 삭제 후 새 데이터만 남는지 확인
- 빈 languages 목록 replace 저장 허용과 기존 데이터 삭제 확인
- `useLanguage=false` 저장 실패
- `languageName`, `testName`, `examDate` 누락 실패
- `examDate > expiredDate` 실패
- Controller GET/POST 성공
- validation 실패 400 + `ApiResponse.fail`
- disabled 섹션 저장 실패 400 + `ApiResponse.fail`
- 타인 지원서 접근 404 + `ApiResponse.fail`
- `SUBMITTED` 저장 실패 400 + `ApiResponse.fail`
- PUT/DELETE 미지원 확인

## 실행한 테스트 명령

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationCertificateServiceTest --tests com.shinyoung.recruit.controller.ApplicationCertificateControllerTest --tests com.shinyoung.recruit.service.ApplicationLanguageServiceTest --tests com.shinyoung.recruit.controller.ApplicationLanguageControllerTest
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationEducationServiceTest --tests com.shinyoung.recruit.controller.ApplicationEducationControllerTest --tests com.shinyoung.recruit.service.ApplicationCareerServiceTest --tests com.shinyoung.recruit.controller.ApplicationCareerControllerTest
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test
```

## 테스트 결과

- `ApplicationCertificateServiceTest`, `ApplicationCertificateControllerTest`, `ApplicationLanguageServiceTest`, `ApplicationLanguageControllerTest`: 성공
- `ApplicationEducationServiceTest`, `ApplicationEducationControllerTest`, `ApplicationCareerServiceTest`, `ApplicationCareerControllerTest`: 성공
- 전체 `clean test`: 성공

## 남은 이슈

- submit 시 `useCertificate=true`, `useLanguage=true`일 때 최소 row 필수 여부는 Phase 03c-7에서 확정한다.
- Language의 score/grade 필수 여부는 DRAFT 저장에서는 강제하지 않았고, submit validator에서 재검토한다.
- 관리자 Application 상세 응답에는 아직 Certificate/Language 섹션이 포함되지 않는다.
- 자격번호의 관리자 마스킹/암호화 여부는 관리자 상세 섹션 확장 시 결정한다.
- Education/Career/Certificate/Language에서 본인 지원서 조회, DRAFT 상태, PUBLISHED 공고, 접수기간, config enabled 검증이 반복되고 있다. Military까지 구현한 뒤 `ApplicationSectionAccessService` 같은 최소 공통 helper 추출을 검토한다.
- helper 후보 범위는 `findOwnedApplication(Long applicantId, Long applicationId)`, `validateWritable(JobApplication application)`, `validateEnabled(JobApplication application, SectionType sectionType)` 정도로 제한한다.
- `certificateNumber`, `scoreOrGrade`, Language의 `score`, `grade`, `issuingOrganization`에는 아직 길이 제한을 두지 않았다. 운영 DB schema를 엄격하게 가져갈 때 `@Column(length = ...)` 또는 DTO `@Size`를 검토한다.

## 다음 Phase 추천

다음 Phase는 `useMilitary=true` submit 필수 1건 정책이 이미 정리된 Military vertical slice를 추천한다. 이후 Award + GapPeriod, Attachment metadata, `ApplicationSubmitValidator` 통합 순서로 진행하면 상세 섹션의 저장 구조와 제출 검증을 자연스럽게 이어갈 수 있다.
