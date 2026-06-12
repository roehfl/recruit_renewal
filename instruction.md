# 구현 지시문 — ApplicationBasicInfo (지원자 기본정보 섹션)

기준 설계: `docs/superpowers/specs/2026-06-12-application-basic-info-design.md` (2차 리뷰 반영본)
참조 패턴: `ApplicationMilitary`(지원자 섹션), `AdminMilitaryResponse`/`AdminApplicationSectionService`(관리자), `ApplicationSubmitValidator`/`ApplicationCompletionReadChecker`(검증·완성도)

## 1. 목표

지원서 작성 중 입력하는 지원자 기본정보를 **지원서별 1:1 스냅샷 섹션**으로 저장/조회하고, 제출 검증·완성도·관리자 조회·PDF까지 일관 연결한다.

기본정보 항목: 내/외국인(외국인이면 국적), 생년월일, 연락처(휴대폰 필수/비상연락처 선택), email, 이름(한글 필수/영문 선택), 보훈여부(대상/비대상), 장애여부(대상/비대상, 대상이면 등급·유형 코드), 주소.

## 2. 구현 범위

### 신규
- 엔티티 `ApplicationBasicInfo` (`BaseEntity` 상속, `@OneToOne(JobApplication)` unique, `create`/`update`)
- Enum: `NationalityType{DOMESTIC,FOREIGN}`, `VeteranStatus{SUBJECT,NOT_SUBJECT}`, `DisabilityStatus{SUBJECT,NOT_SUBJECT}`
- `ApplicationBasicInfoRepository` (`findByJobApplicationId`)
- `ApplicationBasicInfoService` (조회+prefill, upsert, 검증)
- `ApplicationBasicInfoController` (`GET`/`POST /applications/{applicationId}/basic-info`)
- DTO: `BasicInfoSaveRequest`(record), `BasicInfoResponse`(record, prefill 지원), `AdminBasicInfoResponse`(record)

### 수정
- `ApplicationSubmitValidator`: repo 주입, `validate()` 초반 **무조건** `validateBasicInfo()` 호출
- `ApplicationCompletionReadChecker`: repo 주입, **BASIC_INFO 항상 필수 그룹** 추가(config 무관)
- `AdminApplicationSectionService` + `AdminApplicationSectionController`: BasicInfo 관리자 조회 추가
- `ApplicationPdfService`: header 이름/휴대폰/email source = BasicInfo(존재 시), 없으면 기존 fallback
- `CommonCodeRepository`: `boolean existsByGroupCodeAndCodeAndActiveTrue(String groupCode, String code)`
- `ApplicationPiiPurgeRepository`: `purgeBasicInfo(applicationId)` JPQL UPDATE
- `ApplicationPiiPurgeService.purgeRelationalPii()`: `purgeBasicInfo` 호출 1줄
- `docs/codex/.../phase-09-pii-field-inventory.md`: BASIC_INFO 섹션 등록

## 3. 제외 범위
- 증명사진 업로드(후속). 후속 기준: `ApplicationAttachment` 재사용, `attachmentType=PHOTO`(현재 없음→추가), `sectionType=BASIC_INFO`, layout ATTACHMENT 섹션과 별개, 이미지 재인코딩+magic-byte 검증.
- CommonCode 시드(국가/장애 코드) — 검증 로직만, 데이터는 운영 등록.
- Applicant 엔티티 구조 변경 금지(CLAUDE.md §4.3).
- Excel export BasicInfo 반영 — 연락처/email 노출 여부 audit 후 결정(노출 안 하면 변경 없음).

## 4. 엔티티 / 암호화 / 컬럼 길이

- 문자열 PII는 모두 `AesAttributeConverter` 적용: nameKorean, nameEnglish, countryCode, mobilePhone, emergencyPhone, email, disabilityGradeCode, disabilityTypeCode, zipCode, addressBasic, addressDetail.
- 평문 아님: `nationalityType`/`veteranStatus`/`disabilityStatus`(enum), `birthDate`(LocalDate).
- 암호문 저장으로 길이 증가 → 엔티티 컬럼 length: 위 암호화 String 중 nameKorean/nameEnglish/countryCode/mobile/emergency/email/disability codes/zipCode = **500**, addressBasic/addressDetail = **1000**.
- DTO 평문 max: nameKorean 50, nameEnglish 100, email 100, phone 20, zipCode 10, code 50, address 200.
- **DB NOT NULL은 `job_application_id` FK만.** 그 외 전부 nullable(필수성은 검증으로). 파기 시 전 컬럼 null이므로 NOT NULL 금지.

## 5. Validation

### 저장(saveBasicInfo)
1. `findOwnedApplication(applicantId, applicationId)` 소유 검증
2. `validateWritable` (DRAFT + 공고 PUBLISHED + 접수기간) 재사용
3. 조건부: FOREIGN⇒countryCode 필수 / DOMESTIC⇒countryCode null, disability SUBJECT⇒grade+type 필수 / NOT_SUBJECT⇒둘 다 null
4. 코드 검증: countryCode/disabilityGradeCode/disabilityTypeCode를 `existsByGroupCodeAndCodeAndActiveTrue`로 **활성** 확인
5. 형식/상수:
   - birthDate: 만 14세 이상 ~ 만 100세 이하 (기준 `Clock` now)
   - phone: 숫자/하이픈만, 길이 9~20, normalize 안 함(입력값 그대로 저장)
6. upsert: `findByJobApplicationId().orElseGet(save(create))` 후 `update`

### 제출(validateBasicInfo) — 항상 실행
- 존재 필수 + 필수 present(nameKorean, birthDate, nationalityType, mobilePhone, email, veteranStatus, disabilityStatus) + 조건부 present(FOREIGN⇒countryCode, disability SUBJECT⇒grade+type)
- 주소·영문명·비상연락처는 제출 비필수

### 완성도(ApplicationCompletionReadChecker)
- BASIC_INFO를 무조건 required group으로 추가, 규칙은 제출 검증과 동일(완료율 ↔ 제출 일치)

## 6. Purge
- 암호화 컬럼은 `'__PURGED__'` 금지(AES 랜덤 IV → bulk update 평문 시 read 깨짐). **전 개인정보 컬럼 null로 파기.**
- `purgeBasicInfo`: nameKorean/nameEnglish/email/mobilePhone/emergencyPhone/birthDate/nationalityType/countryCode/veteranStatus/disabilityStatus/disabilityGradeCode/disabilityTypeCode/zipCode/addressBasic/addressDetail = null, createdBy/updatedBy = null.
- `purgeRelationalPii()`에 호출 추가. PII 인벤토리 문서 등록(전 필드 PURGE(null)).

## 7. Tests
- Service: upsert, prefill projection(미저장 시 Applicant 매핑), 조건부 검증, 활성코드 검증(비활성/미존재 거부), 비소유/비writable 거부
- Controller: GET prefill/persisted, POST 성공/검증실패, 본인 외 접근
- 암호화 라운드트립 + 파기: 저장 시 raw 컬럼 암호문(평문≠저장값)·read 복호화; purge 후 raw 컬럼 null
- 제출: 기본정보 누락/조건부 누락 시 제출 거부
- 완성도: `ApplicationCompletionReadCheckerTest`/`ApplicationDashboardServiceTest`에 BASIC_INFO 반영
- 관리자/PDF: AdminBasicInfoResponse 조회, PDF header BasicInfo 우선 + 없을 때 fallback
- 명령: scoped 우선. 전체 `clean test`는 명시 요청 시. 보고에 실행 여부/사유 명시.

## 8. Acceptance Criteria
- [ ] 지원자가 `GET/POST /applications/{id}/basic-info`로 기본정보를 저장·조회한다(미저장 GET은 Applicant prefill).
- [ ] 조건부 규칙(국적/장애)·활성 CommonCode·연령/전화 형식이 서비스에서 검증된다.
- [ ] 문자열 PII가 DB에 암호문으로 저장되고 read 시 복호화된다.
- [ ] 제출 시 BASIC_INFO 필수/조건부 누락이면 제출이 거부된다.
- [ ] 완성도 응답에 BASIC_INFO required group이 반영된다(제출 검증과 일치).
- [ ] 관리자가 AdminBasicInfoResponse로 기본정보를 조회한다(ci/password 미노출).
- [ ] PDF header가 BasicInfo 존재 시 그 값을, 없으면 기존 fallback을 사용한다.
- [ ] 파기 실행 시 basic_info 전 개인정보 컬럼이 null이 되고 read가 깨지지 않는다.
- [ ] 신규/수정 테스트가 통과하고, 구현 문서(md)+HTML 리포트+이력+PII 인벤토리가 갱신된다.
