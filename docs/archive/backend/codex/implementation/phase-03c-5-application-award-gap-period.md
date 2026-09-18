# Phase 03c-5 - Application Award + GapPeriod

## Phase 이름

Phase 03c-5: Application Award + GapPeriod vertical slice

## 목적

`JobApplication` 하위 상세 섹션 중 수상/포상사항과 공백기간을 구현한다. 지원자는 본인 지원서의 수상/공백기간 목록을 조회할 수 있고, 지원서가 `DRAFT` 상태일 때 replace 방식으로 저장할 수 있다.

## 구현 범위

- `GapType` enum 추가
- `ApplicationAward` Entity 추가
- `ApplicationGapPeriod` Entity 추가
- Award/GapPeriod Repository 추가
- 지원자 Award/GapPeriod 조회/replace Request/Response DTO 추가
- `ApplicationSectionAccessService`에 `validateAwardEnabled`, `validateGapPeriodEnabled` 추가
- `ApplicationAwardService` 추가
- `ApplicationGapPeriodService` 추가
- `ApplicationAwardController` 추가
- `ApplicationGapPeriodController` 추가
- `GET /applications/{applicationId}/awards`
- `POST /applications/{applicationId}/awards`
- `GET /applications/{applicationId}/gap-periods`
- `POST /applications/{applicationId}/gap-periods`
- `ApplicationFormConfig.useAward=false` 저장 차단
- `ApplicationFormConfig.useGapPeriod=false` 저장 차단
- `DRAFT` 상태에서만 저장 허용
- `SUBMITTED`, `WITHDRAWN` 저장 차단
- 타인 지원서 접근 차단
- GapPeriod 기간 검증
- description 2000자 제한을 DTO와 Service에서 검증
- invalid enum, validation 실패를 `ApiResponse.fail` 형식으로 반환

## 미구현 범위

- Attachment
- StageResult
- 자기소개서/질문답변
- 관리자 상세 섹션 API
- `ApplicationSubmitValidator`
- submit 시 Award/GapPeriod 필수 검증 연결
- 개별 Award/GapPeriod 삭제 API
- 공백기간 overlap 검증
- `PUT`, HTTP `DELETE`
- SecurityConfig 권한 정책 변경
- CommonCode

## 변경 파일 목록

### 코드 변경

- `src/main/java/com/shinyoung/recruit/controller/ApplicationAwardController.java`
- `src/main/java/com/shinyoung/recruit/controller/ApplicationGapPeriodController.java`
- `src/main/java/com/shinyoung/recruit/domain/entity/ApplicationAward.java`
- `src/main/java/com/shinyoung/recruit/domain/entity/ApplicationGapPeriod.java`
- `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationAwardRepository.java`
- `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationGapPeriodRepository.java`
- `src/main/java/com/shinyoung/recruit/dto/request/AwardReplaceRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/request/AwardRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/request/GapPeriodReplaceRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/request/GapPeriodRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/response/AwardResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/GapPeriodResponse.java`
- `src/main/java/com/shinyoung/recruit/enumeration/GapType.java`
- `src/main/java/com/shinyoung/recruit/service/ApplicationAwardService.java`
- `src/main/java/com/shinyoung/recruit/service/ApplicationGapPeriodService.java`
- `src/main/java/com/shinyoung/recruit/service/ApplicationSectionAccessService.java`

### 테스트 변경

- `src/test/java/com/shinyoung/recruit/service/ApplicationAwardServiceTest.java`
- `src/test/java/com/shinyoung/recruit/service/ApplicationGapPeriodServiceTest.java`
- `src/test/java/com/shinyoung/recruit/controller/ApplicationAwardControllerTest.java`
- `src/test/java/com/shinyoung/recruit/controller/ApplicationGapPeriodControllerTest.java`

### 문서 변경

- `docs/codex/implementation/phase-03c-5-application-award-gap-period.md`
- `docs/codex/implementation/phase-03c-4r-application-section-access-helper.md`
- `docs/codex/design/phase-03-application-design.md`
- `docs/codex/design/phase-03c-application-detail-design.md`
- `docs/codex/07-implementation-history.md`

## 신규 클래스 목록

- `GapType`
- `ApplicationAward`
- `ApplicationGapPeriod`
- `ApplicationAwardRepository`
- `ApplicationGapPeriodRepository`
- `AwardReplaceRequest`
- `AwardRequest`
- `GapPeriodReplaceRequest`
- `GapPeriodRequest`
- `AwardResponse`
- `GapPeriodResponse`
- `ApplicationAwardService`
- `ApplicationGapPeriodService`
- `ApplicationAwardController`
- `ApplicationGapPeriodController`
- `ApplicationAwardServiceTest`
- `ApplicationGapPeriodServiceTest`
- `ApplicationAwardControllerTest`
- `ApplicationGapPeriodControllerTest`

## 수정 클래스 목록

- `ApplicationSectionAccessService`

## 클래스별 설명

| 구분 | 패키지 | 클래스 | 역할 | 주요 필드/메서드 | 연관 클래스 | 비고 |
|---|---|---|---|---|---|---|
| Enum | `com.shinyoung.recruit.enumeration` | `GapType` | 공백기간 유형 code | `EDUCATION`, `CAREER`, `OTHER` | `ApplicationGapPeriod` | CommonCode 미사용 |
| Entity | `com.shinyoung.recruit.domain.entity` | `ApplicationAward` | 지원서별 수상/포상 row | `awardName`, `awardingOrganization`, `awardDate`, `description`, `sortOrder`, `create` | `JobApplication` | cascade/orphanRemoval 미사용 |
| Entity | `com.shinyoung.recruit.domain.entity` | `ApplicationGapPeriod` | 지원서별 공백기간 row | `startDate`, `endDate`, `gapType`, `reason`, `description`, `sortOrder`, `create` | `JobApplication`, `GapType` | overlap 검증은 보류 |
| Repository | `com.shinyoung.recruit.domain.repository` | `ApplicationAwardRepository` | Award row 조회/삭제 | `findByJobApplicationIdOrderBySortOrderAscIdAsc`, `findByJobApplicationId`, `deleteByJobApplicationId` | `ApplicationAward` | replace 저장에 사용 |
| Repository | `com.shinyoung.recruit.domain.repository` | `ApplicationGapPeriodRepository` | GapPeriod row 조회/삭제 | `findByJobApplicationIdOrderBySortOrderAscIdAsc`, `findByJobApplicationId`, `deleteByJobApplicationId` | `ApplicationGapPeriod` | replace 저장에 사용 |
| Request DTO | `com.shinyoung.recruit.dto.request` | `AwardReplaceRequest` | Award replace 요청 | `awards` | `AwardRequest` | null이면 400, 빈 목록 허용 |
| Request DTO | `com.shinyoung.recruit.dto.request` | `AwardRequest` | Award row 요청 | `awardName`, `awardingOrganization`, `awardDate`, `description`, `sortOrder` | `ApplicationAwardService` | description 2000자 제한 |
| Request DTO | `com.shinyoung.recruit.dto.request` | `GapPeriodReplaceRequest` | GapPeriod replace 요청 | `gapPeriods` | `GapPeriodRequest` | null이면 400, 빈 목록 허용 |
| Request DTO | `com.shinyoung.recruit.dto.request` | `GapPeriodRequest` | GapPeriod row 요청 | `startDate`, `endDate`, `gapType`, `reason`, `description`, `sortOrder` | `ApplicationGapPeriodService` | 날짜 교차 검증은 Service |
| Response DTO | `com.shinyoung.recruit.dto.response` | `AwardResponse` | Award row 응답 | `awardId`, 수상 필드, `from` | `ApplicationAward` | 계정 민감정보 미포함 |
| Response DTO | `com.shinyoung.recruit.dto.response` | `GapPeriodResponse` | GapPeriod row 응답 | `gapPeriodId`, 기간/유형/사유 필드, `from` | `ApplicationGapPeriod` | 계정 민감정보 미포함 |
| Service | `com.shinyoung.recruit.service` | `ApplicationSectionAccessService` | 상세 섹션 공통 접근/쓰기 검증 helper | `validateAwardEnabled`, `validateGapPeriodEnabled` 추가 | `ApplicationFormConfig` | 기존 helper 범위 유지 |
| Service | `com.shinyoung.recruit.service` | `ApplicationAwardService` | Award 조회/replace 저장 | `getAwards`, `replaceAwards` | `ApplicationSectionAccessService`, `ApplicationAwardRepository` | 상태/기간/config/소유자 검증 |
| Service | `com.shinyoung.recruit.service` | `ApplicationGapPeriodService` | GapPeriod 조회/replace 저장 | `getGapPeriods`, `replaceGapPeriods` | `ApplicationSectionAccessService`, `ApplicationGapPeriodRepository` | 기간/정렬/길이 검증 |
| Controller | `com.shinyoung.recruit.controller` | `ApplicationAwardController` | 지원자 Award API | `getAwards`, `replaceAwards` | `CurrentApplicantService`, `ApplicationAwardService` | 기존 인증 helper 재사용 |
| Controller | `com.shinyoung.recruit.controller` | `ApplicationGapPeriodController` | 지원자 GapPeriod API | `getGapPeriods`, `replaceGapPeriods` | `CurrentApplicantService`, `ApplicationGapPeriodService` | 기존 인증 helper 재사용 |
| Test | `com.shinyoung.recruit.service` | `ApplicationAwardServiceTest` | Award Service 규칙 검증 | 저장/조회/replace/검증 실패 | `ApplicationAwardService` | 고정 Clock 사용 |
| Test | `com.shinyoung.recruit.service` | `ApplicationGapPeriodServiceTest` | GapPeriod Service 규칙 검증 | 저장/조회/replace/검증 실패 | `ApplicationGapPeriodService` | 고정 Clock 사용 |
| Test | `com.shinyoung.recruit.controller` | `ApplicationAwardControllerTest` | Award API 계약 검증 | path/method/응답 포맷 | `ApplicationAwardController` | PUT/DELETE 미지원 확인 |
| Test | `com.shinyoung.recruit.controller` | `ApplicationGapPeriodControllerTest` | GapPeriod API 계약 검증 | path/method/응답 포맷 | `ApplicationGapPeriodController` | PUT/DELETE 미지원 확인 |

## Entity 관계 요약

- `ApplicationAward` N:1 `JobApplication`
- `ApplicationGapPeriod` N:1 `JobApplication`
- `JobApplication`에는 Award/GapPeriod 컬렉션을 추가하지 않았다.
- cascade/orphanRemoval은 사용하지 않는다.
- replace 저장은 `applicationId` 기준 기존 row를 명시 삭제한 뒤 새 row를 저장한다.
- 목록 정렬은 `sortOrder ASC, id ASC`를 사용한다.

## GapType 정책

| GapType | 의미 | 비고 |
|---|---|---|
| `EDUCATION` | 학업 공백 | 지원자가 직접 선택 |
| `CAREER` | 경력 공백 | 지원자가 직접 선택 |
| `OTHER` | 기타 공백 | 지원자가 직접 선택 |

## API 목록

| Method | Path | 목적 | Request | Response |
|---|---|---|---|---|
| GET | `/applications/{applicationId}/awards` | 내 지원서 수상/포상 목록 조회 | 없음 | `ApiResponse<List<AwardResponse>>` |
| POST | `/applications/{applicationId}/awards` | 내 지원서 수상/포상 목록 replace 저장 | `AwardReplaceRequest` | `ApiResponse<List<AwardResponse>>` |
| GET | `/applications/{applicationId}/gap-periods` | 내 지원서 공백기간 목록 조회 | 없음 | `ApiResponse<List<GapPeriodResponse>>` |
| POST | `/applications/{applicationId}/gap-periods` | 내 지원서 공백기간 목록 replace 저장 | `GapPeriodReplaceRequest` | `ApiResponse<List<GapPeriodResponse>>` |

## Request/Response DTO 구조

`AwardReplaceRequest`

- `awards`: null 불가, 빈 목록 허용

`AwardRequest`

- `awardName`: blank 불가
- `awardingOrganization`: blank 불가
- `awardDate`: null 불가
- `description`: 2000자 이하
- `sortOrder`: null 불가, 0 이상

`AwardResponse`

- `awardId`
- `awardName`
- `awardingOrganization`
- `awardDate`
- `description`
- `sortOrder`

`GapPeriodReplaceRequest`

- `gapPeriods`: null 불가, 빈 목록 허용

`GapPeriodRequest`

- `startDate`: null 불가
- `endDate`: null 불가
- `gapType`: null 불가
- `reason`: blank 불가
- `description`: 2000자 이하
- `sortOrder`: null 불가, 0 이상

`GapPeriodResponse`

- `gapPeriodId`
- `startDate`
- `endDate`
- `gapType`
- `reason`
- `description`
- `sortOrder`

## replace 저장 정책

1. `applicationId`와 `applicantId`로 본인 지원서를 조회한다.
2. `DRAFT`, `JobPosting.status=PUBLISHED`, 접수기간 내 조건을 검증한다.
3. Award는 `ApplicationFormConfig.useAward=true`, GapPeriod는 `useGapPeriod=true`를 검증한다.
4. replace request와 목록 null 여부를 검증한다.
5. 새 요청 row의 필수값, 날짜, description 길이, `sortOrder` 중복을 검증한다.
6. 기존 row를 `applicationId` 기준 명시 삭제한다.
7. 새 row를 `saveAll`로 저장한다.
8. `sortOrder ASC, id ASC`로 다시 조회해 응답한다.

## useAward/useGapPeriod 연동 정책

- Award 저장은 `ApplicationFormConfig.useAward=true`일 때만 가능하다.
- GapPeriod 저장은 `ApplicationFormConfig.useGapPeriod=true`일 때만 가능하다.
- disabled 섹션 저장은 `InvalidJobApplicationException`으로 400 처리한다.
- 조회는 본인 지원서이면 모든 상태에서 허용한다.
- submit 시 Award/GapPeriod 필수 검증은 Phase 03c-7에서 연결한다.

## 상태별 수정 정책

| JobApplicationStatus | 조회 | 저장 |
|---|---|---|
| `DRAFT` | 가능 | 가능 |
| `SUBMITTED` | 가능 | 불가 |
| `WITHDRAWN` | 가능 | 불가 |

저장은 추가로 `JobPosting.status=PUBLISHED`이고 접수기간 내여야 한다.

## 날짜 검증 정책

- Award: `awardDate`는 필수다.
- GapPeriod: `startDate`, `endDate`는 필수다.
- GapPeriod: `startDate <= endDate`여야 한다.
- GapPeriod overlap 검증은 이번 Phase에서 하지 않는다.

## 개인정보/응답 제한 정책

Award/GapPeriod 응답에는 CI, ciHash, 전화번호, 이메일, 주소, 비밀번호 등 계정 개인정보를 포함하지 않는다. 수상 설명과 공백기간 사유/설명은 개인정보나 민감한 서술이 들어갈 수 있으므로 관리자 목록에는 포함하지 않고, 관리자 상세 섹션 확장 시 노출/마스킹 정책을 별도 결정한다.

## 테스트 목록

- DRAFT 지원서에 수상 목록 replace 저장 성공
- 수상 저장 후 조회 성공
- 여러 Award 정렬 확인
- replace 저장 시 기존 Award 삭제 후 새 데이터만 남는지 확인
- 빈 awards 목록 replace 저장 허용과 기존 데이터 삭제 확인
- `useAward=false` 저장 실패
- `SUBMITTED`, `WITHDRAWN` 저장 실패
- 타인 지원서 저장/조회 실패
- 접수기간 전/후 저장 실패
- PUBLISHED가 아닌 JobPosting 저장 실패
- `awardName`, `awardingOrganization`, `awardDate` 누락 실패
- `sortOrder` 중복 실패
- description 2000자 초과 실패
- DRAFT 지원서에 공백기간 목록 replace 저장 성공
- 공백기간 저장 후 조회 성공
- 여러 GapPeriod 정렬 확인
- replace 저장 시 기존 GapPeriod 삭제 후 새 데이터만 남는지 확인
- 빈 gapPeriods 목록 replace 저장 허용과 기존 데이터 삭제 확인
- `useGapPeriod=false` 저장 실패
- `startDate`, `endDate`, `gapType`, `reason` 누락 실패
- `startDate > endDate` 실패
- `sortOrder` 중복 실패
- description 2000자 초과 실패
- Controller GET/POST 성공
- validation 실패 400 + `ApiResponse.fail`
- invalid enum 실패 400 + `ApiResponse.fail`
- disabled 섹션 저장 실패 400 + `ApiResponse.fail`
- 타인 지원서 접근 404 + `ApiResponse.fail`
- `SUBMITTED` 저장 실패 400 + `ApiResponse.fail`
- PUT/DELETE 미지원 확인

## 테스트 명령

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationAwardServiceTest --tests com.shinyoung.recruit.controller.ApplicationAwardControllerTest --tests com.shinyoung.recruit.service.ApplicationGapPeriodServiceTest --tests com.shinyoung.recruit.controller.ApplicationGapPeriodControllerTest
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationEducationServiceTest --tests com.shinyoung.recruit.controller.ApplicationEducationControllerTest --tests com.shinyoung.recruit.service.ApplicationCareerServiceTest --tests com.shinyoung.recruit.controller.ApplicationCareerControllerTest --tests com.shinyoung.recruit.service.ApplicationCertificateServiceTest --tests com.shinyoung.recruit.controller.ApplicationCertificateControllerTest --tests com.shinyoung.recruit.service.ApplicationLanguageServiceTest --tests com.shinyoung.recruit.controller.ApplicationLanguageControllerTest --tests com.shinyoung.recruit.service.ApplicationMilitaryServiceTest --tests com.shinyoung.recruit.controller.ApplicationMilitaryControllerTest
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test
```

## 테스트 결과

- `ApplicationAwardServiceTest`, `ApplicationAwardControllerTest`, `ApplicationGapPeriodServiceTest`, `ApplicationGapPeriodControllerTest`: 성공
- Education/Career/Certificate/Language/Military 상세 섹션 회귀 테스트: 성공
- `clean test`: 성공

## 남은 이슈

- `ApplicationSubmitValidator`는 아직 연결하지 않았다.
- submit 시 Award/GapPeriod 최소 row 필수 여부는 현재 기본 선택으로 두고, Phase 03c-7에서 최종 검증 정책을 통합한다.
- GapPeriod overlap 검증은 아직 하지 않는다. 운영 정책상 중복 기간 차단이 필요하면 후속 Phase에서 추가한다.
- 관리자 Application 상세 응답에는 아직 Award/GapPeriod 섹션이 포함되지 않는다.
- 수상 설명과 공백기간 사유/설명의 관리자 노출/마스킹 정책은 관리자 상세 섹션 확장 시 결정한다.

## 다음 Phase 추천

다음 Phase는 Attachment metadata vertical slice를 추천한다. 일반 상세 섹션은 Education, Career, Certificate, Language, Military, Award, GapPeriod까지 구현되었으므로 `ApplicationSubmitValidator`를 먼저 붙일 수도 있지만, 첨부 metadata의 저장/조회 구조와 attachment type, 저장 경로 비노출 정책을 먼저 고정한 뒤 submit validator를 통합하는 편이 안전하다. 실제 파일 업로드/다운로드 저장소 연동은 별도 Phase로 분리한다.
