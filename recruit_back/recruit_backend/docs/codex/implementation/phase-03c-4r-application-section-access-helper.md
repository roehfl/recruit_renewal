# Phase 03c-4R - Application Section Access Helper

## Phase 이름

Phase 03c-4R: Application detail section common access helper

## 목적

Education, Career, Certificate, Language, Military 상세 섹션 Service에 반복되던 지원서 소유자 조회, DRAFT 수정 가능 여부, 공고 상태/접수기간, `ApplicationFormConfig.useXxx` 검증을 최소 공통 helper로 추출한다.

## 구현 범위

- `ApplicationSectionAccessService` 추가
- 상세 섹션 공통 검증 추출
  - `findOwnedApplication(Long applicantId, Long applicationId)`
  - `validateWritable(JobApplication application)`
  - `validateEducationEnabled(JobApplication application)`
  - `validateCareerEnabled(JobApplication application)`
  - `validateCertificateEnabled(JobApplication application)`
  - `validateLanguageEnabled(JobApplication application)`
  - `validateMilitaryEnabled(JobApplication application)`
  - `validateAwardEnabled(JobApplication application)`
  - `validateGapPeriodEnabled(JobApplication application)`
- 기존 상세 섹션 Service에서 중복 검증 제거
- 기존 API path, DTO, 저장 정책, 응답 정책은 유지

## 미구현 범위

- Attachment
- StageResult
- `ApplicationSubmitValidator`
- `SectionType` enum 기반 일반화
- 관리자 상세 섹션 API
- SecurityConfig 권한 정책 변경
- `PUT`, HTTP `DELETE`

> 참고: Phase 03c-4R 작성 당시에는 Award, GapPeriod, Attachment 기능 자체가 미구현이었다. 이후 Phase 03c-5에서 Award/GapPeriod vertical slice가 구현되었고, 이 문서는 현재 상태 기준으로 미구현 범위를 Attachment 중심으로 정리한다.

## 변경 파일 목록

### 코드 변경

- `src/main/java/com/shinyoung/recruit/service/ApplicationSectionAccessService.java`
- `src/main/java/com/shinyoung/recruit/service/ApplicationEducationService.java`
- `src/main/java/com/shinyoung/recruit/service/ApplicationCareerService.java`
- `src/main/java/com/shinyoung/recruit/service/ApplicationCertificateService.java`
- `src/main/java/com/shinyoung/recruit/service/ApplicationLanguageService.java`
- `src/main/java/com/shinyoung/recruit/service/ApplicationMilitaryService.java`

### 테스트 변경

- 없음

### 문서 변경

- `docs/codex/implementation/phase-03c-4r-application-section-access-helper.md`
- `docs/codex/implementation/phase-03c-4-application-military.md`
- `docs/codex/design/phase-03-application-design.md`
- `docs/codex/design/phase-03c-application-detail-design.md`
- `docs/codex/07-implementation-history.md`

## 신규 클래스 목록

- `ApplicationSectionAccessService`

## 수정 클래스 목록

- `ApplicationEducationService`
- `ApplicationCareerService`
- `ApplicationCertificateService`
- `ApplicationLanguageService`
- `ApplicationMilitaryService`

## 클래스별 설명

| 구분 | 패키지 | 클래스 | 역할 | 주요 필드/메서드 | 연관 클래스 | 비고 |
|---|---|---|---|---|---|---|
| Service | `com.shinyoung.recruit.service` | `ApplicationSectionAccessService` | 상세 섹션 공통 접근/쓰기 검증 helper | `findOwnedApplication`, `validateWritable`, `validateEducationEnabled`, `validateCareerEnabled`, `validateCertificateEnabled`, `validateLanguageEnabled`, `validateMilitaryEnabled`, `validateAwardEnabled`, `validateGapPeriodEnabled` | `JobApplicationRepository`, `JobApplication`, `ApplicationFormConfig` | `SectionType` enum 없이 명시 메서드로 시작 |
| Service | `com.shinyoung.recruit.service` | `ApplicationEducationService` | Education 조회/replace 저장 | `sectionAccessService` 사용 | `ApplicationSectionAccessService` | 저장/검증 정책은 유지 |
| Service | `com.shinyoung.recruit.service` | `ApplicationCareerService` | Career 조회/replace 저장 | `sectionAccessService` 사용 | `ApplicationSectionAccessService` | 저장/검증 정책은 유지 |
| Service | `com.shinyoung.recruit.service` | `ApplicationCertificateService` | Certificate 조회/replace 저장 | `sectionAccessService` 사용 | `ApplicationSectionAccessService` | 저장/검증 정책은 유지 |
| Service | `com.shinyoung.recruit.service` | `ApplicationLanguageService` | Language 조회/replace 저장 | `sectionAccessService` 사용 | `ApplicationSectionAccessService` | 저장/검증 정책은 유지 |
| Service | `com.shinyoung.recruit.service` | `ApplicationMilitaryService` | Military 조회/upsert 저장 | `sectionAccessService` 사용 | `ApplicationSectionAccessService` | 저장/검증 정책은 유지 |

## API 목록

신규 API 없음.

기존 상세 섹션 API는 유지한다.

- `GET /applications/{applicationId}/educations`
- `POST /applications/{applicationId}/educations`
- `GET /applications/{applicationId}/careers`
- `POST /applications/{applicationId}/careers`
- `GET /applications/{applicationId}/certificates`
- `POST /applications/{applicationId}/certificates`
- `GET /applications/{applicationId}/languages`
- `POST /applications/{applicationId}/languages`
- `GET /applications/{applicationId}/military`
- `POST /applications/{applicationId}/military`
- `GET /applications/{applicationId}/awards`
- `POST /applications/{applicationId}/awards`
- `GET /applications/{applicationId}/gap-periods`
- `POST /applications/{applicationId}/gap-periods`

## Entity 관계 요약

Entity 관계 변경 없음.

## 주요 비즈니스 규칙

- 본인 지원서만 조회/수정 가능하다.
- 타인 지원서는 `JobApplicationNotFoundException`으로 숨긴다.
- 상세 섹션 저장은 `DRAFT` 상태에서만 가능하다.
- 상세 섹션 저장은 `JobPosting.status=PUBLISHED`이고 접수기간 내에서만 가능하다.
- 섹션별 저장은 해당 `ApplicationFormConfig.useXxx=true`일 때만 가능하다.
- 조회는 본인 지원서라면 `DRAFT`, `SUBMITTED`, `WITHDRAWN` 모두 가능하다.

## 테스트 목록

별도 신규 테스트는 추가하지 않고, 기존 상세 섹션 Service/Controller 테스트로 리팩터링 안전성을 검증했다.

- `ApplicationEducationServiceTest`
- `ApplicationEducationControllerTest`
- `ApplicationCareerServiceTest`
- `ApplicationCareerControllerTest`
- `ApplicationCertificateServiceTest`
- `ApplicationCertificateControllerTest`
- `ApplicationLanguageServiceTest`
- `ApplicationLanguageControllerTest`
- `ApplicationMilitaryServiceTest`
- `ApplicationMilitaryControllerTest`

## 테스트 명령

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationEducationServiceTest --tests com.shinyoung.recruit.controller.ApplicationEducationControllerTest --tests com.shinyoung.recruit.service.ApplicationCareerServiceTest --tests com.shinyoung.recruit.controller.ApplicationCareerControllerTest --tests com.shinyoung.recruit.service.ApplicationCertificateServiceTest --tests com.shinyoung.recruit.controller.ApplicationCertificateControllerTest --tests com.shinyoung.recruit.service.ApplicationLanguageServiceTest --tests com.shinyoung.recruit.controller.ApplicationLanguageControllerTest --tests com.shinyoung.recruit.service.ApplicationMilitaryServiceTest --tests com.shinyoung.recruit.controller.ApplicationMilitaryControllerTest
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test
```

## 테스트 결과

- 상세 섹션 Service/Controller 회귀 테스트: 성공
- `clean test`: 성공

## 남은 이슈

- `validateWritable`의 실패 메시지는 공통 메시지로 통일했다. API 계약은 예외 타입과 HTTP status 중심으로 유지한다.
- `ApplicationSubmitValidator`는 아직 구현하지 않았다.
- submit 시 병역 필수 정책은 Phase 03c-7에서 구현해야 한다.
- Award/GapPeriod 구현에서 이번 helper를 재사용했고, `validateAwardEnabled`, `validateGapPeriodEnabled`를 추가했다.

## 다음 Phase 추천

Phase 03c-6에서 Attachment metadata vertical slice를 구현하는 것을 추천한다. 일반 상세 섹션은 Education, Career, Certificate, Language, Military, Award, GapPeriod까지 구현되었으므로 submit validator로 바로 갈 수도 있지만, 첨부 metadata를 먼저 고정한 뒤 Phase 03c-7에서 `ApplicationSubmitValidator`를 통합하는 순서가 더 자연스럽다.
