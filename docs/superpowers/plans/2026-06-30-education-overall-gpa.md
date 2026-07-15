# 학력 전체(평균) 평점 필드 추가 — 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `ApplicationEducation`(학력 1건)에 전체(평균) 평점·만점기준과 전공 전체 평점·만점기준 4개 필드를 추가하고, 저장/조회/관리자조회 API와 검증·계약·프론트 타입을 동기화한다.

**Architecture:** `ApplicationEducation` 엔티티에 `BigDecimal` 4필드를 추가(DB nullable). 필수 강제는 DB가 아니라 `ApplicationEducationService` 검증 계층에서 학력 레벨별로 분기(`HIGH_SCHOOL`은 선택, 그 외는 필수). 기존 `create()` 오버로드와 `EducationRequest` 호출부를 깨지 않도록, 엔티티는 새 `create()` 오버로드를 추가하고 DTO는 기존 시그니처를 보존하는 호환 생성자를 둔다.

**Tech Stack:** Java 17, Spring Boot 4.x, JPA(H2 dev), Gradle Wrapper, JUnit5 + AssertJ + MockMvc, Vue 3 + TS(타입 정의만).

---

## 배경 / 참고 문서 (구현 전 필독)

- 설계서: `docs/superpowers/specs/2026-06-30-education-overall-gpa-design.md`
- 백엔드 규칙: `recruit_back/recruit_backend/CLAUDE.md` (특히 §5 코드 스타일, §10 테스트, Implementation Documentation Rules)
- 계약 기준: `recruit/api-contract.md` "지원자 학력 (ApplicationEducation)" 섹션

핵심 결정:
- 자동 평균 계산 없음(수동 입력). 학기별 성적은 변경하지 않음.
- 전체 쌍(`overallGradePoint`/`overallMaxGradePoint`): `HIGH_SCHOOL`이 아니면 필수, `HIGH_SCHOOL`이면 선택.
- 전공 전체 쌍(`overallMajorGradePoint`/`overallMajorMaxGradePoint`): 모든 레벨에서 선택.
- DB는 4개 모두 nullable.

## 작업 루트

- 백엔드 작업 디렉토리: `recruit_back/recruit_backend/`
- 프론트 작업 디렉토리: `recruit_front/`
- 계약/플랜: `recruit/`

## 파일 구조 (생성/수정 대상)

백엔드(`recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/`):
- 수정 `domain/entity/ApplicationEducation.java` — 4필드 + 새 create() 오버로드
- 수정 `dto/request/EducationRequest.java` — 4필드 + @DecimalMin + 호환 생성자 2개
- 수정 `dto/response/EducationResponse.java` — 4필드 + from() 매핑
- 수정 `dto/response/AdminEducationResponse.java` — 4필드 + from() 매핑
- 수정 `service/ApplicationEducationService.java` — toEducation() 전달 + 검증 추가

백엔드 테스트(`.../src/test/java/com/shinyoung/recruit/`):
- 수정 `service/ApplicationEducationServiceTest.java`
- 수정 `controller/ApplicationEducationControllerTest.java`

프론트(`recruit_front/`):
- 수정 `src/types/application/sections/education.ts`

계약/문서:
- 수정 `recruit/api-contract.md`
- 생성 `recruit_back/recruit_backend/docs/codex/implementation/education-overall-gpa.md`
- 생성 `recruit_back/recruit_backend/docs/codex/reports/education-overall-gpa.html`
- 수정 `recruit_back/recruit_backend/docs/codex/07-implementation-history.md`

**참고 — `ApplicationEducation.create()` 직접 호출 테스트(시그니처 보존으로 무변경 대상):** `AdminApplicationSectionControllerTest`, `AdminStatisticsControllerTest`, `AdminApplicationSectionServiceTest`, `ApplicationPiiPurgeServiceTest`. 이들은 서비스 검증을 거치지 않고 repository에 직접 seed하므로, 기존 create() 오버로드 시그니처만 유지하면 수정 불필요하다.

---

## Task 1: API 계약 🟡 초안 기재

**Files:**
- Modify: `recruit/api-contract.md` (ApplicationEducation 섹션, 현재 line 60-71 부근)

- [ ] **Step 1: 계약 문서에 4필드 초안 추가**

`recruit/api-contract.md`의 `#### GET·POST /api/applications/{applicationId}/educations` 블록에서, line 68의 요청 필드 목록 끝(`schoolId` 다음)에 4필드를 추가하고, 변경 메모 한 줄을 덧붙인다.

기존 line 67-71을 아래로 교체한다:

```markdown
- 변경(2026-06-25): 요청·응답에서 `degreeName` 제거. `additionalMajorType`(복수/부/세부전공 구분), `additionalMajorName`(해당 전공 명칭), `thesisTitle`(논문명) 추가.
- 변경(2026-06-30, 🟡 구현 중): 학력 단위 전체 평점 요약 4필드 추가 — `overallGradePoint`(전체 평점), `overallMaxGradePoint`(전체 만점기준), `overallMajorGradePoint`(전공 전체 평점), `overallMajorMaxGradePoint`(전공 전체 만점기준). 모두 `BigDecimal`. 전체 쌍은 `HIGH_SCHOOL`이 아니면 필수, `HIGH_SCHOOL`이면 선택. 전공 전체 쌍은 모든 레벨에서 선택. 자동 평균계산 없음(수동 입력).
- 요청: `{ educations: [{ educationLevel, schoolName, majorName, additionalMajorType, additionalMajorName, thesisTitle, admissionDate, graduationDate, graduationStatus, dayNightType, campusType, transfer, countryCode, sortOrder, semesterGrades, schoolId, overallGradePoint, overallMaxGradePoint, overallMajorGradePoint, overallMajorMaxGradePoint }] }`
- 응답(200): `ApiResponse<{ educations: [...] }>` (degreeName 없음, 3필드 + 전체평점 4필드 포함, educationId 포함)
- `additionalMajorType`는 코드 문자열(프론트가 CommonCode 그룹 `MAJOR_TYPE`로 렌더, 백엔드 validation 미결합). `additionalMajorName`/`thesisTitle`는 선택 자유텍스트.
- 전체 평점 쌍/전공 전체 쌍은 함께 입력해야 한다(평점만 있고 만점이 없으면 검증 실패). 평점 ≤ 만점, 만점 > 0.
- 관리자 조회 `GET /api/admin/applications/{id}/educations` 응답도 동일하게 4필드 추가.
```

- [ ] **Step 2: Commit**

```bash
cd /c/Users/roehf/Desktop/recruit
git add api-contract.md
git commit -m "docs(contract): 학력 전체(평균) 평점 4필드 계약 초안 추가"
```

---

## Task 2: 엔티티에 4필드 + 새 create() 오버로드 추가

**Files:**
- Modify: `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/domain/entity/ApplicationEducation.java`

기존 2개 create() 오버로드(15-param: schoolId 없음, 16-param: schoolId 포함)의 **시그니처는 그대로 유지**한다(직접 호출 테스트 보호). 4필드를 받는 새 오버로드를 추가하고, private 생성자에 4필드를 더한다.

- [ ] **Step 1: 필드 4개 추가**

`private Long schoolId;`(line 88) 선언 블록과 `@Column(nullable = false) private Integer sortOrder;`(line 90-91) 사이에 아래를 삽입한다. `import java.math.BigDecimal;`도 추가한다(파일 상단 import에 `java.time.LocalDate` 옆).

```java
    // 학력 단위 전체(평균) 평점·만점기준. HIGH_SCHOOL이 아니면 서비스 검증에서 필수, HIGH_SCHOOL은 선택. DB는 nullable.
    private BigDecimal overallGradePoint;

    private BigDecimal overallMaxGradePoint;

    // 전공 전체 평점·만점기준(모든 레벨 선택).
    private BigDecimal overallMajorGradePoint;

    private BigDecimal overallMajorMaxGradePoint;
```

- [ ] **Step 2: private 생성자에 4 파라미터 추가**

private 생성자(line 93-127)의 파라미터 목록에서 `Long schoolId,` 다음, `Integer sortOrder` 앞에 4개를 추가하고, 본문 대입도 추가한다. 변경 후 생성자는 다음과 같다:

```java
    private ApplicationEducation(
            JobApplication jobApplication,
            EducationLevel educationLevel,
            String schoolName,
            String majorName,
            String additionalMajorType,
            String additionalMajorName,
            String thesisTitle,
            LocalDate admissionDate,
            LocalDate graduationDate,
            GraduationStatus graduationStatus,
            DayNightType dayNightType,
            CampusType campusType,
            Boolean transfer,
            String countryCode,
            Long schoolId,
            BigDecimal overallGradePoint,
            BigDecimal overallMaxGradePoint,
            BigDecimal overallMajorGradePoint,
            BigDecimal overallMajorMaxGradePoint,
            Integer sortOrder
    ) {
        this.jobApplication = jobApplication;
        this.educationLevel = educationLevel;
        this.schoolName = schoolName;
        this.majorName = majorName;
        this.additionalMajorType = additionalMajorType;
        this.additionalMajorName = additionalMajorName;
        this.thesisTitle = thesisTitle;
        this.admissionDate = admissionDate;
        this.graduationDate = graduationDate;
        this.graduationStatus = graduationStatus;
        this.dayNightType = dayNightType;
        this.campusType = campusType;
        this.transfer = transfer;
        this.countryCode = countryCode;
        this.schoolId = schoolId;
        this.overallGradePoint = overallGradePoint;
        this.overallMaxGradePoint = overallMaxGradePoint;
        this.overallMajorGradePoint = overallMajorGradePoint;
        this.overallMajorMaxGradePoint = overallMajorMaxGradePoint;
        this.sortOrder = sortOrder;
    }
```

- [ ] **Step 3: 16-param create() 오버로드가 새 오버로드로 위임하도록 본문 변경 (시그니처 불변)**

기존 16-param(schoolId 포함) create() 오버로드(line 153-189)의 **본문**만 교체한다. 시그니처는 유지하고, 4개 overall에 null을 넘겨 새 20-param 오버로드로 위임한다:

```java
    public static ApplicationEducation create(
            JobApplication jobApplication,
            EducationLevel educationLevel,
            String schoolName,
            String majorName,
            String additionalMajorType,
            String additionalMajorName,
            String thesisTitle,
            LocalDate admissionDate,
            LocalDate graduationDate,
            GraduationStatus graduationStatus,
            DayNightType dayNightType,
            CampusType campusType,
            Boolean transfer,
            String countryCode,
            Long schoolId,
            Integer sortOrder
    ) {
        return create(
                jobApplication, educationLevel, schoolName, majorName,
                additionalMajorType, additionalMajorName, thesisTitle,
                admissionDate, graduationDate, graduationStatus, dayNightType, campusType,
                transfer, countryCode, schoolId, null, null, null, null, sortOrder);
    }
```

(15-param 오버로드 line 129-151은 그대로 둔다 — 이미 16-param 오버로드로 위임하므로 자동으로 overall=null 이 된다.)

- [ ] **Step 4: 4필드를 받는 새 create() 오버로드 추가**

위 16-param 오버로드 바로 아래에 새 오버로드를 추가한다(이것이 private 생성자를 호출하는 유일한 진입점):

```java
    public static ApplicationEducation create(
            JobApplication jobApplication,
            EducationLevel educationLevel,
            String schoolName,
            String majorName,
            String additionalMajorType,
            String additionalMajorName,
            String thesisTitle,
            LocalDate admissionDate,
            LocalDate graduationDate,
            GraduationStatus graduationStatus,
            DayNightType dayNightType,
            CampusType campusType,
            Boolean transfer,
            String countryCode,
            Long schoolId,
            BigDecimal overallGradePoint,
            BigDecimal overallMaxGradePoint,
            BigDecimal overallMajorGradePoint,
            BigDecimal overallMajorMaxGradePoint,
            Integer sortOrder
    ) {
        return new ApplicationEducation(
                jobApplication,
                educationLevel,
                schoolName,
                majorName,
                additionalMajorType,
                additionalMajorName,
                thesisTitle,
                admissionDate,
                graduationDate,
                graduationStatus,
                dayNightType,
                campusType,
                transfer,
                countryCode,
                schoolId,
                overallGradePoint,
                overallMaxGradePoint,
                overallMajorGradePoint,
                overallMajorMaxGradePoint,
                sortOrder
        );
    }
```

- [ ] **Step 5: 컴파일 확인**

Run:
```powershell
cd C:\Users\roehf\Desktop\recruit\recruit_back\recruit_backend
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat compileJava --no-daemon
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
cd /c/Users/roehf/Desktop/recruit/recruit_back/recruit_backend
git add src/main/java/com/shinyoung/recruit/domain/entity/ApplicationEducation.java
git commit -m "feat(education): ApplicationEducation에 전체 평점 4필드 + create 오버로드 추가"
```

---

## Task 3: 요청 DTO에 4필드 + 호환 생성자 추가

**Files:**
- Modify: `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/dto/request/EducationRequest.java`

canonical record에 4필드를 추가하면 컴포넌트가 20개가 된다. 기존 호출부(15-param, 16-param)를 보존하기 위해 호환 생성자 2개를 둔다.

- [ ] **Step 1: import 추가**

상단 import에 다음을 추가한다:
```java
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
```

- [ ] **Step 2: record 컴포넌트 4개 추가**

canonical record 헤더에서 `Long schoolId`(line 54) 다음에 4필드를 추가한다. 변경 후 record 선언부는 다음과 같다(앞부분 동일, 끝부분만 표시):

```java
        List<@Valid SemesterGradeRequest> semesterGrades,

        /** 선택적 School master 참조(Phase 08c). 자동완성 선택 시에만 값, 직접입력이면 null. */
        Long schoolId,

        @DecimalMin(value = "0.0", message = "Overall grade point must be greater than or equal to 0.")
        BigDecimal overallGradePoint,

        @DecimalMin(value = "0.0", inclusive = false, message = "Overall max grade point must be greater than 0.")
        BigDecimal overallMaxGradePoint,

        @DecimalMin(value = "0.0", message = "Overall major grade point must be greater than or equal to 0.")
        BigDecimal overallMajorGradePoint,

        @DecimalMin(value = "0.0", inclusive = false, message = "Overall major max grade point must be greater than 0.")
        BigDecimal overallMajorMaxGradePoint
) {
```

- [ ] **Step 3: 호환 생성자 2개로 교체**

기존 호환 생성자(line 56-78, "schoolId 없이 호출하던 기존 코드 호환용")를 아래 2개로 교체한다. 첫 번째는 15-param(schoolId·overall 없음), 두 번째는 16-param(schoolId 있음, overall 없음). 둘 다 overall 4개를 null로 위임한다.

```java
    /** schoolId·overall 없이 호출하던 기존 코드 호환용(Phase 08c 이전). */
    public EducationRequest(
            EducationLevel educationLevel,
            String schoolName,
            String majorName,
            String additionalMajorType,
            String additionalMajorName,
            String thesisTitle,
            LocalDate admissionDate,
            LocalDate graduationDate,
            GraduationStatus graduationStatus,
            DayNightType dayNightType,
            CampusType campusType,
            Boolean transfer,
            String countryCode,
            Integer sortOrder,
            List<SemesterGradeRequest> semesterGrades
    ) {
        this(educationLevel, schoolName, majorName, additionalMajorType, additionalMajorName, thesisTitle,
                admissionDate, graduationDate, graduationStatus, dayNightType, campusType, transfer, countryCode,
                sortOrder, semesterGrades, null);
    }

    /** schoolId 포함, overall 없이 호출하던 기존 코드 호환용. */
    public EducationRequest(
            EducationLevel educationLevel,
            String schoolName,
            String majorName,
            String additionalMajorType,
            String additionalMajorName,
            String thesisTitle,
            LocalDate admissionDate,
            LocalDate graduationDate,
            GraduationStatus graduationStatus,
            DayNightType dayNightType,
            CampusType campusType,
            Boolean transfer,
            String countryCode,
            Integer sortOrder,
            List<SemesterGradeRequest> semesterGrades,
            Long schoolId
    ) {
        this(educationLevel, schoolName, majorName, additionalMajorType, additionalMajorName, thesisTitle,
                admissionDate, graduationDate, graduationStatus, dayNightType, campusType, transfer, countryCode,
                sortOrder, semesterGrades, schoolId, null, null, null, null);
    }
```

> 주의: canonical 생성자의 파라미터 순서는 record 컴포넌트 순서(… semesterGrades, schoolId, overallGradePoint, overallMaxGradePoint, overallMajorGradePoint, overallMajorMaxGradePoint)와 동일하다. 위 위임 호출의 인자 순서를 정확히 지킬 것.

- [ ] **Step 4: 컴파일 확인**

Run:
```powershell
cd C:\Users\roehf\Desktop\recruit\recruit_back\recruit_backend
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat compileJava --no-daemon
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
cd /c/Users/roehf/Desktop/recruit/recruit_back/recruit_backend
git add src/main/java/com/shinyoung/recruit/dto/request/EducationRequest.java
git commit -m "feat(education): EducationRequest에 전체 평점 4필드 + 호환 생성자 추가"
```

---

## Task 4: 응답 DTO 2개에 4필드 + 매핑 추가

**Files:**
- Modify: `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/dto/response/EducationResponse.java`
- Modify: `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/dto/response/AdminEducationResponse.java`

- [ ] **Step 1: `EducationResponse`에 4필드 + 매핑 추가**

상단 import에 `import java.math.BigDecimal;` 추가. record 컴포넌트에서 `Integer sortOrder,`와 `List<SemesterGradeResponse> semesterGrades` 사이에 4필드를 추가하고, `from()`의 생성자 인자에도 같은 위치에 매핑을 추가한다. 변경 후 파일은 다음과 같다:

```java
public record EducationResponse(
        Long educationId,
        EducationLevel educationLevel,
        String schoolName,
        String majorName,
        String additionalMajorType,
        String additionalMajorName,
        String thesisTitle,
        LocalDate admissionDate,
        LocalDate graduationDate,
        GraduationStatus graduationStatus,
        DayNightType dayNightType,
        CampusType campusType,
        Boolean transfer,
        String countryCode,
        Long schoolId,
        Integer sortOrder,
        BigDecimal overallGradePoint,
        BigDecimal overallMaxGradePoint,
        BigDecimal overallMajorGradePoint,
        BigDecimal overallMajorMaxGradePoint,
        List<SemesterGradeResponse> semesterGrades
) {

    public static EducationResponse from(
            ApplicationEducation education,
            List<ApplicationEducationSemesterGrade> semesterGrades
    ) {
        return new EducationResponse(
                education.getId(),
                education.getEducationLevel(),
                education.getSchoolName(),
                education.getMajorName(),
                education.getAdditionalMajorType(),
                education.getAdditionalMajorName(),
                education.getThesisTitle(),
                education.getAdmissionDate(),
                education.getGraduationDate(),
                education.getGraduationStatus(),
                education.getDayNightType(),
                education.getCampusType(),
                education.getTransfer(),
                education.getCountryCode(),
                education.getSchoolId(),
                education.getSortOrder(),
                education.getOverallGradePoint(),
                education.getOverallMaxGradePoint(),
                education.getOverallMajorGradePoint(),
                education.getOverallMajorMaxGradePoint(),
                semesterGrades.stream()
                        .map(SemesterGradeResponse::from)
                        .toList()
        );
    }
}
```

- [ ] **Step 2: `AdminEducationResponse`에 동일하게 4필드 + 매핑 추가**

상단 import에 `import java.math.BigDecimal;` 추가. 동일 위치에 4필드와 매핑을 추가한다. 변경 후 record/from은 다음과 같다:

```java
public record AdminEducationResponse(
        Long educationId,
        EducationLevel educationLevel,
        String schoolName,
        String majorName,
        String additionalMajorType,
        String additionalMajorName,
        String thesisTitle,
        LocalDate admissionDate,
        LocalDate graduationDate,
        GraduationStatus graduationStatus,
        DayNightType dayNightType,
        CampusType campusType,
        Boolean transfer,
        String countryCode,
        Long schoolId,
        Integer sortOrder,
        BigDecimal overallGradePoint,
        BigDecimal overallMaxGradePoint,
        BigDecimal overallMajorGradePoint,
        BigDecimal overallMajorMaxGradePoint,
        List<AdminSemesterGradeResponse> semesterGrades
) {

    public static AdminEducationResponse from(
            ApplicationEducation education,
            List<ApplicationEducationSemesterGrade> semesterGrades
    ) {
        return new AdminEducationResponse(
                education.getId(),
                education.getEducationLevel(),
                education.getSchoolName(),
                education.getMajorName(),
                education.getAdditionalMajorType(),
                education.getAdditionalMajorName(),
                education.getThesisTitle(),
                education.getAdmissionDate(),
                education.getGraduationDate(),
                education.getGraduationStatus(),
                education.getDayNightType(),
                education.getCampusType(),
                education.getTransfer(),
                education.getCountryCode(),
                education.getSchoolId(),
                education.getSortOrder(),
                education.getOverallGradePoint(),
                education.getOverallMaxGradePoint(),
                education.getOverallMajorGradePoint(),
                education.getOverallMajorMaxGradePoint(),
                semesterGrades.stream()
                        .map(AdminSemesterGradeResponse::from)
                        .toList()
        );
    }
}
```

- [ ] **Step 3: 컴파일 확인**

Run:
```powershell
cd C:\Users\roehf\Desktop\recruit\recruit_back\recruit_backend
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat compileJava --no-daemon
```
Expected: `BUILD SUCCESSFUL`

> 참고: 이 시점까지는 검증이 없어 기존 테스트가 모두 통과한다(overall은 null 허용, 매핑만 추가됨). `ApplicationPdfService`/`AdminApplicationSectionService`는 `AdminEducationResponse.from(...)`만 호출하므로 영향 없음(필드 추가는 from 내부에서 채움).

- [ ] **Step 4: Commit**

```bash
cd /c/Users/roehf/Desktop/recruit/recruit_back/recruit_backend
git add src/main/java/com/shinyoung/recruit/dto/response/EducationResponse.java src/main/java/com/shinyoung/recruit/dto/response/AdminEducationResponse.java
git commit -m "feat(education): Education 응답 DTO에 전체 평점 4필드 + 매핑 추가"
```

---

## Task 5: 서비스 검증·매핑 추가 (TDD)

**Files:**
- Modify: `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/service/ApplicationEducationService.java`
- Test: `recruit_back/recruit_backend/src/test/java/com/shinyoung/recruit/service/ApplicationEducationServiceTest.java`

이 태스크에서 (a) 실패하는 신규 테스트를 먼저 작성하고, (b) 검증·매핑을 구현하고, (c) overall 누락으로 깨질 기존 성공 테스트의 대학 학력 데이터를 보강한다.

- [ ] **Step 1: 기존 성공 테스트의 대학 데이터에 overall 값 보강 (먼저 적용해야 이후 신규 테스트와 함께 통과)**

`ApplicationEducationServiceTest.java`에서 다음 3곳을 수정한다.

(1) `universityEducation(Integer sortOrder)` 헬퍼(line 522-540)를 20-param canonical 생성자로 교체하여 overall 값을 포함시킨다:

```java
    private EducationRequest universityEducation(Integer sortOrder) {
        return new EducationRequest(
                EducationLevel.UNIVERSITY,
                "Shinyoung University",
                "Computer Science",
                "DOUBLE_MAJOR",
                "경영학",
                "딥러닝 추천시스템",
                LocalDate.of(2021, 3, 1),
                LocalDate.of(2025, 2, 28),
                GraduationStatus.GRADUATED,
                DayNightType.DAY,
                CampusType.MAIN,
                false,
                "KR",
                sortOrder,
                List.of(grade(2, 1), grade(1, 1)),
                null,
                new BigDecimal("3.8"),
                new BigDecimal("4.5"),
                new BigDecimal("3.7"),
                new BigDecimal("4.5")
        );
    }
```

(2) `semester_grades_are_sorted_by_school_year_semester_and_id`의 인라인 UNIVERSITY `EducationRequest`(line 145-165)는 15-param 생성자를 쓰므로 overall이 null이 되어 검증 실패한다. 해당 `new EducationRequest(...)` 호출을 20-param canonical로 교체한다(`List.of(grade(2,1), grade(1,2), grade(1,1))` 다음에 `null`(schoolId), overall 4개를 추가):

```java
                new EducationReplaceRequest(List.of(new EducationRequest(
                        EducationLevel.UNIVERSITY,
                        "Sort University",
                        "Computer Science",
                        null,
                        null,
                        null,
                        LocalDate.of(2022, 3, 1),
                        null,
                        GraduationStatus.ENROLLED,
                        DayNightType.DAY,
                        CampusType.MAIN,
                        false,
                        "KR",
                        0,
                        List.of(
                                grade(2, 1),
                                grade(1, 2),
                                grade(1, 1)
                        ),
                        null,
                        new BigDecimal("3.5"),
                        new BigDecimal("4.5"),
                        null,
                        null
                )))
```

(3) `education_persists_optional_school_id_when_selected_and_null_when_freetext`의 `linkedToMaster`(line 118-121, UNIVERSITY, 16-param, 성공 기대)에 overall을 추가해 20-param canonical로 교체한다. `freeText`(HIGH_SCHOOL)는 그대로 둔다:

```java
        EducationRequest linkedToMaster = new EducationRequest(
                EducationLevel.UNIVERSITY, "Linked University", null, null, null, null,
                LocalDate.of(2020, 3, 1), LocalDate.of(2024, 2, 1), GraduationStatus.GRADUATED,
                DayNightType.DAY, CampusType.MAIN, false, "KR", 1, List.of(), 777L,
                new BigDecimal("3.9"), new BigDecimal("4.5"), null, null);
```

- [ ] **Step 2: 신규 실패 테스트 4개 추가**

`ApplicationEducationServiceTest.java`의 `save_education_with_additional_major_and_thesis_roundtrip` 테스트(line 442-457) 뒤에 아래 4개 테스트를 추가한다.

```java
    @Test
    void overall_grades_persist_and_round_trip() {
        Applicant applicant = createApplicant("education-overall", "Education Overall");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));

        applicationEducationService.replaceEducations(
                applicant.getId(), applicationId,
                new EducationReplaceRequest(List.of(universityEducation(0))));
        List<EducationResponse> responses =
                applicationEducationService.getEducations(applicant.getId(), applicationId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).overallGradePoint()).isEqualByComparingTo(new BigDecimal("3.8"));
        assertThat(responses.get(0).overallMaxGradePoint()).isEqualByComparingTo(new BigDecimal("4.5"));
        assertThat(responses.get(0).overallMajorGradePoint()).isEqualByComparingTo(new BigDecimal("3.7"));
        assertThat(responses.get(0).overallMajorMaxGradePoint()).isEqualByComparingTo(new BigDecimal("4.5"));
    }

    @Test
    void high_school_allows_null_overall_grades() {
        Applicant applicant = createApplicant("education-hs-overall", "Education HS Overall");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));

        applicationEducationService.replaceEducations(
                applicant.getId(), applicationId,
                new EducationReplaceRequest(List.of(highSchoolEducation(0))));
        List<EducationResponse> responses =
                applicationEducationService.getEducations(applicant.getId(), applicationId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).overallGradePoint()).isNull();
        assertThat(responses.get(0).overallMaxGradePoint()).isNull();
    }

    @Test
    void replace_fails_when_non_high_school_missing_overall_grades() {
        Applicant applicant = createApplicant("education-overall-missing", "Education Overall Missing");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));

        assertThatThrownBy(() -> applicationEducationService.replaceEducations(
                applicant.getId(),
                applicationId,
                new EducationReplaceRequest(List.of(new EducationRequest(
                        EducationLevel.UNIVERSITY, "University", null, null, null, null,
                        null, null, GraduationStatus.GRADUATED, null, null, false, "KR",
                        0, List.of(), null, null, null, null, null)))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void replace_fails_when_overall_grade_exceeds_max_or_major_missing_max() {
        Applicant applicant = createApplicant("education-overall-invalid", "Education Overall Invalid");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));

        // 전체 평점 > 만점
        assertThatThrownBy(() -> applicationEducationService.replaceEducations(
                applicant.getId(),
                applicationId,
                new EducationReplaceRequest(List.of(new EducationRequest(
                        EducationLevel.UNIVERSITY, "University", null, null, null, null,
                        null, null, GraduationStatus.GRADUATED, null, null, false, "KR",
                        0, List.of(), null,
                        new BigDecimal("4.6"), new BigDecimal("4.5"), null, null)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        // 전공 전체 평점만 있고 만점 없음
        assertThatThrownBy(() -> applicationEducationService.replaceEducations(
                applicant.getId(),
                applicationId,
                new EducationReplaceRequest(List.of(new EducationRequest(
                        EducationLevel.UNIVERSITY, "University", null, null, null, null,
                        null, null, GraduationStatus.GRADUATED, null, null, false, "KR",
                        0, List.of(), null,
                        new BigDecimal("3.8"), new BigDecimal("4.5"),
                        new BigDecimal("3.7"), null)))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }
```

`EducationResponse` 접근자(`overallGradePoint()` 등)와 `BigDecimal` import는 이미 테스트 파일에 존재한다(line 22, line 40).

- [ ] **Step 3: 테스트 실행해 실패 확인**

Run:
```powershell
cd C:\Users\roehf\Desktop\recruit\recruit_back\recruit_backend
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicationEducationServiceTest" --no-daemon
```
Expected: FAIL — `overall_grades_persist_and_round_trip`는 매핑은 되지만 검증 미구현이라 통과할 수 있고, `replace_fails_when_non_high_school_missing_overall_grades`/`replace_fails_when_overall_grade_exceeds_max_or_major_missing_max`는 검증이 없어 예외가 안 던져져 FAIL 한다.

- [ ] **Step 4: 서비스에 매핑(`toEducation`) 전달 추가**

`ApplicationEducationService.java`의 `toEducation()`(line 152-171)에서 `ApplicationEducation.create(...)` 호출을 새 20-param 오버로드로 바꾼다(`request.schoolId()` 다음에 overall 4개, 마지막 `request.sortOrder()`):

```java
    private ApplicationEducation toEducation(JobApplication application, EducationRequest request) {
        return ApplicationEducation.create(
                application,
                request.educationLevel(),
                request.schoolName(),
                request.majorName(),
                request.additionalMajorType(),
                request.additionalMajorName(),
                request.thesisTitle(),
                request.admissionDate(),
                request.graduationDate(),
                request.graduationStatus(),
                request.dayNightType(),
                request.campusType(),
                request.transfer(),
                request.countryCode(),
                request.schoolId(),
                request.overallGradePoint(),
                request.overallMaxGradePoint(),
                request.overallMajorGradePoint(),
                request.overallMajorMaxGradePoint(),
                request.sortOrder()
        );
    }
```

- [ ] **Step 5: 서비스에 검증 메서드 추가 + 루프에 연결**

`validateRequest()`의 for 루프(line 73-83)에서 `validateEducationRequiredFields(education);` 다음 줄에 `validateOverallGrades(education);` 호출을 추가한다. 변경 후 루프:

```java
        Set<Integer> sortOrders = new HashSet<>();
        for (EducationRequest education : request.educations()) {
            validateEducationRequiredFields(education);
            validateOverallGrades(education);
            if (!sortOrders.add(education.sortOrder())) {
                throw new InvalidJobApplicationException("Education sort order must be unique.");
            }
            List<SemesterGradeRequest> semesterGrades = semesterGradeRequests(education);
            if (education.educationLevel() == EducationLevel.HIGH_SCHOOL && !semesterGrades.isEmpty()) {
                throw new InvalidJobApplicationException("High school education cannot include semester grades.");
            }
            semesterGrades.forEach(this::validateSemesterGrade);
        }
```

그리고 `validateSemesterGrade(...)` 메서드(line 109-142) 바로 뒤에 새 검증 메서드를 추가한다(기존 `isNegative`/`isZeroOrNegative` 헬퍼 재사용):

```java
    private void validateOverallGrades(EducationRequest education) {
        BigDecimal overall = education.overallGradePoint();
        BigDecimal overallMax = education.overallMaxGradePoint();
        boolean highSchool = education.educationLevel() == EducationLevel.HIGH_SCHOOL;

        if (!highSchool && (overall == null || overallMax == null)) {
            throw new InvalidJobApplicationException("Overall grade point and max grade point are required.");
        }
        if ((overall == null) != (overallMax == null)) {
            throw new InvalidJobApplicationException("Overall grade point and max grade point must be provided together.");
        }
        if (overall != null) {
            if (isNegative(overall)) {
                throw new InvalidJobApplicationException("Overall grade point must be greater than or equal to 0.");
            }
            if (isZeroOrNegative(overallMax)) {
                throw new InvalidJobApplicationException("Overall max grade point must be greater than 0.");
            }
            if (overall.compareTo(overallMax) > 0) {
                throw new InvalidJobApplicationException("Overall grade point cannot exceed overall max grade point.");
            }
        }

        BigDecimal major = education.overallMajorGradePoint();
        BigDecimal majorMax = education.overallMajorMaxGradePoint();
        if (major != null && majorMax == null) {
            throw new InvalidJobApplicationException("Overall major max grade point is required when overall major grade point is provided.");
        }
        if (major != null && isNegative(major)) {
            throw new InvalidJobApplicationException("Overall major grade point must be greater than or equal to 0.");
        }
        if (majorMax != null && isZeroOrNegative(majorMax)) {
            throw new InvalidJobApplicationException("Overall major max grade point must be greater than 0.");
        }
        if (major != null && majorMax != null && major.compareTo(majorMax) > 0) {
            throw new InvalidJobApplicationException("Overall major grade point cannot exceed overall major max grade point.");
        }
    }
```

- [ ] **Step 6: 테스트 실행해 통과 확인**

Run:
```powershell
cd C:\Users\roehf\Desktop\recruit\recruit_back\recruit_backend
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicationEducationServiceTest" --no-daemon
```
Expected: `BUILD SUCCESSFUL` — 신규 4개 포함 전부 통과.

- [ ] **Step 7: Commit**

```bash
cd /c/Users/roehf/Desktop/recruit/recruit_back/recruit_backend
git add src/main/java/com/shinyoung/recruit/service/ApplicationEducationService.java src/test/java/com/shinyoung/recruit/service/ApplicationEducationServiceTest.java
git commit -m "feat(education): 전체 평점 매핑·레벨별 검증 추가 + 서비스 테스트"
```

---

## Task 6: 컨트롤러 테스트 — JSON에 overall 추가 + 왕복 검증

**Files:**
- Test: `recruit_back/recruit_backend/src/test/java/com/shinyoung/recruit/controller/ApplicationEducationControllerTest.java`

`validEducationJson()`은 UNIVERSITY 성공 케이스인데 overall이 없어 검증 실패하게 된다. JSON에 overall 4필드를 추가하고, 응답 왕복 검증을 한다.

- [ ] **Step 1: `validEducationJson()`에 overall 4필드 추가**

`validEducationJson()`(line 285-319)의 `"sortOrder": 0,` 다음 줄에 overall 4필드를 추가한다(JSON `educations[0]` 객체 안, `semesterGrades` 앞):

```java
                      "sortOrder": 0,
                      "overallGradePoint": 3.8,
                      "overallMaxGradePoint": 4.5,
                      "overallMajorGradePoint": 3.7,
                      "overallMajorMaxGradePoint": 4.5,
                      "semesterGrades": [
```

- [ ] **Step 2: `replace_educations_returns_api_response`에 overall 응답 검증 추가**

해당 테스트(line 111-125)의 마지막 `.andExpect(...)` 체인에 overall 검증 2줄을 덧붙인다:

```java
                .andExpect(jsonPath("$.data[0].educationLevel").value("UNIVERSITY"))
                .andExpect(jsonPath("$.data[0].overallGradePoint").value(3.8))
                .andExpect(jsonPath("$.data[0].overallMajorGradePoint").value(3.7))
                .andExpect(jsonPath("$.data[0].semesterGrades[0].schoolYear").value(1));
```

- [ ] **Step 3: 컨트롤러 테스트 실행**

Run:
```powershell
cd C:\Users\roehf\Desktop\recruit\recruit_back\recruit_backend
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.ApplicationEducationControllerTest" --no-daemon
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
cd /c/Users/roehf/Desktop/recruit/recruit_back/recruit_backend
git add src/test/java/com/shinyoung/recruit/controller/ApplicationEducationControllerTest.java
git commit -m "test(education): 컨트롤러 테스트 전체 평점 JSON·응답 검증 반영"
```

---

## Task 7: 영향받는 인접 테스트 회귀 확인

**Files:** (수정 없음 — 검증만; 실패 시 해당 파일에서 overall 보강)

`ApplicationEducation.create()` 직접 seed 테스트와 admin 읽기/통계/파기 경로가 시그니처 보존으로 깨지지 않는지 확인한다.

- [ ] **Step 1: 인접 테스트 실행**

Run:
```powershell
cd C:\Users\roehf\Desktop\recruit\recruit_back\recruit_backend
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.AdminApplicationSectionServiceTest" --tests "com.shinyoung.recruit.controller.AdminApplicationSectionControllerTest" --tests "com.shinyoung.recruit.controller.AdminStatisticsControllerTest" --tests "com.shinyoung.recruit.service.ApplicationPiiPurgeServiceTest" --tests "com.shinyoung.recruit.service.ApplicationPdfService*" --no-daemon
```
Expected: `BUILD SUCCESSFUL`. 이들은 `replaceEducations`를 거치지 않거나 HIGH_SCHOOL/직접 seed라 검증 영향이 없어야 한다.

- [ ] **Step 2: 실패가 나면 (조건부)**

만약 어떤 테스트가 `replaceEducations(...)`로 비-HIGH_SCHOOL 학력을 성공 기대로 보내고 있었다면, 해당 `EducationRequest`/`ApplicationEducation.create` 호출에 overall 값(`new BigDecimal("3.5")`, `new BigDecimal("4.5")`)을 추가한다. seed가 `ApplicationEducation.create(...)`(repository 직접 저장)면 검증을 안 거치므로 보강 불필요 — 단지 컴파일만 통과하면 된다. 수정 후 Step 1 재실행.

- [ ] **Step 3: (실패 보강이 있었던 경우만) Commit**

```bash
cd /c/Users/roehf/Desktop/recruit/recruit_back/recruit_backend
git add -A
git commit -m "test(education): 인접 테스트 전체 평점 검증 호환 보강"
```

---

## Task 8: 프론트 타입 + 계약 🟢 확정

**Files:**
- Modify: `recruit_front/src/types/application/sections/education.ts`
- Modify: `recruit/api-contract.md`

프론트는 입력 화면이 아직 없으므로 타입 정의에만 4필드를 추가한다(기존 stale 필드 정리는 범위 외).

- [ ] **Step 1: `education.ts`의 두 인터페이스에 4필드 추가**

`EducationRequest` 인터페이스(line 38-53)의 `schoolId: number` 다음에, `EducationResponse` 인터페이스(line 55-70)의 `semesterGrades` 앞에 각각 4필드를 추가한다.

`EducationRequest`에 추가:
```typescript
  schoolId: number
  overallGradePoint?: number
  overallMaxGradePoint?: number
  overallMajorGradePoint?: number
  overallMajorMaxGradePoint?: number
```

`EducationResponse`에 추가(`sortOrder: number` 다음, `semesterGrades` 앞):
```typescript
  sortOrder: number
  overallGradePoint?: number
  overallMaxGradePoint?: number
  overallMajorGradePoint?: number
  overallMajorMaxGradePoint?: number
  semesterGrades: semesterGradeItem[]
```

- [ ] **Step 2: 프론트 타입체크**

Run:
```bash
cd /c/Users/roehf/Desktop/recruit/recruit_front
npm run type-check
```
Expected: 에러 없음(통과).

- [ ] **Step 3: 계약 🟡 → 🟢 확정**

`recruit/api-contract.md` Task 1에서 추가한 변경 메모의 `(2026-06-30, 🟡 구현 중)`를 `(2026-06-30, 🟢 확정)`로 바꾼다. 구현과 필드명이 일치함을 확인한다.

- [ ] **Step 4: Commit (프론트 + 계약 각 저장소)**

```bash
cd /c/Users/roehf/Desktop/recruit/recruit_front
git add src/types/application/sections/education.ts
git commit -m "feat(education): 전체 평점 4필드 타입 정의 추가"
cd /c/Users/roehf/Desktop/recruit
git add api-contract.md
git commit -m "docs(contract): 학력 전체 평점 4필드 계약 🟢 확정"
```

---

## Task 9: 백엔드 구현 문서 (Markdown + HTML + 이력)

**Files:**
- Create: `recruit_back/recruit_backend/docs/codex/implementation/education-overall-gpa.md`
- Create: `recruit_back/recruit_backend/docs/codex/reports/education-overall-gpa.html`
- Modify: `recruit_back/recruit_backend/docs/codex/07-implementation-history.md`

백엔드 CLAUDE.md의 "Implementation Documentation Rules" / "Documentation Output Rule"에 따라 Markdown + HTML을 함께 작성한다. HTML 템플릿은 `docs/codex/templates/human-report-template.md`를 따른다.

- [ ] **Step 1: 구현 Markdown 작성**

`education-overall-gpa.md`를 생성하고 아래 항목을 채운다(실제 구현 코드 기준, 모호한 표현 금지):
1. Phase summary — 학력 단위 전체(평균) 평점 4필드 추가
2. Implemented scope — 엔티티 4필드, 요청/응답 DTO, 레벨별 검증, 계약/프론트 타입
3. Changed files — Task 2~8에서 변경한 파일 목록
4. New/Modified classes — `ApplicationEducation`, `EducationRequest`, `EducationResponse`, `AdminEducationResponse`, `ApplicationEducationService`
5. Class-by-class explanation (package/type/responsibility/key fields/related/notes)
6. API list — `GET·POST /api/applications/{id}/educations`, `GET /api/admin/applications/{id}/educations`
7. Entity relationship summary — `ApplicationEducation` 1—N `ApplicationEducationSemesterGrade`(변경 없음)
8. Business rules — 전체 쌍 HIGH_SCHOOL 외 필수, 전공 쌍 선택, 쌍 동시 입력, 평점≤만점, 만점>0
9. Test coverage — `ApplicationEducationServiceTest`(신규 4 케이스), `ApplicationEducationControllerTest`
10. Known limitations — 자동 평균계산 없음, 프론트 입력 화면 미구현(후속)
11. Next phase considerations — 학력 입력 UI 슬라이스

- [ ] **Step 2: HTML 리포트 생성**

`education-overall-gpa.html`을 자체 포함(inline CSS, 외부 CDN/JS 없음)으로 생성한다. Markdown 내용에서 변환하며, 완료 스코프/검증 규칙/API/변경 파일/테스트 결과/남은 이슈/다음 단계 섹션과 상태 배지를 포함한다. 민감정보(키/접속정보/내부 경로)는 노출하지 않는다.

- [ ] **Step 3: 이력 갱신**

`docs/codex/07-implementation-history.md`에 한 줄(또는 기존 형식에 맞춘 항목)을 추가한다: 날짜 2026-06-30, "학력 전체(평균) 평점 4필드 추가(education-overall-gpa)", 변경 클래스·API 요약.

- [ ] **Step 4: 변경 범위 전체 테스트 재실행(문서-구현 일치 확인)**

Run:
```powershell
cd C:\Users\roehf\Desktop\recruit\recruit_back\recruit_backend
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicationEducationServiceTest" --tests "com.shinyoung.recruit.controller.ApplicationEducationControllerTest" --no-daemon
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
cd /c/Users/roehf/Desktop/recruit/recruit_back/recruit_backend
git add docs/codex/implementation/education-overall-gpa.md docs/codex/reports/education-overall-gpa.html docs/codex/07-implementation-history.md
git commit -m "docs(education): 전체 평점 구현 문서 + HTML 리포트 + 이력"
```

---

## 최종 검증 체크리스트

- [ ] 백엔드 변경 범위 테스트 통과: `ApplicationEducationServiceTest`, `ApplicationEducationControllerTest`
- [ ] 인접 테스트 통과: Admin Section/Statistics, PiiPurge, PDF
- [ ] 프론트 `npm run type-check` 통과
- [ ] `api-contract.md` ApplicationEducation 섹션 🟢, 코드 필드명과 일치
- [ ] 백엔드 Markdown + HTML 문서 + 이력 갱신 완료(둘 중 하나만 갱신 시 미완료)
- [ ] 실제 운영 키/접속정보 미사용(예시 키만)

## 보고 형식 (완료 시, 백엔드 CLAUDE.md §12)

```text
변경 요약 / 변경 파일 / 테스트 결과(실행 명령·성공여부) / 문서 갱신(MD·HTML·이력) / 계약 변경분 / 남은 이슈
```
