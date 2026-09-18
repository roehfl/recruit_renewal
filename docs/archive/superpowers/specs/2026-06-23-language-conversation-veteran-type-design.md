# 어학 scoreOrGrade·conversationalAbility + 기본정보 veteranType 설계서

- 날짜: 2026-06-23
- 상태: 승인됨 (구현 계획 작성 전)
- 범위: **백엔드 전용** (recruit_back/recruit_backend). 프론트 연동은 후속 슬라이스.
- 관련 하네스: `recruit/CLAUDE.md` 화면 슬라이스 워크플로우, 백엔드 `CLAUDE.md` 규칙

## 1. 배경

지원서 도메인의 두 가지 독립 변경 요청 — 어학(language) 점수/등급 정리 + 회화능력 추가, 기본정보(basic info) 보훈 종류 입력 필드 추가.

- **어학**: `ApplicationLanguage`(OneToMany)는 어학 시험 행마다 `score`(점수)·`grade`(등급) 두 개의 nullable String을 가진다. 같은 도메인의 `ApplicationCertificate`는 이미 둘을 합친 `scoreOrGrade: String` 단일 필드를 쓰며 PDF에서 "점수/등급"으로 렌더한다. 회화능력(conversationalAbility)은 신규 개념으로, 공통코드 그룹 `LANGUAGE_CONVERSATION`으로 운영한다(프론트 렌더용, `SCHOOL_TYPE`/`DISABILITY_TYPE`과 동일한 코드-문자열 패턴).
- **기본정보 보훈**: `ApplicationBasicInfo`는 `veteranStatus` enum(`SUBJECT`/`NOT_SUBJECT`)만 보유하고, 보훈 대상일 때 그 "종류"를 입력할 필드가 없다. 장애의 경우 `disabilityStatus==SUBJECT`면 `disabilityType`/`disabilityGradeCode`(공통코드 + AES 암호화)를 조건부로 받는 선례가 있으나, 보훈은 민감정보가 아니므로 그 패턴을 그대로 따르지 않는다.

요청 요지: 어학 `score`·`grade` 제거 → `scoreOrGrade: String` 추가 + `conversationalAbility`(LANGUAGE_CONVERSATION 코드) 추가. 기본정보에 `veteranStatus==SUBJECT`일 때 종류를 입력할 String 필드 추가.

## 2. 목표 / 비목표

### 목표
- 어학 행의 `score`/`grade`를 단일 `scoreOrGrade`(선택)로 합친다(`ApplicationCertificate`와 동일 패턴).
- 어학 행에 `conversationalAbility`(선택, `LANGUAGE_CONVERSATION` 코드값) 추가.
- 기본정보에 `veteranType`(평문 String) 추가 — `veteranStatus==SUBJECT`면 필수, 아니면 금지.
- 변경된 API 계약을 `recruit/api-contract.md`에 기록하고 프론트 영향을 명시한다.
- PII 파기 분류/쿼리를 새 필드에 맞게 갱신한다.

### 비목표
- 프론트(`recruit_front`) 수정 — 후속 슬라이스.
- CommonCode 행 시드(`LANGUAGE_CONVERSATION`) — 런타임 admin으로 등록.
- `conversationalAbility`/`veteranType`의 공통코드 FK 검증 — 검증 미결합(disability만 코드 검증 유지).
- `veteranType` 암호화 — 보훈은 일반 PII(민감정보 아님), 기존 `veteranStatus`(평문 enum)와 동일 취급.
- 전체 리그레션/전체 빌드.
- prod 마이그레이션 스크립트(마이그레이션 도구 없음, 개발 H2는 JPA ddl 자동 반영).

## 3. 핵심 결정 (브레인스토밍 합의)

| # | 주제 | 결정 |
|---|------|------|
| 1 | score/grade 정리 | 둘 제거 → `scoreOrGrade: String`(nullable·선택·무검증). `ApplicationCertificate.scoreOrGrade`와 동일 패턴 |
| 2 | conversationalAbility | `String`(nullable·선택). `LANGUAGE_CONVERSATION` 공통코드 값이나 **백엔드 검증 미결합**(SCHOOL_TYPE 선례). 시드 안 함 |
| 3 | conversationalAbility 파기 | NULLIFY (코드값이나 같은 행이 `__PURGED__`되어 보존 실익 없음) |
| 4 | veteranType 형식 | **자유 입력 String**(공통코드 그룹/검증 없음) |
| 5 | veteranType 암호화 | **평문**(보훈 = 일반 PII, 민감정보 아님). `veteranStatus`(평문 enum)와 동일 취급 |
| 6 | veteranType 검증 | `veteranStatus==SUBJECT`면 필수, `NOT_SUBJECT`면 값 금지(있으면 400). `validateDisability` 구조 미러(코드 조회 제외) |
| 7 | veteranType 파기 | NULLIFY(평문 nullable) |
| 8 | 슬라이스 범위 | 백엔드만 + 계약 변경 기록(프론트 후속) |
| 9 | 문서화 | 경량 슬라이스(마크다운만, HTML 리포트 생략) |

## 4. 변경 상세 — 어학(language)

### 4.1 수정
- `domain/entity/ApplicationLanguage.java` — `score`,`grade` 필드 제거 → `scoreOrGrade`(String, nullable), `conversationalAbility`(String, nullable) 추가. 생성자/`create` 파라미터 반영.
- `dto/request/LanguageRequest.java` — `score`,`grade` 제거 → `scoreOrGrade`, `conversationalAbility`(둘 다 무검증 선택).
- `dto/response/LanguageResponse.java`, `dto/response/AdminLanguageResponse.java` — 동일 필드 교체 + `from()`.
- `service/ApplicationLanguageService.java` — `toLanguage()`에서 score/grade 매핑 제거, scoreOrGrade/conversationalAbility 매핑 추가. 검증 추가 없음(둘 다 선택). `validateLanguageRequiredFields`는 score/grade를 참조하지 않으므로 변화 없음.
- `service/ApplicationPdfService.java` — `languageSection()`의 `field("점수", l.score())`·`field("등급", l.grade())` → `field("점수/등급", l.scoreOrGrade())` 1필드 + `field("회화능력", l.conversationalAbility())` 추가.
- `domain/repository/ApplicationPiiPurgeRepository.java` — `purgeLanguages` 쿼리에서 `l.score=null, l.grade=null` 제거 → `l.scoreOrGrade=null, l.conversationalAbility=null` 추가.

### 4.2 규칙 변화
- `scoreOrGrade`, `conversationalAbility` 모두 **선택값**, 추가 교차검증 없이 단순 저장.
- `conversationalAbility`는 `LANGUAGE_CONVERSATION` 코드 문자열이지만 백엔드는 그룹 멤버십을 검증하지 않는다(프론트 렌더 규약).
- 둘 다 PII 파기 시 NULLIFY 대상(`purgeLanguages`).

## 5. 변경 상세 — 기본정보 보훈(basic info)

### 5.1 수정
- `domain/entity/ApplicationBasicInfo.java` — `veteranType`(String, **평문**, nullable, `@Column(length=200)`) 추가. `@Convert(AesAttributeConverter)`를 붙이지 않는다. 생성자/`create`/`update` 시그니처 반영(`veteranStatus` 바로 뒤 배치).
- `dto/request/BasicInfoSaveRequest.java` — `@Size(max=100) String veteranType` 추가(`veteranStatus` 뒤).
- `dto/response/BasicInfoResponse.java` — `veteranType` 추가 + `of()`(엔티티값), `prefill()`(null).
- `dto/response/AdminBasicInfoResponse.java` — `veteranType` 추가 + `from()`.
- `service/ApplicationBasicInfoService.java` — `validateVeteran(request)` 신규 추가 + `validateRequest`에서 호출. `toBasicInfo`/`update()` 호출에 `veteranType` thread.
  - `validateVeteran`: `veteranStatus==SUBJECT`면 `veteranType` 필수(blank이면 `InvalidJobApplicationException`), `NOT_SUBJECT`면 `veteranType`이 blank가 아니면 예외. (`validateDisability` 구조 미러, 공통코드 조회는 제외.)
- `domain/repository/ApplicationPiiPurgeRepository.java` — `purgeBasicInfo` 쿼리에 `b.veteranType = null` 추가.

### 5.2 규칙
- `veteranType` 검증은 `disabilityType`과 동일한 조건부 구조: SUBJECT↔값 존재 강제, NOT_SUBJECT↔값 부재 강제.
- 암호화하지 않는다. nullable이므로 기본정보 전 필드 NULLIFY 파기 규칙과 일관되게 NULLIFY 처리.

## 6. PII 분류 (인벤토리 갱신)

`docs/codex/implementation/phase-09-pii-field-inventory.md`:
- §5 어학: `ApplicationLanguage.score / grade` 행 → `scoreOrGrade / conversationalAbility`로 교체, nullable → NULLIFY. (languageName/testName PLACEHOLDER, examDate ALTER_NULLABLE+NULLIFY는 불변.)
- §10 기본정보: `ApplicationBasicInfo.veteranType` 행 추가 — nullable, 비암호화, NULLIFY.
- §9 DDL 요약: application_language `score`/`grade` drop + `score_or_grade`/`conversational_ability` add, application_basic_info `veteran_type` add 메모(개발 H2 자동, 운영 MariaDB ALTER 필요).

## 7. 검증 / 테스트

- 백엔드 정책(하네스 §5): **전체 리그레션 금지**, 수정 관련 테스트 클래스만 `--tests`로 선택 실행.
- 보완 대상:
  - 어학 — `ApplicationLanguageServiceTest`, `ApplicationLanguageControllerTest`, `ApplicationPiiPurgeServiceTest`(어학 파기 단언). PDF/AdminSection 테스트가 score/grade를 단언하면 함께.
  - 기본정보 — `ApplicationBasicInfoServiceTest`, `ApplicationBasicInfoControllerTest`, `BasicInfoTestSupport`(공통 빌더), `ApplicationSubmitValidatorBasicInfoTest`, `ApplicationBasicInfoEncryptionTest`(veteranType이 암호화 단언 집합에 끼지 않는지 확인), `ApplicationPiiPurgeServiceTest`(기본정보 파기 단언).
- 추가/변경 케이스:
  - 어학 — "scoreOrGrade 저장·조회", "conversationalAbility 저장·조회", 기존 score/grade 참조 케이스 교체.
  - 기본정보 — "SUBJECT면 veteranType 필수(없으면 400)", "NOT_SUBJECT인데 veteranType 있으면 400", "veteranType 저장·조회".
- 실행 예:
```powershell
$env:AES_SECRET_KEY='<백엔드 CLAUDE.md의 로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicationLanguageServiceTest" --tests "com.shinyoung.recruit.service.ApplicationBasicInfoServiceTest" (…변경 관련 클래스) --no-daemon
```

## 8. API 계약 변경 & 프론트 영향

`recruit/api-contract.md`에 아래를 기록하고 **"백엔드 구현됨 / 프론트 미반영(후속 슬라이스)"** 로 명시한다.

- 화면: 지원자 어학 (ApplicationLanguage) — `GET·POST /applications/{applicationId}/languages`
  - 요청·응답에서 `score`,`grade` 제거 → `scoreOrGrade`(선택), `conversationalAbility`(선택, `LANGUAGE_CONVERSATION` 공통코드, 프론트 렌더) 추가.
  - 관리자 조회 응답(`AdminLanguageResponse`)도 동일.
- 화면: 지원자 기본정보 (ApplicationBasicInfo) — `GET·POST /applications/{applicationId}/basic-info`
  - 요청·응답에 `veteranType`(문자열) 추가. `veteranStatus==SUBJECT`면 필수, 아니면 비워야 함.

→ 현재 `recruit_front`의 language/basic-info 연동이 깨지므로 후속 프론트 슬라이스에서 동기화한다.

## 9. 문서화 방식

새 Phase가 아니라 기존 Phase(03c-3 어학 / 기본정보) 수정이므로 **경량 슬라이스 = 마크다운만** 갱신(HTML 리포트 생략).
- 영향받는 기존 구현 문서(어학·기본정보 phase 문서)의 해당 부분 수정.
- `docs/codex/implementation/phase-09-pii-field-inventory.md` §5/§10/§9 갱신.
- `docs/codex/07-implementation-history.md`에 변경 항목 추가.

## 10. 영향 파일 종합

**수정 — 어학(6):** ApplicationLanguage.java, LanguageRequest.java, LanguageResponse.java, AdminLanguageResponse.java, ApplicationLanguageService.java, ApplicationPdfService.java
**수정 — 기본정보(5):** ApplicationBasicInfo.java, BasicInfoSaveRequest.java, BasicInfoResponse.java, AdminBasicInfoResponse.java, ApplicationBasicInfoService.java
**수정 — 공통(1):** ApplicationPiiPurgeRepository.java (`purgeLanguages` + `purgeBasicInfo`)
**테스트:** §7 목록
**문서:** api-contract.md(계약 기록), phase-09-pii-field-inventory.md, 07-implementation-history.md, 어학/기본정보 phase 구현 문서
