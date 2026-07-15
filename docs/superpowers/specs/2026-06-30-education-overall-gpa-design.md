# 학력 전체(평균) 평점 필드 추가 — 설계서

- 날짜: 2026-06-30
- 슬라이스: 지원자 학력 (ApplicationEducation)
- 방향: 백엔드 → 프론트 (프론트 입력 화면은 후속 슬라이스, 이번엔 타입만)

## 1. 배경 / 문제

현재 학력 입력은 학교/전공/졸업상태 등 메타데이터(`ApplicationEducation`)와 **학년·학기별 성적 배열**(`ApplicationEducationSemesterGrade`)로 구성된다. 학기별 성적은 학기마다 `gradePoint`(취득평점) + `maxGradePoint`(만점) + 전공평점/전공만점/취득학점을 받는다.

그러나 학력 단위의 **전체(평균) 평점**과 그 **만점기준**을 입력하는 필드가 없다. 성적증명서의 총 평점(예: 3.8/4.5)을 그대로 담을 곳이 없어, 학기 데이터만으로는 지원자가 제출하려는 대표 평점을 표현할 수 없다.

자동 평균 계산은 배제한다. `earnedCredits`(이수학점)가 nullable이라 가중치를 신뢰할 수 없고, 학기별 `maxGradePoint`가 4.5/4.3/100 등으로 섞일 수 있어 단순 평균이 무의미하다. → **지원자 수동 입력**.

## 2. 목표 / 비목표

### 목표
- `ApplicationEducation`에 전체 평점 요약 4필드를 추가한다.
- POST(저장)·GET(조회)·관리자 조회 API의 요청/응답에 4필드를 반영한다.
- 레벨별 필수 규칙을 서비스 검증 계층에 추가한다.
- `api-contract.md`와 프론트 타입 정의를 동기화한다.

### 비목표 (YAGNI)
- 자동 평균 계산(가중치 부정확 → 제외 확정).
- 학력 입력 UI 화면 구현(아직 프론트에 화면 없음 → 후속 슬라이스).
- 기존 stale한 프론트 `education.ts` 타입 전면 정리(이번엔 4필드 추가만).
- 학기별 성적 엔티티(`ApplicationEducationSemesterGrade`) 변경.

## 3. 데이터 모델

`ApplicationEducation` 엔티티에 `BigDecimal` 4필드 추가.

| 필드 | 의미 | DB 제약 |
|---|---|---|
| `overallGradePoint` | 전체(평균) 평점 | nullable |
| `overallMaxGradePoint` | 전체 만점기준 | nullable |
| `overallMajorGradePoint` | 전공 전체 평점 | nullable |
| `overallMajorMaxGradePoint` | 전공 전체 만점기준 | nullable |

- DB는 4개 모두 **nullable**. 이유: (1) 고졸은 비필수, (2) 기존 행 백필 불필요. 필수 강제는 DB가 아니라 **서비스 검증 계층**에서 레벨별로 분기한다(기존 `validateRequest` 패턴과 동일).
- 명명은 학기 성적(`gradePoint`/`maxGradePoint`/`majorGradePoint`/`majorMaxGradePoint`)과 일관되게 `overall` 접두사를 둔다.
- 엔티티 생성자 및 `create()` 정적 팩토리(2개 오버로드: schoolId 포함/미포함) 시그니처에 4필드를 추가한다.

## 4. 검증 규칙

`ApplicationEducationService`에 추가(기존 `validateSemesterGrade` 로직 미러링). DTO 애너테이션은 형식 검증(`@DecimalMin`)만 담당하고, **레벨 조건부 필수**는 서비스에서 처리한다.

### 전체 쌍 (`overallGradePoint` / `overallMaxGradePoint`)
- `educationLevel != HIGH_SCHOOL`: **둘 다 필수**(NOT NULL).
- `educationLevel == HIGH_SCHOOL`: 선택(nullable). 값이 있으면 아래 형식 검증 적용. (학기 성적처럼 *금지*하지는 않고 *허용·비필수*.)
- 형식: `overallMaxGradePoint > 0`, `overallGradePoint >= 0`, `overallGradePoint <= overallMaxGradePoint`.

### 전공 전체 쌍 (`overallMajorGradePoint` / `overallMajorMaxGradePoint`)
- 모든 레벨에서 선택.
- `overallMajorGradePoint` 입력 시 `overallMajorMaxGradePoint` 필수.
- 형식: `overallMajorMaxGradePoint > 0`, `overallMajorGradePoint >= 0`, `overallMajorGradePoint <= overallMajorMaxGradePoint`.

검증 실패는 기존과 동일하게 `InvalidJobApplicationException`으로 던진다.

## 5. API 계약 변경

대상: `GET·POST /api/applications/{applicationId}/educations`, 관리자 `GET /api/admin/applications/{id}/educations`.

- 요청 `educations[]` 각 항목에 `overallGradePoint`, `overallMaxGradePoint`, `overallMajorGradePoint`, `overallMajorMaxGradePoint` 추가.
- 응답 `educations[]` 각 항목에 동일 4필드 추가.
- `api-contract.md`의 "지원자 학력 (ApplicationEducation)" 섹션을 🟡 초안 → 구현 후 🟢로 갱신.

## 6. 변경 파일

### 백엔드 (`recruit_back/recruit_backend/`)
- `domain/entity/ApplicationEducation.java` — 4필드 + 생성자/`create()` 오버로드 2개 갱신.
- `dto/request/EducationRequest.java` — 4필드(+`@DecimalMin`), 호환 생성자 갱신.
- `dto/response/EducationResponse.java` — 4필드 + `from()` 매핑.
- `dto/response/AdminEducationResponse.java` — 4필드 + 매핑.
- `service/ApplicationEducationService.java` — `toEducation()` 전달 + 레벨별 검증 메서드 추가.
- `test/.../service/ApplicationEducationServiceTest.java` — 4필드 저장/조회 + 레벨별 필수/형식 검증 케이스.
- `test/.../controller/ApplicationEducationControllerTest.java` — 요청/응답 4필드 왕복.
- 백엔드 문서(백엔드 CLAUDE.md 규약): `docs/codex/implementation/*.md` + `docs/codex/reports/*.html` + `docs/codex/07-implementation-history.md`.

### 프론트 (`recruit_front/`)
- `src/types/application/sections/education.ts` — `EducationRequest`/`EducationResponse` 인터페이스에 4필드 추가(`number`, optional). 입력 화면은 범위 외.

### 계약
- `recruit/api-contract.md` — ApplicationEducation 섹션 갱신.

## 7. 엣지 케이스 / 마이그레이션

- DB nullable → 기존 행 백필 불필요. H2(dev) 스키마는 `ddl-auto`로 컬럼 추가.
- `HIGH_SCHOOL`: 학기 성적은 기존 규칙상 *금지*되지만, 전체 평점은 *허용·비필수*. (혼선 방지를 위해 구현 문서에 명시.)
- `overallGradePoint`만 있고 `overallMaxGradePoint`가 없으면(비고졸) 검증 실패 → 쌍 강제.

## 8. 검증 방법

- 백엔드: 수정 패키지 테스트만 실행.
  ```powershell
  $env:AES_SECRET_KEY='<백엔드 CLAUDE.md 로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicationEducationServiceTest" --tests "com.shinyoung.recruit.controller.ApplicationEducationControllerTest" --no-daemon
  ```
- 프론트: `npm run type-check`.

## 9. 남은 이슈 / 다음 슬라이스

- 학력 입력 UI 화면(전체 평점 4필드 포함)은 후속 프론트 슬라이스에서 구현.
- 프론트 `education.ts`의 누적된 stale 필드(`degreeName` 등, 백엔드 실제 스키마와 불일치) 정리는 별도 슬라이스 권장.
