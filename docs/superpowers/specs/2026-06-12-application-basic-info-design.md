# ApplicationBasicInfo (지원자 기본정보 섹션) — 설계 Spec

- 작성일: 2026-06-12
- 상태: 설계 확정(구현 전) — 1차 리뷰 반영본
- 작업 유형: 신규 도메인 + 지원자용 API 추가 (vertical slice)
- 기준 문서: `CLAUDE.md`, `docs/codex/02-domain-design.md`, `docs/codex/03-legacy-feature-map.md`
- 참조 구현 패턴: `ApplicationMilitary` 섹션 슬라이스

## 0. 리뷰 반영 이력 (instruction.md 1차 리뷰)

| 리뷰 항목 | 반영 결과 |
|---|---|
| Major 1 — countryCode 암호화/파기 정책 애매 | countryCode를 **개인정보성 코드로 일관 처리**: `AesAttributeConverter` 암호화 + nullable + 파기 시 null (§5, §9) |
| Major 2 — purge null vs DB NOT NULL 충돌 | **DB NOT NULL은 `jobApplication` FK만**. 모든 개인정보 컬럼 nullable, 필수 여부는 저장/제출 검증으로 보장 (§5.1) |
| Major 3 — prefill vs GET null 충돌 | **B안 채택**: GET은 항상 응답 반환. 미저장 시 `persisted=false`, `basicInfoId=null`로 Applicant 기반 prefill projection (§7) |
| Major 4 — countryCode DTO 검증 문구 모호 | Request DTO에서 countryCode·장애코드는 **Bean Validation 없음**, 서비스 조건부 검증으로 명시 (§7) |
| Major 5 — 주소 필수 여부 불명확 | **주소는 입력 노출하되 제출 필수 아님**(zipCode/addressBasic/addressDetail 모두 optional)으로 명문화 (§5, §8) |
| Minor — 암호화 컬럼 bulk update | **발견: AES가 랜덤 IV(비결정적)**. 암호화 컬럼에 `'__PURGED__'` 평문 bulk update는 컨버터 미적용 시 read 깨짐 → **암호화 PII는 전부 null-on-purge**로 회피. 라운드트립 + null 파기 검증 테스트 필수 (§9, §11) |
| Minor — SubmitValidator | `validate()` 초반에 **무조건** `validateBasicInfo()` 호출 (§8) |
| Minor — validateWritable / Military 패턴 재사용 | 그대로 유지 |

## 1. Purpose (목적)

지원서 작성 중 입력하는 **지원자 기본정보**를 지원서별 1:1 섹션으로 저장/조회하는 도메인과 지원자용 API를 추가한다.

기본정보 항목(instruction.md):

1. 내/외국인 (외국인이면 국적)
2. 생년월일
3. 연락처 (휴대폰 필수, 비상연락처 선택)
4. email
5. 이름 (한글 필수, 영문 선택)
6. 보훈여부 (대상/비대상)
7. 장애여부 (대상/비대상, 대상이면 등급·유형 코드)
8. 주소 (입력 노출, 제출 필수 아님)
9. 사진 — **본 슬라이스 범위 밖**(§4)

## 2. 핵심 설계 결정 (확정)

| 결정 | 내용 |
|---|---|
| 엔티티 형태 | `JobApplication` 1:1 독립 섹션 엔티티 (`ApplicationMilitary` 패턴). Applicant 확장/Embeddable 분산 기각 |
| 필드 소유 | **전체 스냅샷** — 모든 기본정보를 지원서별로 저장. 제출 시점 정보가 공고별로 동결 |
| prefill (Major 3) | GET 미저장 시 Applicant 기반 prefill projection 반환(`persisted=false`). FE가 바로 화면을 채울 수 있음 |
| 사진 | **후속 슬라이스로 분리**. `PHOTO` 첨부 타입 + 이미지 강화(재인코딩으로 EXIF/메타데이터·내장 스크립트 제거 + magic-byte 검증)로 기존 `ApplicationAttachment` 파이프라인 재사용 |
| 암호화 | 모든 문자열 PII(이름·연락처·이메일·주소·**국적코드**·장애코드)는 `AesAttributeConverter` at-rest 암호화. enum(`nationalityType`/`veteranStatus`/`disabilityStatus`)·`birthDate`는 평문 |
| DB 제약 (Major 2) | **NOT NULL은 `jobApplication` FK만**. 나머지 전부 nullable, 필수 여부는 검증으로 보장 |
| 파기 (Minor) | **모든 개인정보 컬럼 null-on-purge**. `'__PURGED__'` 센티넬 미사용(암호화 컬럼 + 랜덤 IV 안전성). 파기 marker는 root `JobApplication`(purgeResult/purgedAt/applicantNameSnapshot)이 보유 |
| 영문명 | 선택(nullable) |
| 코드값 | 국적/장애등급/장애유형은 `CommonCode` 그룹코드 + application-level 검증(하드 FK 아님) |
| 폼 설정 | 기본정보는 항상 필수 섹션 — `ApplicationFormConfig` 플래그 없음, `validateBasicInfoEnabled` 없음 |
| 제출 검증 | **본 슬라이스 포함** — `ApplicationSubmitValidator.validate()` 초반에 무조건 `validateBasicInfo()` 실행 |

## 3. Scope (구현 범위)

1. 엔티티 `ApplicationBasicInfo` (암호화 컨버터, `create()`/`update()`)
2. Enum 3종: `NationalityType`, `VeteranStatus`, `DisabilityStatus`
3. `ApplicationBasicInfoRepository`
4. `ApplicationBasicInfoService` (조회 + prefill + upsert + 검증)
5. `ApplicationBasicInfoController` (지원자용 GET/POST)
6. DTO: `BasicInfoSaveRequest`, `BasicInfoResponse`(prefill 지원)
7. `ApplicationSubmitValidator`에 `validateBasicInfo()` 추가 + `ApplicationBasicInfoRepository` 주입
8. 파기 saga 편입: `ApplicationPiiPurgeRepository.purgeBasicInfo()` + `ApplicationPiiPurgeService.purgeRelationalPii()` 1줄 추가
9. CommonCode 검증용 repo 메서드(`existsByGroupCodeAndCodeAndActiveTrue` 등) 필요 시 추가
10. 테스트: Service / Controller / 암호화 라운드트립 / 파기
11. 문서: `docs/codex/implementation/phase-XX-application-basic-info.md` + `docs/codex/reports/phase-XX-application-basic-info.html` + `07-implementation-history.md` + `phase-09-pii-field-inventory.md`

## 4. Out of Scope (범위 밖)

- **증명사진 업로드** — 후속 슬라이스. `AttachmentType.PHOTO` 추가 + 이미지 재인코딩/EXIF 제거/magic-byte 검증 + 차원·용량 캡. 기존 첨부 파이프라인 재사용.
- **관리자용 조회 API**(`AdminBasicInfoResponse`) — 후속 슬라이스.
- **CommonCode 시드 데이터** — 국가/장애 코드는 운영/관리자 등록 대상. 본 슬라이스는 검증 로직만.
- Applicant 엔티티 구조 변경(CLAUDE.md §4.3 준수).

## 5. 엔티티 설계 — `ApplicationBasicInfo`

- package: `com.shinyoung.recruit.domain.entity`
- 상속: `BaseEntity`
- 테이블: `application_basic_info`, index `idx_application_basic_info_application(job_application_id)`

| 필드 | 타입 | 암호화 | DB NULL | 필수(검증) | 파기 |
|---|---|:--:|:--:|:--:|:--:|
| `id` | `Long` | - | PK | - | - |
| `jobApplication` | `JobApplication` `@OneToOne(LAZY)` unique | - | **NOT NULL** | - | 유지 |
| `nameKorean` | `String` | ✔ | nullable | ✔ | null |
| `nameEnglish` | `String` | ✔ | nullable | 선택 | null |
| `nationalityType` | `NationalityType` | - | nullable | ✔ | null |
| `countryCode` | `String` (CommonCode `NATIONALITY`) | ✔ | nullable | FOREIGN시 | null |
| `birthDate` | `LocalDate` | - | nullable | ✔ | null |
| `mobilePhone` | `String` | ✔ | nullable | ✔ | null |
| `emergencyPhone` | `String` | ✔ | nullable | 선택 | null |
| `email` | `String` | ✔ | nullable | ✔ | null |
| `veteranStatus` | `VeteranStatus` | - | nullable | ✔ | null |
| `disabilityStatus` | `DisabilityStatus` | - | nullable | ✔ | null |
| `disabilityGradeCode` | `String` (CommonCode `DISABILITY_GRADE`) | ✔ | nullable | SUBJECT시 | null |
| `disabilityTypeCode` | `String` (CommonCode `DISABILITY_TYPE`) | ✔ | nullable | SUBJECT시 | null |
| `zipCode` | `String` | ✔ | nullable | 선택 | null |
| `addressBasic` | `String` | ✔ | nullable | 선택 | null |
| `addressDetail` | `String` | ✔ | nullable | 선택 | null |
| (감사필드) | `BaseEntity` | - | - | - | createdBy/updatedBy → null |

### 5.1 DB 제약 정책 (Major 2 반영)

- **DB NOT NULL은 `job_application_id` FK만.** 그 외 모든 개인정보 컬럼은 nullable.
- 필수성(`nameKorean`/`birthDate`/`nationalityType`/`mobilePhone`/`email`/`veteranStatus`/`disabilityStatus`)은 **저장·제출 검증으로만** 보장.
- 이유: 파기 시 모든 개인정보 컬럼을 null로 지우므로(§9), DB NOT NULL이면 파기에서 충돌한다. 암호화 컬럼은 더더욱 `'__PURGED__'` 평문을 쓸 수 없다(§9 근거).

### 5.2 도메인 메서드

- `static create(...)` / `update(...)` — `ApplicationMilitary` 스타일.
- 파기는 엔티티 메서드가 아니라 `ApplicationPiiPurgeRepository`의 bulk JPQL UPDATE로 수행(영속성 컨텍스트 미로딩 + `createdBy` updatable=false 대응, 기존 9d-1 관례).

## 6. Enum 신규

- `NationalityType { DOMESTIC, FOREIGN }`
- `VeteranStatus { SUBJECT, NOT_SUBJECT }`
- `DisabilityStatus { SUBJECT, NOT_SUBJECT }`

## 7. API

| Method | Path | 설명 | 인증 |
|---|---|---|---|
| GET | `/applications/{applicationId}/basic-info` | 기본정보 조회. **미저장 시 Applicant prefill projection** | 지원자 본인 |
| POST | `/applications/{applicationId}/basic-info` | 기본정보 upsert | 지원자 본인 |

- 응답: `ResponseEntity<ApiResponse<BasicInfoResponse>>` (`ApplicationMilitaryController`와 동일)
- 현재 사용자: `CurrentApplicantService.getCurrentApplicantId(userDetails)`

### 7.1 GET prefill 정책 (Major 3 — B안)

GET은 **항상 `BasicInfoResponse`를 반환**한다(data=null 아님).

- `ApplicationBasicInfo` 행이 있으면: `persisted=true`, `basicInfoId=<id>`, 저장값(복호화 투명 처리).
- 행이 없으면: `persisted=false`, `basicInfoId=null`, **Applicant 기반 prefill**
  - `nameKorean` ← `Applicant.userName`
  - `email` ← `Applicant.email`
  - `mobilePhone` ← `Applicant.phoneNumber`
  - 그 외 필드(영문명/국적/생년월일/보훈/장애/주소/비상연락처)는 null
- FE는 prefill projection을 화면 초기값으로 쓰고, 저장 시 POST.

### 7.2 Request DTO — `BasicInfoSaveRequest` (record) (Major 4 반영)

| 필드 | Bean Validation | 비고 |
|---|---|---|
| `nameKorean` | `@NotBlank` | |
| `nameEnglish` | 없음 | 선택 |
| `nationalityType` | `@NotNull` | |
| `countryCode` | **없음** | 서비스 조건부: FOREIGN시 필수 / DOMESTIC시 null |
| `birthDate` | `@NotNull @Past` | |
| `mobilePhone` | `@NotBlank` | 형식 검증(서비스) |
| `emergencyPhone` | 없음 | 선택, 형식 검증(서비스) |
| `email` | `@NotBlank @Email` | |
| `veteranStatus` | `@NotNull` | |
| `disabilityStatus` | `@NotNull` | |
| `disabilityGradeCode` | **없음** | 서비스 조건부: SUBJECT시 필수 / NOT_SUBJECT시 null |
| `disabilityTypeCode` | **없음** | 서비스 조건부: SUBJECT시 필수 / NOT_SUBJECT시 null |
| `zipCode` / `addressBasic` / `addressDetail` | 없음 | 모두 선택 |

조건부 규칙(국적/장애 코드)은 Bean Validation으로 표현하지 않고 **서비스 검증**으로 처리(Major 4).

### 7.3 Response DTO — `BasicInfoResponse` (record)

- 필드: `basicInfoId`(nullable), `persisted`(boolean) + 전체 기본정보 필드.
- `static of(ApplicationBasicInfo)` — persisted=true.
- `static prefill(Applicant)` — persisted=false, basicInfoId=null, Applicant 매핑.

## 8. Validation & Business Rules

### 8.1 저장 (`saveBasicInfo`)

1. 소유 검증: `findOwnedApplication(applicantId, applicationId)`
2. 쓰기 가능: `validateWritable` (DRAFT + 공고 PUBLISHED + 접수기간 내) — 기존 공통 메서드 재사용
3. 조건부 필드 일관성:
   - `nationalityType == FOREIGN` ⇒ `countryCode` 필수 / `DOMESTIC` ⇒ null이어야 함
   - `disabilityStatus == SUBJECT` ⇒ `disabilityGradeCode`·`disabilityTypeCode` 필수 / `NOT_SUBJECT` ⇒ 둘 다 null이어야 함
4. 코드 검증: `countryCode`/`disabilityGradeCode`/`disabilityTypeCode`는 각 그룹의 **활성 CommonCode**에 존재해야 함
5. 형식: 휴대폰/비상연락처 전화번호 형식, 이메일 형식, `birthDate` 과거 + 합리적 연령 범위
6. upsert: `findByJobApplicationId().orElseGet(save(create(...)))` 후 `update(...)`

### 8.2 제출 (`ApplicationSubmitValidator.validateBasicInfo`) — 항상 실행 (Minor 반영)

- `validate()` 진입 직후 **무조건** 호출(폼 설정 무관, 기본정보는 항상 필수 섹션).
- `ApplicationBasicInfo` 존재 필수.
- 필수 present: `nameKorean`, `birthDate`, `nationalityType`, `mobilePhone`, `email`, `veteranStatus`, `disabilityStatus`.
- 조건부 present: FOREIGN ⇒ `countryCode`, disability SUBJECT ⇒ grade+type.
- **주소·영문명·비상연락처는 제출 필수 아님** (Major 5).

### 8.3 주소 정책 (Major 5)

- 주소(`zipCode`/`addressBasic`/`addressDetail`)는 **입력 항목으로 노출하되 제출 필수가 아니다.** 모두 optional.
- (제품 정책상 추후 주소를 제출 필수로 올릴 수 있음 — 그 경우 §8.2에 추가하고 본 절을 갱신한다.)

## 9. 파기(Purge) saga 편입 (필수)

### 9.1 정책 근거 (Minor 발견 반영)

- `AesCryptoUtil`은 **매 암호화마다 랜덤 IV**를 사용 → 비결정적. 같은 평문도 매번 다른 암호문.
- 암호화 컬럼에 JPQL bulk update로 `'__PURGED__'` 평문을 넣으면, Hibernate가 SET 절 리터럴에 컨버터를 적용하지 않을 경우 **평문이 저장되고 이후 read 시 `decrypt('__PURGED__')`가 예외**를 던진다.
- 따라서 **암호화 PII 컬럼은 `'__PURGED__'` 대신 null로 파기**한다. `convertToDatabaseColumn(null) = null`이라 컨버터를 거치지 않아 안전하다.
- 일관성을 위해 **모든 개인정보 컬럼(문자열·날짜·enum·코드)을 null로** 파기한다. 파기 사실 marker는 root `JobApplication`이 보유하므로 basic_info 행은 빈 shell로 남긴다(삭제 아님 — 1:1 구조 보존).

### 9.2 `ApplicationPiiPurgeRepository.purgeBasicInfo`

```java
@Modifying(flushAutomatically = true)
@Query("""
        update ApplicationBasicInfo b
        set b.nameKorean = null, b.nameEnglish = null, b.email = null,
            b.mobilePhone = null, b.emergencyPhone = null,
            b.birthDate = null, b.nationalityType = null, b.countryCode = null,
            b.veteranStatus = null, b.disabilityStatus = null,
            b.disabilityGradeCode = null, b.disabilityTypeCode = null,
            b.zipCode = null, b.addressBasic = null, b.addressDetail = null,
            b.createdBy = null, b.updatedBy = null
        where b.jobApplication.id = :applicationId""")
int purgeBasicInfo(@Param("applicationId") Long applicationId);
```

- `ApplicationPiiPurgeService.purgeRelationalPii()`에 `purgeRepository.purgeBasicInfo(applicationId);` 1줄 추가.
- `docs/codex/.../phase-09-pii-field-inventory.md`에 신규 섹션 분류 등록(인벤토리가 유일 계약). 분류: **전 필드 PURGE(null)**, KEEP_TOMBSTONE 없음.

## 10. 변경/신규 파일 (예상)

신규:

- `domain/entity/ApplicationBasicInfo.java`
- `enumeration/NationalityType.java`, `VeteranStatus.java`, `DisabilityStatus.java`
- `domain/repository/ApplicationBasicInfoRepository.java`
- `service/ApplicationBasicInfoService.java`
- `controller/ApplicationBasicInfoController.java`
- `dto/request/BasicInfoSaveRequest.java`
- `dto/response/BasicInfoResponse.java`
- `test/.../service/ApplicationBasicInfoServiceTest.java`
- `test/.../controller/ApplicationBasicInfoControllerTest.java`
- (필요 시) `test/.../repository/ApplicationBasicInfoEncryptionTest.java` 또는 파기 테스트에 통합

수정:

- `service/ApplicationSubmitValidator.java` (+ repo 주입, `validateBasicInfo` 최우선 호출)
- `domain/repository/ApplicationPiiPurgeRepository.java` (`purgeBasicInfo`)
- `service/ApplicationPiiPurgeService.java` (호출 1줄)
- `domain/repository/CommonCodeRepository.java` (검증용 메서드, 필요 시)
- 파기 테스트 (`ApplicationPiiPurge*Test` 류) 보강
- `docs/codex/.../phase-09-pii-field-inventory.md`

## 11. 테스트 계획

- `ApplicationBasicInfoServiceTest`: upsert 생성/수정, prefill projection(미저장 시 Applicant 매핑), 조건부 검증(FOREIGN↔countryCode, disability SUBJECT↔grade/type), 코드 미존재 거부, 비소유/비writable 거부
- `ApplicationBasicInfoControllerTest`: GET(prefill/persisted), POST 성공/검증실패, 본인 외 접근
- **암호화 라운드트립 + 파기 검증** (Minor 핵심):
  - 실제 값 저장 시 raw DB 컬럼이 **암호문**(평문 ≠ 저장값)이고, 엔티티 read 시 평문으로 복호화됨
  - `purgeBasicInfo` 실행 후 raw DB 컬럼이 **null**(평문 `'__PURGED__'`나 잔존 암호문이 아님), 엔티티 read도 null
- 제출 검증: 기본정보 누락/조건부 누락 시 제출 거부 (기존 submit 테스트 보강)
- 테스트 명령: scoped 우선 (`./gradlew test --tests "*BasicInfo*"`). 전체 `clean test`는 명시 요청 시에만, 최종 보고에 실행 여부/사유 명시.

## 12. Remaining Issues / 결정 보류

- 전화번호/우편번호 정규화·형식 정책 엄격도 (KR 기준 단순 검증으로 시작)
- `birthDate` 합리적 연령 범위 상·하한 값 (구현 시 상수 확정)
- CommonCode 그룹코드 명칭 최종 확정(`NATIONALITY`/`DISABILITY_GRADE`/`DISABILITY_TYPE` 가정) — 기존 시드/관례 확인 후 결정
- prefill의 `nameKorean` 소스: `Applicant.userName` 우선(대안 `User.name`) — 구현 시 실제 값 채움 여부 확인

## 13. Next Phase

1. 증명사진 슬라이스 (`PHOTO` 첨부 + 이미지 강화)
2. 관리자용 기본정보 조회 API (`AdminBasicInfoResponse`)
3. CommonCode 시드(국가/장애 코드) 등록 절차
