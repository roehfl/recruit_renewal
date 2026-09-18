# 학력 전체(평균) 평점 4필드 추가 (education-overall-gpa)

- 작성일: 2026-06-30
- 상태: 구현 완료
- 참조 커밋: `4422ad2` (Entity), `19058f6` (Request DTO), `b9661a3` (Response DTOs), `006e663` (Service + ServiceTest), `bfc9ca2` (ControllerTest)

---

## 1. Phase Summary

`ApplicationEducation` 엔티티에 전체(평균) 평점 요약 4필드(`overallGradePoint`, `overallMaxGradePoint`, `overallMajorGradePoint`, `overallMajorMaxGradePoint`)를 추가했다. 수동 입력 전용이며 자동 평균 계산은 없다. 학기별 성적(`ApplicationEducationSemesterGrade`)은 변경 없다. Request DTO에는 Bean Validation(`@DecimalMin`)이 적용되고, 서비스에서 쌍(pair) 일관성·범위 검증을 수행한다. 하위 호환을 위한 compact 생성자(15-arg, 16-arg)가 유지된다.

---

## 2. Implemented Scope

- `ApplicationEducation` 엔티티에 `BigDecimal` 4필드 추가 (DB nullable)
- `ApplicationEducation.create()` 20-arg 오버로드 추가 (기존 15/16-arg 오버로드 유지)
- `EducationRequest` record에 4 컴포넌트 + `@DecimalMin` 검증 추가, 15-arg/16-arg 호환 생성자 추가
- `EducationResponse`, `AdminEducationResponse` — 4 컴포넌트 + `from()` 매핑 추가
- `ApplicationEducationService.toEducation()` — 20-arg `create()` 호출로 전환
- `ApplicationEducationService.validateOverallGrades(EducationRequest)` 신규 — `validateRequest` 루프에서 호출
- 테스트: `ApplicationEducationServiceTest` 4 신규 + 기존 정상 경로 픽스처 보강 (총 16건), `ApplicationEducationControllerTest` JSON·응답 단언 (8건)

---

## 3. Changed Files

### 수정 파일

| 경로 | 변경 내용 |
|---|---|
| `src/main/java/com/shinyoung/recruit/domain/entity/ApplicationEducation.java` | 4 필드 + private ctor 확장 + 20-arg `create()` 오버로드 |
| `src/main/java/com/shinyoung/recruit/dto/request/EducationRequest.java` | 4 record 컴포넌트 + `@DecimalMin` + 15/16-arg 호환 생성자 |
| `src/main/java/com/shinyoung/recruit/dto/response/EducationResponse.java` | 4 컴포넌트 + `from()` 매핑 |
| `src/main/java/com/shinyoung/recruit/dto/response/AdminEducationResponse.java` | 4 컴포넌트 + `from()` 매핑 |
| `src/main/java/com/shinyoung/recruit/service/ApplicationEducationService.java` | `toEducation()` 20-arg 호출, `validateOverallGrades` 추가 |
| `src/test/java/com/shinyoung/recruit/service/ApplicationEducationServiceTest.java` | 4 신규 테스트 + 정상 경로 픽스처 보강 |
| `src/test/java/com/shinyoung/recruit/controller/ApplicationEducationControllerTest.java` | JSON 단언 + 응답 단언 추가 |

---

## 4. New Classes

신규 클래스 없음 (기존 클래스 수정만).

---

## 5. Modified Classes

| 클래스 | 변경 요약 |
|---|---|
| `ApplicationEducation` | 4 필드 + 20-arg `create()` + private ctor 확장 |
| `EducationRequest` | 4 컴포넌트 + `@DecimalMin` validation + 호환 생성자 2개 |
| `EducationResponse` | 4 컴포넌트 + `from()` 매핑 |
| `AdminEducationResponse` | 4 컴포넌트 + `from()` 매핑 |
| `ApplicationEducationService` | `toEducation()` 전환, `validateOverallGrades` 신규 |
| `ApplicationEducationServiceTest` | 4 신규 테스트, 픽스처 보강 |
| `ApplicationEducationControllerTest` | JSON·응답 단언 추가 |

---

## 6. Class-by-Class Explanation

### `ApplicationEducation`

- package: `com.shinyoung.recruit.domain.entity`
- class type: Entity
- responsibility: 지원서별 학력 항목 1건. 학교명·학과·학년·성적·입학/졸업일 등 기존 필드에 전체(평균) 평점 요약 4필드 추가.
- 추가 key fields:
  - `overallGradePoint` — `BigDecimal`, DB nullable, 전체 평점
  - `overallMaxGradePoint` — `BigDecimal`, DB nullable, 전체 만점기준
  - `overallMajorGradePoint` — `BigDecimal`, DB nullable, 전공 전체 평점
  - `overallMajorMaxGradePoint` — `BigDecimal`, DB nullable, 전공 전체 만점기준
- key methods:
  - `static create(..., BigDecimal overallGradePoint, BigDecimal overallMaxGradePoint, BigDecimal overallMajorGradePoint, BigDecimal overallMajorMaxGradePoint)` — 20-arg 오버로드 (신규)
  - 기존 `create(...)` 15-arg / 16-arg 오버로드 — 유지, 신규 4필드 null로 위임
- related classes: `JobApplication`, `ApplicationEducationSemesterGrade`, `EducationRequest`, `EducationResponse`, `AdminEducationResponse`
- notes:
  - 4필드 모두 DB nullable — 고등학교(HIGH_SCHOOL) 경우 overall pair 선택이므로 null 허용
  - `ApplicationEducationSemesterGrade` 1-N 관계는 변경 없음

### `EducationRequest`

- package: `com.shinyoung.recruit.dto.request`
- class type: Request DTO (`record`)
- responsibility: 학력 저장 요청. 기존 필드 위에 전체 평점 4 컴포넌트 추가.
- 추가 컴포넌트:
  - `overallGradePoint` — `@DecimalMin(value="0", inclusive=true)`, nullable `BigDecimal`
  - `overallMaxGradePoint` — `@DecimalMin(value="0", inclusive=false)`, nullable `BigDecimal` (0 초과 강제)
  - `overallMajorGradePoint` — `@DecimalMin(value="0", inclusive=true)`, nullable `BigDecimal`
  - `overallMajorMaxGradePoint` — `@DecimalMin(value="0", inclusive=false)`, nullable `BigDecimal` (0 초과 강제)
- 호환 생성자:
  - 15-arg 생성자 — 신규 4필드 null 기본값으로 위임
  - 16-arg 생성자 — 신규 4필드 null 기본값으로 위임
- notes:
  - max 필드는 `inclusive=false`로 0 입력 거부 (0으로 나누기 방지 의도)
  - 쌍 일관성·범위 검증(point<=max 등)은 Bean Validation 제외, 서비스에서 처리

### `EducationResponse`

- package: `com.shinyoung.recruit.dto.response`
- class type: Response DTO (`record`)
- responsibility: 지원자용 학력 조회 응답. 4 컴포넌트 추가.
- 추가 컴포넌트: `overallGradePoint`, `overallMaxGradePoint`, `overallMajorGradePoint`, `overallMajorMaxGradePoint`
- key methods: `static from(ApplicationEducation)` — 4필드 포함 매핑

### `AdminEducationResponse`

- package: `com.shinyoung.recruit.dto.response`
- class type: Response DTO (`record`)
- responsibility: 관리자용 학력 조회 응답. 4 컴포넌트 추가.
- 추가 컴포넌트: `overallGradePoint`, `overallMaxGradePoint`, `overallMajorGradePoint`, `overallMajorMaxGradePoint`
- key methods: `static from(ApplicationEducation)` — 4필드 포함 매핑

### `ApplicationEducationService`

- package: `com.shinyoung.recruit.service`
- class type: Service
- responsibility: 학력 저장·조회. 전체 평점 쌍 검증 추가.
- 변경 내용:
  - `toEducation(EducationRequest)` — 20-arg `ApplicationEducation.create()` 호출로 전환 (4필드 전달)
  - `validateOverallGrades(EducationRequest)` 신규 — `validateRequest` 루프 내에서 호출
    - overall pair 필수 규칙: `educationLevel != HIGH_SCHOOL` 이면 양쪽 모두 non-null 필수; `HIGH_SCHOOL` 이면 선택
    - overall major pair 선택: 모든 레벨에서 선택, 단 점수만 있고 만점이 없으면 거부 (both-or-neither)
    - max > 0: overall/major max 필드 값이 0 이하이면 거부
    - point >= 0: overall/major point 필드 음수 거부
    - point <= max: overall·major 각각 독립 검증
  - 위반 시 `InvalidJobApplicationException` (400) 발생

### `ApplicationEducationServiceTest`

- package: `com.shinyoung.recruit.service` (test)
- class type: Test
- 신규 테스트 4건:
  - `HIGH_SCHOOL_전체평점_선택` — HIGH_SCHOOL 시 overall pair null 허용 확인
  - `비고교_전체평점_필수` — HIGH_SCHOOL 외 overall pair 중 하나 null → 거부
  - `전체평점_범위_초과` — point > max → 거부
  - `전공전체평점_쌍_일관성` — major pair 한쪽만 존재(majorPoint만 또는 majorMax만) → 거부 (both-or-neither)
- 기존 정상 경로 픽스처 보강: 성공 경로 요청에 유효한 overall 4필드 포함

### `ApplicationEducationControllerTest`

- package: `com.shinyoung.recruit.controller` (test)
- class type: Test
- 변경 내용: 저장·조회 응답 JSON에 4필드 포함 단언 추가 (총 8건 그린)

---

## 7. API List

경로 변경 없음. 페이로드 필드만 추가.

| Method | Path | 대상 | 요청 변경 | 응답 변경 | 권한 |
|---|---|---|---|---|---|
| GET | `/api/applications/{applicationId}/educations` | 지원자 | 없음 | `EducationResponse`에 4필드 추가 | 지원자(본인) |
| POST | `/api/applications/{applicationId}/educations` | 지원자 | `EducationRequest`에 4필드 추가 | `EducationResponse`에 4필드 추가 | 지원자(본인) |
| GET | `/api/admin/applications/{applicationId}/educations` | 관리자 | 없음 | `AdminEducationResponse`에 4필드 추가 | 관리자 |

---

## 8. Entity Relationship Summary

```
JobApplication (1) ──── (N) ApplicationEducation
ApplicationEducation (1) ──── (N) ApplicationEducationSemesterGrade  [변경 없음]
```

- `ApplicationEducation`에 4 nullable `BigDecimal` 컬럼 추가
- `ApplicationEducationSemesterGrade` 관계·구조 변경 없음

---

## 9. Business Rules

### 전체 평점 검증 (`validateOverallGrades`)

1. **overall pair 필수 (비고교)**: `educationLevel != HIGH_SCHOOL` 이면 `overallGradePoint`·`overallMaxGradePoint` 모두 non-null 필수. `HIGH_SCHOOL`은 optional.
2. **overall pair 일관성**: 하나만 존재하면 거부 (both-or-neither).
3. **overall max > 0**: `overallMaxGradePoint`가 0 이하이면 거부.
4. **overall point >= 0**: `overallGradePoint`가 음수이면 거부.
5. **overall point <= max**: `overallGradePoint > overallMaxGradePoint` 이면 거부.
6. **major pair 선택**: `overallMajorGradePoint`·`overallMajorMaxGradePoint` 모두 모든 레벨에서 선택. 단 한쪽만 존재하면 거부 (both-or-neither).
7. **major max > 0 / major point >= 0 / major point <= major max**: 값이 존재할 때 위 overall과 동일 검증 적용.

예외 유형: `InvalidJobApplicationException` (400)

---

## 10. Test Coverage

| 클래스 | 건수 | 주요 케이스 |
|---|---|---|
| `ApplicationEducationServiceTest` | 16 (4 신규) | HIGH_SCHOOL overall 선택, 비고교 overall 필수, point>max 거부, major pair 일관성, 기존 정상 경로 |
| `ApplicationEducationControllerTest` | 8 | 저장·조회 4필드 JSON 단언, 기존 시나리오 유지 |

### 인접 리그레션 통과 (scoped)

- `AdminApplicationSectionServiceTest` · `AdminApplicationSectionControllerTest`
- `ApplicationPiiPurgeServiceTest`
- `ApplicationPdfServiceTest`
- `AdminStatisticsControllerTest`

### 테스트 실행 명령

```powershell
$env:AES_SECRET_KEY='<백엔드 CLAUDE.md 예시 키>'; .\gradlew.bat test `
  --tests "com.shinyoung.recruit.service.ApplicationEducationServiceTest" `
  --tests "com.shinyoung.recruit.controller.ApplicationEducationControllerTest" `
  --no-daemon
```

전체 리그레션은 명시 요청 시에만 실행(하네스 §5).

---

## 11. Known Limitations

1. **자동 평균 계산 없음**: `overallGradePoint`는 수동 입력 전용. 학기별 성적(`ApplicationEducationSemesterGrade`)에서 자동 집계하는 로직은 미구현.
2. **프론트 학력 입력 화면 미구현**: 4필드 입력 UI는 후속 슬라이스.
3. **프론트 `education.ts` stale 필드 미정리**: `degreeName` 등 누적 stale 필드 정리는 본 슬라이스 범위 외.
4. **전체 테스트 스위트 타임아웃**: 로컬 환경 전체 `clean test` 타임아웃으로 scoped 검증만 수행.

---

## 12. Next Phase Considerations

1. 학력 입력 UI 슬라이스 — 전체 평점 4필드 입력 화면 구현 (프론트 `recruit_front/`)
2. 자동 평균 계산 정책 결정 — 학기별 성적에서 전체 평점 자동 집계 여부
3. `education.ts` stale 필드 정리 — `degreeName` 등 불필요 필드 제거 (프론트 단독 슬라이스)
