# ApplicationBasicInfo (지원자 기본정보 섹션) — 설계 Spec

- 작성일: 2026-06-12
- 상태: 설계 확정(구현 전) — 2차 리뷰 반영본
- 작업 유형: 신규 도메인 + 지원자/관리자 API 추가 (vertical slice)
- 기준 문서: `CLAUDE.md`, `docs/codex/02-domain-design.md`, `docs/codex/03-legacy-feature-map.md`
- 참조 구현 패턴: `ApplicationMilitary`(지원자 섹션), `AdminMilitaryResponse`/`AdminApplicationSectionService`(관리자 조회), `ApplicationSubmitValidator`/`ApplicationCompletionReadChecker`(검증·완성도)

## 0. 리뷰 반영 이력

### 1차 리뷰
| 항목 | 반영 |
|---|---|
| M1 countryCode 정책 애매 | 개인정보성 코드로 일관 처리: 암호화 + null 파기 |
| M2 purge null vs NOT NULL 충돌 | DB NOT NULL은 FK만, 필수성은 검증으로 |
| M3 prefill vs GET null | B안: GET 항상 응답, 미저장 시 Applicant prefill projection |
| M4 DTO 검증 모호 | countryCode·장애코드 Bean Validation 제거, 서비스 조건부 |
| M5 주소 필수 불명확 | 입력 노출 but 제출 비필수 명문화 |
| Minor 암호화 파기 | AES 랜덤 IV 발견 → 암호화 PII는 null-on-purge |

### 2차 리뷰
| 항목 | 반영 |
|---|---|
| M1 CompletionReadChecker 누락 | `ApplicationCompletionReadChecker`에 **BASIC_INFO 항상 필수 그룹** 추가(§9) |
| M2 PDF/export가 live Applicant를 봄 | **본 슬라이스에서 PDF 스냅샷 배선 포함**: BasicInfo 존재 시 이름/휴대폰/email source of truth = BasicInfo, 없으면 기존 fallback(§10) |
| M3 관리자 조회 후속화 위험 | **A안 채택**: `AdminBasicInfoResponse` + 관리자 조회를 본 슬라이스에 포함(§7) |
| M4 CommonCode active "필요 시" | **필수**: `existsByGroupCodeAndCodeAndActiveTrue` 추가, 필수 수정 파일로 승격(§8.1, §11) |
| M5 암호화 컬럼 길이 정책 없음 | **컬럼 길이 명시**: 암호문 저장 length 별도(§6), DTO 평문 max length 분리(§7.2) |
| M6 instruction.md 부적합 | instruction.md를 **구현 지시문**으로 재작성(별도 파일) |
| Minor1 사진 후속 기준 | §5에 PHOTO 첨부 연결 기준 명문화 |
| Minor2 나이/전화 상수 고정 | §8.4 상수로 고정(만 14~100세, 전화 형식, normalize 정책) |

## 1. Purpose (목적)

지원서 작성 중 입력하는 **지원자 기본정보**를 지원서별 1:1 섹션으로 저장/조회하고, 제출 검증·완성도·관리자 조회·PDF까지 일관된 스냅샷으로 연결한다.

기본정보 항목(instruction 원본):

1. 내/외국인 (외국인이면 국적)
2. 생년월일
3. 연락처 (휴대폰 필수, 비상연락처 선택)
4. email
5. 이름 (한글 필수, 영문 선택)
6. 보훈여부 (대상/비대상)
7. 장애여부 (대상/비대상, 대상이면 등급·유형 코드)
8. 주소 (입력 노출, 제출 비필수)
9. 사진 — **본 슬라이스 범위 밖**(§5 PHOTO 기준 참조)

## 2. 핵심 설계 결정 (확정)

| 결정 | 내용 |
|---|---|
| 엔티티 형태 | `JobApplication` 1:1 독립 섹션 엔티티 (`ApplicationMilitary` 패턴) |
| 필드 소유 | 전체 스냅샷 — 모든 기본정보를 지원서별 저장, 제출 시점 정보 동결 |
| prefill | GET 미저장 시 Applicant 기반 prefill projection(`persisted=false`) |
| 사진 | 후속 슬라이스 분리(§5 기준 명시) |
| 암호화 | 모든 문자열 PII는 `AesAttributeConverter` at-rest 암호화. enum·`birthDate`는 평문 |
| 암호문 컬럼 길이 | 평문보다 길어지므로 엔티티 컬럼 length 별도 지정(§6) |
| DB 제약 | NOT NULL은 `jobApplication` FK만. 필수성은 검증으로 |
| 파기 | 모든 개인정보 컬럼 null-on-purge. `'__PURGED__'` 미사용 |
| 코드값 | 국적/장애등급/장애유형은 `CommonCode` + **활성** 검증 |
| 완성도/제출/PDF | BASIC_INFO를 완성도 체커(항상 필수)·제출 검증·PDF 스냅샷에 모두 연결 |
| 관리자 조회 | 본 슬라이스 포함(A안) |

## 3. Scope (구현 범위)

지원자측:
1. 엔티티 `ApplicationBasicInfo`(암호화 컨버터 + 컬럼 길이)
2. Enum 3종: `NationalityType`, `VeteranStatus`, `DisabilityStatus`
3. `ApplicationBasicInfoRepository`
4. `ApplicationBasicInfoService`(조회+prefill+upsert+검증)
5. `ApplicationBasicInfoController`(지원자 GET/POST)
6. DTO: `BasicInfoSaveRequest`, `BasicInfoResponse`(prefill 지원)

검증/완성도/관리자/PDF:
7. `ApplicationSubmitValidator.validateBasicInfo()` 최우선 무조건 호출
8. `ApplicationCompletionReadChecker` BASIC_INFO 항상 필수 그룹 추가
9. 관리자 조회: `AdminBasicInfoResponse` + `AdminApplicationSectionService`에 read 추가 + `AdminApplicationSectionController` 엔드포인트
10. PDF 스냅샷 배선: BasicInfo 존재 시 PDF header 이름/휴대폰/email source = BasicInfo
11. `CommonCodeRepository.existsByGroupCodeAndCodeAndActiveTrue` 추가
12. 파기: `ApplicationPiiPurgeRepository.purgeBasicInfo()` + `ApplicationPiiPurgeService` 호출

공통:
13. 테스트: Service/Controller/암호화 라운드트립/파기/완성도/제출/관리자/PDF
14. 문서: `docs/codex/implementation/phase-XX-*.md` + `reports/*.html` + `07-implementation-history.md` + `phase-09-pii-field-inventory.md`

## 4. Out of Scope (범위 밖)

- **증명사진 업로드** — 후속 슬라이스(§5 기준 참조).
- **CommonCode 시드 데이터** — 국가/장애 코드는 운영/관리자 등록. 본 슬라이스는 검증 로직만.
- Applicant 엔티티 구조 변경(CLAUDE.md §4.3 준수).
- Excel export의 BasicInfo 반영 — PDF는 본 슬라이스에서 보정. Excel export 경로가 연락처/email을 노출하는지는 구현 시 audit 후, 노출 시 동일 정책 적용(노출 안 하면 변경 없음).

## 5. 사진(PHOTO) 후속 기준 (Minor1)

본 슬라이스는 사진을 다루지 않으나, 후속 혼동 방지를 위해 기준 고정:

- 증명사진은 `ApplicationAttachment`를 사용한다.
  - `attachmentType = PHOTO` (현재 `AttachmentType`에 **PHOTO 없음 → 후속에 추가**)
  - `sectionType = BASIC_INFO`
- layout의 `ATTACHMENT` 섹션과는 **별개**다(`attachmentType`/`sectionType`이 분리되어 있어 BASIC_INFO 사진 연결 가능).
- 보안 강화: 이미지 재인코딩(EXIF/메타데이터·내장 스크립트 제거) + magic-byte 검증 + 차원·용량 캡(기존 `AttachmentProperties` 활용).

## 6. 엔티티 설계 — `ApplicationBasicInfo`

- package: `com.shinyoung.recruit.domain.entity`, 상속 `BaseEntity`
- 테이블: `application_basic_info`, index `idx_application_basic_info_application(job_application_id)`

| 필드 | 타입 | 암호화 | DB length | DB NULL | 필수(검증) | 파기 |
|---|---|:--:|:--:|:--:|:--:|:--:|
| `id` | `Long` | - | - | PK | - | - |
| `jobApplication` | `JobApplication` `@OneToOne(LAZY)` unique | - | - | **NOT NULL** | - | 유지 |
| `nameKorean` | `String` | ✔ | 500 | nullable | ✔ | null |
| `nameEnglish` | `String` | ✔ | 500 | nullable | 선택 | null |
| `nationalityType` | `NationalityType` | - | (enum) | nullable | ✔ | null |
| `countryCode` | `String` (`NATIONALITY`) | ✔ | 500 | nullable | FOREIGN시 | null |
| `birthDate` | `LocalDate` | - | - | nullable | ✔ | null |
| `mobilePhone` | `String` | ✔ | 500 | nullable | ✔ | null |
| `emergencyPhone` | `String` | ✔ | 500 | nullable | 선택 | null |
| `email` | `String` | ✔ | 500 | nullable | ✔ | null |
| `veteranStatus` | `VeteranStatus` | - | (enum) | nullable | ✔ | null |
| `disabilityStatus` | `DisabilityStatus` | - | (enum) | nullable | ✔ | null |
| `disabilityGradeCode` | `String` (`DISABILITY_GRADE`) | ✔ | 500 | nullable | SUBJECT시 | null |
| `disabilityTypeCode` | `String` (`DISABILITY_TYPE`) | ✔ | 500 | nullable | SUBJECT시 | null |
| `zipCode` | `String` | ✔ | 500 | nullable | 선택 | null |
| `addressBasic` | `String` | ✔ | 1000 | nullable | 선택 | null |
| `addressDetail` | `String` | ✔ | 1000 | nullable | 선택 | null |

### 6.1 암호문 컬럼 길이 정책 (M5)

- AES util은 IV(16B)+ciphertext를 Base64로 저장 → 평문보다 길어진다. 따라서 엔티티 컬럼 length는 **암호문 기준**으로 넉넉히 잡는다(위 표 length 열).
- DTO는 **평문 max length**로 제한(§7.2)하고, 엔티티는 암호문 저장 length로 분리한다.
- enum 컬럼은 `@Enumerated(STRING)` 기본(평문, 짧음).

### 6.2 DB 제약 / 도메인 메서드

- DB NOT NULL은 `job_application_id` FK만. 필수성은 저장·제출 검증으로 보장(파기 시 전 컬럼 null이므로 NOT NULL 충돌 방지).
- `static create(...)` / `update(...)` — `ApplicationMilitary` 스타일. 파기는 repository bulk JPQL(§9).

## 7. API

### 7.1 지원자 API

| Method | Path | 설명 |
|---|---|---|
| GET | `/applications/{applicationId}/basic-info` | 조회. 미저장 시 Applicant prefill projection |
| POST | `/applications/{applicationId}/basic-info` | upsert |

- 응답 `ResponseEntity<ApiResponse<BasicInfoResponse>>`, 사용자 `CurrentApplicantService`.
- GET prefill(B안): 행 있으면 `persisted=true`+저장값; 없으면 `persisted=false`,`basicInfoId=null`, Applicant 매핑(`nameKorean←userName`, `email←email`, `mobilePhone←phoneNumber`, 나머지 null).

### 7.2 Request DTO — `BasicInfoSaveRequest` (record)

| 필드 | Bean Validation(평문) | 비고 |
|---|---|---|
| `nameKorean` | `@NotBlank @Size(max=50)` | |
| `nameEnglish` | `@Size(max=100)` | 선택 |
| `nationalityType` | `@NotNull` | |
| `countryCode` | `@Size(max=50)` | 서비스 조건부(FOREIGN 필수/DOMESTIC null) |
| `birthDate` | `@NotNull @Past` | §8.4 연령 범위 서비스 검증 |
| `mobilePhone` | `@NotBlank @Size(max=20)` | §8.4 형식 |
| `emergencyPhone` | `@Size(max=20)` | 선택, §8.4 형식 |
| `email` | `@NotBlank @Email @Size(max=100)` | |
| `veteranStatus` | `@NotNull` | |
| `disabilityStatus` | `@NotNull` | |
| `disabilityGradeCode` | `@Size(max=50)` | 서비스 조건부(SUBJECT 필수/NOT_SUBJECT null) |
| `disabilityTypeCode` | `@Size(max=50)` | 서비스 조건부 |
| `zipCode` | `@Size(max=10)` | 선택 |
| `addressBasic` | `@Size(max=200)` | 선택 |
| `addressDetail` | `@Size(max=200)` | 선택 |

조건부 규칙(국적/장애 코드 필수·금지)은 Bean Validation이 아니라 **서비스 검증**(M4 of 1차).

### 7.3 Response DTO — `BasicInfoResponse` (record)

- `basicInfoId`(nullable), `persisted`(boolean) + 전체 필드.
- `static of(ApplicationBasicInfo)` / `static prefill(Applicant)`.

### 7.4 관리자 조회 (A안)

| Method | Path | 설명 |
|---|---|---|
| GET | `/admin/applications/{applicationId}/basic-info` | 관리자 기본정보 조회 |

- 경로는 기존 관리자 섹션 패턴(`/admin/applications/{applicationId}/military`, `/admin/applications/{applicationId}/answers`)과 일관되게 고정.
- `AdminBasicInfoResponse`(record) + `from(ApplicationBasicInfo)`.
- `AdminApplicationSectionService`에 BasicInfo read 메서드 추가(다른 섹션과 동일 톤), `AdminApplicationSectionController`에 위 엔드포인트 추가.
- `ci`/`ciHash`/`password`는 절대 노출 금지(기존 PDF 정책 상속).

## 8. Validation & Business Rules

### 8.1 저장(`saveBasicInfo`)
1. 소유 검증 `findOwnedApplication`
2. 쓰기 가능 `validateWritable`(DRAFT + PUBLISHED + 접수기간) — 공통 재사용
3. 조건부: FOREIGN⇒countryCode 필수/DOMESTIC⇒null, disability SUBJECT⇒grade+type 필수/NOT_SUBJECT⇒null
4. 코드 검증: `existsByGroupCodeAndCodeAndActiveTrue`로 **활성** CommonCode 확인(M4)
5. 형식: §8.4
6. upsert: `findByJobApplicationId().orElseGet(save(create))` 후 `update`

### 8.2 제출(`ApplicationSubmitValidator.validateBasicInfo`) — 항상 실행
- **호출 순서**: `validate()` 진입 직후, **config null 검사보다 먼저** `validateBasicInfo(applicationId)`를 호출. 즉:
  ```java
  Long applicationId = application.getId();
  validateBasicInfo(applicationId);          // BasicInfo는 폼 설정 무관 항상 필수 → 최우선

  ApplicationFormConfig config = application.getJobPosting().getApplicationFormConfig();
  if (config == null) { throw ...; }
  // 이하 기존 섹션 검증
  ```
- 존재 필수 + 필수 present(`nameKorean`,`birthDate`,`nationalityType`,`mobilePhone`,`email`,`veteranStatus`,`disabilityStatus`) + 조건부 present(FOREIGN⇒countryCode, disability SUBJECT⇒grade+type).
- 주소·영문명·비상연락처는 제출 비필수.

### 8.3 완성도(`ApplicationCompletionReadChecker`) — BASIC_INFO 항상 필수 그룹 (M1)
- `check()`에서 **무조건** `BASIC_INFO`를 required group으로 추가(config 무관).
- 누락/조건부 위반 시 required issue 등록. 규칙은 §8.2와 동일하게 유지(완료율과 제출 검증 일치).
- group 코드 상수 `BASIC_INFO`, `ApplicationBasicInfoRepository` 주입.

### 8.4 고정 상수 (Minor2)
- `birthDate`: 만 14세 이상 ~ 만 100세 이하. 기준일은 `Clock`(now). 범위 밖이면 검증 예외.
- 전화번호(`mobilePhone`/`emergencyPhone`): **숫자와 하이픈만 허용**, 길이 9~20. carrier prefix 강제는 하지 않음.
- normalize: **v1은 입력값 그대로 저장(normalize 안 함)**, 형식 검증만 수행. (정규화는 후속 정책)

## 9. 파기(Purge) saga 편입

### 9.1 근거
- `AesCryptoUtil` 랜덤 IV(비결정적) → 암호화 컬럼에 bulk update로 `'__PURGED__'` 평문을 넣으면 컨버터 미적용 시 read 깨짐 → 암호화 PII는 **null로 파기**.
- 일관성을 위해 전 개인정보 컬럼 null. 파기 marker는 root `JobApplication`이 보유, basic_info 행은 빈 shell.

### 9.2 쿼리
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
- `ApplicationPiiPurgeService.purgeRelationalPii()`에 호출 1줄.
- `phase-09-pii-field-inventory.md` 신규 섹션 등록(전 필드 PURGE(null), KEEP_TOMBSTONE 없음).

## 10. PDF/export 스냅샷 배선 (M2)

- `ApplicationPdfService.buildHeader()`는 현재 이름=snapshot, **휴대폰/email=live Applicant**. → 스냅샷 계약 미완성.
- 보정: **`ApplicationBasicInfo` 존재 시 PDF header 이름/휴대폰/email source of truth = BasicInfo**.
- **fallback 조건은 "BasicInfo row 부재"만**이다(레거시 지원서). **row가 존재하지만 파기로 전 필드 null인 경우 fallback 금지** — purge된 지원서가 PDF에서 live Applicant PII로 부활하면 안 된다. 즉 분기 기준은 "필드 null 여부"가 아니라 "row 존재 여부".
  - 회귀 방지 테스트명: `PDF_BasicInfo_존재하지만_필드_null이면_Applicant로_fallback하지_않는다`
- 구현 위치: `AdminApplicationSectionService`에 BasicInfo read를 추가(§7.4)하고 `ApplicationPdfService`가 이를 사용하도록 배선. 복호화는 컨버터가 투명 처리, `ci`/`password`는 미노출.
- Excel export는 §4대로 audit 후 동일 정책.

## 11. 변경/신규 파일

신규:
- `domain/entity/ApplicationBasicInfo.java`
- `enumeration/NationalityType.java`, `VeteranStatus.java`, `DisabilityStatus.java`
- `domain/repository/ApplicationBasicInfoRepository.java`
- `service/ApplicationBasicInfoService.java`
- `controller/ApplicationBasicInfoController.java`
- `dto/request/BasicInfoSaveRequest.java`
- `dto/response/BasicInfoResponse.java`, `dto/response/AdminBasicInfoResponse.java`
- 테스트: `ApplicationBasicInfoServiceTest`, `ApplicationBasicInfoControllerTest`, 암호화/파기 테스트

수정(필수):
- `service/ApplicationSubmitValidator.java`(repo 주입, `validateBasicInfo` 최우선)
- `service/ApplicationCompletionReadChecker.java`(BASIC_INFO 그룹, repo 주입)
- `service/AdminApplicationSectionService.java`(BasicInfo read) + `controller/AdminApplicationSectionController.java`(엔드포인트)
- `service/ApplicationPdfService.java`(header BasicInfo 우선 + fallback)
- `domain/repository/CommonCodeRepository.java`(`existsByGroupCodeAndCodeAndActiveTrue`)
- `domain/repository/ApplicationPiiPurgeRepository.java`(`purgeBasicInfo`)
- `service/ApplicationPiiPurgeService.java`(호출 1줄)
- `phase-09-pii-field-inventory.md`
- 관련 테스트(`ApplicationCompletionReadCheckerTest`/`ApplicationDashboardServiceTest`, `ApplicationPdfServiceTest`, `ApplicationPiiPurge*Test`) 보강

## 12. 테스트 계획

- Service: upsert, prefill projection, 조건부 검증, 활성코드 검증(비활성/미존재 거부), 비소유/비writable 거부
- Controller: GET prefill/persisted, POST 성공/검증실패, 본인 외 접근
- 암호화 라운드트립 + 파기: 실제 값 저장 시 raw 컬럼 암호문(평문≠저장값)·read 복호화; `purgeBasicInfo` 후 raw 컬럼 null
- 제출: 기본정보 누락/조건부 누락 시 제출 거부
- 완성도: BASIC_INFO required group 반영(누락 시 미완료) — `ApplicationCompletionReadCheckerTest`/`ApplicationDashboardServiceTest`
- 관리자/PDF: AdminBasicInfoResponse 조회, PDF header가 BasicInfo 우선 + row 부재 시에만 fallback + `PDF_BasicInfo_존재하지만_필드_null이면_Applicant로_fallback하지_않는다`
- 명령: scoped 우선(`./gradlew test --tests "*BasicInfo*"` 등). 전체 `clean test`는 명시 요청 시에만, 보고에 실행 여부/사유 명시.

## 13. Remaining Issues
- CommonCode 그룹코드 명칭 최종 확정(`NATIONALITY`/`DISABILITY_GRADE`/`DISABILITY_TYPE` 가정).
- prefill `nameKorean` 소스: `Applicant.userName` 우선(대안 `User.name`).
- Excel export의 연락처/email 노출 여부 audit 결과에 따른 추가 보정.

## 14. Next Phase
1. 증명사진 슬라이스(`PHOTO` 첨부 + 이미지 강화)
2. CommonCode 시드(국가/장애 코드) 등록 절차
3. (필요 시) Excel export BasicInfo 반영
