# 경력·학력 입력 필드 변경 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 경력에서 `responsibilities` 제거·`currentSalary`(Integer 만원) 추가, 학력에서 `degreeName` 제거·`additionalMajorType`/`additionalMajorName`/`thesisTitle` 추가. PII purge·PDF·계약·인벤토리까지 동기화.

**Architecture:** 각 도메인(경력/학력)은 엔티티 필드 제거가 모듈 전체 컴파일을 깨므로 **하나의 atomic vertical slice**로 처리한다(엔티티→DTO→서비스→PDF→purge→테스트를 함께 바꾼 뒤 컴파일·테스트). `additionalMajorType`은 기존 `veteranType` 선례대로 **String 코드값, FK·CommonCode 검증 없음**.

**Tech Stack:** Java 17, Spring Boot 4.x, JPA(H2 dev), JUnit5 + AssertJ + MockMvc, Gradle Wrapper.

> **커밋 주의:** 본 레포(recruit_back)는 자체 git이며 CLAUDE.md상 **명시 요청 없이는 commit 금지**다. 각 Task의 커밋 스텝은 *사용자가 커밋을 승인한 경우에만* 실행한다. 미승인이면 변경만 남기고 다음 Task로 진행한다.

> **검증 키:** 아래 `<AES_KEY>`는 백엔드 CLAUDE.md의 로컬 예시 키 `22791194512954214612461221261067`를 사용한다(운영 키 금지).

---

## File Structure

**Production (main):**
- `domain/entity/ApplicationCareer.java` — `responsibilities` → `currentSalary`
- `dto/request/CareerRequest.java` — 동일
- `dto/response/CareerItemResponse.java` / `dto/response/AdminCareerItemResponse.java` — 동일
- `service/ApplicationCareerService.java` — 매핑·검증
- `domain/entity/ApplicationEducation.java` — `degreeName` → 3필드
- `dto/request/EducationRequest.java` (하위호환 생성자 포함) — 동일
- `dto/response/EducationResponse.java` / `dto/response/AdminEducationResponse.java` — 동일
- `service/ApplicationEducationService.java` — 매핑
- `service/ApplicationPdfService.java` — 경력/학력 섹션(공유 파일, 메서드별 분리 수정)
- `domain/repository/ApplicationPiiPurgeRepository.java` — `purgeCareers`/`purgeEducations`(공유 파일)

**Tests:**
- 경력: `service/ApplicationCareerServiceTest`, `controller/ApplicationCareerControllerTest`
- 학력: `service/ApplicationEducationServiceTest`, `controller/ApplicationEducationControllerTest`
- 공유(두 도메인 모두 생성): `service/ApplicationPiiPurgeServiceTest`, `service/AdminApplicationSectionServiceTest`, `controller/AdminApplicationSectionControllerTest`
- 학력만 생성: `controller/AdminStatisticsControllerTest`

**Docs:**
- `recruit/api-contract.md`
- `recruit_back/recruit_backend/docs/codex/implementation/phase-09-pii-field-inventory.md`

경로 접두사(생략형): `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/` (main), `recruit_back/recruit_backend/src/test/java/com/shinyoung/recruit/` (test).

---

## Task 1: 경력 — responsibilities 제거 / currentSalary 추가

**Files:**
- Modify: `domain/entity/ApplicationCareer.java`
- Modify: `dto/request/CareerRequest.java`
- Modify: `dto/response/CareerItemResponse.java`, `dto/response/AdminCareerItemResponse.java`
- Modify: `service/ApplicationCareerService.java`
- Modify: `service/ApplicationPdfService.java` (careerSection만)
- Modify: `domain/repository/ApplicationPiiPurgeRepository.java` (purgeCareers만)
- Test: `service/ApplicationCareerServiceTest.java`, `controller/ApplicationCareerControllerTest.java`, `service/ApplicationPiiPurgeServiceTest.java`(경력 부분), `service/AdminApplicationSectionServiceTest.java`(career helper), `controller/AdminApplicationSectionControllerTest.java`(career 생성)

- [ ] **Step 1: 엔티티 필드 교체 — `ApplicationCareer.java`**

`responsibilities` 필드 선언을 교체:

```java
    @Column(nullable = false)
    private Boolean currentlyEmployed;

    // 현재연봉(만원 단위). 선택 입력이며 파기(NULLIFY) 대상이다(PII 인벤토리 §Career).
    private Integer currentSalary;

    @Column(length = 2000)
    private String resignationReason;
```

private 생성자와 정적 팩토리 `create(...)`의 파라미터 `String responsibilities`(currentlyEmployed 다음 위치)를 `Integer currentSalary`로 바꾸고, 본문 대입 `this.responsibilities = responsibilities;` → `this.currentSalary = currentSalary;`, 팩토리 인자 전달도 `currentSalary`로 변경. (파라미터 순서는 그대로 currentlyEmployed → currentSalary → resignationReason 유지.)

- [ ] **Step 2: 요청 DTO — `CareerRequest.java`**

`responsibilities` 컴포넌트를 교체하고 import 추가:

```java
import jakarta.validation.constraints.PositiveOrZero;
```

```java
        @NotNull(message = "Currently employed flag is required.")
        Boolean currentlyEmployed,

        @PositiveOrZero(message = "Current salary must be greater than or equal to 0.")
        Integer currentSalary,

        @Size(max = 2000, message = "Resignation reason must be 2000 characters or less.")
        String resignationReason,
```

(`@Size`는 resignationReason에서 계속 쓰이므로 import 유지.)

- [ ] **Step 3: 응답 DTO 2개 — `CareerItemResponse.java` / `AdminCareerItemResponse.java`**

두 파일 모두 동일하게: 레코드 컴포넌트 `String responsibilities` → `Integer currentSalary` (위치: currentlyEmployed 다음), `from()`의 `career.getResponsibilities()` → `career.getCurrentSalary()`.

`CareerItemResponse.java`:

```java
public record CareerItemResponse(
        Long careerId,
        String companyName,
        String departmentName,
        String positionTitle,
        EmploymentType employmentType,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate promotionDate,
        Boolean currentlyEmployed,
        Integer currentSalary,
        String resignationReason,
        Integer sortOrder
) {

    public static CareerItemResponse from(ApplicationCareer career) {
        return new CareerItemResponse(
                career.getId(),
                career.getCompanyName(),
                career.getDepartmentName(),
                career.getPositionTitle(),
                career.getEmploymentType(),
                career.getStartDate(),
                career.getEndDate(),
                career.getPromotionDate(),
                career.getCurrentlyEmployed(),
                career.getCurrentSalary(),
                career.getResignationReason(),
                career.getSortOrder()
        );
    }
}
```

`AdminCareerItemResponse.java`도 레코드명만 다르고 컴포넌트·`from()`은 동일하게 변경.

- [ ] **Step 4: 서비스 — `ApplicationCareerService.java`**

`toCareer()`의 `request.responsibilities()` 인자를 `request.currentSalary()`로 교체(위치 동일).

`validateCareerRequiredFields(...)`에서 responsibilities 길이검증 블록 제거 후 현재연봉 음수검증 추가:

```java
        if (!career.currentlyEmployed() && career.endDate() == null) {
            throw new InvalidJobApplicationException("End date is required when currently employed is false.");
        }
        if (career.currentlyEmployed() && career.endDate() != null) {
            throw new InvalidJobApplicationException("End date must be empty when currently employed is true.");
        }
        if (career.endDate() != null && career.startDate().isAfter(career.endDate())) {
            throw new InvalidJobApplicationException("Start date cannot be after end date.");
        }
        if (career.currentSalary() != null && career.currentSalary() < 0) {
            throw new InvalidJobApplicationException("Current salary must be greater than or equal to 0.");
        }
        if (isLongerThan(career.resignationReason(), CAREER_TEXT_MAX_LENGTH)) {
            throw new InvalidJobApplicationException("Resignation reason must be 2000 characters or less.");
        }
    }
```

(`CAREER_TEXT_MAX_LENGTH` 상수와 `isLongerThan` 헬퍼는 resignationReason에서 계속 사용하므로 유지.)

- [ ] **Step 5: PDF — `ApplicationPdfService.java` careerSection 교체**

`careerSection`을 가변 리스트 + 조건부 add로 교체(현재연봉은 값 있을 때만):

```java
    private Section careerSection(Long applicationId) {
        AdminCareerResponse career = sectionService.getCareers(applicationId);
        List<RecordRow> rows = new ArrayList<>();
        for (AdminCareerItemResponse c : career.careers()) {
            List<Field> fields = new ArrayList<>(List.of(
                    field("회사명", c.companyName()),
                    field("부서", c.departmentName()),
                    field("직위", c.positionTitle()),
                    field("고용형태", c.employmentType()),
                    field("입사일", c.startDate()),
                    field("퇴사일", c.endDate()),
                    field("진급일", c.promotionDate()),
                    field("재직중", c.currentlyEmployed())));
            if (c.currentSalary() != null) {
                fields.add(field("현재연봉", c.currentSalary()));
            }
            fields.add(field("퇴사사유", c.resignationReason()));
            rows.add(new RecordRow(fields));
        }
        return new Section("경력", rows, EMPTY);
    }
```

- [ ] **Step 6: PII purge — `ApplicationPiiPurgeRepository.java` purgeCareers 교체**

`purgeCareers`의 `@Query`에서 `c.responsibilities = null,`를 `c.currentSalary = null,`로 교체:

```java
    @Modifying(flushAutomatically = true)
    @Query("""
            update ApplicationCareer c
            set c.companyName = '__PURGED__', c.departmentName = null, c.positionTitle = null,
                c.currentSalary = null, c.resignationReason = null, c.startDate = null, c.endDate = null,
                c.promotionDate = null,
                c.createdBy = null, c.updatedBy = null
            where c.jobApplication.id = :applicationId""")
    int purgeCareers(@Param("applicationId") Long applicationId);
```

- [ ] **Step 7: 서비스 테스트 — `ApplicationCareerServiceTest.java`**

7-1. `career(...)` 헬퍼(현재 9번째 인자 `"Backend development"`)를 currentSalary `null`로:

```java
    private CareerRequest career(String companyName, Integer sortOrder) {
        return new CareerRequest(
                companyName,
                "Platform",
                "Engineer",
                EmploymentType.FULL_TIME,
                LocalDate.of(2022, 1, 1),
                LocalDate.of(2024, 12, 31),
                null,
                false,
                null,
                "Career change",
                sortOrder
        );
    }
```

7-2. `save_career_with_promotion_date_roundtrip`의 `new CareerRequest(...)` 9번째 인자 `"백엔드"` → `null`:

```java
        CareerRequest item = new CareerRequest(
                "신영증권",
                "IT",
                "과장",
                EmploymentType.FULL_TIME,
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2023, 1, 1),
                LocalDate.of(2022, 1, 1),
                false,
                null,
                "이직",
                0
        );
```

7-3. `replace_fails_when_text_fields_exceed_max_length` 메서드 전체를 아래로 교체(responsibilities 블록 삭제, resignationReason 블록만 유지하며 9번째 인자 null):

```java
    @Test
    void replace_fails_when_resignation_reason_exceeds_max_length() {
        Applicant applicant = createApplicant("career-text-validation", "Career Text Validation");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));
        String longText = "a".repeat(2001);

        assertThatThrownBy(() -> applicationCareerService.replaceCareers(
                applicant.getId(),
                applicationId,
                new CareerReplaceRequest(List.of(new CareerRequest(
                        "Company",
                        null,
                        null,
                        EmploymentType.FULL_TIME,
                        LocalDate.of(2024, 1, 1),
                        LocalDate.of(2024, 12, 31),
                        null,
                        false,
                        null,
                        longText,
                        0
                )))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }
```

7-4. 나머지 `new CareerRequest(...)` 호출(required/date 검증 테스트, 라인 ~189·207·225·249·267)은 9번째 인자가 모두 이미 `null`이므로 타입상 그대로 유효 — **변경 불필요**.

7-5. 신규 테스트 2개를 클래스에 추가(예: `save_career_with_promotion_date_roundtrip` 다음):

```java
    @Test
    void save_career_with_current_salary_roundtrip() {
        Applicant applicant = createApplicant("career-salary", "Career Salary");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));

        CareerRequest item = new CareerRequest(
                "신영증권", "IT", "과장", EmploymentType.FULL_TIME,
                LocalDate.of(2020, 1, 1), LocalDate.of(2023, 1, 1), null,
                false, 4500, "이직", 0
        );

        applicationCareerService.replaceCareers(
                applicant.getId(), applicationId, new CareerReplaceRequest(List.of(item)));
        CareerResponse response = applicationCareerService.getCareers(applicant.getId(), applicationId);

        assertThat(response.careers()).hasSize(1);
        assertThat(response.careers().get(0).currentSalary()).isEqualTo(4500);
    }

    @Test
    void replace_fails_when_current_salary_is_negative() {
        Applicant applicant = createApplicant("career-salary-neg", "Career Salary Neg");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));

        assertThatThrownBy(() -> applicationCareerService.replaceCareers(
                applicant.getId(),
                applicationId,
                new CareerReplaceRequest(List.of(new CareerRequest(
                        "Company", null, null, EmploymentType.FULL_TIME,
                        LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), null,
                        false, -1, null, 0
                )))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }
```

- [ ] **Step 8: 컨트롤러 테스트 — `ApplicationCareerControllerTest.java`**

`validExperiencedCareerJson()`의 JSON에서 `"responsibilities": "Backend development",`를 `"currentSalary": 4500,`로 교체:

```java
                      "currentlyEmployed": false,
                      "currentSalary": 4500,
                      "resignationReason": "Career change",
                      "sortOrder": 0
```

- [ ] **Step 9: 공유 테스트의 경력 생성부 수정**

9-1. `service/AdminApplicationSectionServiceTest.java` `career(...)` 헬퍼: 10번째 인자 `"Backend development"` → `null`:

```java
    private ApplicationCareer career(JobApplication application, String companyName, Integer sortOrder) {
        return ApplicationCareer.create(
                application,
                companyName,
                "Platform",
                "Engineer",
                EmploymentType.FULL_TIME,
                LocalDate.of(2023, 1, 1),
                LocalDate.of(2024, 12, 31),
                null,
                false,
                null,
                "Career move",
                sortOrder
        );
    }
```

9-2. `controller/AdminApplicationSectionControllerTest.java` 경력 생성(라인 ~375): 10번째 인자 `"Backend development"` → `4500`:

```java
        careerRepository.save(ApplicationCareer.create(
                application,
                "Shinyoung Securities",
                "Platform",
                "Engineer",
                EmploymentType.FULL_TIME,
                LocalDate.of(2023, 1, 1),
                LocalDate.of(2024, 12, 31),
                null,
                false,
                4500,
                "Career move",
                0
        ));
```

9-3. `service/ApplicationPiiPurgeServiceTest.java` 경력 생성(라인 ~159): 10번째 인자 `"백엔드 개발 담당"` → `5000`:

```java
        careerRepository.save(ApplicationCareer.create(
                application, "이전회사", "개발팀", "대리", EmploymentType.FULL_TIME,
                LocalDate.of(2019, 3, 1), LocalDate.of(2023, 5, 31), null, false, 5000, "이직", 1));
```

9-4. 같은 파일 purge 단언(라인 ~241): `assertThat(career.getResponsibilities()).isNull();` → `assertThat(career.getCurrentSalary()).isNull();`

- [ ] **Step 10: 테스트 소스 컴파일 검증(누락 호출처 적발)**

Run: `cd recruit_back/recruit_backend && AES_SECRET_KEY=<AES_KEY> ./gradlew.bat testClasses --no-daemon`
Expected: BUILD SUCCESSFUL (학력은 미변경이라 그대로 컴파일됨). 실패 시 메시지의 `ApplicationCareer`/`CareerRequest` 호출처를 위 패턴대로 수정.

- [ ] **Step 11: 경력 테스트 실행**

PowerShell:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test `
  --tests "com.shinyoung.recruit.service.ApplicationCareerServiceTest" `
  --tests "com.shinyoung.recruit.controller.ApplicationCareerControllerTest" `
  --tests "com.shinyoung.recruit.service.ApplicationPiiPurgeServiceTest" `
  --tests "com.shinyoung.recruit.service.AdminApplicationSectionServiceTest" `
  --tests "com.shinyoung.recruit.controller.AdminApplicationSectionControllerTest" `
  --no-daemon
```

Expected: BUILD SUCCESSFUL, 위 5개 클래스 전부 통과.

- [ ] **Step 12: (커밋 승인 시에만) 커밋**

```bash
git add -A
git commit -m "refactor(career): replace responsibilities with currentSalary"
```

---

## Task 2: 학력 — degreeName 제거 / additionalMajorType·additionalMajorName·thesisTitle 추가

**Files:**
- Modify: `domain/entity/ApplicationEducation.java`
- Modify: `dto/request/EducationRequest.java`
- Modify: `dto/response/EducationResponse.java`, `dto/response/AdminEducationResponse.java`
- Modify: `service/ApplicationEducationService.java`
- Modify: `service/ApplicationPdfService.java` (educationSection만)
- Modify: `domain/repository/ApplicationPiiPurgeRepository.java` (purgeEducations만)
- Test: `service/ApplicationEducationServiceTest.java`, `controller/ApplicationEducationControllerTest.java`, `service/ApplicationPiiPurgeServiceTest.java`(학력 부분), `service/AdminApplicationSectionServiceTest.java`(education helper), `controller/AdminApplicationSectionControllerTest.java`(education 생성), `controller/AdminStatisticsControllerTest.java`(education 생성)

- [ ] **Step 1: 엔티티 — `ApplicationEducation.java`**

`degreeName` 필드 선언 제거 후 `majorName` 다음에 3필드 추가:

```java
    private String majorName;

    // 복수/부/세부전공 구분 — CommonCode 그룹 MAJOR_TYPE 코드값(veteranType 선례: String 저장, FK·검증 없음).
    @Column(length = 200)
    private String additionalMajorType;

    // additionalMajorType에 해당하는 전공 명칭(자유텍스트). 파기 시 NULLIFY.
    private String additionalMajorName;

    // 논문명(자유텍스트). 파기 시 NULLIFY.
    private String thesisTitle;

    private LocalDate admissionDate;
```

private 생성자: 파라미터 `String degreeName`을 제거하고 `String majorName` 다음에 `String additionalMajorType, String additionalMajorName, String thesisTitle` 추가. 본문 `this.degreeName = degreeName;` 제거 후 `this.additionalMajorType = additionalMajorType; this.additionalMajorName = additionalMajorName; this.thesisTitle = thesisTitle;` 추가(majorName 대입 다음 위치).

정적 팩토리 2개 교체:

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
            Integer sortOrder
    ) {
        return create(
                jobApplication, educationLevel, schoolName, majorName,
                additionalMajorType, additionalMajorName, thesisTitle,
                admissionDate, graduationDate, graduationStatus, dayNightType, campusType,
                transfer, countryCode, null, sortOrder);
    }

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
                sortOrder
        );
    }
```

(private 생성자 파라미터 순서: ..., countryCode, schoolId, sortOrder — 기존과 동일하게 schoolId가 sortOrder 앞.)

- [ ] **Step 2: 요청 DTO — `EducationRequest.java`**

import 추가: `import jakarta.validation.constraints.Size;`

레코드 컴포넌트에서 `String degreeName` 제거 후 `majorName` 다음에 3필드 추가, 하위호환 생성자도 교체:

```java
public record EducationRequest(
        @NotNull(message = "Education level is required.")
        EducationLevel educationLevel,

        @NotBlank(message = "School name is required.")
        String schoolName,

        String majorName,

        @Size(max = 100, message = "Additional major type must be 100 characters or less.")
        String additionalMajorType,

        String additionalMajorName,

        String thesisTitle,

        LocalDate admissionDate,

        LocalDate graduationDate,

        @NotNull(message = "Graduation status is required.")
        GraduationStatus graduationStatus,

        DayNightType dayNightType,

        CampusType campusType,

        Boolean transfer,

        String countryCode,

        @NotNull(message = "Sort order is required.")
        @Min(value = 0, message = "Sort order must be greater than or equal to 0.")
        Integer sortOrder,

        List<@Valid SemesterGradeRequest> semesterGrades,

        /** 선택적 School master 참조(Phase 08c). 자동완성 선택 시에만 값, 직접입력이면 null. */
        Long schoolId
) {

    /** schoolId 없이 호출하던 기존 코드 호환용(Phase 08c 이전). */
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
}
```

- [ ] **Step 3: 응답 DTO 2개 — `EducationResponse.java` / `AdminEducationResponse.java`**

두 파일 모두: 컴포넌트 `String degreeName` → `String additionalMajorType, String additionalMajorName, String thesisTitle` (majorName 다음), `from()`의 `education.getDegreeName()` → 3개 getter.

`EducationResponse.java` (컴포넌트 + from 발췌):

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
                semesterGrades.stream()
                        .map(SemesterGradeResponse::from)
                        .toList()
        );
    }
}
```

`AdminEducationResponse.java`도 동일 패턴(레코드명·`AdminSemesterGradeResponse`만 다름).

- [ ] **Step 4: 서비스 — `ApplicationEducationService.java` toEducation 교체**

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
                request.sortOrder()
        );
    }
```

- [ ] **Step 5: PDF — `ApplicationPdfService.java` educationSection 교체**

`field("학위", e.degreeName())` 제거, `전공` 다음에 신규 3필드를 값 있을 때만 조건부 add:

```java
    private Section educationSection(Long applicationId) {
        List<RecordRow> rows = new ArrayList<>();
        for (AdminEducationResponse e : sectionService.getEducations(applicationId)) {
            List<Field> fields = new ArrayList<>(List.of(
                    field("학교명", e.schoolName()),
                    field("전공", e.majorName())));
            if (hasText(e.additionalMajorType())) {
                fields.add(field("전공구분", e.additionalMajorType()));
            }
            if (hasText(e.additionalMajorName())) {
                fields.add(field("복수/부전공명", e.additionalMajorName()));
            }
            if (hasText(e.thesisTitle())) {
                fields.add(field("논문명", e.thesisTitle()));
            }
            fields.addAll(List.of(
                    field("학력수준", e.educationLevel()),
                    field("입학일", e.admissionDate()),
                    field("졸업일", e.graduationDate()),
                    field("졸업상태", e.graduationStatus()),
                    field("주야구분", e.dayNightType()),
                    field("캠퍼스", e.campusType()),
                    field("편입여부", e.transfer()),
                    field("국가코드", e.countryCode())));
            for (AdminSemesterGradeResponse g : e.semesterGrades()) {
                fields.add(field(
                        str(g.schoolYear()) + "학년 " + str(g.semester()) + "학기 성적",
                        "취득 " + str(g.earnedCredits())
                                + " / 평점 " + str(g.gradePoint()) + "/" + str(g.maxGradePoint())
                                + " (전공 " + str(g.majorGradePoint()) + "/" + str(g.majorMaxGradePoint()) + ")"));
            }
            rows.add(new RecordRow(fields));
        }
        return new Section("학력", rows, EMPTY);
    }
```

`field(...)` 헬퍼 근처에 `hasText` 헬퍼 추가(Task 1에서 미추가 시):

```java
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
```

- [ ] **Step 6: PII purge — `ApplicationPiiPurgeRepository.java` purgeEducations 교체**

`e.degreeName = null,` 제거, `e.additionalMajorName = null, e.thesisTitle = null,` 추가(`additionalMajorType`은 코드값 KEEP — 건드리지 않음):

```java
    @Modifying(flushAutomatically = true)
    @Query("""
            update ApplicationEducation e
            set e.schoolName = '__PURGED__', e.majorName = null,
                e.additionalMajorName = null, e.thesisTitle = null, e.countryCode = null,
                e.admissionDate = null, e.graduationDate = null, e.createdBy = null, e.updatedBy = null
            where e.jobApplication.id = :applicationId""")
    int purgeEducations(@Param("applicationId") Long applicationId);
```

- [ ] **Step 7: 서비스 테스트 — `ApplicationEducationServiceTest.java`**

모든 `new EducationRequest(...)`에서 `majorName` 다음 `degreeName` 인자(현재 4번째 컴포넌트)를 **3개 인자(additionalMajorType, additionalMajorName, thesisTitle)** 로 치환한다. 구체 위치:

7-1. `universityEducation(...)` 헬퍼 — 4번째 `"Bachelor"`를 `"DOUBLE_MAJOR", "경영학", "딥러닝 추천시스템"`로:

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
                List.of(grade(2, 1), grade(1, 1))
        );
    }
```

7-2. `highSchoolEducation(...)` 헬퍼 — 4번째 `null` → `null, null, null`:

```java
    private EducationRequest highSchoolEducation(Integer sortOrder) {
        return new EducationRequest(
                EducationLevel.HIGH_SCHOOL,
                "Shinyoung High School",
                null,
                null,
                null,
                null,
                LocalDate.of(2018, 3, 1),
                LocalDate.of(2021, 2, 28),
                GraduationStatus.GRADUATED,
                DayNightType.DAY,
                CampusType.MAIN,
                false,
                "KR",
                sortOrder,
                List.of()
        );
    }
```

7-3. `education_persists_optional_school_id_when_selected_and_null_when_freetext`의 두 요청:

```java
        EducationRequest linkedToMaster = new EducationRequest(
                EducationLevel.UNIVERSITY, "Linked University", null, null, null, null,
                LocalDate.of(2020, 3, 1), LocalDate.of(2024, 2, 1), GraduationStatus.GRADUATED,
                DayNightType.DAY, CampusType.MAIN, false, "KR", 1, List.of(), 777L);
        EducationRequest freeText = new EducationRequest(
                EducationLevel.HIGH_SCHOOL, "Free Text High", null, null, null, null,
                null, null, GraduationStatus.GRADUATED, null, null, false, "KR", 0, List.of());
```

7-4. `semester_grades_are_sorted_by_school_year_semester_and_id`의 요청(현재 majorName `"Computer Science"` 다음 4번째 `null`) → `null, null, null`:

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
                        )
                )))
```

7-5. `replace_fails_when_required_or_cross_field_validation_fails`의 `new EducationRequest(...)` 4곳: 각 요청의 `schoolName` 다음 4번째 `null`(degreeName) 자리에 `null, null,`을 한 줄 더 추가(즉 `null,` 1개 → `null,` 3개). 4개 요청 모두 majorName=null, degreeName=null 형태이므로 동일 처리. 예(첫 요청):

```java
                new EducationReplaceRequest(List.of(new EducationRequest(
                        null,
                        "University",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        GraduationStatus.GRADUATED,
                        null,
                        null,
                        null,
                        null,
                        0,
                        List.of()
                )))
```

(원래 `null`(level), `"University"`, `null`(major), `null`(degree), `null`(admission), `null`(grad), GRADUATED, ... 였던 것을 → level, school, major(null), **addType(null), addName(null), thesis(null)**, admission(null), grad(null), GRADUATED, ...로. 나머지 3개 요청도 majorName 다음에 `null`을 2개씩 더 추가.)

7-6. `replace_fails_when_high_school_has_grade_or_sort_order_is_duplicated`의 첫 `new EducationRequest(...)`(HIGH_SCHOOL, "High School", null, null, ...) — majorName 다음 `null` 1개를 3개로:

```java
                new EducationReplaceRequest(List.of(new EducationRequest(
                        EducationLevel.HIGH_SCHOOL,
                        "High School",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        GraduationStatus.GRADUATED,
                        null,
                        null,
                        false,
                        "KR",
                        0,
                        List.of(grade(1, 1))
                )))
```

7-7. 신규 roundtrip 테스트 추가(`universityEducation` 사용 테스트 근처):

```java
    @Test
    void save_education_with_additional_major_and_thesis_roundtrip() {
        Applicant applicant = createApplicant("education-major", "Education Major");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));

        applicationEducationService.replaceEducations(
                applicant.getId(), applicationId,
                new EducationReplaceRequest(List.of(universityEducation(0))));
        List<EducationResponse> responses =
                applicationEducationService.getEducations(applicant.getId(), applicationId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).additionalMajorType()).isEqualTo("DOUBLE_MAJOR");
        assertThat(responses.get(0).additionalMajorName()).isEqualTo("경영학");
        assertThat(responses.get(0).thesisTitle()).isEqualTo("딥러닝 추천시스템");
    }
```

- [ ] **Step 8: 컨트롤러 테스트 — `ApplicationEducationControllerTest.java`**

`validEducationJson()`에서 `"degreeName": "Bachelor",`를 3개 키로 교체:

```java
                      "majorName": "Computer Science",
                      "additionalMajorType": "DOUBLE_MAJOR",
                      "additionalMajorName": "경영학",
                      "thesisTitle": "딥러닝 추천시스템",
                      "admissionDate": "2021-03-01",
```

- [ ] **Step 9: 공유 테스트의 학력 생성부 수정**

9-1. `service/AdminApplicationSectionServiceTest.java` `education(...)` 헬퍼(라인 ~546): majorName 다음 5번째 `null`(degreeName) → `null, null, null`:

```java
        return ApplicationEducation.create(
                application,
                educationLevel,
                schoolName,
                educationLevel == EducationLevel.HIGH_SCHOOL ? null : "Computer Science",
                null,
                null,
                null,
                LocalDate.of(2021, 3, 1),
                LocalDate.of(2025, 2, 28),
                GraduationStatus.GRADUATED,
                DayNightType.DAY,
                CampusType.MAIN,
                false,
                "KR",
                sortOrder
        );
```

9-2. `controller/AdminApplicationSectionControllerTest.java` 학력 생성(라인 ~350): 5번째 `"Bachelor"`(degreeName) → `null, null, null`:

```java
        ApplicationEducation education = educationRepository.save(ApplicationEducation.create(
                application,
                EducationLevel.UNIVERSITY,
                "Shinyoung University",
                "Computer Science",
                null,
                null,
                null,
                LocalDate.of(2021, 3, 1),
                LocalDate.of(2025, 2, 28),
                GraduationStatus.GRADUATED,
                DayNightType.DAY,
                CampusType.MAIN,
                false,
                "KR",
                0
        ));
```

9-3. `controller/AdminStatisticsControllerTest.java` 학력 생성(라인 ~524): 5번째 `null`(degreeName) → `null, null, null` (with-schoolId 14→16 인자):

```java
        educationRepository.saveAndFlush(ApplicationEducation.create(
                application, level, "School-" + sortOrder, null, null, null, null, null, null,
                GraduationStatus.GRADUATED, null, null, false, "KR", schoolId, sortOrder));
```

9-4. `service/ApplicationPiiPurgeServiceTest.java` 학력 생성(라인 ~155): 5번째 `"학사"`(degreeName) → `"DOUBLE_MAJOR", "경영학", "딥러닝 추천시스템"`:

```java
        educationRepository.save(ApplicationEducation.create(
                application, EducationLevel.UNIVERSITY, "서울대학교", "컴퓨터공학",
                "DOUBLE_MAJOR", "경영학", "딥러닝 추천시스템",
                LocalDate.of(2015, 3, 2), LocalDate.of(2019, 2, 25),
                GraduationStatus.GRADUATED, DayNightType.DAY, CampusType.MAIN, false, "KR", 1));
```

9-5. 같은 파일 purge 단언(라인 ~229-230): `getDegreeName` 단언 제거 후 신규 단언 추가:

```java
        ApplicationEducation education = educationRepository.findAll().get(0);
        assertThat(education.getSchoolName()).isEqualTo(PURGED);
        assertThat(education.getMajorName()).isNull();
        assertThat(education.getAdditionalMajorName()).isNull();
        assertThat(education.getThesisTitle()).isNull();
        assertThat(education.getAdditionalMajorType()).isEqualTo("DOUBLE_MAJOR");
        assertThat(education.getCountryCode()).isNull();
```

- [ ] **Step 10: 테스트 소스 컴파일 검증**

Run: `cd recruit_back/recruit_backend && AES_SECRET_KEY=<AES_KEY> ./gradlew.bat testClasses --no-daemon`
Expected: BUILD SUCCESSFUL. 실패 시 `ApplicationEducation`/`EducationRequest` 잔여 호출처를 위 패턴대로 수정.

- [ ] **Step 11: 학력 + 영향 테스트 실행**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test `
  --tests "com.shinyoung.recruit.service.ApplicationEducationServiceTest" `
  --tests "com.shinyoung.recruit.controller.ApplicationEducationControllerTest" `
  --tests "com.shinyoung.recruit.service.ApplicationPiiPurgeServiceTest" `
  --tests "com.shinyoung.recruit.service.AdminApplicationSectionServiceTest" `
  --tests "com.shinyoung.recruit.controller.AdminApplicationSectionControllerTest" `
  --tests "com.shinyoung.recruit.controller.AdminStatisticsControllerTest" `
  --no-daemon
```

Expected: BUILD SUCCESSFUL, 6개 클래스 통과.

- [ ] **Step 12: (커밋 승인 시에만) 커밋**

```bash
git add -A
git commit -m "refactor(education): replace degreeName with additionalMajorType/Name and thesisTitle"
```

---

## Task 3: 계약 + PII 인벤토리 문서 동기화

**Files:**
- Modify: `recruit/api-contract.md`
- Modify: `recruit_back/recruit_backend/docs/codex/implementation/phase-09-pii-field-inventory.md`

- [ ] **Step 1: `api-contract.md` 경력 섹션 갱신**

`#### GET·POST /api/applications/{applicationId}/careers` 의 요청 라인에서 `responsibilities` → `currentSalary`로 바꾸고 변경 메모 추가:

```markdown
- 변경(2026-06-25): 요청·응답에서 `responsibilities` 제거, `currentSalary`(현재연봉, Integer 만원 단위, nullable, 0 이상) 추가.
- 요청: `{ careers: [{ companyName, departmentName, positionTitle, employmentType, startDate, endDate, promotionDate, currentlyEmployed, currentSalary, resignationReason, sortOrder }] }`
```

- [ ] **Step 2: `api-contract.md` 학력 섹션 추가**

경력 섹션 뒤(또는 학교 마스터 섹션 인근)에 신규 학력 화면 계약을 추가:

```markdown
### 화면: 지원자 학력 (ApplicationEducation)

- 프론트: (후속) 학력 입력 화면 + `src/api` education 관련
- 백엔드: `com.shinyoung.recruit.controller.ApplicationEducationController`

#### GET·POST `/api/applications/{applicationId}/educations`  🔴 백엔드 구현됨 / 프론트 미반영

- 변경(2026-06-25): 요청·응답에서 `degreeName` 제거. `additionalMajorType`(복수/부/세부전공 구분), `additionalMajorName`(해당 전공 명칭), `thesisTitle`(논문명) 추가.
- 요청: `{ educations: [{ educationLevel, schoolName, majorName, additionalMajorType, additionalMajorName, thesisTitle, admissionDate, graduationDate, graduationStatus, dayNightType, campusType, transfer, countryCode, sortOrder, semesterGrades, schoolId }] }`
- 응답(200): `ApiResponse<{ educations: [...] }>` (degreeName 없음, 3필드 포함, educationId 포함)
- `additionalMajorType`는 코드 문자열(프론트가 CommonCode 그룹 `MAJOR_TYPE`로 렌더, 백엔드 validation 미결합). `additionalMajorName`/`thesisTitle`는 선택 자유텍스트.
- 관리자 조회 `GET /api/admin/applications/{id}/educations` 응답도 동일하게 degreeName 제거 + 3필드 추가.
```

- [ ] **Step 3: `phase-09-pii-field-inventory.md` 분류표 갱신**

해당 문서의 Career/Education 필드 분류 항목을 코드와 일치시킨다:
- Career: `responsibilities`(NULLIFY) 항목 제거, `currentSalary`(NULLIFY, 재무정보) 추가.
- Education: `degreeName`(NULLIFY) 항목 제거, `additionalMajorName`(NULLIFY)·`thesisTitle`(NULLIFY) 추가, `additionalMajorType`(KEEP_TOMBSTONE, 코드값) 추가.
- `ApplicationPiiPurgeRepository.purgeCareers`/`purgeEducations`의 실제 set 절과 1:1로 대조되도록 기술.

- [ ] **Step 4: 계약-코드 일치 최종 확인**

Run: `cd recruit_back/recruit_backend && AES_SECRET_KEY=<AES_KEY> ./gradlew.bat test --tests "com.shinyoung.recruit.*Career*" --tests "com.shinyoung.recruit.*Education*" --tests "com.shinyoung.recruit.service.ApplicationPiiPurgeServiceTest" --no-daemon`
Expected: BUILD SUCCESSFUL. 통과하면 계약/인벤토리 문서가 구현과 일치함을 보고.

- [ ] **Step 5: (커밋 승인 시에만) 문서 커밋**

recruit_back 인벤토리 문서는 백엔드 레포에서, `api-contract.md`는 recruit 로컬 레포에서 각각 커밋(요청 시).

---

## Self-Review (작성자 점검 결과)

**Spec coverage:** 설계서 §3.1~3.5 전 항목이 Task 1~3에 매핑됨 — Career 필드(T1), Education 필드(T2), PII purge(T1 Step6 + T2 Step6 + T3 Step3), PDF 조건부(T1 Step5 + T2 Step5), 계약/인벤토리(T3). Acceptance 8개 모두 대응 스텝 존재.

**Placeholder scan:** 모든 코드 스텝에 실제 코드 포함. `<AES_KEY>`는 의도된 보안 치환자(본문에 실제 로컬 예시 키 명시).

**Type consistency:** getter/accessor 일관 — 엔티티 `getCurrentSalary()`/`getAdditionalMajorType()`/`getAdditionalMajorName()`/`getThesisTitle()`(Lombok @Getter), 레코드 accessor `currentSalary()`/`additionalMajorType()` 등. `currentSalary`는 전 구간 `Integer`. `ApplicationEducation.create` 16-인자 오버로드(with schoolId)·15-인자(without)와 호출처 인자 수 일치 확인.

**알려진 리스크:** ① 위에 열거되지 않은 추가 호출처가 있으면 각 Task의 Step 10(`testClasses`)에서 컴파일 에러로 적발 → 동일 패턴으로 수정. ② PDF 테스트 3종(`ApplicationPdfServiceTest`/`ApplicationPdfControllerTest`/`ApplicationPdfSecurityHardeningTest`)은 변경 라벨·필드를 단언하지 않고 엔티티를 직접 생성하지도 않음을 확인함 → 영향 없음(추가 대응 불필요).
