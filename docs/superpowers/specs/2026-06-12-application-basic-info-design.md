# ApplicationBasicInfo (지원자 기본정보 섹션) — 설계 Spec

- 작성일: 2026-06-12
- 상태: 설계 확정(구현 전)
- 작업 유형: 신규 도메인 + 지원자용 API 추가 (vertical slice)
- 기준 문서: `CLAUDE.md`, `docs/codex/02-domain-design.md`, `docs/codex/03-legacy-feature-map.md`
- 참조 구현 패턴: `ApplicationMilitary` 섹션 슬라이스

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
8. 주소
9. 사진 — **본 슬라이스 범위 밖**(아래 §4 참조)

## 2. 핵심 설계 결정 (확정)

| 결정 | 내용 |
|---|---|
| 엔티티 형태 | `JobApplication` 1:1 독립 섹션 엔티티 (`ApplicationMilitary` 패턴). Applicant 확장/Embeddable 분산은 기각 |
| 필드 소유 | **전체 스냅샷** — 이름/이메일/휴대폰 포함 모든 기본정보를 지원서별로 저장(Applicant 값으로 prefill). 제출 시점 정보가 공고별로 동결됨 |
| 사진 | **후속 슬라이스로 분리**. 본 슬라이스는 텍스트 기본정보만. 사진은 추후 `PHOTO` 첨부 타입 + 이미지 강화(재인코딩으로 EXIF/메타데이터·내장 스크립트 제거 + magic-byte 검증)로 기존 `ApplicationAttachment` 파이프라인 재사용 |
| 암호화 | 민감 PII 필드(이름·연락처·이메일·주소·장애코드)는 기존 `AesAttributeConverter`로 at-rest 암호화. enum/`nationalityType`/`birthDate`는 평문 |
| 영문명 | 선택(nullable) |
| 코드값 | 국적/장애등급/장애유형은 `CommonCode` 그룹코드 + application-level 검증(하드 FK 아님) |
| 폼 설정 | 기본정보는 항상 필수 섹션 — `ApplicationFormConfig`에 use/require 플래그 없음, `validateBasicInfoEnabled` 없음 |
| 제출 검증 | **본 슬라이스에 포함** — `ApplicationSubmitValidator`에 기본정보 필수/조건부 검증을 무조건 실행 항목으로 추가 |
| 파기 saga 편입 | **필수** — `ApplicationPiiPurgeRepository.purgeBasicInfo()` + `ApplicationPiiPurgeService` 호출 + PII 인벤토리 문서 등록 |

## 3. Scope (구현 범위)

1. 엔티티 `ApplicationBasicInfo` (암호화 컨버터 적용, `create()`/`update()`/`purgePersonalData()`)
2. Enum 3종: `NationalityType`, `VeteranStatus`, `DisabilityStatus`
3. `ApplicationBasicInfoRepository`
4. `ApplicationBasicInfoService` (조회 + upsert + 검증)
5. `ApplicationBasicInfoController` (지원자용 GET/POST)
6. DTO: `BasicInfoSaveRequest`, `BasicInfoResponse`
7. `ApplicationSectionAccessService`에 코드 검증 보조가 필요하면 추가 (또는 서비스 내 처리)
8. `ApplicationSubmitValidator`에 `validateBasicInfo()` 추가 + `ApplicationBasicInfoRepository` 주입
9. 파기 saga 편입: `ApplicationPiiPurgeRepository.purgeBasicInfo()` + `ApplicationPiiPurgeService.purgeRelationalPii()` 1줄 추가
10. CommonCode 검증용 repo 메서드(`existsByGroupCodeAndCodeAndActiveTrue` 등) 필요 시 추가
11. 테스트: Service / Controller / 파기 보강
12. 문서: `docs/codex/implementation/phase-XX-application-basic-info.md` + `docs/codex/reports/phase-XX-application-basic-info.html` + `07-implementation-history.md`

## 4. Out of Scope (범위 밖)

- **증명사진 업로드** — 후속 슬라이스. `AttachmentType.PHOTO` 추가 + 이미지 재인코딩/EXIF 제거/magic-byte 검증 + 차원·용량 캡. 기존 첨부 파이프라인(`ApplicationAttachment`, `AttachmentProperties`, 다운로드 엔드포인트, soft-delete/파기 saga)을 재사용한다.
- **관리자용 조회 API**(`AdminBasicInfoResponse`) — 다른 섹션과 동일하게 후속 슬라이스.
- **CommonCode 시드 데이터** — 국가(`NATIONALITY`)/장애등급(`DISABILITY_GRADE`)/장애유형(`DISABILITY_TYPE`) 코드는 운영/관리자 등록 대상. 본 슬라이스는 검증 로직만.
- Applicant 엔티티 구조 변경(CLAUDE.md §4.3 준수).

## 5. 엔티티 설계 — `ApplicationBasicInfo`

- package: `com.shinyoung.recruit.domain.entity`
- 상속: `BaseEntity`
- 테이블: `application_basic_info`, index `idx_application_basic_info_application(job_application_id)`

| 필드 | 타입 | 제약/비고 |
|---|---|---|
| `id` | `Long` | PK, IDENTITY |
| `jobApplication` | `JobApplication` | `@OneToOne(LAZY)`, `job_application_id` unique, NOT NULL |
| `nameKorean` | `String` | 필수, **암호화** |
| `nameEnglish` | `String` | 선택, **암호화** |
| `nationalityType` | `NationalityType` | 필수, `@Enumerated(STRING)` |
| `countryCode` | `String` | FOREIGN일 때만 값, CommonCode(`NATIONALITY`) |
| `birthDate` | `LocalDate` | 필수, 평문 |
| `mobilePhone` | `String` | 필수, **암호화** |
| `emergencyPhone` | `String` | 선택, **암호화** |
| `email` | `String` | 필수, **암호화** |
| `veteranStatus` | `VeteranStatus` | 필수, `@Enumerated(STRING)` |
| `disabilityStatus` | `DisabilityStatus` | 필수, `@Enumerated(STRING)` |
| `disabilityGradeCode` | `String` | SUBJECT일 때만, CommonCode(`DISABILITY_GRADE`), 민감정보 **암호화** |
| `disabilityTypeCode` | `String` | SUBJECT일 때만, CommonCode(`DISABILITY_TYPE`), 민감정보 **암호화** |
| `zipCode` | `String` | 선택, **암호화** |
| `addressBasic` | `String` | 주소 기본, **암호화** |
| `addressDetail` | `String` | 주소 상세, **암호화** |
| (감사필드) | | `BaseEntity` — `createdAt/updatedAt/createdBy/updatedBy` |

도메인 메서드:

- `static create(...)` / `update(...)` — `ApplicationMilitary`와 동일한 스타일
- `purgePersonalData()` — 단, 파기는 `ApplicationPiiPurgeRepository`의 bulk JPQL UPDATE로 수행하므로(영속성 컨텍스트 미로딩 + `createdBy` updatable=false 대응), 엔티티 메서드는 보조로만 둔다. 실제 파기 계약은 repository 쿼리가 단일 소스.

## 6. Enum 신규

- `NationalityType { DOMESTIC, FOREIGN }`
- `VeteranStatus { SUBJECT, NOT_SUBJECT }`
- `DisabilityStatus { SUBJECT, NOT_SUBJECT }`

## 7. API

| Method | Path | 설명 | 인증 |
|---|---|---|---|
| GET | `/applications/{applicationId}/basic-info` | 기본정보 조회(없으면 data=null) | 지원자 본인 |
| POST | `/applications/{applicationId}/basic-info` | 기본정보 upsert | 지원자 본인 |

- 응답: `ResponseEntity<ApiResponse<BasicInfoResponse>>` (`ApplicationMilitaryController`와 동일)
- 현재 사용자: `CurrentApplicantService.getCurrentApplicantId(userDetails)`

### Request DTO — `BasicInfoSaveRequest` (record)

- `@NotBlank nameKorean`, `nameEnglish`(선택)
- `@NotNull nationalityType`, `countryCode`
- `@NotNull @Past birthDate`
- `@NotBlank mobilePhone`(형식 검증), `emergencyPhone`(선택, 형식 검증)
- `@NotBlank @Email email`
- `@NotNull veteranStatus`, `@NotNull disabilityStatus`
- `disabilityGradeCode`, `disabilityTypeCode`
- `zipCode`, `addressBasic`, `addressDetail`

(조건부 규칙은 Bean Validation만으로 표현하기 어려우므로 서비스 검증으로 보강)

### Response DTO — `BasicInfoResponse` (record)

- `from(ApplicationBasicInfo)` 정적 팩토리. 모든 필드 + `basicInfoId`. (복호화는 컨버터가 투명 처리)

## 8. Validation & Business Rules

저장(`saveBasicInfo`) 시:

1. 소유 검증: `findOwnedApplication(applicantId, applicationId)`
2. 쓰기 가능: `validateWritable` (DRAFT 상태 + 공고 PUBLISHED + 접수기간 내)
3. 조건부 필드 일관성:
   - `nationalityType == FOREIGN` ⇒ `countryCode` 필수 / `DOMESTIC` ⇒ `countryCode` null이어야 함
   - `disabilityStatus == SUBJECT` ⇒ `disabilityGradeCode`·`disabilityTypeCode` 필수 / `NOT_SUBJECT` ⇒ 둘 다 null이어야 함
4. 코드 검증: `countryCode`/`disabilityGradeCode`/`disabilityTypeCode`는 각 그룹의 **활성 CommonCode**에 존재해야 함
5. 형식: 휴대폰/비상연락처 전화번호 형식, 이메일 형식, `birthDate`는 과거 + 합리적 연령 범위
6. upsert: `findByJobApplicationId().orElseGet(save(create(...)))` 후 `update(...)`

제출(`ApplicationSubmitValidator.validateBasicInfo`) 시 (항상 실행):

- `ApplicationBasicInfo` 존재 필수
- 필수 필드 present: `nameKorean`, `birthDate`, `nationalityType`, `mobilePhone`, `email`, `veteranStatus`, `disabilityStatus`
- 조건부 present: FOREIGN ⇒ `countryCode`, disability SUBJECT ⇒ grade+type

## 9. 파기(Purge) saga 편입 (필수)

`ApplicationPiiPurgeRepository`에 추가:

```java
@Modifying(flushAutomatically = true)
@Query("""
        update ApplicationBasicInfo b
        set b.nameKorean = '__PURGED__', b.email = '__PURGED__', b.mobilePhone = '__PURGED__',
            b.nameEnglish = null, b.emergencyPhone = null,
            b.birthDate = null, b.countryCode = null,
            b.disabilityGradeCode = null, b.disabilityTypeCode = null,
            b.zipCode = null, b.addressBasic = null, b.addressDetail = null,
            b.createdBy = null, b.updatedBy = null
        where b.jobApplication.id = :applicationId""")
int purgeBasicInfo(@Param("applicationId") Long applicationId);
```

- NOT NULL String PII(`nameKorean`/`email`/`mobilePhone`) → `'__PURGED__'`
- 나머지 PII + **장애코드(민감정보)** → null
- `nationalityType`/`veteranStatus`/`disabilityStatus` enum은 KEEP_TOMBSTONE(식별성 없음)
- `ApplicationPiiPurgeService.purgeRelationalPii()`에 `purgeRepository.purgeBasicInfo(applicationId);` 1줄 추가
- `docs/codex/.../phase-09-pii-field-inventory.md`에 신규 섹션 분류 등록(인벤토리가 유일 계약)

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

수정:

- `service/ApplicationSubmitValidator.java` (+ repo 주입, `validateBasicInfo`)
- `domain/repository/ApplicationPiiPurgeRepository.java` (`purgeBasicInfo`)
- `service/ApplicationPiiPurgeService.java` (호출 1줄)
- `domain/repository/CommonCodeRepository.java` (검증용 메서드, 필요 시)
- 파기 테스트 (`ApplicationPiiPurge*Test` 류) 보강
- `docs/codex/.../phase-09-pii-field-inventory.md`

## 11. 테스트 계획

- `ApplicationBasicInfoServiceTest`: upsert 생성/수정, 조건부 검증(FOREIGN↔countryCode, disability SUBJECT↔grade/type), 코드 미존재 거부, 비소유/비writable 거부
- `ApplicationBasicInfoControllerTest`: GET(null/존재), POST 성공/검증실패, 본인 외 접근
- 제출 검증: 기본정보 누락 시 제출 거부 (기존 submit 테스트 보강)
- 파기: `purgeBasicInfo` tombstone 검증
- 테스트 명령: scoped 우선 (`./gradlew test --tests "*BasicInfo*"`), 최종 보고 시 전체 실행 여부/사유 명시 (전체 `clean test`는 명시 요청 시에만)

## 12. Remaining Issues / 결정 보류

- 전화번호/우편번호 정규화·형식 정책의 엄격도 (KR 기준 단순 검증으로 시작)
- `birthDate` 합리적 연령 범위 상·하한 값 (구현 시 상수로 확정)
- CommonCode 그룹코드 명칭 최종 확정(`NATIONALITY` vs `COUNTRY` 등) — 기존 시드/관례 확인 후 결정
- 제출 검증의 영문명/주소/비상연락처는 선택 → 제출 필수 항목에서 제외(현 설계)

## 13. Next Phase

1. 증명사진 슬라이스 (`PHOTO` 첨부 + 이미지 강화)
2. 관리자용 기본정보 조회 API (`AdminBasicInfoResponse`)
3. CommonCode 시드(국가/장애 코드) 등록 절차
