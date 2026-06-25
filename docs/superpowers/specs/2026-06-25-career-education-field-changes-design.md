# 경력·학력 입력 필드 변경 설계서

- 날짜: 2026-06-25
- 범위: 백엔드 단독 (recruit_back/recruit_backend)
- 슬라이스 유형: 기존 화면(지원자 경력 / 지원자 학력) 필드 수정
- 상태: 설계 확정(사용자 승인 대기 → 검토 후 구현 계획 작성)

## 1. 배경 / 목표

지원자 입력 화면의 두 섹션에서 필드를 교체한다.

1. **경력(ApplicationCareer)**: `responsibilities`(담당업무) 제거, `currentSalary`(현재연봉) 추가.
2. **학력(ApplicationEducation)**: `degreeName`(학위명) 제거, `additionalMajorType`(복수/부/세부전공 구분, CommonCode) · `additionalMajorName`(해당 전공 명칭) · `thesisTitle`(논문명) 추가.

프론트엔드는 별도 작업이며, 이 설계서는 백엔드 소스/계약/PII 인벤토리 변경만 다룬다.

## 2. 의사결정 요약 (사용자 확정)

| # | 결정 | 값 |
|---|---|---|
| 1 | currentSalary 배치 | `ApplicationCareer` per-row 컬럼 (responsibilities 자리 대체) |
| 2 | currentSalary 타입/단위 | `Integer`, **만원 단위**, nullable, `≥ 0` |
| 3 | 학력 신규 필드명 | `additionalMajorType` / `additionalMajorName` / `thesisTitle` |
| 4 | degreeName | **삭제** (레벨 구분은 `educationLevel`에 보존되므로 손실 없음) |
| 5 | additionalMajorType 모델링 | `String` 코드값 저장, FK 없음, CommonCode 테이블 검증 안 함 (`veteranType`·`schoolType` 선례) |
| 6 | CommonCode 그룹명 | `MAJOR_TYPE` (계약에 명시, 백엔드 미검증) |
| 7 | PDF 신규 필드 | **입력이 있는 경우에만** 줄 렌더 (코드값 그대로 출력) |
| 8 | 문서화 범위 | `api-contract.md` + `phase-09-pii-field-inventory.md` 갱신만 (Phase Markdown/HTML 리포트 생략) |

## 3. 설계 상세

### 3.1 Career — responsibilities 제거 / currentSalary 추가

신규 필드 정의:

- 엔티티: `private Integer currentSalary;` (컬럼 `current_salary`, nullable). 의미 주석: "현재연봉(만원 단위), 파기 시 NULLIFY".
- 위치: 생성자/팩토리/DTO에서 기존 `responsibilities` 자리(= `currentlyEmployed` 다음, `resignationReason` 앞)를 그대로 대체.

검증:

- Request DTO `CareerRequest`: `@PositiveOrZero(message = "Current salary must be greater than or equal to 0.") Integer currentSalary` (null 허용).
- Service `validateCareerRequiredFields`: `responsibilities` 길이검증 **제거**, `currentSalary != null && currentSalary < 0` 검증 **추가**. `resignationReason` 길이검증과 `CAREER_TEXT_MAX_LENGTH` 상수는 **유지**.

### 3.2 Education — degreeName 제거 / 3개 필드 추가

신규 필드 정의(엔티티):

- `private String additionalMajorType;` (`@Column(length = 200)`) — CommonCode `MAJOR_TYPE` 그룹의 코드값. 예: `DOUBLE_MAJOR`/`MINOR`/`SUB_MAJOR`. (코드 집합은 프론트/관리자 소관)
- `private String additionalMajorName;` (기본 길이) — 해당 복수/부/세부전공의 명칭.
- `private String thesisTitle;` (기본 길이) — 논문명.

Request DTO `EducationRequest`:

- `degreeName` 제거, 3개 필드 추가. `additionalMajorType`은 `@Size(max = 100)`. 나머지 둘은 `majorName` 스타일대로 검증 애너테이션 없음.
- **하위호환 생성자**(schoolId 없던 Phase 08c 이전 호환)도 `degreeName` 제거 + 신규 필드 반영. 비테스트 호출처가 없으면 제거 검토.

규칙:

- 3개 필드 모두 **선택 입력**, **교차검증 없음** (type 없이 name만, 혹은 그 반대도 허용; YAGNI).

엔티티 팩토리:

- `ApplicationEducation.create(...)` 오버로드 2개(with/without schoolId) 모두 `degreeName` 파라미터 제거 + 3개 파라미터 추가(위치: `majorName` 다음).

### 3.3 PII purge 분류 (`ApplicationPiiPurgeRepository` + 인벤토리)

| 필드 | 분류 | purge 쿼리 변경 |
|---|---|---|
| `currentSalary` (재무정보) | NULLIFY | `purgeCareers`: `c.responsibilities = null` 제거, `c.currentSalary = null` 추가 |
| `additionalMajorType` (코드값) | KEEP_TOMBSTONE | 변경 없음 (건드리지 않음) |
| `additionalMajorName` (자유텍스트) | NULLIFY | `purgeEducations`: `e.additionalMajorName = null` 추가 |
| `thesisTitle` (자유텍스트) | NULLIFY | `purgeEducations`: `e.thesisTitle = null` 추가 |
| `degreeName` (삭제) | — | `purgeEducations`: `e.degreeName = null` 제거 |

`phase-09-pii-field-inventory.md`의 Career/Education 섹션 분류표를 위와 동기화한다(이 인벤토리가 purge 쿼리의 단일 계약).

### 3.4 PDF (`ApplicationPdfService`)

- **educationSection**: `field("학위", e.degreeName())` 줄 제거. `전공`(majorName) 다음에 신규 3줄을 **값이 있을 때만** 조건부 `add`:
  - `전공구분` ← `additionalMajorType` (코드값)
  - `복수/부전공명` ← `additionalMajorName`
  - `논문명` ← `thesisTitle`
- **careerSection**: `field("담당업무", c.responsibilities())` 줄 제거. `재직중` 다음에 `현재연봉` ← `currentSalary` 를 **값이 있을 때만** 조건부 `add`. 이를 위해 careerSection의 `List.of(...)`(불변)를 `ArrayList`(가변)로 전환.
- 조건부 판단: 문자열은 비어있지 않을 때(null/blank 제외), `currentSalary`는 null 아닐 때. 기존 `str(null)→""` 헬퍼는 빈 줄을 만들므로 명시적 조건부가 필요하다.
- 코드값(`additionalMajorType`)은 표시명 변환 없이 그대로 출력(`veteranType` 선례 동일).

### 3.5 계약 문서 (`api-contract.md`)

- 경력 섹션: 요청·응답 필드목록에서 `responsibilities` → `currentSalary` 교체. "현재연봉(만원, nullable, 0 이상)" 주석.
- 학력 섹션: 신규 추가. 요청·응답 필드에서 `degreeName` 제거, 3개 필드 추가. `additionalMajorType`은 코드 문자열이며 프론트가 CommonCode 그룹 `MAJOR_TYPE`로 렌더, 백엔드 validation 미결합임을 명시(`schoolType`/`schoolCategory` 주석 패턴 동일). 관리자 조회 응답도 동일 반영.

## 4. 변경 파일 목록

### 코드 (main)

1. `domain/entity/ApplicationCareer.java`
2. `dto/request/CareerRequest.java`
3. `dto/response/CareerItemResponse.java`
4. `dto/response/AdminCareerItemResponse.java`
5. `service/ApplicationCareerService.java`
6. `domain/entity/ApplicationEducation.java`
7. `dto/request/EducationRequest.java`
8. `dto/response/EducationResponse.java`
9. `dto/response/AdminEducationResponse.java`
10. `service/ApplicationEducationService.java`
11. `service/ApplicationPdfService.java`
12. `domain/repository/ApplicationPiiPurgeRepository.java`

### 테스트

13. `test/.../service/ApplicationCareerServiceTest.java`
14. `test/.../controller/ApplicationCareerControllerTest.java`
15. `test/.../service/ApplicationEducationServiceTest.java`
16. `test/.../controller/ApplicationEducationControllerTest.java`
17. (확인) PII purge 테스트 / PDF 테스트가 `responsibilities`·`degreeName`·라벨을 단언하면 갱신

### 문서

18. `recruit/api-contract.md` (경력 갱신 + 학력 신규)
19. `recruit_back/recruit_backend/docs/codex/implementation/phase-09-pii-field-inventory.md`

## 5. 검증 정책

수정 패키지만 실행(전체 리그레션은 명시 요청 시):

```powershell
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test `
  --tests "com.shinyoung.recruit.service.ApplicationCareerServiceTest" `
  --tests "com.shinyoung.recruit.controller.ApplicationCareerControllerTest" `
  --tests "com.shinyoung.recruit.service.ApplicationEducationServiceTest" `
  --tests "com.shinyoung.recruit.controller.ApplicationEducationControllerTest" `
  --no-daemon
```

PII purge / PDF 관련 테스트가 영향받으면 해당 테스트 클래스도 추가 실행한다.

## 6. 가정 / 구현 시 확인사항

- **스키마**: JPA `ddl-auto`로 반영 가정. 구현 시 Flyway/`schema.sql`/`data.sql` 존재 여부를 grep으로 확인하고, 있으면 컬럼 추가/삭제 반영.
- **호출처**: `ApplicationCareer.create(...)`, `ApplicationEducation.create(...)`, `EducationRequest`(하위호환 생성자 포함)의 **비테스트 호출처**를 grep으로 확인 후 시그니처 일괄 갱신.
- **CareerResponse / AdminCareerResponse**(리스트 래퍼)는 필드 변경 없음.
- 응답 DTO 필드 순서 변경은 프론트 영향 없음(JSON 키 기반)이나, 가독성 위해 신규 필드는 관련 필드 옆에 배치.

## 7. Out of scope

- 프론트엔드 화면/스토어/`src/api` 변경.
- `MAJOR_TYPE` CommonCode 초기 데이터 시드(관리자/프론트 소관, 백엔드 미검증).
- PDF에서 CommonCode 코드 → 표시명 변환.
- 경력 기존 필드(담당업무 외)·학력 기존 필드의 의미/검증 변경.
- 백엔드 Phase Markdown 구현문서 + HTML 리포트 작성(사용자 결정 8).

## 8. Acceptance criteria

- [ ] `responsibilities`·`degreeName`가 엔티티/DTO/서비스/PDF/purge/테스트에서 완전히 제거됨(잔존 참조 0).
- [ ] `currentSalary`(Integer, ≥0, nullable)가 경력 요청→저장→조회 왕복으로 보존됨.
- [ ] `additionalMajorType`/`additionalMajorName`/`thesisTitle`가 학력 요청→저장→조회 왕복으로 보존됨.
- [ ] PII purge 실행 시 `currentSalary`·`additionalMajorName`·`thesisTitle`는 NULL, `additionalMajorType`는 보존.
- [ ] PDF에 신규 필드가 값 있을 때만 렌더됨.
- [ ] 수정 패키지 테스트 통과.
- [ ] `api-contract.md`·PII 인벤토리가 코드와 일치.
