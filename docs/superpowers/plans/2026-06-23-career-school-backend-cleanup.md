# 경력/학교 백엔드 정리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 백엔드에서 `careerType`/`ApplicationCareerProfile`를 완전히 제거하고 `ApplicationCareer`에 `promotionDate`를 추가하며, School 마스터에서 `schoolCode`를 제거하고 `schoolCategory`를 추가한다.

**Architecture:** 작업 위치는 `recruit_back/recruit_backend`(Spring Boot, JPA, 패키지 루트 `com.shinyoung.recruit`). Java는 부분 변경이 컴파일되지 않으므로, careerType 제거와 schoolCode 제거는 각각 **원자적 태스크**(참조 파일 + 테스트를 함께 수정 후 컴파일)로 진행한다. 검증은 수정 관련 테스트만 `--tests`로 선택 실행(전체 리그레션 금지).

**Tech Stack:** Java 17, Spring Boot, Spring Data JPA, JUnit5 + AssertJ, Gradle Wrapper(`gradlew.bat`), H2(@DataJpaTest), POI(xlsx import).

> **공통 — 테스트 실행 (PowerShell, `recruit_back/recruit_backend/`에서):** AES 키 주입 필수. 아래 `<KEY>`는 백엔드 `CLAUDE.md`에 명시된 로컬 예시 키 `22791194512954214612461221261067` 이다.
> ```powershell
> $env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "<클래스>" --no-daemon
> ```

> **커밋 규약:** 각 커밋 메시지 끝에
> ```
> Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
> ```
> 백엔드 변경은 `recruit_back/recruit_backend/` 저장소(branch `main`)에 커밋한다. 계약 문서·문서 갱신만 recruit/ 저장소에 커밋한다(Task 4).

---

## File Structure

| 영역 | 파일 | 변경 |
|------|------|------|
| 경력 entity | `domain/entity/ApplicationCareer.java` | `promotionDate` 추가 |
| 경력 entity | `domain/entity/ApplicationCareerProfile.java` | **삭제** |
| 경력 enum | `enumeration/CareerType.java` | **삭제** |
| 경력 repo | `domain/repository/ApplicationCareerProfileRepository.java` | **삭제** |
| 경력 dto | `dto/request/CareerRequest.java` | `promotionDate` 추가 |
| 경력 dto | `dto/request/CareerReplaceRequest.java` | `careerType` 제거 |
| 경력 dto | `dto/response/CareerItemResponse.java`, `AdminCareerItemResponse.java` | `promotionDate` 추가 |
| 경력 dto | `dto/response/CareerResponse.java`, `AdminCareerResponse.java` | `careerType` 제거 |
| 경력 svc | `service/ApplicationCareerService.java` | 프로필/타입검증 제거 + promotionDate 매핑 |
| 경력 svc | `service/ApplicationSubmitValidator.java` | careerType 검증 블록 제거 |
| 경력 svc | `service/ApplicationCompletionReadChecker.java` | careerType 완료 블록 제거 |
| 경력 svc | `service/AdminApplicationSectionService.java` | careerType 제거 |
| 경력 svc | `service/ApplicationPdfService.java` | 헤더 careerType 제거 + 진급일 행 |
| 경력 purge | `service/ApplicationPiiPurgeService.java` | `purgeCareerProfile` 호출 제거 |
| 경력 purge | `domain/repository/ApplicationPiiPurgeRepository.java` | `purgeCareerProfile` 제거 + `purgeCareers`에 promotionDate |
| 학교 | `domain/entity/School.java` 외 11파일 | schoolCode 제거 / schoolCategory 추가 (Task 3 상세) |

---

## Task 1: 경력 — promotionDate 추가 (additive)

careerType는 아직 그대로 둔다. promotionDate만 추가하므로 독립적으로 컴파일·통과한다.

**Files:**
- Modify: `src/main/java/com/shinyoung/recruit/domain/entity/ApplicationCareer.java`
- Modify: `src/main/java/com/shinyoung/recruit/dto/request/CareerRequest.java`
- Modify: `src/main/java/com/shinyoung/recruit/dto/response/CareerItemResponse.java`
- Modify: `src/main/java/com/shinyoung/recruit/dto/response/AdminCareerItemResponse.java`
- Modify: `src/main/java/com/shinyoung/recruit/service/ApplicationCareerService.java` (`toCareer`만)
- Modify: `src/main/java/com/shinyoung/recruit/service/ApplicationPdfService.java` (진급일 행)
- Modify: `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationPiiPurgeRepository.java` (`purgeCareers`에 promotionDate)
- Test: `src/test/java/com/shinyoung/recruit/service/ApplicationCareerServiceTest.java`

**규칙:** `promotionDate`는 선택값(nullable), 교차검증 없음. 필드 위치는 모든 곳에서 `endDate` 바로 뒤에 둔다(날짜 그룹화).

- [ ] **Step 1: 실패 테스트 추가** — `ApplicationCareerServiceTest`에 promotionDate 왕복 저장 검증 추가

```java
@Test
void 경력_진급일을_저장하고_조회한다() {
    // given: 기존 테스트의 application 준비 패턴을 따른다(소유 application + writable + careerEnabled)
    Long applicantId = /* 기존 픽스처의 applicantId */ ;
    Long applicationId = /* 기존 픽스처의 applicationId */ ;
    CareerRequest item = new CareerRequest(
            "신영증권", "IT", "과장", null,
            LocalDate.of(2020, 1, 1), LocalDate.of(2023, 1, 1),
            LocalDate.of(2022, 1, 1),            // promotionDate (endDate 뒤)
            false, "백엔드", "이직", 0);
    CareerReplaceRequest request = new CareerReplaceRequest(CareerType.EXPERIENCED, List.of(item));

    // when
    careerService.replaceCareers(applicantId, applicationId, request);
    CareerResponse response = careerService.getCareers(applicantId, applicationId);

    // then
    assertThat(response.careers()).hasSize(1);
    assertThat(response.careers().get(0).promotionDate()).isEqualTo(LocalDate.of(2022, 1, 1));
}
```

> 주의: 위 픽스처(`applicantId`, `applicationId`, `careerService` 주입)는 이 테스트 클래스의 기존 셋업 방식을 그대로 사용한다. 기존 테스트의 `@BeforeEach`/헬퍼를 확인해 동일 패턴으로 채운다.

- [ ] **Step 2: 컴파일 실패 확인** — `CareerRequest`/`CareerItemResponse`에 promotionDate가 없어 컴파일 에러

Run:
```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicationCareerServiceTest" --no-daemon
```
Expected: 컴파일 실패 (`promotionDate` symbol not found in CareerRequest/CareerItemResponse).

- [ ] **Step 3: 엔티티에 필드 추가** — `ApplicationCareer.java`

`endDate` 필드 바로 뒤에 추가:
```java
    private LocalDate promotionDate;
```
생성자 파라미터: `endDate` 파라미터 뒤에 `LocalDate promotionDate,` 추가하고 본문에 `this.promotionDate = promotionDate;` 추가.
`create(...)` 정적 팩토리: 동일하게 `endDate` 뒤에 `LocalDate promotionDate,` 파라미터를 추가하고, `new ApplicationCareer(... endDate, promotionDate, currentlyEmployed ...)`로 전달. (즉 인자 순서는 `application, companyName, departmentName, positionTitle, employmentType, startDate, endDate, promotionDate, currentlyEmployed, responsibilities, resignationReason, sortOrder`)

- [ ] **Step 4: 요청/응답 DTO에 필드 추가**

`CareerRequest.java` — `endDate` 뒤에 추가:
```java
        LocalDate promotionDate,
```
`CareerItemResponse.java` — record 컴포넌트 `endDate` 뒤에 `LocalDate promotionDate,` 추가하고, `from()`의 `career.getEndDate(),` 뒤에 `career.getPromotionDate(),` 추가.
`AdminCareerItemResponse.java` — 동일하게 `LocalDate promotionDate,` 추가 + `from()`에 `career.getPromotionDate(),` 추가.

- [ ] **Step 5: 서비스 매핑 추가** — `ApplicationCareerService.toCareer`

`ApplicationCareer.create(...)` 호출에서 `request.endDate(),` 뒤에 `request.promotionDate(),` 추가:
```java
    private ApplicationCareer toCareer(JobApplication application, CareerRequest request) {
        return ApplicationCareer.create(
                application,
                request.companyName(),
                request.departmentName(),
                request.positionTitle(),
                request.employmentType(),
                request.startDate(),
                request.endDate(),
                request.promotionDate(),
                request.currentlyEmployed(),
                request.responsibilities(),
                request.resignationReason(),
                request.sortOrder()
        );
    }
```

- [ ] **Step 6: PII 파기에 promotionDate 포함** — `ApplicationPiiPurgeRepository.purgeCareers`

`@Query`의 set 절에 `c.endDate = null,` 뒤에 `c.promotionDate = null,`를 추가:
```java
    @Modifying(flushAutomatically = true)
    @Query("""
            update ApplicationCareer c
            set c.companyName = '__PURGED__', c.departmentName = null, c.positionTitle = null,
                c.responsibilities = null, c.resignationReason = null, c.startDate = null, c.endDate = null,
                c.promotionDate = null,
                c.createdBy = null, c.updatedBy = null
            where c.jobApplication.id = :applicationId""")
    int purgeCareers(@Param("applicationId") Long applicationId);
```

- [ ] **Step 7: PDF에 진급일 행 추가** — `ApplicationPdfService`

경력 항목 row 생성부(예: `field("퇴사사유", c.resignationReason())` 인근)에 진급일 행을 추가한다. 기존 `field(...)`/`str(...)` 헬퍼 패턴을 그대로 사용:
```java
                    field("진급일", str(c.promotionDate())),
```
(이 단계는 careerType 헤더와 무관. careerType 헤더 제거는 Task 2에서.)

- [ ] **Step 8: 테스트 통과 확인**

Run:
```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicationCareerServiceTest" --tests "com.shinyoung.recruit.service.ApplicationPdfServiceTest" --no-daemon
```
Expected: PASS. (ApplicationPdfServiceTest가 없으면 해당 `--tests`는 생략.)

- [ ] **Step 9: 커밋**
```powershell
cd C:/Users/roehf/Desktop/recruit/recruit_back/recruit_backend
git add -A
git commit -m @'
feat(career): 경력에 진급일(promotionDate) 추가

ApplicationCareer + 요청/응답 DTO에 promotionDate(nullable), PDF 진급일 행,
PII 파기 NULLIFY 대상에 포함.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
'@
```

---

## Task 2: 경력 — careerType / ApplicationCareerProfile 완전 제거 (atomic)

`CareerType`/`ApplicationCareerProfile`/`ApplicationCareerProfileRepository`를 삭제하면 이를 참조하는 모든 파일이 동시에 컴파일 안 되므로, 아래 수정·삭제를 **한 태스크에서 함께** 적용하고 마지막에 컴파일+테스트한다.

**Files:**
- Delete: `domain/entity/ApplicationCareerProfile.java`
- Delete: `domain/repository/ApplicationCareerProfileRepository.java`
- Delete: `enumeration/CareerType.java`
- Modify: `dto/request/CareerReplaceRequest.java`
- Modify: `dto/response/CareerResponse.java`, `dto/response/AdminCareerResponse.java`
- Modify: `service/ApplicationCareerService.java`
- Modify: `service/ApplicationSubmitValidator.java`
- Modify: `service/ApplicationCompletionReadChecker.java`
- Modify: `service/AdminApplicationSectionService.java`
- Modify: `service/ApplicationPdfService.java`
- Modify: `service/ApplicationPiiPurgeService.java`
- Modify: `domain/repository/ApplicationPiiPurgeRepository.java`
- Test: `ApplicationCareerServiceTest`, `ApplicationSubmitValidatorTest`, `AdminApplicationSectionServiceTest`, `AdminApplicationSectionControllerTest`, `ApplicationCareerControllerTest`, `ApplicationDashboardServiceTest`, 완료판정 관련 테스트

- [ ] **Step 1: DTO 정리**

`CareerReplaceRequest.java` 전체를 아래로 교체(careerType 제거, CareerType import 제거):
```java
package com.shinyoung.recruit.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CareerReplaceRequest(
        @NotNull(message = "Career list is required.")
        List<@Valid CareerRequest> careers
) {
}
```

`CareerResponse.java` 전체를 아래로 교체:
```java
package com.shinyoung.recruit.dto.response;

import java.util.List;

public record CareerResponse(
        List<CareerItemResponse> careers
) {
}
```

`AdminCareerResponse.java` 전체를 아래로 교체:
```java
package com.shinyoung.recruit.dto.response;

import java.util.List;

public record AdminCareerResponse(
        List<AdminCareerItemResponse> careers
) {
}
```

- [ ] **Step 2: ApplicationCareerService 정리**

`ApplicationCareerService.java`에서:
- import 제거: `ApplicationCareerProfile`, `ApplicationCareerProfileRepository`, `CareerType`.
- 필드 제거: `private final ApplicationCareerProfileRepository careerProfileRepository;`
- `replaceCareers`에서 프로필 생성/갱신 블록 제거(아래 블록 삭제):
```java
        ApplicationCareerProfile profile = careerProfileRepository.findByJobApplicationId(applicationId)
                .orElseGet(() -> careerProfileRepository.save(
                        ApplicationCareerProfile.create(application, request.careerType())
                ));
        profile.updateCareerType(request.careerType());

```
- `validateRequest`에서 careerType 관련 블록 제거(아래 삭제):
```java
        if (request == null || request.careerType() == null) {
            throw new InvalidJobApplicationException("Career type is required.");
        }
        if (request.careers() == null) {
            throw new InvalidJobApplicationException("Career list is required.");
        }
        if (request.careerType() != CareerType.EXPERIENCED && !request.careers().isEmpty()) {
            throw new InvalidJobApplicationException("Career items are allowed only for EXPERIENCED career type.");
        }
```
  대체(널 가드만 유지):
```java
        if (request == null || request.careers() == null) {
            throw new InvalidJobApplicationException("Career list is required.");
        }
```
  (이후 `for (CareerRequest career : request.careers())` 행 검증/ sortOrder 유일성 로직은 그대로 유지)
- `getCareerResponse`를 careerType 없이 교체:
```java
    private CareerResponse getCareerResponse(Long applicationId) {
        List<CareerItemResponse> careers = careerRepository.findByJobApplicationIdOrderBySortOrderAscIdAsc(applicationId)
                .stream()
                .map(CareerItemResponse::from)
                .toList();
        return new CareerResponse(careers);
    }
```

- [ ] **Step 3: ApplicationSubmitValidator 정리**

careerType/프로필 검증 블록을 제거한다. 아래 블록(프로필 조회 ~ 행 강제 규칙) 전체 삭제:
```java
        ApplicationCareerProfile profile = careerProfileRepository.findByJobApplicationId(applicationId)
                .orElseThrow(() -> new InvalidJobApplicationException("Career profile is required before submit."));
        CareerType careerType = profile.getCareerType();
        if (careerType == null || careerType == CareerType.NOT_SELECTED) {
            throw new InvalidJobApplicationException("Career type must be selected before submit.");
        }

        boolean hasCareerRows = careerRepository.existsByJobApplicationId(applicationId);
        if (careerType == CareerType.EXPERIENCED && !hasCareerRows) {
            throw new InvalidJobApplicationException("Career rows are required for experienced applicants before submit.");
        }
        if ((careerType == CareerType.NEWCOMER || careerType == CareerType.NOT_APPLICABLE) && hasCareerRows) {
            throw new InvalidJobApplicationException("Career rows are not allowed for the selected career type before submit.");
        }
```
그 후 import(`ApplicationCareerProfile`, `CareerType`)와, 이 블록 제거로 더 이상 쓰이지 않는 필드(`careerProfileRepository`, 그리고 `careerRepository`가 이 클래스에서 더 쓰이지 않으면 그 필드까지)를 제거한다. **판단 기준: 컴파일러가 unused/미해결로 알려주는 것만 정리**(아래 Step 7 컴파일에서 확정).

- [ ] **Step 4: ApplicationCompletionReadChecker 정리**

careerType 기반 완료 이슈 블록을 제거한다(프로필 조회 + 아래 careerType 분기 전체):
```java
        CareerType careerType = profile.get().getCareerType();
        if (careerType == null || careerType == CareerType.NOT_SELECTED) {
            addIssue(item(
                    CAREER,
                    ...
        boolean hasCareerRows = careerRepository.existsByJobApplicationId(applicationId);
        if (careerType == CareerType.EXPERIENCED && !hasCareerRows) {
            addIssue(item(
                    CAREER,
                    ...
        if ((careerType == CareerType.NEWCOMER || careerType == CareerType.NOT_APPLICABLE) && hasCareerRows) {
            addIssue(item(
                    CAREER,
                    ...
```
경력 섹션은 완료 판정에서 제외되므로, 이 메서드에서 career 관련 검사(프로필 조회 `profile` + 위 블록)를 통째로 제거한다. import(`CareerType`, `ApplicationCareerProfile`)와 unused가 된 의존 필드(`careerProfileRepository`, 필요시 `careerRepository`)는 Step 7 컴파일 기준으로 정리한다.

> 주의: 이 파일은 여러 섹션 완료검사를 모은 클래스다. **CAREER 섹션 블록만** 제거하고 다른 섹션(학력/병역 등) 로직은 손대지 않는다. 해당 블록의 정확한 시작/끝은 `careerProfileRepository`/`careerType`/`CAREER` 가 나오는 범위로 식별한다.

- [ ] **Step 5: AdminApplicationSectionService 정리**

`getCareers`에서 careerType 조회/반환 제거:
```java
    public AdminCareerResponse getCareers(Long applicationId) {
        validateApplicationExists(applicationId);
        List<AdminCareerItemResponse> careers = careerRepository.findByJobApplicationIdOrderBySortOrderAscIdAsc(applicationId)
                .stream()
                .map(AdminCareerItemResponse::from)
                .toList();
        return new AdminCareerResponse(careers);
    }
```
import(`CareerType`, `ApplicationCareerProfile`)와 unused가 된 `careerProfileRepository` 필드를 제거(Step 7 기준).

- [ ] **Step 6: PDF 헤더 + PII 파기 호출 정리**

`ApplicationPdfService` 경력 섹션 헤더에서 careerType 제거:
```java
        return new Section("경력", rows, EMPTY);
```
(`career.careerType()` 참조 제거. `career`는 이제 `CareerResponse(careers)`이므로 `career.careers()`만 사용.)

`ApplicationPiiPurgeService.purgeRelationalPii`에서 아래 한 줄 제거:
```java
        purgeRepository.purgeCareerProfile(applicationId);
```

`ApplicationPiiPurgeRepository`에서 `purgeCareerProfile` 메서드 전체 제거:
```java
    @Modifying(flushAutomatically = true)
    @Query("""
            update ApplicationCareerProfile p
            set p.createdBy = null, p.updatedBy = null
            where p.jobApplication.id = :applicationId""")
    int purgeCareerProfile(@Param("applicationId") Long applicationId);
```

- [ ] **Step 7: 3개 파일 삭제 + 테스트 정리 + 컴파일/실행**

삭제:
```powershell
cd C:/Users/roehf/Desktop/recruit/recruit_back/recruit_backend
git rm src/main/java/com/shinyoung/recruit/domain/entity/ApplicationCareerProfile.java src/main/java/com/shinyoung/recruit/domain/repository/ApplicationCareerProfileRepository.java src/main/java/com/shinyoung/recruit/enumeration/CareerType.java
```

테스트 정리(컴파일 기준으로 careerType/Profile 참조 제거):
- careerType 인자를 쓰던 `new CareerReplaceRequest(CareerType.X, ...)` → `new CareerReplaceRequest(List.of(...))`로 변경.
- careerType 관련 단언/케이스 삭제: "EXPERIENCED만 행 허용", 제출 시 "타입 미선택 거부/EXPERIENCED 행 강제/NEWCOMER 행 금지", 완료판정 careerType 이슈, `AdminCareerResponse.careerType()`/`CareerResponse.careerType()` 단언.
- 추가 케이스:
  - `ApplicationCareerServiceTest`: "careerType 없이 경력 0개로 replace 후 careers 빈 리스트" / "경력 N개 자유 저장".
  - `ApplicationSubmitValidatorTest`: "경력 행 0개여도 제출 검증 통과"(경력이 더 이상 제출 게이트가 아님).
  - 완료판정 테스트: 경력 미입력이 완료 이슈에 포함되지 않음.

컴파일+실행:
```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicationCareerServiceTest" --tests "com.shinyoung.recruit.service.ApplicationSubmitValidatorTest" --tests "com.shinyoung.recruit.service.AdminApplicationSectionServiceTest" --tests "com.shinyoung.recruit.controller.AdminApplicationSectionControllerTest" --tests "com.shinyoung.recruit.controller.ApplicationCareerControllerTest" --tests "com.shinyoung.recruit.service.ApplicationDashboardServiceTest" --no-daemon
```
Expected: 컴파일 성공 + 모든 지정 테스트 PASS. (완료판정 테스트 클래스가 별도면 `--tests`에 추가.)

- [ ] **Step 8: 커밋**
```powershell
cd C:/Users/roehf/Desktop/recruit/recruit_back/recruit_backend
git add -A
git commit -m @'
refactor(career): careerType / ApplicationCareerProfile 완전 제거

경력 타입 기반 검증(저장/제출/완료) 제거 → 경력은 선택 목록.
CareerType enum·ApplicationCareerProfile·Repository 삭제, DTO/PDF/관리자/PII 파기에서 제거.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
'@
```

---

## Task 3: 학교 — schoolCode 제거 + schoolCategory 추가 (atomic)

School 마스터의 `schoolCode` 제거와 `schoolCategory` 추가가 동일 파일군(엔티티/DTO/파서/서비스)을 건드리므로 한 태스크로 함께 적용한다. xlsx 컬럼은 7개 유지(`schoolName, schoolType, schoolCategory, educationMode, region, address, countryCode`).

**Files:**
- Modify: `domain/entity/School.java`
- Modify: `domain/repository/SchoolRepository.java`
- Modify: `service/SchoolService.java`
- Modify: `service/SchoolImportService.java`
- Modify: `service/SchoolImportParser.java`
- Modify: `controller/AdminSchoolController.java`
- Modify: `dto/request/SchoolCreateRequest.java`, `SchoolUpdateRequest.java`, `SchoolImportRowRequest.java`
- Modify: `dto/response/SchoolResponse.java`
- Modify: `exception/InvalidSchoolException.java`
- Test: `SchoolControllerTest`, `SchoolImportControllerTest`, School 서비스/Import 테스트

- [ ] **Step 1: 엔티티** — `School.java`

- `@Table`에서 `uniqueConstraints = { @UniqueConstraint(name = "uk_school_code", columnNames = {"school_code"}) },` 줄 제거(인덱스는 유지).
- `schoolCode` 필드 제거:
```java
    @Column(name = "school_code", length = 100)
    private String schoolCode;
```
- `schoolType` 필드 바로 뒤에 `schoolCategory` 추가:
```java
    @Column(name = "school_category", length = 50)
    private String schoolCategory;
```
- private 생성자: `String schoolCode,` 파라미터 제거, `this.schoolCode = normalize(schoolCode);` 제거. `String schoolType,` 뒤에 `String schoolCategory,` 추가, `this.schoolType = normalize(schoolType);` 뒤에 `this.schoolCategory = normalize(schoolCategory);` 추가.
- `create(...)`: `String schoolCode,` 파라미터 제거, `String schoolType,` 뒤에 `String schoolCategory,` 추가. 새 시그니처:
```java
    public static School create(
            String schoolName,
            String schoolType,
            String schoolCategory,
            String educationMode,
            String region,
            String address,
            String countryCode,
            Boolean active
    ) {
        return new School(schoolName, schoolType, schoolCategory, educationMode, region, address, countryCode,
                active == null || active);
    }
```
  (private 생성자 인자 순서도 동일하게 `schoolName, schoolType, schoolCategory, educationMode, region, address, countryCode, active`로 맞춘다.)
- `update(...)`: `String schoolType,` 뒤에 `String schoolCategory,` 파라미터 추가, 본문 `this.schoolType = normalize(schoolType);` 뒤에 `this.schoolCategory = normalize(schoolCategory);` 추가.
- 클래스 javadoc에서 schoolCode 식별/dedup 서술 제거, "식별/중복제거는 `(schoolName, schoolType, region)` fallback" 으로 수정.

- [ ] **Step 2: 리포지토리** — `SchoolRepository.java`

아래 두 메서드 제거:
```java
    boolean existsBySchoolCode(String schoolCode);

    Optional<School> findBySchoolCode(String schoolCode);
```
사용하지 않게 된 `import java.util.Optional;`가 다른 곳에서 안 쓰이면 함께 제거(컴파일 기준).

- [ ] **Step 3: DTO**

`SchoolCreateRequest.java` — `schoolCode` 필드 제거, `schoolType` 뒤에 `schoolCategory` 추가:
```java
public record SchoolCreateRequest(
        @NotBlank(message = "schoolName은(는) 필수입니다.")
        @Size(max = 200, message = "schoolName은(는) 200자 이하여야 합니다.")
        String schoolName,

        @Size(max = 50, message = "schoolType은(는) 50자 이하여야 합니다.")
        String schoolType,

        @Size(max = 50, message = "schoolCategory은(는) 50자 이하여야 합니다.")
        String schoolCategory,

        @Size(max = 50, message = "educationMode은(는) 50자 이하여야 합니다.")
        String educationMode,

        @Size(max = 100, message = "region은(는) 100자 이하여야 합니다.")
        String region,

        @Size(max = 500, message = "address은(는) 500자 이하여야 합니다.")
        String address,

        @Size(max = 10, message = "countryCode은(는) 10자 이하여야 합니다.")
        String countryCode,

        Boolean active
) {
}
```
(javadoc의 schoolCode 문구 제거.)

`SchoolUpdateRequest.java` — `schoolType` 뒤에 동일한 `schoolCategory`(@Size max 50) 컴포넌트 추가.

`SchoolImportRowRequest.java` — `schoolCode` 제거, `schoolType` 뒤에 `String schoolCategory,` 추가. 새 순서:
```java
public record SchoolImportRowRequest(
        int rowNumber,
        String schoolName,
        String schoolType,
        String schoolCategory,
        String educationMode,
        String region,
        String address,
        String countryCode,
        boolean formulaCellPresent
) {
}
```

`SchoolResponse.java` — `schoolCode` 제거, `schoolType` 뒤에 `String schoolCategory,` 추가, `from()`에서 `school.getSchoolCode()` 제거하고 `school.getSchoolType(),` 뒤에 `school.getSchoolCategory(),` 추가:
```java
public record SchoolResponse(
        Long id,
        String schoolName,
        String schoolType,
        String schoolCategory,
        String educationMode,
        String region,
        String address,
        String countryCode,
        boolean active
) {
    public static SchoolResponse from(School school) {
        return new SchoolResponse(
                school.getId(),
                school.getSchoolName(),
                school.getSchoolType(),
                school.getSchoolCategory(),
                school.getEducationMode(),
                school.getRegion(),
                school.getAddress(),
                school.getCountryCode(),
                school.isActive()
        );
    }
}
```

- [ ] **Step 4: SchoolService**

`create`에서 schoolCode 중복 선검사 + DataIntegrityViolationException 처리를 제거하고 schoolCategory를 전달한다(유일 제약이 사라져 unique 위반 catch 불필요):
```java
    @Transactional
    public SchoolResponse create(SchoolCreateRequest request) {
        School saved = schoolRepository.save(School.create(
                request.schoolName(),
                request.schoolType(),
                request.schoolCategory(),
                request.educationMode(),
                request.region(),
                request.address(),
                request.countryCode(),
                request.active()
        ));
        return SchoolResponse.from(saved);
    }
```
사용하지 않게 된 import(`DataIntegrityViolationException`, `blankToNull`가 다른 곳에서 안 쓰이면 그 헬퍼/임포트)는 컴파일 기준으로 정리. `update`는 `school.update(...)` 호출에 `request.schoolType()` 뒤로 `request.schoolCategory()`를 추가(새 `update` 시그니처에 맞춤). 클래스 javadoc의 schoolCode 문구 제거.

- [ ] **Step 5: SchoolImportService**

- `findExisting`에서 schoolCode 분기 제거 → fallback만:
```java
    private ExistingMatch findExisting(String schoolName, String schoolType, String region) {
        // 기존 fallback(이름+유형+지역) 매칭 로직만 유지
        ...
    }
```
  호출부 `findExisting(schoolCode, schoolName, schoolType, region)` → `findExisting(schoolName, schoolType, region)`.
- 행 파싱: `String schoolCode = blankToNull(row.schoolCode());` 제거, `String schoolCategory = blankToNull(row.schoolCategory());` 추가.
- insert 시 `School.create(...)`를 새 시그니처로:
```java
                schoolRepository.save(School.create(
                        schoolName, schoolType, schoolCategory, educationMode, region, address, countryCode, true));
```
- 기존 매칭 update 분기에도 `schoolCategory`를 전달(새 `update` 시그니처).
- 행 검증: `validateMax(errors, "schoolCode", row.schoolCode(), 100);` 제거, `validateMax(errors, "schoolCategory", row.schoolCategory(), 50);` 추가.
- 클래스 javadoc upsert 키 서술을 "fallback `(schoolName, schoolType, region)`"만으로 수정.

- [ ] **Step 6: SchoolImportParser**

HEADERS/매핑 변경:
```java
    public static final List<String> HEADERS = List.of(
            "schoolName", "schoolType", "schoolCategory", "educationMode", "region", "address", "countryCode");

    private static final int COLUMN_COUNT = 7;
```
`toRow`의 생성자 호출을 새 필드 순서로:
```java
        return new SchoolImportRowRequest(
                row.getRowNum() + 1,
                values[0], values[1], values[2], values[3], values[4], values[5], values[6],
                formula);
```
(values[0]=schoolName, [1]=schoolType, [2]=schoolCategory, [3]=educationMode, [4]=region, [5]=address, [6]=countryCode)

- [ ] **Step 7: 컨트롤러/예외 javadoc**

`AdminSchoolController.java` 클래스 javadoc에서 "schoolCode 는 생성 후 불변(수정 요청에 미포함)이다." 문구 제거.
`InvalidSchoolException.java` javadoc에서 "schoolCode 중복" 예시 문구 제거.

- [ ] **Step 8: 테스트 정리 + 실행**

- `SchoolControllerTest`/School 서비스 테스트: `SchoolCreateRequest`/`SchoolResponse`에서 schoolCode 제거, schoolCategory 추가에 맞춰 생성/단언 수정. schoolCode 중복 409/400 케이스 삭제. "schoolCategory 저장·조회" 케이스 추가.
- `SchoolImportControllerTest`: xlsx 헤더 생성부를 새 HEADERS(7개, schoolCode 없음 / schoolCategory 포함)로 수정. 행 데이터 컬럼 순서 맞춤. schoolCode 기반 dedup 케이스 → `(schoolName, schoolType, region)` fallback dedup 케이스로 수정/추가.

Run:
```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.SchoolControllerTest" --tests "com.shinyoung.recruit.controller.SchoolImportControllerTest" --no-daemon
```
Expected: 컴파일 성공 + PASS. (School 서비스/Import 전용 테스트 클래스가 있으면 `--tests`에 추가.)

- [ ] **Step 9: 커밋**
```powershell
cd C:/Users/roehf/Desktop/recruit/recruit_back/recruit_backend
git add -A
git commit -m @'
refactor(school): School 마스터 schoolCode 제거 + schoolCategory 추가

import/생성 중복제거를 (schoolName, schoolType, region) fallback 으로 단일화.
xlsx 컬럼 7개 유지(schoolCategory 포함), DTO/파서/서비스 반영.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
'@
```

---

## Task 4: 계약 기록 + 문서 갱신

코드 변경 후 계약/문서를 갱신한다. (api-contract.md는 recruit/ 저장소, 백엔드 docs는 recruit_back 저장소)

**Files:**
- Modify: `recruit/api-contract.md` (recruit 저장소)
- Modify: `recruit_back/recruit_backend/docs/adr/0004-school-optional-application-level-link.md`
- Modify: `recruit_back/recruit_backend/docs/codex/07-implementation-history.md`
- Modify: 영향받는 기존 구현 문서(`docs/codex/implementation/phase-03c-2-application-career.md`, `phase-08b-school.md`, `phase-08c-school-import-education-link.md`)의 해당 부분

- [ ] **Step 1: api-contract.md 기록** — `recruit/api-contract.md`의 "## 화면 계약" 아래에 추가

```markdown
## 화면: 지원자 경력 (ApplicationCareer)
- 프론트: (후속) `src/api/applicationApi.ts` 의 경력 관련
- 백엔드: `com.shinyoung.recruit.controller.ApplicationCareerController`

### GET/POST 경력  🔴 백엔드 구현됨 / 프론트 미반영
- 변경: 응답·요청에서 `careerType` 제거. 경력 행에 `promotionDate`(진급일, nullable) 추가.
- 경력은 선택(0개 허용). 신입/경력 타입 개념 폐지.

## 화면: 관리자 학교 마스터 (School)
- 백엔드: `com.shinyoung.recruit.controller.AdminSchoolController` / `SchoolController`

### School 생성·수정·조회·xlsx import  🔴 백엔드 구현됨 / 프론트 미반영
- 변경: `schoolCode` 제거, `schoolCategory` 추가. xlsx 컬럼: schoolName, schoolType, schoolCategory, educationMode, region, address, countryCode.
- 중복제거: `(schoolName, schoolType, region)` fallback.
```

- [ ] **Step 2: 백엔드 문서 갱신**

- `docs/adr/0004-...md`: schoolCode 기반 식별/dedup 서술을 "schoolCode 제거, `(schoolName, schoolType, region)` fallback 으로 단일화"로 개정(개정 이력 한 줄 추가).
- `docs/codex/07-implementation-history.md`: 항목 추가 — "careerType/ApplicationCareerProfile 제거 + promotionDate 추가; School schoolCode 제거 + schoolCategory 추가 (2026-06-23)".
- `phase-03c-2-application-career.md`: careerType 서술 제거, promotionDate·경력 선택화 반영. `phase-08b-school.md`/`phase-08c-...md`: schoolCode 제거 + schoolCategory + fallback dedup 반영.

- [ ] **Step 3: 커밋 (저장소별로)**
```powershell
cd C:/Users/roehf/Desktop/recruit/recruit_back/recruit_backend
git add -A
git commit -m @'
docs: 경력/학교 변경 반영 (ADR 0004 개정, 구현 이력/Phase 문서)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
'@
cd C:/Users/roehf/Desktop/recruit
git add api-contract.md
git commit -m @'
docs(contract): 경력/학교 백엔드 계약 변경 기록 (프론트 후속)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
'@
```

---

## Self-Review

**1. Spec coverage:**
- 스펙 §4 경력 삭제(3)/수정(14) → Task 1(promotionDate 추가) + Task 2(careerType 제거) ✓
- 스펙 §5 학교 수정(11) → Task 3 ✓
- 스펙 §6 검증(수정 테스트만, AES 키) → 각 Task의 `--tests` 선택 실행 ✓
- 스펙 §7 계약 기록 + 프론트 영향 → Task 4 Step 1 ✓
- 스펙 §8 문서(마크다운만) → Task 4 Step 2 ✓
- 스펙 §4.3 promotionDate PII NULLIFY → Task 1 Step 6 ✓
- 스펙 §5.2 xlsx 컬럼 순서 / 생성 dedup 제거 → Task 3 Step 4·6 ✓

**2. Placeholder scan:** 코드 블록은 실제 내용. "컴파일러가 unused로 알려주는 것만 정리"는 placeholder가 아니라 컴파일 기준의 결정 절차(삭제 대상 enum/엔티티가 광범위 참조되어 unused 범위가 파일별로 달라지므로 컴파일로 확정하는 게 정확하다). 완료판정 테스트 클래스명은 "있으면 --tests 추가"로 명시.

**3. Type/시그니처 일관성:**
- `School.create(schoolName, schoolType, schoolCategory, educationMode, region, address, countryCode, active)` — Task 3 Step 1·4·5 전부 동일 순서 ✓
- `ApplicationCareer.create(... endDate, promotionDate, currentlyEmployed ...)` — Task 1 Step 3·5 동일 ✓
- promotionDate 위치: 모든 경력 DTO/엔티티에서 endDate 뒤 ✓
- schoolCategory 위치: 모든 학교 DTO/엔티티에서 schoolType 뒤 ✓
- `CareerReplaceRequest(List<CareerRequest> careers)` / `CareerResponse(List<CareerItemResponse> careers)` — Task 2 Step 1과 Step 2 getCareerResponse 일치 ✓
