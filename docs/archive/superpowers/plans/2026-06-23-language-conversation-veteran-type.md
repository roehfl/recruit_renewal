# 어학 scoreOrGrade·conversationalAbility + 기본정보 veteranType 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 어학 섹션의 `score`/`grade`를 단일 `scoreOrGrade`로 합치고 `conversationalAbility`(공통코드 LANGUAGE_CONVERSATION)를 추가하며, 기본정보에 `veteranStatus==SUBJECT`일 때 입력하는 평문 `veteranType` 필드를 추가한다.

**Architecture:** Spring Boot 백엔드 전용 수직 슬라이스. 기존 선례를 미러링한다 — `scoreOrGrade`는 `ApplicationCertificate.scoreOrGrade`(평문 nullable String), `conversationalAbility`는 `SCHOOL_TYPE`(검증 미결합 코드 문자열), `veteranType`은 `disabilityType`의 조건부 검증 구조(단, 공통코드/암호화 없음). 새 엔티티/공통코드 시드 없음.

**Tech Stack:** Java 17, Spring Boot, JPA(H2 dev), Gradle Wrapper, JUnit5 + AssertJ + MockMvc.

**작업 경로:** 백엔드 = `recruit_back/recruit_backend/`(자체 git). 계약 문서 = `recruit/api-contract.md`(recruit/ git). 모든 gradle/test/commit는 백엔드 루트에서 수행.

**테스트 실행 규약(하네스 §5):** 전체 리그레션 금지, 수정 관련 클래스만 `--tests`로 선택 실행. 명령은 `recruit_back/recruit_backend`에서:
```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "<FQCN>" --no-daemon
```
(키 `22791194512954214612461221261067`는 백엔드 CLAUDE.md §10의 로컬 예시 키. 테스트는 `crypto.aes.key` property도 자체 설정함.)

**핵심 사실(영향 범위, 사전 조사 확정):**
- `LanguageRequest`/`ApplicationLanguage.create`는 필드 −2/+2라 **인자 수 불변(8/9)** → 위치 인자 호출부는 대부분 그대로 컴파일됨(의미만 score/grade→scoreOrGrade/conversationalAbility로 이동). 단 **`ApplicationLanguage.getScore()/getGrade()` 접근자는 제거**되므로 그걸 읽는 테스트(PII purge 테스트)는 깨진다.
- `ApplicationBasicInfo.create`(16→17 인자)·`new BasicInfoSaveRequest`(15→16 인자)는 **모든 호출부가 컴파일 깨짐** → 전부 인자 삽입 필요.
- `AdminApplicationSectionServiceTest`/`AdminApplicationSectionControllerTest`는 `ApplicationLanguage.create`(9인자 유지)만 쓰고 `languageId`/`languageName`만 단언 → **수정 불필요, 실행만**. (`.score()` 단언은 StageResult 응답으로 어학과 무관.)

---

## File Structure

**소스 수정 (12):**
- 어학(6): `domain/entity/ApplicationLanguage.java`, `dto/request/LanguageRequest.java`, `dto/response/LanguageResponse.java`, `dto/response/AdminLanguageResponse.java`, `service/ApplicationLanguageService.java`, `service/ApplicationPdfService.java`
- 기본정보(5): `domain/entity/ApplicationBasicInfo.java`, `dto/request/BasicInfoSaveRequest.java`, `dto/response/BasicInfoResponse.java`, `dto/response/AdminBasicInfoResponse.java`, `service/ApplicationBasicInfoService.java`
- 공통(1): `domain/repository/ApplicationPiiPurgeRepository.java` (`purgeLanguages` + `purgeBasicInfo`)

**테스트 수정:**
- 어학: `service/ApplicationLanguageServiceTest`(신규 케이스 추가), `controller/ApplicationLanguageControllerTest`(JSON), `service/ApplicationPiiPurgeServiceTest`(어학 접근자)
- 기본정보: `support/BasicInfoTestSupport`, `domain/repository/ApplicationBasicInfoEncryptionTest`, `service/ApplicationPdfServiceTest`(create×2), `service/ApplicationDashboardServiceTest`(create), `service/ApplicationPiiPurgeServiceTest`(basic-info create+단언), `service/ApplicationBasicInfoServiceTest`(create×8 + 신규 케이스), `controller/ApplicationBasicInfoControllerTest`(신규 케이스)
- 수정 불필요(실행만): `service/AdminApplicationSectionServiceTest`, `controller/AdminApplicationSectionControllerTest`, `service/ApplicationSubmitValidatorBasicInfoTest`(BasicInfoTestSupport 의존만)

**문서:** `recruit/api-contract.md`, `recruit_back/.../docs/codex/implementation/phase-09-pii-field-inventory.md`, `recruit_back/.../docs/codex/07-implementation-history.md`

---

## Task 0: 백엔드 feature 브랜치 생성

**Files:** (git only)

- [ ] **Step 1: 현재 상태 확인**

Run (from `recruit_back/recruit_backend`):
```bash
git -C recruit_back/recruit_backend status --short
git -C recruit_back/recruit_backend branch --show-current
```
Expected: 클린 또는 무관한 변경만. 현재 브랜치 확인.

- [ ] **Step 2: feature 브랜치 생성**

```bash
git -C recruit_back/recruit_backend checkout -b feature/language-veteran-fields
```
Expected: `Switched to a new branch 'feature/language-veteran-fields'`

---

## Task 1: 어학 — score/grade를 scoreOrGrade + conversationalAbility로 교체

**Files:**
- Modify: `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/domain/entity/ApplicationLanguage.java`
- Modify: `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/dto/request/LanguageRequest.java`
- Modify: `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/dto/response/LanguageResponse.java`
- Modify: `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/dto/response/AdminLanguageResponse.java`
- Modify: `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/service/ApplicationLanguageService.java`
- Modify: `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/service/ApplicationPdfService.java`
- Modify: `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/domain/repository/ApplicationPiiPurgeRepository.java`
- Test: `recruit_back/recruit_backend/src/test/java/com/shinyoung/recruit/service/ApplicationPiiPurgeServiceTest.java`, `.../controller/ApplicationLanguageControllerTest.java`, `.../service/ApplicationLanguageServiceTest.java`

- [ ] **Step 1: 엔티티 필드 교체 — `ApplicationLanguage.java`**

`score`/`grade` 필드를 `scoreOrGrade`/`conversationalAbility`로 교체(같은 위치).

Replace:
```java
    private String score;

    private String grade;
```
With:
```java
    private String scoreOrGrade;

    private String conversationalAbility;
```

생성자 파라미터 교체 — replace:
```java
            String testName,
            String score,
            String grade,
            LocalDate examDate,
```
With (생성자·factory 두 곳 모두 동일 패턴, 각각 적용):
```java
            String testName,
            String scoreOrGrade,
            String conversationalAbility,
            LocalDate examDate,
```

생성자 본문 — replace:
```java
        this.score = score;
        this.grade = grade;
```
With:
```java
        this.scoreOrGrade = scoreOrGrade;
        this.conversationalAbility = conversationalAbility;
```

factory `create(...)`의 `new ApplicationLanguage(...)` 전달 인자 — replace:
```java
                testName,
                score,
                grade,
                examDate,
```
With:
```java
                testName,
                scoreOrGrade,
                conversationalAbility,
                examDate,
```

- [ ] **Step 2: 요청 DTO — `LanguageRequest.java`**

Replace:
```java
        String score,

        String grade,
```
With:
```java
        String scoreOrGrade,

        String conversationalAbility,
```

- [ ] **Step 3: 응답 DTO — `LanguageResponse.java`**

레코드 컴포넌트 replace:
```java
        String testName,
        String score,
        String grade,
        LocalDate examDate,
```
With:
```java
        String testName,
        String scoreOrGrade,
        String conversationalAbility,
        LocalDate examDate,
```

`from(...)` replace:
```java
                language.getTestName(),
                language.getScore(),
                language.getGrade(),
                language.getExamDate(),
```
With:
```java
                language.getTestName(),
                language.getScoreOrGrade(),
                language.getConversationalAbility(),
                language.getExamDate(),
```

- [ ] **Step 4: 관리자 응답 DTO — `AdminLanguageResponse.java`**

(LanguageResponse와 코드는 같지만 **다른 파일**이라 별도 적용.)

레코드 컴포넌트 replace:
```java
        String testName,
        String score,
        String grade,
        LocalDate examDate,
```
With:
```java
        String testName,
        String scoreOrGrade,
        String conversationalAbility,
        LocalDate examDate,
```

`from(...)` replace:
```java
                language.getTestName(),
                language.getScore(),
                language.getGrade(),
                language.getExamDate(),
```
With:
```java
                language.getTestName(),
                language.getScoreOrGrade(),
                language.getConversationalAbility(),
                language.getExamDate(),
```

- [ ] **Step 5: 서비스 매핑 — `ApplicationLanguageService.java`**

`toLanguage(...)` replace:
```java
                request.testName(),
                request.score(),
                request.grade(),
                request.examDate(),
```
With:
```java
                request.testName(),
                request.scoreOrGrade(),
                request.conversationalAbility(),
                request.examDate(),
```
(검증 로직 변경 없음 — 둘 다 선택값.)

- [ ] **Step 6: PDF 렌더 — `ApplicationPdfService.java` `languageSection()`**

Replace:
```java
                    field("점수", l.score()),
                    field("등급", l.grade()),
```
With:
```java
                    field("점수/등급", l.scoreOrGrade()),
                    field("회화능력", l.conversationalAbility()),
```

- [ ] **Step 7: 파기 쿼리 — `ApplicationPiiPurgeRepository.java` `purgeLanguages`**

Replace:
```java
            update ApplicationLanguage l
            set l.languageName = '__PURGED__', l.testName = '__PURGED__', l.examDate = null, l.score = null,
                l.grade = null, l.expiredDate = null, l.issuingOrganization = null,
                l.createdBy = null, l.updatedBy = null
            where l.jobApplication.id = :applicationId""")
```
With:
```java
            update ApplicationLanguage l
            set l.languageName = '__PURGED__', l.testName = '__PURGED__', l.examDate = null,
                l.scoreOrGrade = null, l.conversationalAbility = null,
                l.expiredDate = null, l.issuingOrganization = null,
                l.createdBy = null, l.updatedBy = null
            where l.jobApplication.id = :applicationId""")
```

- [ ] **Step 8: PII purge 테스트 — 어학 접근자/픽스처 수정 (`ApplicationPiiPurgeServiceTest.java`)**

어학 픽스처 값 라벨 정리 — replace:
```java
        languageRepository.save(ApplicationLanguage.create(
                application, "영어", "TOEIC", "900", "A", LocalDate.of(2024, 1, 1),
                LocalDate.of(2026, 1, 1), "ETS", 1));
```
With:
```java
        languageRepository.save(ApplicationLanguage.create(
                application, "영어", "TOEIC", "900", "상", LocalDate.of(2024, 1, 1),
                LocalDate.of(2026, 1, 1), "ETS", 1));
```

어학 검증부 — replace:
```java
        assertThat(language.getExamDate()).isNull();
        assertThat(language.getScore()).isNull();
        assertThat(language.getGrade()).isNull();
        assertThat(language.getExpiredDate()).isNull();
```
With:
```java
        assertThat(language.getExamDate()).isNull();
        assertThat(language.getScoreOrGrade()).isNull();
        assertThat(language.getConversationalAbility()).isNull();
        assertThat(language.getExpiredDate()).isNull();
```

- [ ] **Step 9: 컨트롤러 테스트 JSON 갱신 (`ApplicationLanguageControllerTest.java`)**

`validLanguageJson()` replace:
```java
                      "languageName": "English",
                      "testName": "TOEIC",
                      "score": "900",
                      "grade": null,
                      "examDate": "2024-01-01",
```
With:
```java
                      "languageName": "English",
                      "testName": "TOEIC",
                      "scoreOrGrade": "900",
                      "conversationalAbility": "상",
                      "examDate": "2024-01-01",
```

- [ ] **Step 10: 서비스 테스트 — 신규 라운드트립 케이스 추가 (`ApplicationLanguageServiceTest.java`)**

`replace_languages_success_and_get_success()` 메서드 바로 위(혹은 아래)에 추가:
```java
    @Test
    void replace_persists_score_or_grade_and_conversational_ability() {
        Applicant applicant = createApplicant("language-fields", "Language Fields");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));

        List<LanguageResponse> responses = applicationLanguageService.replaceLanguages(
                applicant.getId(),
                applicationId,
                new LanguageReplaceRequest(List.of(new LanguageRequest(
                        "English", "TOEIC", "950", "BUSINESS",
                        LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1), "ETS", 0))));

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).scoreOrGrade()).isEqualTo("950");
        assertThat(responses.get(0).conversationalAbility()).isEqualTo("BUSINESS");
    }
```
(기존 인라인 `new LanguageRequest(..., "900", null, ...)` 호출과 `language(...)` 헬퍼는 위치 인자가 그대로 컴파일됨 — 수정 불필요. 위치 3·4는 이제 scoreOrGrade·conversationalAbility.)

- [ ] **Step 11: 어학 관련 테스트 실행**

Run (from `recruit_back/recruit_backend`):
```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicationLanguageServiceTest" --tests "com.shinyoung.recruit.controller.ApplicationLanguageControllerTest" --tests "com.shinyoung.recruit.service.ApplicationPiiPurgeServiceTest" --tests "com.shinyoung.recruit.service.AdminApplicationSectionServiceTest" --tests "com.shinyoung.recruit.controller.AdminApplicationSectionControllerTest" --tests "com.shinyoung.recruit.service.ApplicationPdfServiceTest" --no-daemon
```
Expected: BUILD SUCCESSFUL, 6개 클래스 통과. 실패 시 컴파일 오류(접근자 잔존) 우선 확인.

- [ ] **Step 12: 커밋**

```bash
git -C recruit_back/recruit_backend add -A
git -C recruit_back/recruit_backend commit -m "refactor(language): score/grade를 scoreOrGrade로 합치고 conversationalAbility 추가"
```

---

## Task 2: 기본정보 — veteranType 필드 배선 (검증 없이 컴파일·기존 그린)

**Files:**
- Modify: `.../domain/entity/ApplicationBasicInfo.java`
- Modify: `.../dto/request/BasicInfoSaveRequest.java`
- Modify: `.../dto/response/BasicInfoResponse.java`
- Modify: `.../dto/response/AdminBasicInfoResponse.java`
- Modify: `.../service/ApplicationBasicInfoService.java`
- Modify: `.../domain/repository/ApplicationPiiPurgeRepository.java` (`purgeBasicInfo`)
- Test: `support/BasicInfoTestSupport`, `domain/repository/ApplicationBasicInfoEncryptionTest`, `service/ApplicationPdfServiceTest`, `service/ApplicationDashboardServiceTest`, `service/ApplicationPiiPurgeServiceTest`, `service/ApplicationBasicInfoServiceTest`

- [ ] **Step 1: 엔티티 필드 추가 — `ApplicationBasicInfo.java`**

`veteranStatus`와 `disabilityStatus` 사이에 평문 컬럼 추가. Replace:
```java
    @Enumerated(EnumType.STRING)
    private VeteranStatus veteranStatus;

    @Enumerated(EnumType.STRING)
    private DisabilityStatus disabilityStatus;
```
With:
```java
    @Enumerated(EnumType.STRING)
    private VeteranStatus veteranStatus;

    @Column(length = 200)
    private String veteranType;

    @Enumerated(EnumType.STRING)
    private DisabilityStatus disabilityStatus;
```

- [ ] **Step 2: 생성자/`create`/`update`에 `veteranType` 추가 — `ApplicationBasicInfo.java`**

private 생성자 전체를 replace:
```java
    private ApplicationBasicInfo(
            JobApplication jobApplication,
            String nameKorean,
            String nameEnglish,
            NationalityType nationalityType,
            String countryCode,
            LocalDate birthDate,
            String mobilePhone,
            String emergencyPhone,
            String email,
            VeteranStatus veteranStatus,
            DisabilityStatus disabilityStatus,
            String disabilityGradeCode,
            String disabilityTypeCode,
            String zipCode,
            String addressBasic,
            String addressDetail
    ) {
        this.jobApplication = jobApplication;
        this.nameKorean = nameKorean;
        this.nameEnglish = nameEnglish;
        this.nationalityType = nationalityType;
        this.countryCode = countryCode;
        this.birthDate = birthDate;
        this.mobilePhone = mobilePhone;
        this.emergencyPhone = emergencyPhone;
        this.email = email;
        this.veteranStatus = veteranStatus;
        this.disabilityStatus = disabilityStatus;
        this.disabilityGradeCode = disabilityGradeCode;
        this.disabilityTypeCode = disabilityTypeCode;
        this.zipCode = zipCode;
        this.addressBasic = addressBasic;
        this.addressDetail = addressDetail;
    }
```
With:
```java
    private ApplicationBasicInfo(
            JobApplication jobApplication,
            String nameKorean,
            String nameEnglish,
            NationalityType nationalityType,
            String countryCode,
            LocalDate birthDate,
            String mobilePhone,
            String emergencyPhone,
            String email,
            VeteranStatus veteranStatus,
            String veteranType,
            DisabilityStatus disabilityStatus,
            String disabilityGradeCode,
            String disabilityTypeCode,
            String zipCode,
            String addressBasic,
            String addressDetail
    ) {
        this.jobApplication = jobApplication;
        this.nameKorean = nameKorean;
        this.nameEnglish = nameEnglish;
        this.nationalityType = nationalityType;
        this.countryCode = countryCode;
        this.birthDate = birthDate;
        this.mobilePhone = mobilePhone;
        this.emergencyPhone = emergencyPhone;
        this.email = email;
        this.veteranStatus = veteranStatus;
        this.veteranType = veteranType;
        this.disabilityStatus = disabilityStatus;
        this.disabilityGradeCode = disabilityGradeCode;
        this.disabilityTypeCode = disabilityTypeCode;
        this.zipCode = zipCode;
        this.addressBasic = addressBasic;
        this.addressDetail = addressDetail;
    }
```

`create(...)` 전체를 replace:
```java
    public static ApplicationBasicInfo create(
            JobApplication jobApplication,
            String nameKorean,
            String nameEnglish,
            NationalityType nationalityType,
            String countryCode,
            LocalDate birthDate,
            String mobilePhone,
            String emergencyPhone,
            String email,
            VeteranStatus veteranStatus,
            DisabilityStatus disabilityStatus,
            String disabilityGradeCode,
            String disabilityTypeCode,
            String zipCode,
            String addressBasic,
            String addressDetail
    ) {
        return new ApplicationBasicInfo(
                jobApplication, nameKorean, nameEnglish, nationalityType, countryCode, birthDate,
                mobilePhone, emergencyPhone, email, veteranStatus, disabilityStatus,
                disabilityGradeCode, disabilityTypeCode, zipCode, addressBasic, addressDetail
        );
    }
```
With:
```java
    public static ApplicationBasicInfo create(
            JobApplication jobApplication,
            String nameKorean,
            String nameEnglish,
            NationalityType nationalityType,
            String countryCode,
            LocalDate birthDate,
            String mobilePhone,
            String emergencyPhone,
            String email,
            VeteranStatus veteranStatus,
            String veteranType,
            DisabilityStatus disabilityStatus,
            String disabilityGradeCode,
            String disabilityTypeCode,
            String zipCode,
            String addressBasic,
            String addressDetail
    ) {
        return new ApplicationBasicInfo(
                jobApplication, nameKorean, nameEnglish, nationalityType, countryCode, birthDate,
                mobilePhone, emergencyPhone, email, veteranStatus, veteranType, disabilityStatus,
                disabilityGradeCode, disabilityTypeCode, zipCode, addressBasic, addressDetail
        );
    }
```

`update(...)` 전체를 replace:
```java
    public void update(
            String nameKorean,
            String nameEnglish,
            NationalityType nationalityType,
            String countryCode,
            LocalDate birthDate,
            String mobilePhone,
            String emergencyPhone,
            String email,
            VeteranStatus veteranStatus,
            DisabilityStatus disabilityStatus,
            String disabilityGradeCode,
            String disabilityTypeCode,
            String zipCode,
            String addressBasic,
            String addressDetail
    ) {
        this.nameKorean = nameKorean;
        this.nameEnglish = nameEnglish;
        this.nationalityType = nationalityType;
        this.countryCode = countryCode;
        this.birthDate = birthDate;
        this.mobilePhone = mobilePhone;
        this.emergencyPhone = emergencyPhone;
        this.email = email;
        this.veteranStatus = veteranStatus;
        this.disabilityStatus = disabilityStatus;
        this.disabilityGradeCode = disabilityGradeCode;
        this.disabilityTypeCode = disabilityTypeCode;
        this.zipCode = zipCode;
        this.addressBasic = addressBasic;
        this.addressDetail = addressDetail;
    }
```
With:
```java
    public void update(
            String nameKorean,
            String nameEnglish,
            NationalityType nationalityType,
            String countryCode,
            LocalDate birthDate,
            String mobilePhone,
            String emergencyPhone,
            String email,
            VeteranStatus veteranStatus,
            String veteranType,
            DisabilityStatus disabilityStatus,
            String disabilityGradeCode,
            String disabilityTypeCode,
            String zipCode,
            String addressBasic,
            String addressDetail
    ) {
        this.nameKorean = nameKorean;
        this.nameEnglish = nameEnglish;
        this.nationalityType = nationalityType;
        this.countryCode = countryCode;
        this.birthDate = birthDate;
        this.mobilePhone = mobilePhone;
        this.emergencyPhone = emergencyPhone;
        this.email = email;
        this.veteranStatus = veteranStatus;
        this.veteranType = veteranType;
        this.disabilityStatus = disabilityStatus;
        this.disabilityGradeCode = disabilityGradeCode;
        this.disabilityTypeCode = disabilityTypeCode;
        this.zipCode = zipCode;
        this.addressBasic = addressBasic;
        this.addressDetail = addressDetail;
    }
```

- [ ] **Step 3: 요청 DTO — `BasicInfoSaveRequest.java`**

Replace:
```java
        @NotNull VeteranStatus veteranStatus,
        @NotNull DisabilityStatus disabilityStatus,
```
With:
```java
        @NotNull VeteranStatus veteranStatus,
        @Size(max = 100) String veteranType,
        @NotNull DisabilityStatus disabilityStatus,
```

- [ ] **Step 4: 응답 DTO — `BasicInfoResponse.java`**

레코드 컴포넌트 replace:
```java
        VeteranStatus veteranStatus,
        DisabilityStatus disabilityStatus,
```
With:
```java
        VeteranStatus veteranStatus,
        String veteranType,
        DisabilityStatus disabilityStatus,
```

`of(...)` replace:
```java
                basicInfo.getVeteranStatus(), basicInfo.getDisabilityStatus(),
```
With:
```java
                basicInfo.getVeteranStatus(), basicInfo.getVeteranType(), basicInfo.getDisabilityStatus(),
```

`prefill(...)` — `veteranStatus`/`disabilityStatus` 자리가 `null, null,`인 줄에 veteranType=null 1개 추가. Replace:
```java
                applicant.getPhoneNumber(), null, applicant.getEmail(),
                null, null,
                null, null,
                null, null, null);
```
With:
```java
                applicant.getPhoneNumber(), null, applicant.getEmail(),
                null, null, null,
                null, null,
                null, null, null);
```
(컴포넌트 순서: …email, **veteranStatus, veteranType, disabilityStatus**, disabilityGradeCode, disabilityTypeCode, zipCode, addressBasic, addressDetail.)

- [ ] **Step 5: 관리자 응답 DTO — `AdminBasicInfoResponse.java`**

레코드 컴포넌트 replace:
```java
        VeteranStatus veteranStatus,
        DisabilityStatus disabilityStatus,
```
With:
```java
        VeteranStatus veteranStatus,
        String veteranType,
        DisabilityStatus disabilityStatus,
```

`from(...)` replace:
```java
                basicInfo.getVeteranStatus(), basicInfo.getDisabilityStatus(),
```
With:
```java
                basicInfo.getVeteranStatus(), basicInfo.getVeteranType(), basicInfo.getDisabilityStatus(),
```

- [ ] **Step 6: 서비스 매핑 — `ApplicationBasicInfoService.java`** (검증은 Task 3)

`saveBasicInfo`의 `basicInfo.update(...)` replace:
```java
                request.veteranStatus(), request.disabilityStatus(),
                request.disabilityGradeCode(), request.disabilityTypeCode(),
```
(이 패턴은 `update(...)`와 `toBasicInfo(...)` 두 곳에 동일하게 등장 — **둘 다** 아래로 교체)
With:
```java
                request.veteranStatus(), request.veteranType(), request.disabilityStatus(),
                request.disabilityGradeCode(), request.disabilityTypeCode(),
```

- [ ] **Step 7: 파기 쿼리 — `ApplicationPiiPurgeRepository.java` `purgeBasicInfo`**

Replace:
```java
                b.veteranStatus = null, b.disabilityStatus = null,
```
With:
```java
                b.veteranStatus = null, b.veteranType = null, b.disabilityStatus = null,
```

- [ ] **Step 8: 테스트 `ApplicationBasicInfo.create` 호출부 5파일 인자 삽입**

각 호출에서 `VeteranStatus.*` 인자 **바로 뒤**에 veteranType을 삽입한다.

(8a) `support/BasicInfoTestSupport.java` — replace:
```java
                VeteranStatus.NOT_SUBJECT, DisabilityStatus.NOT_SUBJECT,
                null, null, null, null, null));
```
With:
```java
                VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.NOT_SUBJECT,
                null, null, null, null, null));
```

(8b) `domain/repository/ApplicationBasicInfoEncryptionTest.java` — replace:
```java
                VeteranStatus.NOT_SUBJECT, DisabilityStatus.NOT_SUBJECT, null, null,
                "06236", "서울시 강남구", "101동 1001호"));
```
With:
```java
                VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.NOT_SUBJECT, null, null,
                "06236", "서울시 강남구", "101동 1001호"));
```

(8c) `service/ApplicationPdfServiceTest.java` — 첫 번째 호출(`"새이름"`) replace:
```java
                VeteranStatus.NOT_SUBJECT, DisabilityStatus.NOT_SUBJECT,
                null, null, null, null, null);
```
With:
```java
                VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.NOT_SUBJECT,
                null, null, null, null, null);
```
두 번째 호출(all-null `purgedBasicInfo`) replace:
```java
                null, null,
                null, null, null, null, null);
```
With:
```java
                null, null, null,
                null, null, null, null, null);
```

(8d) `service/ApplicationDashboardServiceTest.java` — `validBasicInfo()` replace:
```java
                VeteranStatus.NOT_SUBJECT, DisabilityStatus.NOT_SUBJECT,
                null, null, null, null, null
```
With:
```java
                VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.NOT_SUBJECT,
                null, null, null, null, null
```

(8e) `service/ApplicationPiiPurgeServiceTest.java` — 파기 검증을 위해 값 부여(SUBJECT+종류). Replace:
```java
        basicInfoRepository.save(ApplicationBasicInfo.create(
                application, "홍길동", "Hong", NationalityType.DOMESTIC, null,
                LocalDate.of(1995, 1, 1), "01012345678", "01099998888", "hong@example.com",
                VeteranStatus.NOT_SUBJECT, DisabilityStatus.NOT_SUBJECT, null, null,
                "06236", "서울시 강남구", "101동"));
```
With:
```java
        basicInfoRepository.save(ApplicationBasicInfo.create(
                application, "홍길동", "Hong", NationalityType.DOMESTIC, null,
                LocalDate.of(1995, 1, 1), "01012345678", "01099998888", "hong@example.com",
                VeteranStatus.SUBJECT, "국가유공자", DisabilityStatus.NOT_SUBJECT, null, null,
                "06236", "서울시 강남구", "101동"));
```
그리고 BasicInfo 검증부에 veteranType null 단언 추가 — replace:
```java
        assertThat(purgedBasicInfo.getVeteranStatus()).isNull();
        assertThat(purgedBasicInfo.getDisabilityStatus()).isNull();
```
With:
```java
        assertThat(purgedBasicInfo.getVeteranStatus()).isNull();
        assertThat(purgedBasicInfo.getVeteranType()).isNull();
        assertThat(purgedBasicInfo.getDisabilityStatus()).isNull();
```

- [ ] **Step 9: 테스트 `new BasicInfoSaveRequest` 호출부 8곳 인자 삽입 (`ApplicationBasicInfoServiceTest.java`)**

규칙: 각 호출의 `VeteranStatus.*` 인자 뒤에 veteranType을 삽입. **`VeteranStatus.SUBJECT`이면 `"국가유공자"`, `NOT_SUBJECT`이면 `null`.** (Task 3에서 SUBJECT는 종류 필수가 되므로 `save_is_upsert`의 SUBJECT 호출은 값이 있어야 한다.)

(9a) `save_is_upsert` 내부(SUBJECT) — replace:
```java
                        VeteranStatus.SUBJECT, DisabilityStatus.NOT_SUBJECT, null, null, null, null, null));
```
With:
```java
                        VeteranStatus.SUBJECT, "국가유공자", DisabilityStatus.NOT_SUBJECT, null, null, null, null, null));
```

(9b) `domestic_with_country_code_is_rejected`(NOT_SUBJECT) — replace:
```java
                        VeteranStatus.NOT_SUBJECT, DisabilityStatus.NOT_SUBJECT, null, null, null, null, null)))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void disability_subject_requires_active_grade_and_type() {
```
With:
```java
                        VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.NOT_SUBJECT, null, null, null, null, null)))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void disability_subject_requires_active_grade_and_type() {
```

(9c) `invalid_phone_format_is_rejected`(NOT_SUBJECT) — replace:
```java
                        LocalDate.of(1995, 1, 1), "010-abc-1234", null, "a@b.com",
                        VeteranStatus.NOT_SUBJECT, DisabilityStatus.NOT_SUBJECT, null, null, null, null, null)))
```
With:
```java
                        LocalDate.of(1995, 1, 1), "010-abc-1234", null, "a@b.com",
                        VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.NOT_SUBJECT, null, null, null, null, null)))
```

(9d) `disability_not_subject_with_codes_is_rejected`(NOT_SUBJECT, 장애코드 보유) — replace:
```java
                        VeteranStatus.NOT_SUBJECT, DisabilityStatus.NOT_SUBJECT, "G1", "T1", null, null, null)))
```
With:
```java
                        VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.NOT_SUBJECT, "G1", "T1", null, null, null)))
```

(9e) `domesticRequest()`(NOT_SUBJECT) — replace:
```java
                VeteranStatus.NOT_SUBJECT, DisabilityStatus.NOT_SUBJECT, null, null, "06236", "서울", "101호");
```
With:
```java
                VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.NOT_SUBJECT, null, null, "06236", "서울", "101호");
```

(9f) `foreignRequest(...)`(NOT_SUBJECT) — replace:
```java
                LocalDate.of(1995, 1, 1), "01012345678", null, "hong@example.com",
                VeteranStatus.NOT_SUBJECT, DisabilityStatus.NOT_SUBJECT, null, null, null, null, null);
    }

    private BasicInfoSaveRequest disabilityRequest(String grade, String type) {
```
With:
```java
                LocalDate.of(1995, 1, 1), "01012345678", null, "hong@example.com",
                VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.NOT_SUBJECT, null, null, null, null, null);
    }

    private BasicInfoSaveRequest disabilityRequest(String grade, String type) {
```

(9g) `disabilityRequest(...)`(NOT_SUBJECT, 장애 SUBJECT) — replace:
```java
                VeteranStatus.NOT_SUBJECT, DisabilityStatus.SUBJECT, grade, type, null, null, null);
```
With:
```java
                VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.SUBJECT, grade, type, null, null, null);
```

(9h) `ageRequest(...)`(NOT_SUBJECT) — replace:
```java
                birthDate, "01012345678", null, "hong@example.com",
                VeteranStatus.NOT_SUBJECT, DisabilityStatus.NOT_SUBJECT, null, null, null, null, null);
```
With:
```java
                birthDate, "01012345678", null, "hong@example.com",
                VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.NOT_SUBJECT, null, null, null, null, null);
```

- [ ] **Step 10: 기본정보 관련 테스트 실행 (검증 추가 전 그린 확인)**

Run (from `recruit_back/recruit_backend`):
```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicationBasicInfoServiceTest" --tests "com.shinyoung.recruit.controller.ApplicationBasicInfoControllerTest" --tests "com.shinyoung.recruit.domain.repository.ApplicationBasicInfoEncryptionTest" --tests "com.shinyoung.recruit.service.ApplicationDashboardServiceTest" --tests "com.shinyoung.recruit.service.ApplicationPdfServiceTest" --tests "com.shinyoung.recruit.service.ApplicationPiiPurgeServiceTest" --tests "com.shinyoung.recruit.service.ApplicationSubmitValidatorBasicInfoTest" --no-daemon
```
Expected: BUILD SUCCESSFUL, 7개 클래스 통과(veteranType=null 전파 + purge null 단언 포함).

- [ ] **Step 11: 커밋**

```bash
git -C recruit_back/recruit_backend add -A
git -C recruit_back/recruit_backend commit -m "feat(basic-info): veteranType 평문 필드 배선(엔티티/DTO/서비스/파기)"
```

---

## Task 3: 기본정보 — veteranType 조건부 검증 (TDD)

**Files:**
- Modify: `.../service/ApplicationBasicInfoService.java`
- Test: `service/ApplicationBasicInfoServiceTest.java`, `controller/ApplicationBasicInfoControllerTest.java`

- [ ] **Step 1: 실패하는 서비스 검증 테스트 추가 (`ApplicationBasicInfoServiceTest.java`)**

`disability_not_subject_with_codes_is_rejected()` 메서드 아래에 추가:
```java
    @Test
    void veteran_subject_requires_type_and_persists_it() {
        Applicant applicant = createApplicant("bi-veteran", "Veteran");
        Long applicationId = createApplication(applicant);

        // SUBJECT인데 종류 누락 → 거부
        assertThatThrownBy(() -> basicInfoService.saveBasicInfo(applicant.getId(), applicationId,
                new BasicInfoSaveRequest("홍길동", null, NationalityType.DOMESTIC, null,
                        LocalDate.of(1995, 1, 1), "01012345678", null, "hong@example.com",
                        VeteranStatus.SUBJECT, null, DisabilityStatus.NOT_SUBJECT, null, null, null, null, null)))
                .isInstanceOf(InvalidJobApplicationException.class);

        // SUBJECT + 종류 → 성공 + 평문 라운드트립
        BasicInfoResponse ok = basicInfoService.saveBasicInfo(applicant.getId(), applicationId,
                new BasicInfoSaveRequest("홍길동", null, NationalityType.DOMESTIC, null,
                        LocalDate.of(1995, 1, 1), "01012345678", null, "hong@example.com",
                        VeteranStatus.SUBJECT, "국가유공자", DisabilityStatus.NOT_SUBJECT, null, null, null, null, null));
        assertThat(ok.veteranType()).isEqualTo("국가유공자");
    }

    @Test
    void veteran_not_subject_with_type_is_rejected() {
        Applicant applicant = createApplicant("bi-veteran-forbidden", "VeteranForbidden");
        Long applicationId = createApplication(applicant);

        assertThatThrownBy(() -> basicInfoService.saveBasicInfo(applicant.getId(), applicationId,
                new BasicInfoSaveRequest("홍길동", null, NationalityType.DOMESTIC, null,
                        LocalDate.of(1995, 1, 1), "01012345678", null, "hong@example.com",
                        VeteranStatus.NOT_SUBJECT, "국가유공자", DisabilityStatus.NOT_SUBJECT, null, null, null, null, null)))
                .isInstanceOf(InvalidJobApplicationException.class);
    }
```

- [ ] **Step 2: 실행 → 실패 확인**

Run:
```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicationBasicInfoServiceTest" --no-daemon
```
Expected: FAIL — `veteran_subject_requires_type_and_persists_it`(SUBJECT+null이 통과되어 두 번째 저장에서 예외 미발생) 및 `veteran_not_subject_with_type_is_rejected`(예외 미발생). 검증 미구현 때문.

- [ ] **Step 3: 검증 구현 — `ApplicationBasicInfoService.java`**

import 추가(기존 `DisabilityStatus`/`NationalityType` import 옆):
```java
import com.shinyoung.recruit.enumeration.VeteranStatus;
```

`validateRequest`에 호출 추가 — replace:
```java
        validateNationality(request);
        validateDisability(request);
```
With:
```java
        validateNationality(request);
        validateVeteran(request);
        validateDisability(request);
```

`validateDisability(...)` 메서드 바로 위에 신규 메서드 추가:
```java
    private void validateVeteran(BasicInfoSaveRequest request) {
        if (request.veteranStatus() == VeteranStatus.SUBJECT) {
            if (isBlank(request.veteranType())) {
                throw new InvalidJobApplicationException("Veteran type is required for a veteran subject.");
            }
        } else if (!isBlank(request.veteranType())) {
            throw new InvalidJobApplicationException("Veteran type is not allowed when not a veteran subject.");
        }
    }
```

- [ ] **Step 4: 실행 → 통과 확인**

Run:
```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicationBasicInfoServiceTest" --no-daemon
```
Expected: BUILD SUCCESSFUL (신규 2 케이스 + 기존 케이스 통과).

- [ ] **Step 5: 컨트롤러 조건부 검증 테스트 추가 (`ApplicationBasicInfoControllerTest.java`)**

`save_with_missing_required_field_returns_bad_request()` 아래에 추가:
```java
    @Test
    void veteran_subject_without_type_returns_bad_request() throws Exception {
        Applicant applicant = createApplicant("bi-api-veteran", "Api Veteran");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        authenticate(applicant);

        mockMvc.perform(post("/api/applications/{applicationId}/basic-info", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nameKorean": "홍길동",
                                  "nationalityType": "DOMESTIC",
                                  "birthDate": "1995-01-01",
                                  "mobilePhone": "01012345678",
                                  "email": "hong@example.com",
                                  "veteranStatus": "SUBJECT",
                                  "disabilityStatus": "NOT_SUBJECT"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void veteran_subject_with_type_persists() throws Exception {
        Applicant applicant = createApplicant("bi-api-veteran-ok", "Api Veteran Ok");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        authenticate(applicant);

        mockMvc.perform(post("/api/applications/{applicationId}/basic-info", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nameKorean": "홍길동",
                                  "nationalityType": "DOMESTIC",
                                  "birthDate": "1995-01-01",
                                  "mobilePhone": "01012345678",
                                  "email": "hong@example.com",
                                  "veteranStatus": "SUBJECT",
                                  "veteranType": "국가유공자",
                                  "disabilityStatus": "NOT_SUBJECT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.persisted").value(true))
                .andExpect(jsonPath("$.data.veteranType").value("국가유공자"));
    }
```

- [ ] **Step 6: 실행 → 통과 확인**

Run:
```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicationBasicInfoServiceTest" --tests "com.shinyoung.recruit.controller.ApplicationBasicInfoControllerTest" --no-daemon
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: 커밋**

```bash
git -C recruit_back/recruit_backend add -A
git -C recruit_back/recruit_backend commit -m "feat(basic-info): veteranType 조건부 검증(SUBJECT 필수/NOT_SUBJECT 금지)"
```

---

## Task 4: 문서 — api-contract + PII 인벤토리 + 구현 이력 (경량 슬라이스)

**Files:**
- Modify: `recruit/api-contract.md` (recruit/ git)
- Modify: `recruit_back/recruit_backend/docs/codex/implementation/phase-09-pii-field-inventory.md`
- Modify: `recruit_back/recruit_backend/docs/codex/07-implementation-history.md`

- [ ] **Step 1: API 계약 — `recruit/api-contract.md`**

파일 맨 끝(학교 섹션 다음)에 추가:
```markdown

### 화면: 지원자 어학 (ApplicationLanguage)

- 프론트: (후속) `src/api`의 어학 관련 + 어학 입력 화면
- 백엔드: `com.shinyoung.recruit.controller.ApplicationLanguageController`

#### GET·POST `/api/applications/{applicationId}/languages`  🔴 백엔드 구현됨 / 프론트 미반영

- 변경(2026-06-23): 요청·응답에서 `score`,`grade` 제거. `scoreOrGrade`(선택), `conversationalAbility`(선택) 추가.
- 요청: `{ languages: [{ languageName, testName, scoreOrGrade, conversationalAbility, examDate, expiredDate, issuingOrganization, sortOrder }] }`
- 응답(200): `ApiResponse<[{ languageId, languageName, testName, scoreOrGrade, conversationalAbility, examDate, expiredDate, issuingOrganization, sortOrder }]>`
- `conversationalAbility`는 공통코드 그룹 `LANGUAGE_CONVERSATION` 코드 문자열(프론트가 CommonCode로 렌더, 백엔드 validation 미결합, 코드 시드 안 함).
- 관리자 조회 `GET /api/admin/applications/{id}/languages` 응답도 동일하게 `scoreOrGrade`/`conversationalAbility` 반영.

### 화면: 지원자 기본정보 — 보훈 (ApplicationBasicInfo)

- 프론트: (후속) 기본정보 입력 화면 보훈 영역
- 백엔드: `com.shinyoung.recruit.controller.ApplicationBasicInfoController`

#### GET·POST `/api/applications/{applicationId}/basic-info`  🔴 백엔드 구현됨 / 프론트 미반영

- 변경(2026-06-23): 요청·응답에 `veteranType`(문자열, 평문, 선택) 추가.
- 규칙: `veteranStatus=="SUBJECT"`면 `veteranType` 필수, `"NOT_SUBJECT"`면 비어 있어야 함(값 있으면 400).
- 자유 입력 String(공통코드/암호화 아님 — 보훈은 일반 PII). 관리자 조회 응답(`AdminBasicInfoResponse`)에도 `veteranType` 포함.
```

- [ ] **Step 2: PII 인벤토리 — `phase-09-pii-field-inventory.md`**

§5 어학 행 replace:
```markdown
| `ApplicationLanguage.score` / `grade` / `expiredDate` / `issuingOrganization` | true | NULLIFY | null |
```
With:
```markdown
| `ApplicationLanguage.scoreOrGrade` / `conversationalAbility` / `expiredDate` / `issuingOrganization` | true | NULLIFY | null (conversationalAbility는 LANGUAGE_CONVERSATION 코드값이나 행이 `__PURGED__`되어 보존 실익 없음) |
```

§10 기본정보 표의 `veteranStatus` 행 다음에 행 추가 — replace:
```markdown
| `ApplicationBasicInfo.veteranStatus` | true | - | NULLIFY | null (enum) |
```
With:
```markdown
| `ApplicationBasicInfo.veteranStatus` | true | - | NULLIFY | null (enum) |
| `ApplicationBasicInfo.veteranType` | true | - | NULLIFY | null (보훈 종류, 평문·일반 PII) |
```

§9 DDL 요약에 메모 1줄 추가(§9 "신규 컬럼" 항목 아래):
```markdown
**어학/보훈 변경(2026-06-23)**: `application_language` `score`/`grade` 컬럼 제거 + `score_or_grade`/`conversational_ability` 추가; `application_basic_info` `veteran_type` 추가. 개발 H2는 JPA ddl-auto, 운영 MariaDB는 ALTER(컬럼 drop/add) 필요. veteranType은 평문(암호화 아님).
```

- [ ] **Step 3: 구현 이력 — `07-implementation-history.md`**

`# 07. Implementation History` 헤더 바로 아래(가장 최근 항목 위)에 추가:
```markdown

## 2026-06-23 - 어학 scoreOrGrade·conversationalAbility / 기본정보 veteranType

- Date: 2026-06-23
- Work type: refactor + 소규모 기능 추가 (백엔드 전용 슬라이스, 브랜치 `feature/language-veteran-fields`).
- 어학(language):
  - `ApplicationLanguage.score`/`grade` 제거 → `scoreOrGrade`(평문 nullable String, `ApplicationCertificate`와 동일 패턴) 1필드로 합침.
  - `conversationalAbility`(평문 nullable String) 추가. 공통코드 그룹 `LANGUAGE_CONVERSATION` 코드값이나 백엔드 validation 미결합(SCHOOL_TYPE 선례), 코드 시드 안 함.
  - DTO(LanguageRequest/LanguageResponse/AdminLanguageResponse)·서비스 매핑·PDF "점수/등급"+"회화능력" 행·`purgeLanguages` NULLIFY 반영. 둘 다 선택값.
- 기본정보(basic info):
  - `ApplicationBasicInfo.veteranType`(평문 nullable String, `@Column(length=200)`) 추가 — 보훈은 일반 PII(민감정보 아님)라 암호화하지 않음(`veteranStatus` 평문 enum과 동일 취급).
  - 조건부 검증 `validateVeteran`: `veteranStatus==SUBJECT`면 종류 필수, `NOT_SUBJECT`면 값 금지(`validateDisability` 구조 미러, 공통코드 조회 제외).
  - BasicInfoSaveRequest/BasicInfoResponse/AdminBasicInfoResponse·서비스 create/update·`purgeBasicInfo` NULLIFY 반영.
- 계약: `recruit/api-contract.md`에 어학·기본정보(보훈) 섹션 기록(🔴 백엔드 구현됨/프론트 미반영). 프론트는 후속 슬라이스.
- Tests: 어학(LanguageService/LanguageController/PiiPurge/AdminSection*/Pdf) + 기본정보(BasicInfoService/BasicInfoController/Encryption/Dashboard/Pdf/PiiPurge/SubmitValidatorBasicInfo) scoped 통과. 전체 리그레션 미실행(하네스 §5).
```

- [ ] **Step 4: 어학 phase 문서 변경 주의 note — `phase-03c-3-application-certificate-language.md`**

(역사적 기록은 그대로 두고 상단에 supersession note만 추가 — 경량 슬라이스.) H1 제목 줄 바로 다음에 빈 줄 + note 추가. Replace:
```markdown
# Phase 03c-3 - Application Certificate + Language

## Phase 이름
```
With:
```markdown
# Phase 03c-3 - Application Certificate + Language

> **변경 주의(2026-06-23)**: 본 문서의 Language `score`/`grade` 서술은 이후 슬라이스에서 단일 `scoreOrGrade`로 통합되고 `conversationalAbility`(LANGUAGE_CONVERSATION 코드)가 추가되었다. 현재 계약/스키마는 `07-implementation-history.md`(2026-06-23 항목)와 `recruit/api-contract.md` 어학 섹션을 따른다.

## Phase 이름
```

- [ ] **Step 5: 기본정보 phase 문서 변경 주의 note — `phase-10-application-basic-info.md`**

H1 제목 줄 바로 다음에 빈 줄 + note 추가. Replace:
```markdown
# Phase 10 — ApplicationBasicInfo (지원자 기본정보 섹션)

- 작성일: 2026-06-12
```
With:
```markdown
# Phase 10 — ApplicationBasicInfo (지원자 기본정보 섹션)

> **변경 주의(2026-06-23)**: `veteranStatus==SUBJECT`일 때 입력하는 평문 `veteranType`(보훈 종류) 필드가 이후 슬라이스에서 추가되었다(조건부 검증: SUBJECT 필수 / NOT_SUBJECT 금지, 파기 NULLIFY 포함). 상세는 `07-implementation-history.md`(2026-06-23 항목) 참고.

- 작성일: 2026-06-12
```

- [ ] **Step 6: 백엔드 문서 커밋 (backend git)**

```bash
git -C recruit_back/recruit_backend add docs/codex/implementation/phase-09-pii-field-inventory.md docs/codex/07-implementation-history.md docs/codex/implementation/phase-03c-3-application-certificate-language.md docs/codex/implementation/phase-10-application-basic-info.md
git -C recruit_back/recruit_backend commit -m "docs: 어학/보훈 필드 변경 PII 인벤토리·구현 이력·phase note 갱신"
```

- [ ] **Step 7: 계약 문서 커밋 (recruit/ git)**

```bash
git -C . add api-contract.md
git -C . commit -m "docs(contract): 어학 scoreOrGrade·conversationalAbility / 기본정보 veteranType 기록 (프론트 후속)"
```

---

## 최종 검증 (선택, 사용자 요청 시)

전체 빌드는 명시 요청 시에만(하네스 §5). 요청 시:
```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --no-daemon
```
주의: `ApplicationSubmitValidatorTest`는 본 변경 이전부터 basicInfo @Mock 누락으로 NPE 실패하던 기존 이슈(구현 이력 2026-06-23 career/school 항목 참고)이며 본 슬라이스 범위 밖이다.

## 남은 이슈 / 다음 단계

- 프론트(`recruit_front`) 어학·기본정보 화면 동기화 — 후속 슬라이스(`scoreOrGrade`/`conversationalAbility` 렌더, `LANGUAGE_CONVERSATION` 드롭다운, `veteranType` 조건부 입력).
- `LANGUAGE_CONVERSATION` 공통코드 행은 운영 admin이 런타임 등록(시드 없음).
- feature 브랜치 `feature/language-veteran-fields` 병합은 별도(사용자 판단).
