# Phase 03c-2 - Application Career

## Phase 이름

Phase 03c-2: Application Career vertical slice

## 목적

`JobApplication` 하위 상세 섹션 중 경력사항을 구현한다. 지원자가 본인 지원서의 경력 선택 상태와 경력 row 목록을 조회하고, `DRAFT` 상태에서 replace 방식으로 저장할 수 있게 한다.

## 구현 범위

- `CareerType`, `EmploymentType` enum 추가
- `ApplicationCareerProfile` 단건 Entity 추가
- `ApplicationCareer` 다건 Entity 추가
- Career profile/career row Repository 추가
- 지원자 Career 조회/replace Request/Response DTO 추가
- `ApplicationCareerService` 추가
- `ApplicationCareerController` 추가
- `GET /applications/{applicationId}/careers`
- `POST /applications/{applicationId}/careers`
- `ApplicationFormConfig.useCareer=false` 저장 차단
- `DRAFT` 상태에서만 저장 허용
- `SUBMITTED`, `WITHDRAWN` 저장 차단
- 타인 지원서 접근 차단
- invalid enum, validation 실패를 `ApiResponse.fail` 형식으로 반환

## 구현하지 않은 범위

- Education 동작 변경
- Certificate, Language, Military, Award, GapPeriod, Attachment
- StageResult
- 자기소개서/질문답변
- 관리자 상세 섹션 API
- `ApplicationSubmitValidator`
- submit 시 Career 필수 검증 연결
- 개별 Career 삭제 API
- `PUT`, HTTP `DELETE`
- SecurityConfig 권한 정책 변경
- CommonCode

## 변경 파일 목록

### 코드 변경

- `src/main/java/com/shinyoung/recruit/controller/ApplicationCareerController.java`
- `src/main/java/com/shinyoung/recruit/domain/entity/ApplicationCareer.java`
- `src/main/java/com/shinyoung/recruit/domain/entity/ApplicationCareerProfile.java`
- `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationCareerRepository.java`
- `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationCareerProfileRepository.java`
- `src/main/java/com/shinyoung/recruit/dto/request/CareerReplaceRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/request/CareerRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/response/CareerResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/CareerItemResponse.java`
- `src/main/java/com/shinyoung/recruit/enumeration/CareerType.java`
- `src/main/java/com/shinyoung/recruit/enumeration/EmploymentType.java`
- `src/main/java/com/shinyoung/recruit/service/ApplicationCareerService.java`

### 테스트 변경

- `src/test/java/com/shinyoung/recruit/service/ApplicationCareerServiceTest.java`
- `src/test/java/com/shinyoung/recruit/controller/ApplicationCareerControllerTest.java`

### 문서 변경

- `docs/codex/implementation/phase-03c-2-application-career.md`
- `docs/codex/design/phase-03-application-design.md`
- `docs/codex/design/phase-03c-application-detail-design.md`
- `docs/codex/07-implementation-history.md`

## 신규 클래스 목록

- `CareerType`
- `EmploymentType`
- `ApplicationCareerProfile`
- `ApplicationCareer`
- `ApplicationCareerProfileRepository`
- `ApplicationCareerRepository`
- `CareerReplaceRequest`
- `CareerRequest`
- `CareerResponse`
- `CareerItemResponse`
- `ApplicationCareerService`
- `ApplicationCareerController`
- `ApplicationCareerServiceTest`
- `ApplicationCareerControllerTest`

## 수정 클래스 목록

- 없음

## 클래스별 설명

| 구분 | 패키지 | 클래스 | 역할 | 주요 필드/메서드 | 연관 클래스 | 비고 |
|---|---|---|---|---|---|---|
| Enum | `com.shinyoung.recruit.enumeration` | `CareerType` | 지원서별 경력 선택 상태 | `NOT_SELECTED`, `NEWCOMER`, `EXPERIENCED`, `NOT_APPLICABLE` | `ApplicationCareerProfile` | submit 검증은 후속 Phase |
| Enum | `com.shinyoung.recruit.enumeration` | `EmploymentType` | 고용형태 code | `FULL_TIME`, `CONTRACT`, `INTERN`, `FREELANCE`, `PART_TIME`, `ETC` | `ApplicationCareer` | CommonCode 미사용 |
| Entity | `com.shinyoung.recruit.domain.entity` | `ApplicationCareerProfile` | 지원서별 경력 선택 상태 단건 record | `jobApplication`, `careerType`, `updateCareerType` | `JobApplication` | `job_application_id` unique |
| Entity | `com.shinyoung.recruit.domain.entity` | `ApplicationCareer` | 지원서별 경력 row | `companyName`, `employmentType`, `startDate`, `endDate`, `currentlyEmployed`, `sortOrder`, `create` | `JobApplication` | cascade/orphanRemoval 미사용 |
| Repository | `com.shinyoung.recruit.domain.repository` | `ApplicationCareerProfileRepository` | Career profile 조회/삭제 | `findByJobApplicationId`, `deleteByJobApplicationId`, `existsByJobApplicationId` | `ApplicationCareerProfile` | profile upsert에 사용 |
| Repository | `com.shinyoung.recruit.domain.repository` | `ApplicationCareerRepository` | Career row 조회/삭제 | `findByJobApplicationIdOrderBySortOrderAscIdAsc`, `deleteByJobApplicationId` | `ApplicationCareer` | replace 저장에 사용 |
| Request DTO | `com.shinyoung.recruit.dto.request` | `CareerReplaceRequest` | Career replace 요청 | `careerType`, `careers` | `CareerRequest` | null이면 400 |
| Request DTO | `com.shinyoung.recruit.dto.request` | `CareerRequest` | Career row 요청 | `companyName`, `startDate`, `endDate`, `currentlyEmployed`, `sortOrder` | `ApplicationCareerService` | 교차 검증은 Service |
| Response DTO | `com.shinyoung.recruit.dto.response` | `CareerResponse` | Career 섹션 응답 | `careerType`, `careers` | `CareerItemResponse` | profile 미저장 시 `NOT_SELECTED` |
| Response DTO | `com.shinyoung.recruit.dto.response` | `CareerItemResponse` | Career row 응답 | `careerId`, 회사/기간/업무 필드, `from` | `ApplicationCareer` | 민감 원문 개인정보 미포함 |
| Service | `com.shinyoung.recruit.service` | `ApplicationCareerService` | Career 조회/replace 저장 | `getCareers`, `replaceCareers` | `JobApplicationRepository`, Career repositories | 상태/기간/config/소유자 검증 |
| Controller | `com.shinyoung.recruit.controller` | `ApplicationCareerController` | 지원자 Career API | `getCareers`, `replaceCareers` | `CurrentApplicantService`, `ApplicationCareerService` | 기존 인증 helper 재사용 |
| Test | `com.shinyoung.recruit.service` | `ApplicationCareerServiceTest` | Service 규칙 검증 | 저장/조회/replace/검증 실패 | `ApplicationCareerService` | 고정 Clock 사용 |
| Test | `com.shinyoung.recruit.controller` | `ApplicationCareerControllerTest` | API 계약 검증 | path/method/응답 포맷 | `ApplicationCareerController` | PUT/DELETE 미지원 확인 |

## Entity 관계 요약

- `ApplicationCareerProfile` 1:1 `JobApplication`
- `ApplicationCareer` N:1 `JobApplication`
- `JobApplication`에는 Career 관련 컬렉션/필드를 추가하지 않았다.
- cascade/orphanRemoval은 사용하지 않는다.
- Career row replace 저장은 `applicationId` 기준 명시 삭제 후 새 row를 저장한다.

## CareerType 정책

| CareerType | 의미 | DRAFT 저장 정책 | submit 검증 후보 |
|---|---|---|---|
| `NOT_SELECTED` | 아직 선택하지 않음 | `careers`는 비어 있어야 함 | `useCareer=true`이면 실패 후보 |
| `NEWCOMER` | 신입/경력 없음 | `careers`는 비어 있어야 함 | 통과 후보 |
| `EXPERIENCED` | 경력 있음 | 빈 목록도 허용 | 최소 1개 필수 후보 |
| `NOT_APPLICABLE` | 해당 없음 | `careers`는 비어 있어야 함 | 통과 후보 |

## API 목록

| Method | Path | 목적 | Request | Response |
|---|---|---|---|---|
| GET | `/applications/{applicationId}/careers` | 내 지원서 경력 조회 | 없음 | `ApiResponse<CareerResponse>` |
| POST | `/applications/{applicationId}/careers` | 내 지원서 경력 replace 저장 | `CareerReplaceRequest` | `ApiResponse<CareerResponse>` |

## Request/Response DTO 구조

`CareerReplaceRequest`

- `careerType`: null 불가
- `careers`: null 불가, 빈 목록 허용

`CareerRequest`

- `companyName`
- `departmentName`
- `positionTitle`
- `employmentType`
- `startDate`
- `endDate`
- `currentlyEmployed`
- `responsibilities`
- `resignationReason`
- `sortOrder`

`CareerResponse`

- `careerType`
- `careers: List<CareerItemResponse>`

## replace 저장 정책

1. `applicationId`와 `applicantId`로 본인 지원서를 조회한다.
2. `DRAFT`, `JobPosting.status=PUBLISHED`, 접수기간 내, `useCareer=true`를 검증한다.
3. `careerType`, `careers` null 여부와 careerType별 row 허용 여부를 검증한다.
4. Career row 필수값, 기간, 길이, `sortOrder` 중복을 검증한다.
5. 기존 profile이 있으면 `careerType`을 갱신하고, 없으면 생성한다.
6. 기존 Career row를 `applicationId` 기준 명시 삭제한다.
7. 새 Career row를 저장한다.
8. `sortOrder ASC, id ASC`로 다시 조회해 응답한다.

## useCareer 연동 정책

- `ApplicationFormConfig.useCareer=true`일 때만 저장 가능하다.
- `useCareer=false`이면 `InvalidJobApplicationException`으로 400 처리한다.
- 조회는 본인 지원서이면 모든 상태에서 허용한다.
- submit 시 Career 필수 검증은 Phase 03c-7에서 연결한다.

## 상태별 수정 정책

| JobApplicationStatus | 조회 | 저장 |
|---|---|---|
| `DRAFT` | 가능 | 가능 |
| `SUBMITTED` | 가능 | 불가 |
| `WITHDRAWN` | 가능 | 불가 |

저장은 추가로 `JobPosting.status=PUBLISHED`이고 접수기간 내여야 한다.

## 개인정보/응답 제한 정책

Career 응답에는 CI, ciHash, 전화번호, 이메일, 주소, 비밀번호 등 민감 개인정보를 포함하지 않는다. 회사명, 부서명, 담당업무, 퇴사사유는 지원서 상세 정보로 취급하며 관리자 노출/마스킹 정책은 관리자 상세 섹션 확장 시 별도 검토한다.

## 테스트 목록

- `NEWCOMER` + 빈 목록 저장 성공
- `EXPERIENCED` + 여러 Career 저장 성공
- 저장 후 조회 성공
- Career 정렬 확인
- replace 저장 시 기존 Career 삭제와 profile 갱신 확인
- `EXPERIENCED` 빈 목록 저장 허용과 기존 row 삭제 확인
- `NEWCOMER`, `NOT_APPLICABLE`, `NOT_SELECTED`에 Career row가 있으면 실패
- `careerType=null` 실패
- `careers=null` 실패
- `companyName`, `startDate`, `currentlyEmployed` 누락 실패
- `currentlyEmployed=false`인데 `endDate=null` 실패
- `currentlyEmployed=true`인데 `endDate`가 있으면 실패
- `startDate > endDate` 실패
- `responsibilities`, `resignationReason` 2000자 초과 실패
- `sortOrder` 중복 실패
- `useCareer=false` 저장 실패
- `SUBMITTED`, `WITHDRAWN` 저장 실패
- 타인 지원서 조회/저장 실패
- 접수기간 전/후 저장 실패
- `PUBLISHED`가 아닌 JobPosting 저장 실패
- Controller GET/POST 성공
- validation 실패 400 + `ApiResponse.fail`
- invalid enum 실패 400 + `ApiResponse.fail`
- PUT/DELETE 미지원 확인

## 실행한 테스트 명령

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationCareerServiceTest --tests com.shinyoung.recruit.controller.ApplicationCareerControllerTest
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicationEducationServiceTest
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.controller.ApplicationEducationControllerTest
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test
```

## 테스트 결과

- `ApplicationCareerServiceTest`, `ApplicationCareerControllerTest`: 성공
- `ApplicationEducationServiceTest`: 성공
- `ApplicationEducationControllerTest`: 성공
- 전체 `clean test`: 성공

## 남은 이슈

- submit 시 `useCareer=true`이고 `CareerType.NOT_SELECTED`이면 실패시키는 검증은 Phase 03c-7에서 연결한다.
- submit 시 `CareerType.EXPERIENCED`일 때 Career row 최소 1개 필수 여부는 Phase 03c-7에서 확정한다.
- 관리자 Application 상세 응답에는 아직 Career 섹션이 포함되지 않는다.
- Career 상세 정보의 관리자 마스킹/노출 정책은 관리자 상세 섹션 확장 시 결정한다.
- Education/Career의 접근, 상태, 접수기간, config enabled 검증이 반복되므로 다음 섹션에서 반복이 커지면 최소 공통 helper 추출을 검토한다.

## 다음 Phase 추천

다음 Phase는 Certificate + Language 또는 Military 중 하나로 진행할 수 있다. `useMilitary=true` submit 필수 정책이 이미 정리되어 있으므로 단건 구조가 필요한 Military를 먼저 구현하거나, 화면 입력량이 큰 Certificate + Language를 먼저 구현해 반복되는 replace 패턴을 안정화하는 선택지가 있다.
