# 경력/학교 백엔드 정리 설계서 (careerType 제거 · promotionDate 추가 · schoolCode 제거 · schoolCategory 추가)

- 날짜: 2026-06-23
- 상태: 승인됨 (구현 계획 작성 전)
- 범위: **백엔드 전용** (recruit_back/recruit_backend). 프론트 연동은 후속 슬라이스.
- 관련 하네스: `recruit/CLAUDE.md` 화면 슬라이스 워크플로우, 백엔드 `CLAUDE.md` 규칙

## 1. 배경

지원서 도메인의 경력/학력 관련 두 가지 정리 요청.

- **경력**: 엔티티가 둘이다. `ApplicationCareerProfile`(지원서당 1개, OneToOne)은 `careerType` enum(`NOT_SELECTED/NEWCOMER/EXPERIENCED/NOT_APPLICABLE`) 하나만 보유하며, 이것이 "신입/경력 구분"으로 경력 행(rows) 필수·허용 여부를 통제한다. `ApplicationCareer`(OneToMany)는 개별 경력(직장) 행이다.
- **학교**: `School`(마스터, Phase 08b)은 자동완성/통계 기준 테이블로 `schoolCode`(외부 식별 키, import/생성 중복제거 1순위, ADR 0004)를 가진다. 개별 학력은 `ApplicationEducation`로 분리돼 있다.

요청 요지: careerType는 불필요 → 제거, 경력 행마다 진급일 필요 → 추가, schoolCode 불필요 → 제거, schoolCategory 추가(+ schoolCategory/schoolType은 CommonCode로 운영).

## 2. 목표 / 비목표

### 목표
- `careerType`와 그것을 축으로 한 경력 검증 규칙을 완전히 제거하고, 경력 섹션을 자유(선택) 목록으로 단순화한다.
- 각 경력에 `promotionDate`(진급일)를 추가한다(`positionTitle`을 직급으로 사용).
- School 마스터에서 `schoolCode`를 제거하고 `schoolCategory`를 추가한다.
- 변경된 API 계약을 `recruit/api-contract.md`에 기록하고 프론트 영향을 명시한다.

### 비목표
- 프론트(`recruit_front`) 수정 — 후속 슬라이스.
- CommonCode 행 시드 — 사용자가 런타임 admin으로 등록.
- 별도 진급 이력 테이블, finalRank 별도 필드.
- School 마스터에 복합 unique 제약 신규 도입.
- 전체 리그레션/전체 빌드.
- prod 마이그레이션 스크립트(현재 Flyway 등 마이그레이션 도구 없음, 개발 H2는 JPA ddl 자동 반영).

## 3. 핵심 결정 (브레인스토밍 합의)

| # | 주제 | 결정 |
|---|------|------|
| 1 | careerType 제거 범위 | 완전 제거 + 경력=자유 목록 (`ApplicationCareerProfile`/`CareerType` 통째 삭제, 타입 기반 규칙 전부 제거) |
| 2 | 최종직급/진급일 | `positionTitle`을 직급으로 사용, `promotionDate`(LocalDate, nullable)만 신규 추가 |
| 3 | schoolCode 제거 | 제거 + `(schoolName, schoolType, region)` fallback만으로 중복제거 |
| 4 | schoolCategory | School 마스터에 코드-문자열 컬럼 추가(schoolType과 동일 패턴), CommonCode 시드 안 함 |
| 5 | 슬라이스 범위 | 백엔드만 + 계약 변경 기록(프론트 후속) |
| 6 | 문서화 | 마크다운만 갱신(새 Phase 아님, HTML 리포트 생략) |

## 4. 변경 상세 — 경력(career)

### 4.1 삭제
- `domain/entity/ApplicationCareerProfile.java`
- `domain/repository/ApplicationCareerProfileRepository.java`
- `enumeration/CareerType.java`

### 4.2 수정
- `domain/entity/ApplicationCareer.java` — `promotionDate`(LocalDate, nullable) 필드 + 생성자/`create` 파라미터 추가.
- `dto/request/CareerRequest.java` — `promotionDate`(선택) 추가.
- `dto/request/CareerReplaceRequest.java` — `careerType` 제거 → `(List<CareerRequest> careers)`만. `@NotNull` import 정리.
- `dto/response/CareerItemResponse.java`, `dto/response/AdminCareerItemResponse.java` — `promotionDate` 추가(+ `from`).
- `dto/response/CareerResponse.java`, `dto/response/AdminCareerResponse.java` — `careerType` 제거 → `(careers)`만.
- `service/ApplicationCareerService.java` — 프로필 repo 의존/생성·갱신 제거, `validateRequest`의 careerType 검증 + "EXPERIENCED만 행 허용" 규칙 제거, `toCareer`에 promotionDate 매핑, `getCareerResponse`는 careers만.
- `service/ApplicationSubmitValidator.java` — careerType 기반 경력 검증 블록 제거(프로필 필수, 타입 선택 필수, EXPERIENCED↔행 강제). 경력은 제출 게이트에서 제외.
- `service/ApplicationCompletionReadChecker.java` — careerType 기반 완료 이슈(미선택/행 불일치) 제거.
- `service/AdminApplicationSectionService.java` — `getCareers`에서 careerType 조회/반환 제거.
- `service/ApplicationPdfService.java` — PDF 경력 섹션 헤더의 "경력구분: <careerType>" 제거, 진급일 행 추가.
- `service/ApplicationPiiPurgeService.java` — `purgeCareerProfile()` 호출 제거.
- `domain/repository/ApplicationPiiPurgeRepository.java` — `purgeCareerProfile` 메서드 제거 + `purgeCareers` 쿼리에 `promotionDate = null` 추가.

### 4.3 규칙 변화
- 경력 섹션은 **선택**(0개 허용). 행이 있으면 기존 행 검증(회사명·시작일 필수, 재직중↔종료일 정합성, sortOrder 유일성, 텍스트 길이 2000)은 유지한다.
- `promotionDate`는 선택값이며 추가 교차검증(시작~종료 범위) 없이 단순 저장.
- `promotionDate`는 PII(경력 날짜)라 파기 시 NULLIFY 대상(`purgeCareers`)에 포함한다.

## 5. 변경 상세 — 학교(school 마스터)

### 5.1 수정
- `domain/entity/School.java` — `schoolCode` 필드 + `uk_school_code` unique 제약 제거 / `schoolCategory`(String, 코드 문자열, schoolType과 동일 패턴) 추가. 생성자·`create`·`update` 시그니처 반영.
- `domain/repository/SchoolRepository.java` — `existsBySchoolCode`, `findBySchoolCode` 제거.
- `service/SchoolService.java` — `create`의 schoolCode 중복 선검사 제거 / `create`·`update`에 schoolCategory 전달. 수동 생성은 별도 중복검사 두지 않음(중복제거는 import fallback에만).
- `service/SchoolImportService.java` — `findExisting`에서 schoolCode 분기 제거(= `(schoolName, schoolType, region)` fallback만) / schoolCategory 파싱·적용.
- `service/SchoolImportParser.java` — HEADERS/COLUMN_COUNT 갱신.
- `controller/AdminSchoolController.java` — javadoc의 "schoolCode 불변" 문구 정리.
- `dto/request/SchoolCreateRequest.java` — schoolCode 제거 / schoolCategory 추가.
- `dto/request/SchoolUpdateRequest.java` — schoolCategory 추가.
- `dto/request/SchoolImportRowRequest.java` — schoolCode 제거 / schoolCategory 추가.
- `dto/response/SchoolResponse.java` — schoolCode 제거 / schoolCategory 추가(+ `from`).
- `exception/InvalidSchoolException.java` — javadoc의 schoolCode 중복 문구 정리.

### 5.2 결정 사항
- **xlsx import 컬럼 순서(7개 유지)**: `schoolName, schoolType, schoolCategory, educationMode, region, address, countryCode`.
- **schoolCategory**: 백엔드 validation 미결합. 그룹코드 `SCHOOL_CATEGORY`/`SCHOOL_TYPE`는 프론트 렌더용 명명 규약일 뿐 enforcement 없음. CommonCode 행 시드 안 함.
- **문서**: ADR `0004`, phase-08b/08c 구현 문서의 schoolCode dedup 서술을 "fallback만"으로 개정.

## 6. 검증 / 테스트

- 백엔드 정책(하네스 §5): **전체 리그레션 금지**, 수정 관련 테스트 클래스만 `--tests`로 선택 실행.
- 보완 대상: 경력 — `ApplicationCareerServiceTest`, `ApplicationCareerControllerTest`, `ApplicationSubmitValidatorTest`, `AdminApplicationSectionServiceTest`, `AdminApplicationSectionControllerTest`, `ApplicationDashboardServiceTest`, 완료판정 관련 테스트 / 학교 — `SchoolControllerTest`, `SchoolImportControllerTest`, School 서비스·Import 테스트.
- 추가/변경 케이스: careerType 관련 테스트(EXPERIENCED만 행 허용, 제출 게이트) 삭제 → "경력 0개 제출 허용", "promotionDate 저장·조회", "schoolCategory 저장·조회", "schoolCode 제거 후 fallback dedup".
- 실행 예:
```powershell
$env:AES_SECRET_KEY='<백엔드 CLAUDE.md의 로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicationCareerServiceTest" --tests "com.shinyoung.recruit.service.SchoolImportServiceTest" (…변경 관련 클래스) --no-daemon
```

## 7. API 계약 변경 & 프론트 영향

`recruit/api-contract.md`에 아래를 기록하고 **"백엔드 구현됨 / 프론트 미반영(후속 슬라이스)"** 로 명시한다.

- 지원자 경력 GET·POST: 응답/요청에서 `careerType` 제거, 경력 행에 `promotionDate` 추가.
- 관리자 경력 조회: 응답에서 `careerType` 제거.
- School admin 생성·수정·import·응답: `schoolCode` 제거, `schoolCategory` 추가.

→ 현재 `recruit_front`의 career/school 연동(api 모듈·화면)이 깨지므로 후속 프론트 슬라이스에서 동기화한다.

## 8. 문서화 방식

새 Phase가 아니라 기존 Phase(03c 경력 / 08b·08c 학교) 수정이므로 **마크다운만** 갱신한다(HTML 리포트 생략).
- 영향받는 기존 구현 문서의 해당 부분 수정.
- `docs/codex/07-implementation-history.md`에 변경 항목 추가.
- ADR 0004 개정.

## 9. 영향 파일 종합

**삭제(3):** ApplicationCareerProfile.java, ApplicationCareerProfileRepository.java, CareerType.java
**수정(25 = 경력 14 + 학교 11):**
- 경력: ApplicationCareer.java, CareerRequest.java, CareerReplaceRequest.java, CareerItemResponse.java, AdminCareerItemResponse.java, CareerResponse.java, AdminCareerResponse.java, ApplicationCareerService.java, ApplicationSubmitValidator.java, ApplicationCompletionReadChecker.java, AdminApplicationSectionService.java, ApplicationPdfService.java, ApplicationPiiPurgeService.java, ApplicationPiiPurgeRepository.java
- 학교: School.java, SchoolRepository.java, SchoolService.java, SchoolImportService.java, SchoolImportParser.java, AdminSchoolController.java, SchoolCreateRequest.java, SchoolUpdateRequest.java, SchoolImportRowRequest.java, SchoolResponse.java, InvalidSchoolException.java
**테스트:** §6 목록
**문서:** api-contract.md(계약 기록), ADR 0004, phase-08b/08c 구현 문서, 07-implementation-history.md
