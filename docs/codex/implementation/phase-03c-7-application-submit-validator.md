# Phase 03c-7 - Application Submit Validator

## Phase Summary

Phase 03c-7은 `JobApplicationService.submit()`에 `ApplicationFormConfig` 기반 상세 섹션 최종제출 검증을 연결한 작업이다. DRAFT 저장 단계에서는 허용하던 미완성 상태를 최종제출 직전에 검증하며, 실패 시 기존 `InvalidJobApplicationException` 기반 400 응답 정책을 유지한다.

## Implemented Scope

- `ApplicationSubmitValidator` 신규 추가
- `JobApplicationService.submit()`에 submit validator 호출 연결
- `ApplicationEducationRepository.existsByJobApplicationId` 추가
- `ApplicationCareerRepository.existsByJobApplicationId` 추가
- Education, Career, Military 필수 섹션 제출 검증 구현
- Certificate, Language, Award, GapPeriod, Attachment는 이번 Phase에서 선택 섹션으로 유지
- Validator 단위 테스트 추가
- JobApplicationService submit 연결 테스트 보강
- ApplicationController submit 검증 실패 API 테스트 보강
- 상세 섹션 회귀 테스트 fixture의 `ApplicationFormConfig` flag를 테스트 목적에 맞게 축소

## Not Implemented

- 상세 섹션 저장 API 변경
- 상세 섹션 Entity 구조 변경
- Attachment 실제 파일 업로드, 다운로드, 저장소 연동
- `MultipartFile`, multipart/form-data API
- 파일 삭제 API
- 관리자 상세 섹션 API
- StageResult
- 자기소개서/질문답변 도메인
- `PUT`, HTTP `DELETE`
- CommonCode
- SecurityConfig 대규모 변경

## Changed Files

### Code

- `src/main/java/com/shinyoung/recruit/service/ApplicationSubmitValidator.java`
- `src/main/java/com/shinyoung/recruit/service/JobApplicationService.java`
- `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationEducationRepository.java`
- `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationCareerRepository.java`

### Test

- `src/test/java/com/shinyoung/recruit/service/ApplicationSubmitValidatorTest.java`
- `src/test/java/com/shinyoung/recruit/service/JobApplicationServiceTest.java`
- `src/test/java/com/shinyoung/recruit/controller/ApplicationControllerTest.java`
- `src/test/java/com/shinyoung/recruit/service/ApplicationEducationServiceTest.java`
- `src/test/java/com/shinyoung/recruit/controller/ApplicationEducationControllerTest.java`
- `src/test/java/com/shinyoung/recruit/service/ApplicationCareerServiceTest.java`
- `src/test/java/com/shinyoung/recruit/controller/ApplicationCareerControllerTest.java`
- `src/test/java/com/shinyoung/recruit/service/ApplicationCertificateServiceTest.java`
- `src/test/java/com/shinyoung/recruit/controller/ApplicationCertificateControllerTest.java`
- `src/test/java/com/shinyoung/recruit/service/ApplicationLanguageServiceTest.java`
- `src/test/java/com/shinyoung/recruit/controller/ApplicationLanguageControllerTest.java`
- `src/test/java/com/shinyoung/recruit/service/ApplicationMilitaryServiceTest.java`
- `src/test/java/com/shinyoung/recruit/controller/ApplicationMilitaryControllerTest.java`
- `src/test/java/com/shinyoung/recruit/service/ApplicationAwardServiceTest.java`
- `src/test/java/com/shinyoung/recruit/controller/ApplicationAwardControllerTest.java`
- `src/test/java/com/shinyoung/recruit/service/ApplicationGapPeriodServiceTest.java`
- `src/test/java/com/shinyoung/recruit/controller/ApplicationGapPeriodControllerTest.java`
- `src/test/java/com/shinyoung/recruit/service/ApplicationAttachmentServiceTest.java`
- `src/test/java/com/shinyoung/recruit/controller/ApplicationAttachmentControllerTest.java`

### Documentation

- `docs/codex/implementation/phase-03c-7-application-submit-validator.md`
- `docs/codex/design/phase-03-application-design.md`
- `docs/codex/design/phase-03c-application-detail-design.md`
- `docs/codex/07-implementation-history.md`
- `docs/codex/reports/phase-03c-7-application-submit-validator.html`

## New Classes

- `ApplicationSubmitValidator`
- `ApplicationSubmitValidatorTest`

## Modified Classes

- `JobApplicationService`
- `ApplicationEducationRepository`
- `ApplicationCareerRepository`
- `JobApplicationServiceTest`
- `ApplicationControllerTest`
- 상세 섹션 Service/Controller 테스트 fixture

## Class-by-Class Explanation

| 구분 | 패키지 | 클래스 | 책임 | 주요 필드/메서드 | 관련 클래스 | 구현 메모 |
|---|---|---|---|---|---|---|
| Service | `com.shinyoung.recruit.service` | `ApplicationSubmitValidator` | 최종제출 전 상세 섹션 필수값 검증 | `validate`, `validateEducation`, `validateCareer`, `validateMilitary` | `JobApplication`, `ApplicationFormConfig`, 상세 섹션 Repository | 상태 전이는 수행하지 않고 검증만 담당 |
| Repository | `com.shinyoung.recruit.domain.repository` | `ApplicationEducationRepository` | Education 존재 여부 조회 | `existsByJobApplicationId` | `ApplicationEducation` | `useEducation=true` 제출 검증용 |
| Repository | `com.shinyoung.recruit.domain.repository` | `ApplicationCareerRepository` | Career row 존재 여부 조회 | `existsByJobApplicationId` | `ApplicationCareer` | `CareerType`별 제출 검증용 |
| Service | `com.shinyoung.recruit.service` | `JobApplicationService` | 지원서 command 흐름 | `submit` | `ApplicationSubmitValidator` | 기존 submit 검증 이후, `application.submit(now)` 이전에 validator 호출 |
| Test | `com.shinyoung.recruit.service` | `ApplicationSubmitValidatorTest` | validator 정책 단위 검증 | Education/Career/Military/Optional 테스트 | 상세 섹션 Repository mock | DB 없이 정책만 검증 |
| Test | `com.shinyoung.recruit.service` | `JobApplicationServiceTest` | submit 연결 검증 | 필수 섹션 누락 실패, DRAFT/submittedAt 유지 | `JobApplicationService` | validator 실패 시 상태 미변경 확인 |
| Test | `com.shinyoung.recruit.controller` | `ApplicationControllerTest` | submit API 계약 검증 | 상세 검증 실패 400 | `ApplicationController` | `ApiResponse.fail` 형태 유지 |

## API List

신규 API는 없다. 기존 `POST /applications/{applicationId}/submit` 내부 검증만 강화했다.

## Entity Relationship Summary

Entity 관계는 변경하지 않았다. `JobApplication`에 상세 섹션 컬렉션을 추가하지 않았고 cascade/orphanRemoval도 추가하지 않았다. Validator는 Repository 기반 존재 여부 조회만 수행한다.

## Submit Validator Policy

| Section | Config flag | Submit policy |
|---|---|---|
| Education | `useEducation` | true이면 `ApplicationEducation` 최소 1건 필수 |
| Career | `useCareer` | true이면 `ApplicationCareerProfile` 필수, `CareerType`별 row 검증 |
| Certificate | `useCertificate` | 선택 섹션, row 없어도 제출 가능 |
| Language | `useLanguage` | 선택 섹션, row 없어도 제출 가능 |
| Military | `useMilitary` | true이면 `ApplicationMilitary` 1건 및 유형별 최소값 필수 |
| Award | `useAward` | 선택 섹션, row 없어도 제출 가능 |
| GapPeriod | `useGapPeriod` | 선택 섹션, row 없어도 제출 가능 |
| Attachment | 없음 | 필수 검증 없음 |

## Business Rules

- Education: `useEducation=true`이면 `ApplicationEducation` 최소 1건 필수. `EducationSemesterGrade` 최소 1건은 이번 Phase에서 강제하지 않는다.
- Career: `useCareer=true`이면 profile 필수, `careerType` null/`NOT_SELECTED` 실패, `EXPERIENCED`는 Career row 필수, `NEWCOMER`/`NOT_APPLICABLE`은 Career row가 있으면 실패.
- Military: `useMilitary=true`이면 record 필수, `militarySubjectType` 필수, `COMPLETED`는 복무 시작/종료일 필수, `EXEMPTED`는 blank가 아닌 면제 사유 필수.
- Optional sections: Certificate, Language, Award, GapPeriod는 최소 row를 강제하지 않는다.
- Attachment: `ApplicationFormConfig`에 flag가 없어 submit 필수 검증에서 제외한다.

## JobApplicationService.submit Flow

1. `applicantId + applicationId`로 본인 지원서를 조회한다.
2. 기존 검증을 유지한다: PUBLISHED 공고, 접수기간, DRAFT 상태, form config 존재, 모집분야 소속.
3. `ApplicationSubmitValidator.validate(application)`을 호출한다.
4. 검증 통과 후 `application.submit(now)`로 상태를 변경한다.

Validator 실패 시 `status`는 `DRAFT`로 남고 `submittedAt`은 null을 유지한다.

## Test Coverage

- `ApplicationSubmitValidatorTest`: Education/Career/Military/Optional 정책 단위 검증
- `JobApplicationServiceTest`: 필수 섹션 누락 submit 실패, DRAFT 및 `submittedAt=null` 유지
- `ApplicationControllerTest`: submit 상세 검증 실패 시 `400 + ApiResponse.fail`
- 상세 섹션 Service/Controller 회귀 테스트: Education, Career, Certificate, Language, Military, Award, GapPeriod, Attachment

## Test Commands

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationSubmitValidatorTest
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.JobApplicationServiceTest
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.controller.ApplicationControllerTest
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationEducationServiceTest --tests com.shinyoung.recruit.controller.ApplicationEducationControllerTest --tests com.shinyoung.recruit.service.ApplicationCareerServiceTest --tests com.shinyoung.recruit.controller.ApplicationCareerControllerTest --tests com.shinyoung.recruit.service.ApplicationCertificateServiceTest --tests com.shinyoung.recruit.controller.ApplicationCertificateControllerTest --tests com.shinyoung.recruit.service.ApplicationLanguageServiceTest --tests com.shinyoung.recruit.controller.ApplicationLanguageControllerTest --tests com.shinyoung.recruit.service.ApplicationMilitaryServiceTest --tests com.shinyoung.recruit.controller.ApplicationMilitaryControllerTest --tests com.shinyoung.recruit.service.ApplicationAwardServiceTest --tests com.shinyoung.recruit.controller.ApplicationAwardControllerTest --tests com.shinyoung.recruit.service.ApplicationGapPeriodServiceTest --tests com.shinyoung.recruit.controller.ApplicationGapPeriodControllerTest --tests com.shinyoung.recruit.service.ApplicationAttachmentServiceTest --tests com.shinyoung.recruit.controller.ApplicationAttachmentControllerTest
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test
```

## Test Result

- `ApplicationSubmitValidatorTest`: 성공
- `JobApplicationServiceTest`: 성공
- `ApplicationControllerTest`: 성공
- Education/Career/Certificate/Language/Military/Award/GapPeriod/Attachment 상세 섹션 회귀 테스트: 성공
- `./gradlew.bat clean test`: 성공

## Known Limitations

- Certificate, Language, Award, GapPeriod는 현재 선택 섹션으로만 검증한다.
- Attachment 필수 정책은 `ApplicationFormConfig` 확장 전까지 보류한다.
- Education 성적 필수 여부는 공고별 세부 flag가 없어 보류한다.
- 관리자 상세 섹션 조회 API는 아직 없다.

## Next Phase Considerations

- 관리자 상세 섹션 조회 API 확장
- Attachment 필수 정책 또는 `ApplicationFormConfig.useAttachment` 확장 검토
- 자기소개서/질문답변 도메인 설계 및 submit 검증 연결
- StageResult 구현 전 Application 상세 aggregate 조회 범위 확정
