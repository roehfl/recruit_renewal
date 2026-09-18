# Phase 10 — ApplicationBasicInfo (지원자 기본정보 섹션)

> **변경 주의(2026-06-23)**: `veteranStatus==SUBJECT`일 때 입력하는 평문 `veteranType`(보훈 종류) 필드가 이후 슬라이스에서 추가되었다(조건부 검증: SUBJECT 필수 / NOT_SUBJECT 금지, 파기 NULLIFY 포함). 상세는 `07-implementation-history.md`(2026-06-23 항목) 참고.

- 작성일: 2026-06-12
- 상태: 구현 완료
- 기준 스펙: `docs/superpowers/specs/2026-06-12-application-basic-info-design.md`
- 참조 커밋 범위: `76793f8` … `98d33b9` (11개 커밋)

---

## 1. Phase Summary

지원서 작성 중 입력하는 지원자 기본정보(이름·생년월일·국적·연락처·이메일·보훈·장애·주소)를 지원서별 1:1 섹션 엔티티 `ApplicationBasicInfo`로 저장·조회한다. 제출 검증(항상 필수), 완성도 체커, 관리자 조회, PDF 스냅샷 배선, 파기(Purge) saga까지 전체 수직 슬라이스를 구현했다.

---

## 2. Implemented Scope

지원자 API:
- 엔티티 `ApplicationBasicInfo` (AES-at-rest 암호화 + `BaseEntity`)
- Enum 3종: `NationalityType`, `VeteranStatus`, `DisabilityStatus`
- `ApplicationBasicInfoRepository` (`findByJobApplicationId`, `existsByJobApplicationId`)
- `ApplicationBasicInfoService` (조회+prefill+upsert+조건부 검증)
- `ApplicationBasicInfoController` (지원자 GET/POST)
- DTO: `BasicInfoSaveRequest`, `BasicInfoResponse` (prefill 지원), `AdminBasicInfoResponse`

검증/완성도/관리자/PDF/파기:
- `ApplicationSubmitValidator.validateBasicInfo()` — 최우선 항상 실행 (`config == null` 검사보다 앞)
- `ApplicationCompletionReadChecker` — BASIC_INFO 항상 필수 그룹 추가
- `CommonCodeRepository.existsByGroupCodeAndCodeAndActiveTrue` — 활성 코드 존재 검증
- `AdminApplicationSectionService.getBasicInfo()` + `AdminApplicationSectionController GET /admin/applications/{applicationId}/basic-info`
- `ApplicationPdfService.buildHeader()` — BasicInfo 행 존재 시 이름/휴대폰/이메일 source of truth = BasicInfo (row 부재 시만 fallback)
- `ApplicationPiiPurgeRepository.purgeBasicInfo()` — 전 PII 컬럼 null (JPQL bulk update)
- `ApplicationPiiPurgeService.purgeRelationalPii()` — 첫 번째 호출로 편입

---

## 3. Changed Files

### 신규 파일

| 경로 | 유형 |
|---|---|
| `src/main/java/com/shinyoung/recruit/domain/entity/ApplicationBasicInfo.java` | Entity |
| `src/main/java/com/shinyoung/recruit/enumeration/NationalityType.java` | Enum |
| `src/main/java/com/shinyoung/recruit/enumeration/VeteranStatus.java` | Enum |
| `src/main/java/com/shinyoung/recruit/enumeration/DisabilityStatus.java` | Enum |
| `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationBasicInfoRepository.java` | Repository |
| `src/main/java/com/shinyoung/recruit/service/ApplicationBasicInfoService.java` | Service |
| `src/main/java/com/shinyoung/recruit/controller/ApplicationBasicInfoController.java` | Controller |
| `src/main/java/com/shinyoung/recruit/dto/request/BasicInfoSaveRequest.java` | Request DTO |
| `src/main/java/com/shinyoung/recruit/dto/response/BasicInfoResponse.java` | Response DTO |
| `src/main/java/com/shinyoung/recruit/dto/response/AdminBasicInfoResponse.java` | Response DTO |
| `src/test/java/com/shinyoung/recruit/domain/repository/ApplicationBasicInfoEncryptionTest.java` | Test |
| `src/test/java/com/shinyoung/recruit/service/ApplicationBasicInfoServiceTest.java` | Test |
| `src/test/java/com/shinyoung/recruit/controller/ApplicationBasicInfoControllerTest.java` | Test |
| `src/test/java/com/shinyoung/recruit/service/ApplicationSubmitValidatorBasicInfoTest.java` | Test |
| `src/test/java/com/shinyoung/recruit/support/BasicInfoTestSupport.java` | Test Support |

### 수정 파일

| 경로 | 변경 내용 |
|---|---|
| `src/main/java/com/shinyoung/recruit/domain/repository/CommonCodeRepository.java` | `existsByGroupCodeAndCodeAndActiveTrue` 추가 |
| `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationPiiPurgeRepository.java` | `purgeBasicInfo` JPQL bulk update 추가 |
| `src/main/java/com/shinyoung/recruit/service/ApplicationPiiPurgeService.java` | `purgeRelationalPii` 첫 호출에 `purgeBasicInfo` 추가 |
| `src/main/java/com/shinyoung/recruit/service/ApplicationSubmitValidator.java` | `ApplicationBasicInfoRepository` 주입, `validateBasicInfo` 최우선 호출 |
| `src/main/java/com/shinyoung/recruit/service/ApplicationCompletionReadChecker.java` | `ApplicationBasicInfoRepository` 주입, `checkBasicInfo` 항상 필수 그룹 추가 |
| `src/main/java/com/shinyoung/recruit/service/AdminApplicationSectionService.java` | `ApplicationBasicInfoRepository` 주입, `getBasicInfo` 메서드 추가 |
| `src/main/java/com/shinyoung/recruit/controller/AdminApplicationSectionController.java` | `GET /admin/applications/{applicationId}/basic-info` 엔드포인트 추가 |
| `src/main/java/com/shinyoung/recruit/service/ApplicationPdfService.java` | `ApplicationBasicInfoRepository` 주입, `buildHeader` BasicInfo 우선 배선 |
| `src/test/java/com/shinyoung/recruit/service/ApplicationPiiPurgeServiceTest.java` | BasicInfo 생성·파기 검증 추가 |
| `src/test/java/com/shinyoung/recruit/service/ApplicationCompletionReadCheckerTest.java` | BASIC_INFO 그룹 검증 추가 |
| `src/test/java/com/shinyoung/recruit/service/ApplicationPdfServiceTest.java` | PDF header BasicInfo 우선·fallback·파기 후 no-fallback 검증 추가 |

---

## 4. New Classes

| 클래스 | 패키지 | 유형 |
|---|---|---|
| `ApplicationBasicInfo` | `com.shinyoung.recruit.domain.entity` | Entity |
| `NationalityType` | `com.shinyoung.recruit.enumeration` | Enum |
| `VeteranStatus` | `com.shinyoung.recruit.enumeration` | Enum |
| `DisabilityStatus` | `com.shinyoung.recruit.enumeration` | Enum |
| `ApplicationBasicInfoRepository` | `com.shinyoung.recruit.domain.repository` | Repository |
| `ApplicationBasicInfoService` | `com.shinyoung.recruit.service` | Service |
| `ApplicationBasicInfoController` | `com.shinyoung.recruit.controller` | Controller |
| `BasicInfoSaveRequest` | `com.shinyoung.recruit.dto.request` | Request DTO |
| `BasicInfoResponse` | `com.shinyoung.recruit.dto.response` | Response DTO |
| `AdminBasicInfoResponse` | `com.shinyoung.recruit.dto.response` | Response DTO |

---

## 5. Modified Classes

| 클래스 | 변경 요약 |
|---|---|
| `CommonCodeRepository` | `existsByGroupCodeAndCodeAndActiveTrue` 파생 쿼리 메서드 추가 |
| `ApplicationPiiPurgeRepository` | `purgeBasicInfo` JPQL `@Modifying(flushAutomatically=true)` 추가 |
| `ApplicationPiiPurgeService` | `purgeRelationalPii` 내 `purgeBasicInfo` 첫 줄 추가 |
| `ApplicationSubmitValidator` | `basicInfoRepository` 주입, `validate()` 진입 직후 `validateBasicInfo` 최우선 호출 |
| `ApplicationCompletionReadChecker` | `basicInfoRepository` 주입, `check()` 내 `checkBasicInfo` 항상 필수 그룹 등록 |
| `AdminApplicationSectionService` | `basicInfoRepository` 주입, `getBasicInfo(Long applicationId)` 추가 |
| `AdminApplicationSectionController` | `GET /admin/applications/{applicationId}/basic-info` 엔드포인트 추가 |
| `ApplicationPdfService` | `basicInfoRepository` 주입, `buildHeader` BasicInfo 행 존재 시 PII source 교체 |

---

## 6. Class-by-Class Explanation

### `ApplicationBasicInfo`

- package: `com.shinyoung.recruit.domain.entity`
- class type: Entity
- responsibility: 지원서별 1:1 기본정보 스냅샷 저장. 모든 문자열 PII를 `AesAttributeConverter`로 at-rest 암호화. enum(`nationalityType`, `veteranStatus`, `disabilityStatus`)과 `birthDate`는 평문.
- key fields:
  - `id` — `Long`, `@GeneratedValue(IDENTITY)`, PK
  - `jobApplication` — `@OneToOne(LAZY)` + `unique = true`, `nullable = false` (유일한 NOT NULL 제약)
  - `nameKorean`, `nameEnglish`, `countryCode`, `mobilePhone`, `emergencyPhone`, `email`, `disabilityGradeCode`, `disabilityTypeCode`, `zipCode` — `@Convert(AesAttributeConverter)`, `@Column(length=500)`, nullable
  - `addressBasic`, `addressDetail` — `@Convert(AesAttributeConverter)`, `@Column(length=2000)`, nullable
  - `nationalityType` — `NationalityType` enum, `@Enumerated(STRING)`, nullable
  - `veteranStatus` — `VeteranStatus` enum, nullable
  - `disabilityStatus` — `DisabilityStatus` enum, nullable
  - `birthDate` — `LocalDate`, nullable (파기 후 null 가능)
- key methods: `static create(...)` 정적 팩토리, `update(...)` 모든 필드 일괄 갱신
- related classes: `BaseEntity`, `JobApplication`, `AesAttributeConverter`, `NationalityType`, `VeteranStatus`, `DisabilityStatus`
- notes:
  - 테이블명 `application_basic_info`, 인덱스 `idx_application_basic_info_application(job_application_id)`
  - DB NOT NULL은 FK만. 모든 PII 컬럼 nullable → 파기 시 null 처리 가능.
  - 스펙 문서의 `addressBasic/addressDetail` length 1000 vs 실제 구현 2000: 커밋 `8fc652f`에서 widen 조치됨. 문서와 코드 불일치.

### `NationalityType`

- package: `com.shinyoung.recruit.enumeration`
- class type: Enum
- responsibility: 내/외국인 구분 (`DOMESTIC`, `FOREIGN`)
- related classes: `ApplicationBasicInfo`, `BasicInfoSaveRequest`, `ApplicationBasicInfoService`

### `VeteranStatus`

- package: `com.shinyoung.recruit.enumeration`
- class type: Enum
- responsibility: 보훈 대상 여부 (`SUBJECT`, `NOT_SUBJECT`)

### `DisabilityStatus`

- package: `com.shinyoung.recruit.enumeration`
- class type: Enum
- responsibility: 장애 대상 여부 (`SUBJECT`, `NOT_SUBJECT`)
- notes: SUBJECT 시 `disabilityGradeCode`·`disabilityTypeCode`가 추가 필수.

### `ApplicationBasicInfoRepository`

- package: `com.shinyoung.recruit.domain.repository`
- class type: Repository (`JpaRepository<ApplicationBasicInfo, Long>`)
- responsibility: 기본정보 조회·존재 확인
- key methods:
  - `findByJobApplicationId(Long)` — prefill/GET 조회
  - `existsByJobApplicationId(Long)` — 제출·완성도 존재 확인
- related classes: `ApplicationBasicInfo`

### `ApplicationBasicInfoService`

- package: `com.shinyoung.recruit.service`
- class type: Service
- responsibility: 기본정보 조회(prefill 포함), upsert, 조건부 검증 (국적/장애/나이/전화)
- key methods:
  - `getBasicInfo(Long applicantId, Long applicationId)` — 행 있으면 `BasicInfoResponse.of`, 없으면 `BasicInfoResponse.prefill(applicant)` (`persisted=false`)
  - `saveBasicInfo(Long applicantId, Long applicationId, BasicInfoSaveRequest)` — 소유·쓰기 가능 검증 후 upsert
  - `validateRequest(BasicInfoSaveRequest)` — 국적/장애 조건부 + 나이 범위(만 14~100세, `Clock`) + 전화 형식(`^[0-9-]{9,20}$`)
- key constants: `GROUP_NATIONALITY = "NATIONALITY"`, `GROUP_DISABILITY_GRADE = "DISABILITY_GRADE"`, `GROUP_DISABILITY_TYPE = "DISABILITY_TYPE"`, `MIN_AGE = 14`, `MAX_AGE = 100`
- related classes: `ApplicationSectionAccessService`, `ApplicationBasicInfoRepository`, `CommonCodeRepository`, `Clock`
- notes:
  - upsert: `findByJobApplicationId().orElseGet(save(create))` 후 `update(...)` 적용. 신규·기존 행 모두 update() 경로 통일.
  - 국적/장애 코드 활성 검증: `existsByGroupCodeAndCodeAndActiveTrue` 사용. 비활성·미존재 코드 거부.
  - 전화번호 normalize는 미구현 (v1 입력값 그대로 저장).

### `ApplicationBasicInfoController`

- package: `com.shinyoung.recruit.controller`
- class type: Controller (`@RestController`)
- responsibility: 지원자 기본정보 GET/POST 엔드포인트
- key methods:
  - `GET /applications/{applicationId}/basic-info` — `BasicInfoResponse` (persisted / prefill)
  - `POST /applications/{applicationId}/basic-info` — `BasicInfoResponse` (upsert 결과)
- related classes: `ApplicationBasicInfoService`, `CurrentApplicantService`, `CustomUserDetails`
- notes: `@AuthenticationPrincipal CustomUserDetails`로 applicantId 추출.

### `BasicInfoSaveRequest`

- package: `com.shinyoung.recruit.dto.request`
- class type: Request DTO (`record`)
- responsibility: 기본정보 저장 요청. Bean Validation은 평문 max length 기준. 국적/장애 코드 조건부 필수는 Bean Validation 제외(서비스에서 검증).
- key fields: `nameKorean (@NotBlank @Size(max=50))`, `nameEnglish (@Size(max=100))`, `nationalityType (@NotNull)`, `countryCode (@Size(max=50))`, `birthDate (@NotNull @Past)`, `mobilePhone (@NotBlank @Size(max=20))`, `emergencyPhone (@Size(max=20))`, `email (@NotBlank @Email @Size(max=100))`, `veteranStatus (@NotNull)`, `disabilityStatus (@NotNull)`, `disabilityGradeCode (@Size(max=50))`, `disabilityTypeCode (@Size(max=50))`, `zipCode (@Size(max=10))`, `addressBasic (@Size(max=200))`, `addressDetail (@Size(max=200))`

### `BasicInfoResponse`

- package: `com.shinyoung.recruit.dto.response`
- class type: Response DTO (`record`)
- responsibility: 조회 응답. `persisted` 플래그로 저장된 행 vs prefill 구분.
- key methods:
  - `static of(ApplicationBasicInfo)` — persisted=true, 저장값 전달
  - `static prefill(Applicant)` — persisted=false, basicInfoId=null, `applicant.getUserName()` → nameKorean, `applicant.getPhoneNumber()` → mobilePhone, `applicant.getEmail()` → email, 나머지 null

### `AdminBasicInfoResponse`

- package: `com.shinyoung.recruit.dto.response`
- class type: Response DTO (`record`)
- responsibility: 관리자 기본정보 조회 응답. 전체 필드 포함.
- key methods: `static from(ApplicationBasicInfo)` — 전 필드 매핑
- notes:
  - `ci`, `ciHash`, `password` 절대 미노출 (Applicant/User 계층에서도 노출 금지).
  - 장애 정보(`disabilityStatus`, `disabilityGradeCode`, `disabilityTypeCode`)는 민감정보 — 관리자 전용 엔드포인트.

### `ApplicationSubmitValidator` (수정)

- package: `com.shinyoung.recruit.service`
- 변경 내용:
  - `ApplicationBasicInfoRepository` 필드 주입
  - `validate()` 진입 직후 `validateBasicInfo(applicationId)` 최우선 호출 — `ApplicationFormConfig == null` 검사보다 앞
  - `validateBasicInfo`: 행 없으면 예외, 필수 7개 필드 누락 검증, 국적/장애 조건부 검증

### `ApplicationCompletionReadChecker` (수정)

- package: `com.shinyoung.recruit.service`
- 변경 내용:
  - `ApplicationBasicInfoRepository` 필드 주입
  - `check()` 내 `checkBasicInfo(applicationId, accumulator)` 항상 필수 그룹 등록 — `config == null` 분기 앞
  - `checkBasicInfo`: 행 없으면 `MISSING_ROW`, 필수 필드 누락이면 `MISSING_REQUIRED_FIELD`

### `CommonCodeRepository` (수정)

- 변경 내용: `boolean existsByGroupCodeAndCodeAndActiveTrue(String groupCode, String code)` 파생 쿼리 추가

### `ApplicationPiiPurgeRepository` (수정)

- 변경 내용: `purgeBasicInfo(Long applicationId)` JPQL bulk update 추가
  - 전 PII 컬럼 null: `nameKorean`, `nameEnglish`, `email`, `mobilePhone`, `emergencyPhone`, `birthDate`, `nationalityType`, `countryCode`, `veteranStatus`, `disabilityStatus`, `disabilityGradeCode`, `disabilityTypeCode`, `zipCode`, `addressBasic`, `addressDetail`, `createdBy`, `updatedBy`
  - `@Modifying(flushAutomatically = true)` — 프로젝트 관례

### `ApplicationPiiPurgeService` (수정)

- 변경 내용: `purgeRelationalPii` 내 첫 번째 호출로 `purgeRepository.purgeBasicInfo(applicationId)` 추가

### `AdminApplicationSectionService` (수정)

- 변경 내용: `basicInfoRepository` 주입, `getBasicInfo(Long applicationId)` — 행 있으면 `AdminBasicInfoResponse.from(...)`, 없으면 `null`

### `AdminApplicationSectionController` (수정)

- 변경 내용: `GET /admin/applications/{applicationId}/basic-info` 추가 — `AdminBasicInfoResponse` 응답

### `ApplicationPdfService` (수정)

- 변경 내용: `buildHeader` 내 `basicInfoRepository.findByJobApplicationId` 조회, 행 존재 시 BasicInfo 값(nameKorean, mobilePhone, email) 사용
- fallback 기준: "BasicInfo **행 존재 여부**". 행이 있지만 파기로 필드가 null인 경우 Applicant live 값으로 fallback 하지 않는다.

---

## 7. API List

| Method | Path | 대상 | 요청 | 응답 | 권한 |
|---|---|---|---|---|---|
| GET | `/applications/{applicationId}/basic-info` | 지원자 | - | `ApiResponse<BasicInfoResponse>` | 지원자(본인) |
| POST | `/applications/{applicationId}/basic-info` | 지원자 | `BasicInfoSaveRequest` | `ApiResponse<BasicInfoResponse>` | 지원자(본인) |
| GET | `/admin/applications/{applicationId}/basic-info` | 관리자 | - | `ApiResponse<AdminBasicInfoResponse>` | 관리자 |

### GET `/applications/{applicationId}/basic-info`

- 행 있으면: `persisted=true`, `basicInfoId=<id>`, 저장값
- 행 없으면: `persisted=false`, `basicInfoId=null`, Applicant prefill (`nameKorean←userName`, `mobilePhone←phoneNumber`, `email←email`, 나머지 null)

### POST `/applications/{applicationId}/basic-info`

- 소유 검증 → 쓰기 가능 검증(`DRAFT`/`PUBLISHED` + 접수 기간) → 조건부 검증 → upsert
- 400: Bean Validation 위반, 조건부 검증 실패(국적·장애 코드·나이·전화)
- 403/404: 비소유

### GET `/admin/applications/{applicationId}/basic-info`

- 행 없으면 null 반환 (에러 아님)
- 장애 등급·유형 코드 포함(민감정보, 관리자 전용)

---

## 8. Entity Relationship Summary

```
JobApplication (1) ──── (1) ApplicationBasicInfo
```

- `JobApplication.id ← ApplicationBasicInfo.job_application_id` (FK, UNIQUE, NOT NULL)
- `ApplicationBasicInfo`는 `BaseEntity` 상속 (`createdAt`, `updatedAt`, `createdBy`, `updatedBy`)
- 파기 후 모든 PII 컬럼(+createdBy/updatedBy) null, 행(shell) 자체는 유지

---

## 9. Business Rules

### 저장 검증 (`saveBasicInfo`)

1. 소유 검증 (`findOwnedApplication`) — 타 지원자 접근 거부
2. 쓰기 가능 (`validateWritable`) — 제출/취소된 지원서 저장 불가
3. 국적 조건부: `FOREIGN` → `countryCode` 필수 + 활성 `NATIONALITY` 코드 검증; `DOMESTIC` → `countryCode` 금지
4. 장애 조건부: `SUBJECT` → `disabilityGradeCode`·`disabilityTypeCode` 필수 + 활성 코드 검증; `NOT_SUBJECT` → 두 코드 모두 금지
5. 나이: `birthDate`로부터 만 14세 이상 ~ 만 100세 이하 (`Clock` 기준)
6. 전화: 숫자와 하이픈만, 9~20자 (`^[0-9-]{9,20}$`). normalize 미수행(v1).

### 제출 검증 (`validateBasicInfo` in `ApplicationSubmitValidator`)

- `ApplicationFormConfig` 존재 여부와 무관하게 항상 최우선 실행
- 필수 7개: `nameKorean`, `birthDate`, `nationalityType`, `mobilePhone`, `email`, `veteranStatus`, `disabilityStatus`
- 조건부: `FOREIGN` → `countryCode`, `SUBJECT` → `disabilityGradeCode` + `disabilityTypeCode`
- 주소·영문명·비상연락처는 제출 비필수

### 완성도 체커 (`checkBasicInfo` in `ApplicationCompletionReadChecker`)

- BASIC_INFO는 config 무관 항상 필수 그룹
- 규칙은 제출 검증과 동일 (완성도·제출 일치)

### PDF 스냅샷 (`buildHeader` in `ApplicationPdfService`)

- BasicInfo **행 존재** 시: `nameKorean`, `mobilePhone`, `email` → BasicInfo source
- 행 **부재** 시에만: `applicantNameSnapshot`, `applicant.phoneNumber`, `applicant.email` fallback
- 파기 후 필드 null이어도 행 존재 시 fallback 금지

### 파기 (`purgeBasicInfo`)

- AES 랜덤 IV(비결정적) → 암호화 컬럼 `'__PURGED__'` 치환 불가 → 전 PII 컬럼 `null`
- `createdBy`/`updatedBy`도 null (JPQL bulk update로 `@Column(updatable=false)` 우회)
- 행 자체는 보존 (파기 marker는 `JobApplication`이 보유)

---

## 10. Test Coverage

### 테스트 클래스 및 주요 케이스

| 클래스 | 케이스 수 | 주요 케이스 |
|---|---|---|
| `ApplicationBasicInfoEncryptionTest` (`@DataJpaTest`) | 1 | AES 암호화·복호화 라운드트립 (raw 컬럼 != 평문) |
| `ApplicationBasicInfoServiceTest` | 11 | save+get persisted, prefill, upsert, FOREIGN 국적코드 활성 검증, DOMESTIC countryCode 금지, SUBJECT 장애코드, 나이 범위, 전화 형식, 비소유, 쓰기 불가, 만 14세 경계값 |
| `ApplicationBasicInfoControllerTest` | 4 | GET prefill, save→get persisted, 필수 필드 누락 400, 타 지원자 접근 |
| `ApplicationSubmitValidatorBasicInfoTest` | 2 | 기본정보 없으면 제출 거부, 기본정보 있으면 제출 통과 |
| `ApplicationCompletionReadCheckerTest` (기존, 보강) | - | BASIC_INFO required group 반영 검증 |
| `ApplicationPdfServiceTest` (기존, 보강) | 3 | BasicInfo 존재 시 BasicInfo 값 사용, 필드 null이어도 fallback 금지, 행 없으면 Applicant fallback |
| `ApplicationPiiPurgeServiceTest` (기존, 보강) | - | purgeBasicInfo 후 전 PII 컬럼 null 검증 |

### 테스트 실행 방식

- scoped 실행 우선 (`*BasicInfo*`, `*SubmitValidator*`, `*PdfService*`, `*PurgeService*` 등)
- 전체 `./gradlew clean test` 는 명시 요청 시에만 실행 (로컬 환경 타임아웃)
- 암호화 키 필요 시: `AES_SECRET_KEY=22791194512954214612461221261067`

---

## 11. Known Limitations

1. **증명사진(PHOTO)**: `ApplicationAttachment`의 `PHOTO` 첨부 타입 및 이미지 강화(EXIF 제거, magic-byte 검증) — 후속 슬라이스 분리.
2. **CommonCode 시드 데이터 미포함**: `NATIONALITY`, `DISABILITY_GRADE`, `DISABILITY_TYPE` 그룹 코드는 서비스 가정. 운영/관리자 도구로 별도 등록 필요.
3. **Excel export BasicInfo 미배선**: PDF는 본 슬라이스에서 보정. Excel export 연락처·이메일 노출 경로 audit 결과에 따른 추가 보정 대기.
4. **전체 테스트 스위트 타임아웃**: 로컬 환경에서 `./gradlew clean test` 전체 실행 시 타임아웃 발생. scoped 테스트로 검증 완료.
5. **`addressBasic`/`addressDetail` 컬럼 length**: 스펙 문서는 1000, 구현 엔티티는 2000 (커밋 `8fc642f`에서 widen). 운영 DDL 생성 시 2000 기준 사용.
6. **전화번호 normalize 미구현**: v1은 입력값 그대로 저장. 정규화(하이픈 제거 등)는 후속 정책.

---

## 12. Next Phase Considerations

1. 증명사진 슬라이스 — `AttachmentType.PHOTO` 추가 + `sectionType = BASIC_INFO` + 이미지 강화
2. CommonCode 시드 데이터 등록 절차 (국가/장애 코드)
3. Excel export BasicInfo 반영 (audit 후 결정)
4. 전화번호 normalize 정책 결정
5. `addressBasic`/`addressDetail` 컬럼 length(스펙 1000 vs 구현 2000) 운영 DDL 명문화
