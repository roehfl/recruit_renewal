# ApplicationBasicInfo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 지원서 작성 중 입력하는 지원자 기본정보를 지원서별 1:1 스냅샷 섹션으로 저장/조회하고, 제출 검증·완성도·관리자 조회·PDF·파기까지 일관 연결한다.

**Architecture:** 기존 `ApplicationMilitary` 섹션 패턴(`@OneToOne(JobApplication)` + service upsert + `/applications/{id}/...` 컨트롤러)을 그대로 따른다. 문자열 PII는 `AesAttributeConverter`로 at-rest 암호화하고, DB NOT NULL은 FK만 두며(필수성은 검증으로), 파기 시 전 개인정보 컬럼을 null로 지운다. 기본정보는 폼 설정과 무관한 항상 필수 섹션이라 제출 검증·완성도 체커에 무조건 편입된다.

**Tech Stack:** Java 17, Spring Boot, Spring Data JPA(Hibernate), H2(test), JUnit5 + AssertJ + MockMvc, Lombok, Gradle Wrapper(`gradlew.bat`).

**기준 문서:** `docs/superpowers/specs/2026-06-12-application-basic-info-design.md`, `instruction.md`

---

## 파일 구조 (생성/수정 맵)

**신규 (production):**
- `enumeration/NationalityType.java`, `VeteranStatus.java`, `DisabilityStatus.java` — 기본정보 enum 3종
- `domain/entity/ApplicationBasicInfo.java` — 1:1 섹션 엔티티(암호화 + 컬럼 길이)
- `domain/repository/ApplicationBasicInfoRepository.java`
- `dto/request/BasicInfoSaveRequest.java`
- `dto/response/BasicInfoResponse.java`(prefill 지원), `dto/response/AdminBasicInfoResponse.java`
- `service/ApplicationBasicInfoService.java`
- `controller/ApplicationBasicInfoController.java`

**수정 (production):**
- `domain/repository/CommonCodeRepository.java` — `existsByGroupCodeAndCodeAndActiveTrue`
- `service/ApplicationSubmitValidator.java` — `validateBasicInfo` 최우선 호출
- `service/ApplicationCompletionReadChecker.java` — BASIC_INFO 항상 필수 그룹
- `service/AdminApplicationSectionService.java` + `controller/AdminApplicationSectionController.java` — 관리자 조회
- `service/ApplicationPdfService.java` — header BasicInfo 우선 + row 부재 시만 fallback
- `domain/repository/ApplicationPiiPurgeRepository.java` + `service/ApplicationPiiPurgeService.java` — `purgeBasicInfo`

**신규 (test):**
- `support/BasicInfoTestSupport.java` — 공용 시드 헬퍼
- `domain/repository/ApplicationBasicInfoEncryptionTest.java`
- `service/ApplicationBasicInfoServiceTest.java`
- `controller/ApplicationBasicInfoControllerTest.java`
- `service/ApplicationCompletionReadCheckerTest.java`

**수정 (test):** `ApplicationPiiPurgeServiceTest`, `ApplicationDashboardServiceTest`, `AdminApplicationSectionServiceTest`(있으면), `ApplicationPdfServiceTest`(또는 신규), 그리고 **submit 마이그레이션 대상 58개 파일**(Task 7).

> 경로 주의: 컨트롤러 애노테이션은 `/applications/...`, 테스트는 context-path 포함 `/api/applications/...`로 호출한다(기존 패턴). 관리자도 애노테이션 `/admin/...`, 테스트 `/api/admin/...`.

---

## Task 1: 기본정보 Enum 3종

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/enumeration/NationalityType.java`
- Create: `src/main/java/com/shinyoung/recruit/enumeration/VeteranStatus.java`
- Create: `src/main/java/com/shinyoung/recruit/enumeration/DisabilityStatus.java`

- [ ] **Step 1: enum 3개 작성**

`NationalityType.java`:
```java
package com.shinyoung.recruit.enumeration;

public enum NationalityType {
    DOMESTIC,
    FOREIGN
}
```

`VeteranStatus.java`:
```java
package com.shinyoung.recruit.enumeration;

public enum VeteranStatus {
    SUBJECT,
    NOT_SUBJECT
}
```

`DisabilityStatus.java`:
```java
package com.shinyoung.recruit.enumeration;

public enum DisabilityStatus {
    SUBJECT,
    NOT_SUBJECT
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `gradlew.bat compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/shinyoung/recruit/enumeration/NationalityType.java src/main/java/com/shinyoung/recruit/enumeration/VeteranStatus.java src/main/java/com/shinyoung/recruit/enumeration/DisabilityStatus.java
git commit -m "feat: add ApplicationBasicInfo enums (nationality/veteran/disability)"
```

---

## Task 2: CommonCodeRepository 활성 코드 검증 메서드 (M4)

**Files:**
- Modify: `src/main/java/com/shinyoung/recruit/domain/repository/CommonCodeRepository.java`
- Test: `src/test/java/com/shinyoung/recruit/domain/repository/CommonCodeRepositoryTest.java` (없으면 생성)

- [ ] **Step 1: 실패 테스트 작성**

`CommonCodeRepositoryTest.java` (신규):
```java
package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.CommonCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CommonCodeRepositoryTest {

    @Autowired
    private CommonCodeRepository commonCodeRepository;

    @Test
    void existsByGroupCodeAndCodeAndActiveTrue_only_matches_active_codes() {
        commonCodeRepository.save(CommonCode.create("NATIONALITY", "US", "United States", 1, true, null));
        commonCodeRepository.save(CommonCode.create("NATIONALITY", "JP", "Japan", 2, false, null));

        assertThat(commonCodeRepository.existsByGroupCodeAndCodeAndActiveTrue("NATIONALITY", "US")).isTrue();
        assertThat(commonCodeRepository.existsByGroupCodeAndCodeAndActiveTrue("NATIONALITY", "JP")).isFalse();
        assertThat(commonCodeRepository.existsByGroupCodeAndCodeAndActiveTrue("NATIONALITY", "ZZ")).isFalse();
    }
}
```

> `CommonCode.create(groupCode, code, displayName, sortOrder, active, description)` 시그니처는 `CommonCodeService.create`에서 사용하는 것과 동일하다(존재 확인됨).

- [ ] **Step 2: 테스트 실패 확인**

Run: `gradlew.bat test --tests "com.shinyoung.recruit.domain.repository.CommonCodeRepositoryTest"`
Expected: FAIL — `existsByGroupCodeAndCodeAndActiveTrue` 메서드 없음(컴파일 에러)

- [ ] **Step 3: 메서드 추가**

`CommonCodeRepository.java`에 메서드 추가(기존 `existsByGroupCodeAndCode` 아래):
```java
    boolean existsByGroupCodeAndCodeAndActiveTrue(String groupCode, String code);
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `gradlew.bat test --tests "com.shinyoung.recruit.domain.repository.CommonCodeRepositoryTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/shinyoung/recruit/domain/repository/CommonCodeRepository.java src/test/java/com/shinyoung/recruit/domain/repository/CommonCodeRepositoryTest.java
git commit -m "feat: add CommonCode active-code existence check for basic info validation"
```

---

## Task 3: 엔티티 + 리포지토리 + 암호화 라운드트립 테스트

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/domain/entity/ApplicationBasicInfo.java`
- Create: `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationBasicInfoRepository.java`
- Test: `src/test/java/com/shinyoung/recruit/domain/repository/ApplicationBasicInfoEncryptionTest.java`

- [ ] **Step 1: 엔티티 작성**

`ApplicationBasicInfo.java`:
```java
package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.common.crypto.AesAttributeConverter;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.VeteranStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(
        name = "application_basic_info",
        indexes = {
                @Index(name = "idx_application_basic_info_application", columnList = "job_application_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationBasicInfo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_application_id", nullable = false, unique = true)
    private JobApplication jobApplication;

    @Convert(converter = AesAttributeConverter.class)
    @Column(length = 500)
    private String nameKorean;

    @Convert(converter = AesAttributeConverter.class)
    @Column(length = 500)
    private String nameEnglish;

    @Enumerated(EnumType.STRING)
    private NationalityType nationalityType;

    @Convert(converter = AesAttributeConverter.class)
    @Column(length = 500)
    private String countryCode;

    private LocalDate birthDate;

    @Convert(converter = AesAttributeConverter.class)
    @Column(length = 500)
    private String mobilePhone;

    @Convert(converter = AesAttributeConverter.class)
    @Column(length = 500)
    private String emergencyPhone;

    @Convert(converter = AesAttributeConverter.class)
    @Column(length = 500)
    private String email;

    @Enumerated(EnumType.STRING)
    private VeteranStatus veteranStatus;

    @Enumerated(EnumType.STRING)
    private DisabilityStatus disabilityStatus;

    @Convert(converter = AesAttributeConverter.class)
    @Column(length = 500)
    private String disabilityGradeCode;

    @Convert(converter = AesAttributeConverter.class)
    @Column(length = 500)
    private String disabilityTypeCode;

    @Convert(converter = AesAttributeConverter.class)
    @Column(length = 500)
    private String zipCode;

    @Convert(converter = AesAttributeConverter.class)
    @Column(length = 1000)
    private String addressBasic;

    @Convert(converter = AesAttributeConverter.class)
    @Column(length = 1000)
    private String addressDetail;

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
}
```

- [ ] **Step 2: 리포지토리 작성**

`ApplicationBasicInfoRepository.java`:
```java
package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApplicationBasicInfoRepository extends JpaRepository<ApplicationBasicInfo, Long> {

    Optional<ApplicationBasicInfo> findByJobApplicationId(Long applicationId);

    boolean existsByJobApplicationId(Long applicationId);
}
```

- [ ] **Step 3: 암호화 라운드트립 + raw 컬럼 검증 테스트 작성**

`ApplicationBasicInfoEncryptionTest.java`:
```java
package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.VeteranStatus;
import com.shinyoung.recruit.service.JobApplicationService;
import com.shinyoung.recruit.service.JobPostingService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicationBasicInfoEncryptionTest {

    @Autowired private ApplicationBasicInfoRepository basicInfoRepository;
    @Autowired private ApplicantRepository applicantRepository;
    @Autowired private JobPostingService jobPostingService;
    @Autowired private JobApplicationService jobApplicationService;
    @Autowired private JobPostingRepository jobPostingRepository;
    @Autowired private JobApplicationRepository jobApplicationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void string_pii_is_stored_as_ciphertext_and_decrypted_on_read() {
        JobApplication application = newApplication();

        ApplicationBasicInfo saved = basicInfoRepository.save(ApplicationBasicInfo.create(
                application, "홍길동", "Hong Gildong", NationalityType.DOMESTIC, null,
                LocalDate.of(1995, 1, 1), "01012345678", null, "test@example.com",
                VeteranStatus.NOT_SUBJECT, DisabilityStatus.NOT_SUBJECT, null, null,
                "06236", "서울시 강남구", "101동 1001호"));
        entityManager.flush();
        entityManager.clear();

        // read: 복호화된 평문
        ApplicationBasicInfo reloaded = basicInfoRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getNameKorean()).isEqualTo("홍길동");
        assertThat(reloaded.getEmail()).isEqualTo("test@example.com");
        assertThat(reloaded.getMobilePhone()).isEqualTo("01012345678");

        // raw 컬럼: 암호문(평문과 다름)
        Object rawName = entityManager.createNativeQuery(
                        "select name_korean from application_basic_info where id = :id")
                .setParameter("id", saved.getId())
                .getSingleResult();
        assertThat(rawName).isNotNull();
        assertThat(rawName.toString()).isNotEqualTo("홍길동");
    }

    private JobApplication newApplication() {
        Applicant applicant = new Applicant("enc-ci", HashUtil.sha256("enc-ci"));
        applicant.setLoginId("enc-applicant");
        applicant.setName("User-Enc");
        applicant.setUserName("Enc User");
        applicant.setPassword("encoded");
        applicant.setPhoneNumber("01000000000");
        applicant = applicantRepository.save(applicant);

        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                "2026 recruitment", "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0), LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(new JobPositionRequest("Backend", 0)),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)));
        jobPostingService.publish(jobPostingId);
        JobPosting posting = jobPostingRepository.findDetailById(jobPostingId).orElseThrow();
        Long positionId = posting.getJobPositions().stream()
                .sorted(Comparator.comparing(JobPosition::getSortOrder)).map(JobPosition::getId)
                .findFirst().orElseThrow();
        Long applicationId = jobApplicationService.create(
                applicant.getId(), new ApplicationCreateRequest(jobPostingId, positionId));
        return jobApplicationRepository.findById(applicationId).orElseThrow();
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `gradlew.bat test --tests "com.shinyoung.recruit.domain.repository.ApplicationBasicInfoEncryptionTest"`
Expected: PASS (raw 컬럼이 암호문, read는 평문)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/shinyoung/recruit/domain/entity/ApplicationBasicInfo.java src/main/java/com/shinyoung/recruit/domain/repository/ApplicationBasicInfoRepository.java src/test/java/com/shinyoung/recruit/domain/repository/ApplicationBasicInfoEncryptionTest.java
git commit -m "feat: add ApplicationBasicInfo entity/repository with AES-encrypted PII columns"
```

---

## Task 4: DTO 3종 (Request / Response / AdminResponse)

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/dto/request/BasicInfoSaveRequest.java`
- Create: `src/main/java/com/shinyoung/recruit/dto/response/BasicInfoResponse.java`
- Create: `src/main/java/com/shinyoung/recruit/dto/response/AdminBasicInfoResponse.java`

- [ ] **Step 1: Request DTO 작성**

`BasicInfoSaveRequest.java`:
```java
package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.VeteranStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record BasicInfoSaveRequest(
        @NotBlank @Size(max = 50) String nameKorean,
        @Size(max = 100) String nameEnglish,
        @NotNull NationalityType nationalityType,
        @Size(max = 50) String countryCode,
        @NotNull @Past LocalDate birthDate,
        @NotBlank @Size(max = 20) String mobilePhone,
        @Size(max = 20) String emergencyPhone,
        @NotBlank @Email @Size(max = 100) String email,
        @NotNull VeteranStatus veteranStatus,
        @NotNull DisabilityStatus disabilityStatus,
        @Size(max = 50) String disabilityGradeCode,
        @Size(max = 50) String disabilityTypeCode,
        @Size(max = 10) String zipCode,
        @Size(max = 200) String addressBasic,
        @Size(max = 200) String addressDetail
) {
}
```

- [ ] **Step 2: Response DTO 작성 (prefill 지원)**

`BasicInfoResponse.java`:
```java
package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.VeteranStatus;

import java.time.LocalDate;

public record BasicInfoResponse(
        Long basicInfoId,
        boolean persisted,
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

    public static BasicInfoResponse of(ApplicationBasicInfo basicInfo) {
        return new BasicInfoResponse(
                basicInfo.getId(), true,
                basicInfo.getNameKorean(), basicInfo.getNameEnglish(),
                basicInfo.getNationalityType(), basicInfo.getCountryCode(), basicInfo.getBirthDate(),
                basicInfo.getMobilePhone(), basicInfo.getEmergencyPhone(), basicInfo.getEmail(),
                basicInfo.getVeteranStatus(), basicInfo.getDisabilityStatus(),
                basicInfo.getDisabilityGradeCode(), basicInfo.getDisabilityTypeCode(),
                basicInfo.getZipCode(), basicInfo.getAddressBasic(), basicInfo.getAddressDetail());
    }

    /** 미저장 시 Applicant 기반 prefill projection(B안). 저장 가능 필드만 채우고 basicInfoId=null, persisted=false. */
    public static BasicInfoResponse prefill(Applicant applicant) {
        return new BasicInfoResponse(
                null, false,
                applicant.getUserName(), null,
                null, null, null,
                applicant.getPhoneNumber(), null, applicant.getEmail(),
                null, null,
                null, null,
                null, null, null);
    }
}
```

- [ ] **Step 3: AdminResponse DTO 작성**

`AdminBasicInfoResponse.java`:
```java
package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.VeteranStatus;

import java.time.LocalDate;

public record AdminBasicInfoResponse(
        Long basicInfoId,
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

    public static AdminBasicInfoResponse from(ApplicationBasicInfo basicInfo) {
        return new AdminBasicInfoResponse(
                basicInfo.getId(),
                basicInfo.getNameKorean(), basicInfo.getNameEnglish(),
                basicInfo.getNationalityType(), basicInfo.getCountryCode(), basicInfo.getBirthDate(),
                basicInfo.getMobilePhone(), basicInfo.getEmergencyPhone(), basicInfo.getEmail(),
                basicInfo.getVeteranStatus(), basicInfo.getDisabilityStatus(),
                basicInfo.getDisabilityGradeCode(), basicInfo.getDisabilityTypeCode(),
                basicInfo.getZipCode(), basicInfo.getAddressBasic(), basicInfo.getAddressDetail());
    }
}
```

- [ ] **Step 4: 컴파일 확인**

Run: `gradlew.bat compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/shinyoung/recruit/dto/request/BasicInfoSaveRequest.java src/main/java/com/shinyoung/recruit/dto/response/BasicInfoResponse.java src/main/java/com/shinyoung/recruit/dto/response/AdminBasicInfoResponse.java
git commit -m "feat: add basic info request/response/admin DTOs"
```

---

## Task 5: 서비스 (조회+prefill, upsert, 검증)

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/service/ApplicationBasicInfoService.java`
- Test: `src/test/java/com/shinyoung/recruit/service/ApplicationBasicInfoServiceTest.java`

- [ ] **Step 1: 서비스 작성**

`ApplicationBasicInfoService.java`:
```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.CommonCodeRepository;
import com.shinyoung.recruit.dto.request.BasicInfoSaveRequest;
import com.shinyoung.recruit.dto.response.BasicInfoResponse;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ApplicationBasicInfoService {

    static final String GROUP_NATIONALITY = "NATIONALITY";
    static final String GROUP_DISABILITY_GRADE = "DISABILITY_GRADE";
    static final String GROUP_DISABILITY_TYPE = "DISABILITY_TYPE";

    static final int MIN_AGE = 14;
    static final int MAX_AGE = 100;
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9-]{9,20}$");

    private final ApplicationSectionAccessService sectionAccessService;
    private final ApplicationBasicInfoRepository basicInfoRepository;
    private final CommonCodeRepository commonCodeRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public BasicInfoResponse getBasicInfo(Long applicantId, Long applicationId) {
        JobApplication application = sectionAccessService.findOwnedApplication(applicantId, applicationId);
        return basicInfoRepository.findByJobApplicationId(applicationId)
                .map(BasicInfoResponse::of)
                .orElseGet(() -> BasicInfoResponse.prefill(application.getApplicant()));
    }

    @Transactional
    public BasicInfoResponse saveBasicInfo(Long applicantId, Long applicationId, BasicInfoSaveRequest request) {
        JobApplication application = sectionAccessService.findOwnedApplication(applicantId, applicationId);
        sectionAccessService.validateWritable(application);
        validateRequest(request);

        ApplicationBasicInfo basicInfo = basicInfoRepository.findByJobApplicationId(applicationId)
                .orElseGet(() -> basicInfoRepository.save(toBasicInfo(application, request)));

        basicInfo.update(
                request.nameKorean(), request.nameEnglish(), request.nationalityType(), request.countryCode(),
                request.birthDate(), request.mobilePhone(), request.emergencyPhone(), request.email(),
                request.veteranStatus(), request.disabilityStatus(),
                request.disabilityGradeCode(), request.disabilityTypeCode(),
                request.zipCode(), request.addressBasic(), request.addressDetail());

        return BasicInfoResponse.of(basicInfo);
    }

    private void validateRequest(BasicInfoSaveRequest request) {
        if (request == null) {
            throw new InvalidJobApplicationException("Basic info request is required.");
        }
        validateNationality(request);
        validateDisability(request);
        validateBirthDate(request.birthDate());
        validatePhone("Mobile phone", request.mobilePhone(), true);
        validatePhone("Emergency phone", request.emergencyPhone(), false);
    }

    private void validateNationality(BasicInfoSaveRequest request) {
        if (request.nationalityType() == NationalityType.FOREIGN) {
            if (isBlank(request.countryCode())) {
                throw new InvalidJobApplicationException("Country code is required for a foreign applicant.");
            }
            if (!commonCodeRepository.existsByGroupCodeAndCodeAndActiveTrue(GROUP_NATIONALITY, request.countryCode())) {
                throw new InvalidJobApplicationException("Country code is not an active common code.");
            }
        } else if (!isBlank(request.countryCode())) {
            throw new InvalidJobApplicationException("Country code is not allowed for a domestic applicant.");
        }
    }

    private void validateDisability(BasicInfoSaveRequest request) {
        if (request.disabilityStatus() == DisabilityStatus.SUBJECT) {
            if (isBlank(request.disabilityGradeCode()) || isBlank(request.disabilityTypeCode())) {
                throw new InvalidJobApplicationException("Disability grade and type are required for a disability subject.");
            }
            if (!commonCodeRepository.existsByGroupCodeAndCodeAndActiveTrue(GROUP_DISABILITY_GRADE, request.disabilityGradeCode())) {
                throw new InvalidJobApplicationException("Disability grade code is not an active common code.");
            }
            if (!commonCodeRepository.existsByGroupCodeAndCodeAndActiveTrue(GROUP_DISABILITY_TYPE, request.disabilityTypeCode())) {
                throw new InvalidJobApplicationException("Disability type code is not an active common code.");
            }
        } else if (!isBlank(request.disabilityGradeCode()) || !isBlank(request.disabilityTypeCode())) {
            throw new InvalidJobApplicationException("Disability grade/type are not allowed when not a disability subject.");
        }
    }

    private void validateBirthDate(LocalDate birthDate) {
        LocalDate today = LocalDate.now(clock);
        int age = Period.between(birthDate, today).getYears();
        if (age < MIN_AGE || age > MAX_AGE) {
            throw new InvalidJobApplicationException("Birth date must be between age " + MIN_AGE + " and " + MAX_AGE + ".");
        }
    }

    private void validatePhone(String label, String phone, boolean required) {
        if (isBlank(phone)) {
            if (required) {
                throw new InvalidJobApplicationException(label + " is required.");
            }
            return;
        }
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw new InvalidJobApplicationException(label + " must contain only digits and hyphens (9-20 chars).");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ApplicationBasicInfo toBasicInfo(JobApplication application, BasicInfoSaveRequest request) {
        return ApplicationBasicInfo.create(
                application,
                request.nameKorean(), request.nameEnglish(), request.nationalityType(), request.countryCode(),
                request.birthDate(), request.mobilePhone(), request.emergencyPhone(), request.email(),
                request.veteranStatus(), request.disabilityStatus(),
                request.disabilityGradeCode(), request.disabilityTypeCode(),
                request.zipCode(), request.addressBasic(), request.addressDetail());
    }
}
```

> 참고: `birthDate` non-null은 `@NotNull @Past`로 컨트롤러 단에서 보장되므로 서비스 `validateBirthDate`는 null을 받지 않는다(서비스 단독 호출 테스트는 항상 non-null birthDate를 넘길 것).

- [ ] **Step 2: 서비스 테스트 작성**

`ApplicationBasicInfoServiceTest.java`:
```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.CommonCode;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.CommonCodeRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.BasicInfoSaveRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.response.BasicInfoResponse;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.VeteranStatus;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicationBasicInfoServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), ZoneId.of("UTC"));

    @Autowired private ApplicationBasicInfoService basicInfoService;
    @Autowired private JobApplicationService jobApplicationService;
    @Autowired private JobPostingService jobPostingService;
    @Autowired private ApplicantRepository applicantRepository;
    @Autowired private JobPostingRepository jobPostingRepository;
    @Autowired private ApplicationBasicInfoRepository basicInfoRepository;
    @Autowired private CommonCodeRepository commonCodeRepository;

    @Test
    void save_domestic_then_get_returns_persisted() {
        Applicant applicant = createApplicant("bi-domestic", "Domestic");
        Long applicationId = createApplication(applicant);

        BasicInfoResponse saved = basicInfoService.saveBasicInfo(applicant.getId(), applicationId, domesticRequest());
        BasicInfoResponse found = basicInfoService.getBasicInfo(applicant.getId(), applicationId);

        assertThat(saved.persisted()).isTrue();
        assertThat(found.basicInfoId()).isEqualTo(saved.basicInfoId());
        assertThat(found.nameKorean()).isEqualTo("홍길동");
        assertThat(found.nationalityType()).isEqualTo(NationalityType.DOMESTIC);
    }

    @Test
    void get_without_row_returns_applicant_prefill() {
        Applicant applicant = createApplicant("bi-prefill", "Prefill");
        Long applicationId = createApplication(applicant);

        BasicInfoResponse response = basicInfoService.getBasicInfo(applicant.getId(), applicationId);

        assertThat(response.persisted()).isFalse();
        assertThat(response.basicInfoId()).isNull();
        assertThat(response.nameKorean()).isEqualTo("Prefill");           // Applicant.userName
        assertThat(response.mobilePhone()).isEqualTo("01000000000");       // Applicant.phoneNumber
        assertThat(response.birthDate()).isNull();
    }

    @Test
    void save_is_upsert() {
        Applicant applicant = createApplicant("bi-upsert", "Upsert");
        Long applicationId = createApplication(applicant);
        BasicInfoResponse first = basicInfoService.saveBasicInfo(applicant.getId(), applicationId, domesticRequest());

        BasicInfoResponse second = basicInfoService.saveBasicInfo(applicant.getId(), applicationId,
                new BasicInfoSaveRequest("김철수", null, NationalityType.DOMESTIC, null,
                        LocalDate.of(1990, 5, 5), "01099998888", null, "kim@example.com",
                        VeteranStatus.SUBJECT, DisabilityStatus.NOT_SUBJECT, null, null, null, null, null));

        assertThat(second.basicInfoId()).isEqualTo(first.basicInfoId());
        assertThat(second.nameKorean()).isEqualTo("김철수");
        assertThat(basicInfoRepository.findAll()).hasSize(1);
    }

    @Test
    void foreign_requires_active_country_code() {
        Applicant applicant = createApplicant("bi-foreign", "Foreign");
        Long applicationId = createApplication(applicant);

        // countryCode 누락
        assertThatThrownBy(() -> basicInfoService.saveBasicInfo(applicant.getId(), applicationId,
                foreignRequest(null))).isInstanceOf(InvalidJobApplicationException.class);

        // 비활성/미존재 코드
        assertThatThrownBy(() -> basicInfoService.saveBasicInfo(applicant.getId(), applicationId,
                foreignRequest("ZZ"))).isInstanceOf(InvalidJobApplicationException.class);

        // 활성 코드 → 성공
        commonCodeRepository.save(CommonCode.create("NATIONALITY", "US", "United States", 1, true, null));
        BasicInfoResponse ok = basicInfoService.saveBasicInfo(applicant.getId(), applicationId, foreignRequest("US"));
        assertThat(ok.countryCode()).isEqualTo("US");
    }

    @Test
    void domestic_with_country_code_is_rejected() {
        Applicant applicant = createApplicant("bi-domestic-cc", "DomesticCC");
        Long applicationId = createApplication(applicant);

        assertThatThrownBy(() -> basicInfoService.saveBasicInfo(applicant.getId(), applicationId,
                new BasicInfoSaveRequest("홍길동", null, NationalityType.DOMESTIC, "US",
                        LocalDate.of(1995, 1, 1), "01012345678", null, "a@b.com",
                        VeteranStatus.NOT_SUBJECT, DisabilityStatus.NOT_SUBJECT, null, null, null, null, null)))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void disability_subject_requires_active_grade_and_type() {
        Applicant applicant = createApplicant("bi-disability", "Disability");
        Long applicationId = createApplication(applicant);

        // grade/type 누락
        assertThatThrownBy(() -> basicInfoService.saveBasicInfo(applicant.getId(), applicationId,
                disabilityRequest(null, null))).isInstanceOf(InvalidJobApplicationException.class);

        // 미존재 코드
        assertThatThrownBy(() -> basicInfoService.saveBasicInfo(applicant.getId(), applicationId,
                disabilityRequest("G1", "T1"))).isInstanceOf(InvalidJobApplicationException.class);

        // 활성 코드 → 성공
        commonCodeRepository.save(CommonCode.create("DISABILITY_GRADE", "G1", "1급", 1, true, null));
        commonCodeRepository.save(CommonCode.create("DISABILITY_TYPE", "T1", "지체", 1, true, null));
        BasicInfoResponse ok = basicInfoService.saveBasicInfo(applicant.getId(), applicationId, disabilityRequest("G1", "T1"));
        assertThat(ok.disabilityGradeCode()).isEqualTo("G1");
    }

    @Test
    void birth_date_out_of_age_range_is_rejected() {
        Applicant applicant = createApplicant("bi-age", "Age");
        Long applicationId = createApplication(applicant);

        // 만 13세 (기준일 2026-06-15)
        assertThatThrownBy(() -> basicInfoService.saveBasicInfo(applicant.getId(), applicationId,
                ageRequest(LocalDate.of(2013, 1, 1)))).isInstanceOf(InvalidJobApplicationException.class);

        // 만 101세
        assertThatThrownBy(() -> basicInfoService.saveBasicInfo(applicant.getId(), applicationId,
                ageRequest(LocalDate.of(1924, 1, 1)))).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void invalid_phone_format_is_rejected() {
        Applicant applicant = createApplicant("bi-phone", "Phone");
        Long applicationId = createApplication(applicant);

        assertThatThrownBy(() -> basicInfoService.saveBasicInfo(applicant.getId(), applicationId,
                new BasicInfoSaveRequest("홍길동", null, NationalityType.DOMESTIC, null,
                        LocalDate.of(1995, 1, 1), "010-abc-1234", null, "a@b.com",
                        VeteranStatus.NOT_SUBJECT, DisabilityStatus.NOT_SUBJECT, null, null, null, null, null)))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void other_applicants_application_is_hidden() {
        Applicant owner = createApplicant("bi-owner", "Owner");
        Applicant other = createApplicant("bi-other", "Other");
        Long applicationId = createApplication(owner);

        assertThatThrownBy(() -> basicInfoService.getBasicInfo(other.getId(), applicationId))
                .isInstanceOf(JobApplicationNotFoundException.class);
        assertThatThrownBy(() -> basicInfoService.saveBasicInfo(other.getId(), applicationId, domesticRequest()))
                .isInstanceOf(JobApplicationNotFoundException.class);
    }

    @Test
    void save_fails_when_application_is_not_draft() {
        Applicant applicant = createApplicant("bi-submitted", "Submitted");
        Long applicationId = createApplication(applicant);
        basicInfoService.saveBasicInfo(applicant.getId(), applicationId, domesticRequest());
        jobApplicationService.submit(applicant.getId(), applicationId);

        assertThatThrownBy(() -> basicInfoService.saveBasicInfo(applicant.getId(), applicationId, domesticRequest()))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    // ---- fixtures ----

    private BasicInfoSaveRequest domesticRequest() {
        return new BasicInfoSaveRequest("홍길동", "Hong", NationalityType.DOMESTIC, null,
                LocalDate.of(1995, 1, 1), "01012345678", null, "hong@example.com",
                VeteranStatus.NOT_SUBJECT, DisabilityStatus.NOT_SUBJECT, null, null, "06236", "서울", "101호");
    }

    private BasicInfoSaveRequest foreignRequest(String countryCode) {
        return new BasicInfoSaveRequest("홍길동", null, NationalityType.FOREIGN, countryCode,
                LocalDate.of(1995, 1, 1), "01012345678", null, "hong@example.com",
                VeteranStatus.NOT_SUBJECT, DisabilityStatus.NOT_SUBJECT, null, null, null, null, null);
    }

    private BasicInfoSaveRequest disabilityRequest(String grade, String type) {
        return new BasicInfoSaveRequest("홍길동", null, NationalityType.DOMESTIC, null,
                LocalDate.of(1995, 1, 1), "01012345678", null, "hong@example.com",
                VeteranStatus.NOT_SUBJECT, DisabilityStatus.SUBJECT, grade, type, null, null, null);
    }

    private BasicInfoSaveRequest ageRequest(LocalDate birthDate) {
        return new BasicInfoSaveRequest("홍길동", null, NationalityType.DOMESTIC, null,
                birthDate, "01012345678", null, "hong@example.com",
                VeteranStatus.NOT_SUBJECT, DisabilityStatus.NOT_SUBJECT, null, null, null, null, null);
    }

    private Applicant createApplicant(String loginId, String userName) {
        String ci = loginId + "-ci";
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId(loginId);
        applicant.setName("User-" + userName);
        applicant.setUserName(userName);
        applicant.setPassword("encoded");
        applicant.setPhoneNumber("01000000000");
        return applicantRepository.save(applicant);
    }

    private Long createApplication(Applicant applicant) {
        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                "2026 recruitment", "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0), LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(new JobPositionRequest("Backend", 0)),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)));
        jobPostingService.publish(jobPostingId);
        JobPosting posting = jobPostingRepository.findDetailById(jobPostingId).orElseThrow();
        Long positionId = posting.getJobPositions().stream()
                .sorted(Comparator.comparing(JobPosition::getSortOrder)).map(JobPosition::getId)
                .findFirst().orElseThrow();
        return jobApplicationService.create(applicant.getId(), new ApplicationCreateRequest(jobPostingId, positionId));
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return FIXED_CLOCK;
        }
    }
}
```

> 주의: `save_fails_when_application_is_not_draft`는 submit 전에 BasicInfo를 저장하므로 Task 7 적용 후에도 통과한다.

- [ ] **Step 3: 테스트 통과 확인**

Run: `gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicationBasicInfoServiceTest"`
Expected: PASS (모든 케이스)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/shinyoung/recruit/service/ApplicationBasicInfoService.java src/test/java/com/shinyoung/recruit/service/ApplicationBasicInfoServiceTest.java
git commit -m "feat: add ApplicationBasicInfoService with upsert/prefill/conditional validation"
```

---

## Task 6: 지원자 컨트롤러

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/controller/ApplicationBasicInfoController.java`
- Test: `src/test/java/com/shinyoung/recruit/controller/ApplicationBasicInfoControllerTest.java`

- [ ] **Step 1: 컨트롤러 작성**

`ApplicationBasicInfoController.java`:
```java
package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.BasicInfoSaveRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.BasicInfoResponse;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ApplicationBasicInfoService;
import com.shinyoung.recruit.service.CurrentApplicantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ApplicationBasicInfoController {

    private final ApplicationBasicInfoService applicationBasicInfoService;
    private final CurrentApplicantService currentApplicantService;

    @GetMapping("/applications/{applicationId}/basic-info")
    public ResponseEntity<ApiResponse<BasicInfoResponse>> getBasicInfo(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        BasicInfoResponse response = applicationBasicInfoService.getBasicInfo(applicantId, applicationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/applications/{applicationId}/basic-info")
    public ResponseEntity<ApiResponse<BasicInfoResponse>> saveBasicInfo(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId,
            @Valid @RequestBody BasicInfoSaveRequest request
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        BasicInfoResponse response = applicationBasicInfoService.saveBasicInfo(applicantId, applicationId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
```

- [ ] **Step 2: 컨트롤러 테스트 작성**

`ApplicationBasicInfoControllerTest.java` — `ApplicationMilitaryControllerTest`의 fixture/auth 헬퍼(`createApplicant`, `createApplication`, `createPublishedJobPosting`, `firstJobPositionId`, `authenticate`, `FixedClockConfig`)를 그대로 복사하고, 아래 테스트 메서드로 교체:
```java
    @Test
    void get_returns_prefill_before_save() throws Exception {
        Applicant applicant = createApplicant("bi-api-prefill", "Api Prefill");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        authenticate(applicant);

        mockMvc.perform(get("/api/applications/{applicationId}/basic-info", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.persisted").value(false))
                .andExpect(jsonPath("$.data.basicInfoId").isEmpty())
                .andExpect(jsonPath("$.data.nameKorean").value("Api Prefill"));
    }

    @Test
    void save_then_get_returns_persisted() throws Exception {
        Applicant applicant = createApplicant("bi-api-save", "Api Save");
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
                                  "veteranStatus": "NOT_SUBJECT",
                                  "disabilityStatus": "NOT_SUBJECT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.persisted").value(true))
                .andExpect(jsonPath("$.data.nameKorean").value("홍길동"));

        mockMvc.perform(get("/api/applications/{applicationId}/basic-info", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.persisted").value(true));
    }

    @Test
    void save_with_missing_required_field_returns_bad_request() throws Exception {
        Applicant applicant = createApplicant("bi-api-validation", "Api Validation");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        authenticate(applicant);

        mockMvc.perform(post("/api/applications/{applicationId}/basic-info", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nationalityType": "DOMESTIC",
                                  "birthDate": "1995-01-01",
                                  "mobilePhone": "01012345678",
                                  "email": "hong@example.com",
                                  "veteranStatus": "NOT_SUBJECT",
                                  "disabilityStatus": "NOT_SUBJECT"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void other_applicants_application_is_hidden() throws Exception {
        Applicant owner = createApplicant("bi-api-owner", "Api Owner");
        Applicant other = createApplicant("bi-api-other", "Api Other");
        Long applicationId = createApplication(owner, createPublishedJobPosting());
        authenticate(other);

        mockMvc.perform(get("/api/applications/{applicationId}/basic-info", applicationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
```

> `createPublishedJobPosting()`는 인자 없는 버전으로(폼 설정 전부 false) 작성: `new ApplicationFormConfigRequest(false, false, false, false, false, false, false)`.

- [ ] **Step 3: 테스트 통과 확인**

Run: `gradlew.bat test --tests "com.shinyoung.recruit.controller.ApplicationBasicInfoControllerTest"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/shinyoung/recruit/controller/ApplicationBasicInfoController.java src/test/java/com/shinyoung/recruit/controller/ApplicationBasicInfoControllerTest.java
git commit -m "feat: add applicant basic-info GET/POST controller"
```

---

## Task 7: 제출 검증(BasicInfo 필수) + 공용 시드 헬퍼 + 전체 테스트 마이그레이션 (옵션 A)

> 이 Task는 의도적으로 큰 단위다. SubmitValidator 변경은 BasicInfo 없이 제출하는 기존 58개 테스트를 깨므로, **검증 변경 → 헬퍼 도입 → 전 테스트 마이그레이션 → 전체 스위트 green**까지를 하나의 커밋 경계로 묶는다.

**Files:**
- Create: `src/test/java/com/shinyoung/recruit/support/BasicInfoTestSupport.java`
- Modify: `src/main/java/com/shinyoung/recruit/service/ApplicationSubmitValidator.java`
- Modify: submit를 호출하는 기존 테스트들(아래 목록)

- [ ] **Step 1: 공용 시드 헬퍼 작성 (검증 우회, 리포지토리 직접 저장)**

`BasicInfoTestSupport.java`:
```java
package com.shinyoung.recruit.support;

import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.VeteranStatus;

import java.time.LocalDate;

/**
 * 테스트 전용: submit 가능한 최소 유효 BasicInfo 를 리포지토리에 직접 시드한다.
 * 서비스 검증(쓰기 가능 기간)을 우회하므로 공고 상태/기간을 닫은 뒤에도 호출할 수 있다.
 * 이미 행이 있으면 no-op.
 */
public final class BasicInfoTestSupport {

    private BasicInfoTestSupport() {
    }

    public static void seedValidBasicInfo(ApplicationBasicInfoRepository repository, JobApplication application) {
        if (repository.existsByJobApplicationId(application.getId())) {
            return;
        }
        repository.save(ApplicationBasicInfo.create(
                application,
                "홍길동", null, NationalityType.DOMESTIC, null,
                LocalDate.of(1995, 1, 1), "01012345678", null, "test@example.com",
                VeteranStatus.NOT_SUBJECT, DisabilityStatus.NOT_SUBJECT,
                null, null, null, null, null));
    }
}
```

- [ ] **Step 2: SubmitValidator에 실패하는 새 테스트를 추가 (신규 파일)**

`src/test/java/com/shinyoung/recruit/service/ApplicationSubmitValidatorBasicInfoTest.java`:
```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import com.shinyoung.recruit.support.BasicInfoTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicationSubmitValidatorBasicInfoTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), ZoneId.of("UTC"));

    @Autowired private ApplicationSubmitValidator submitValidator;
    @Autowired private JobApplicationService jobApplicationService;
    @Autowired private JobPostingService jobPostingService;
    @Autowired private ApplicantRepository applicantRepository;
    @Autowired private JobPostingRepository jobPostingRepository;
    @Autowired private JobApplicationRepository jobApplicationRepository;
    @Autowired private ApplicationBasicInfoRepository basicInfoRepository;

    @Test
    void submit_validation_fails_without_basic_info() {
        JobApplication application = newApplication();
        assertThatThrownBy(() -> submitValidator.validate(application))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void submit_validation_passes_with_basic_info() {
        JobApplication application = newApplication();
        BasicInfoTestSupport.seedValidBasicInfo(basicInfoRepository, application);
        assertThatCode(() -> submitValidator.validate(application)).doesNotThrowAnyException();
    }

    private JobApplication newApplication() {
        Applicant applicant = new Applicant("submit-ci", HashUtil.sha256("submit-ci"));
        applicant.setLoginId("submit-applicant");
        applicant.setName("User-Submit");
        applicant.setUserName("Submit");
        applicant.setPassword("encoded");
        applicant.setPhoneNumber("01000000000");
        applicant = applicantRepository.save(applicant);

        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                "2026 recruitment", "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0), LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(new JobPositionRequest("Backend", 0)),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)));
        jobPostingService.publish(jobPostingId);
        JobPosting posting = jobPostingRepository.findDetailById(jobPostingId).orElseThrow();
        Long positionId = posting.getJobPositions().stream()
                .sorted(Comparator.comparing(JobPosition::getSortOrder)).map(JobPosition::getId)
                .findFirst().orElseThrow();
        Long applicationId = jobApplicationService.create(
                applicant.getId(), new ApplicationCreateRequest(jobPostingId, positionId));
        return jobApplicationRepository.findById(applicationId).orElseThrow();
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return FIXED_CLOCK;
        }
    }
}
```

- [ ] **Step 3: 새 테스트 실패 확인**

Run: `gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicationSubmitValidatorBasicInfoTest"`
Expected: `submit_validation_fails_without_basic_info` FAIL(아직 검증 없음 → 예외 미발생)

- [ ] **Step 4: SubmitValidator 수정**

`ApplicationSubmitValidator.java`:
1. import 추가:
```java
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.NationalityType;
```
2. 필드 추가(다른 repository 필드 옆):
```java
    private final ApplicationBasicInfoRepository basicInfoRepository;
```
3. `validate` 메서드를 아래로 교체(BasicInfo를 config null 검사보다 먼저):
```java
    public void validate(JobApplication application) {
        Long applicationId = application.getId();
        validateBasicInfo(applicationId);

        ApplicationFormConfig config = application.getJobPosting().getApplicationFormConfig();
        if (config == null) {
            throw new InvalidJobApplicationException("Application form config is required before submit.");
        }

        validateEducation(config, applicationId);
        validateCareer(config, applicationId);
        validateMilitary(config, applicationId);
        validateSimpleRequiredSection(config.isUseCertificate(), config.isRequireCertificate(), () -> certificateRepository.existsByJobApplicationId(applicationId), "Certificate");
        validateSimpleRequiredSection(config.isUseLanguage(), config.isRequireLanguage(), () -> languageRepository.existsByJobApplicationId(applicationId), "Language");
        validateSimpleRequiredSection(config.isUseAward(), config.isRequireAward(), () -> awardRepository.existsByJobApplicationId(applicationId), "Award");
        validateSimpleRequiredSection(config.isUseGapPeriod(), config.isRequireGapPeriod(), () -> gapPeriodRepository.existsByJobApplicationId(applicationId), "Gap period");
        validateAnswers(application);
        validateAttachmentRequirements(application);
    }

    private void validateBasicInfo(Long applicationId) {
        ApplicationBasicInfo basicInfo = basicInfoRepository.findByJobApplicationId(applicationId)
                .orElseThrow(() -> new InvalidJobApplicationException("Basic info is required before submit."));

        if (isBlank(basicInfo.getNameKorean())
                || basicInfo.getBirthDate() == null
                || basicInfo.getNationalityType() == null
                || isBlank(basicInfo.getMobilePhone())
                || isBlank(basicInfo.getEmail())
                || basicInfo.getVeteranStatus() == null
                || basicInfo.getDisabilityStatus() == null) {
            throw new InvalidJobApplicationException("Basic info required fields are missing before submit.");
        }
        if (basicInfo.getNationalityType() == NationalityType.FOREIGN && isBlank(basicInfo.getCountryCode())) {
            throw new InvalidJobApplicationException("Country code is required for a foreign applicant before submit.");
        }
        if (basicInfo.getDisabilityStatus() == DisabilityStatus.SUBJECT
                && (isBlank(basicInfo.getDisabilityGradeCode()) || isBlank(basicInfo.getDisabilityTypeCode()))) {
            throw new InvalidJobApplicationException("Disability grade/type are required for a disability subject before submit.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
```

- [ ] **Step 5: 새 테스트 통과 확인**

Run: `gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicationSubmitValidatorBasicInfoTest"`
Expected: PASS (양쪽 케이스)

- [ ] **Step 6: 전체 스위트 실행 → 깨진 테스트 목록 수집**

Run: `gradlew.bat test`
Expected: 다수 FAIL — submit을 호출하지만 BasicInfo를 시드하지 않는 테스트들. (예상 대상 58개 파일, 아래 목록.)

- [ ] **Step 7: 깨진 각 테스트에 시드 마이그레이션 적용 (기계적 레시피)**

각 실패 테스트에 대해 동일 레시피를 적용한다:

1. import 추가:
```java
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.support.BasicInfoTestSupport;
```
2. 필드 추가(이미 `jobApplicationRepository`가 없으면 그것도 함께 autowire):
```java
    @Autowired
    private ApplicationBasicInfoRepository basicInfoRepository;
    // (없을 경우)
    @Autowired
    private JobApplicationRepository jobApplicationRepository;
```
3. 해당 application을 `submit` 하기 **직전**(또는 application 생성 직후)에 시드:
```java
BasicInfoTestSupport.seedValidBasicInfo(
        basicInfoRepository,
        jobApplicationRepository.findById(applicationId).orElseThrow());
```

**예시 (ApplicationMilitaryServiceTest 의 `save_fails_when_application_is_not_draft`):**
변경 전:
```java
        Long submittedApplicationId = createApplication(submittedApplicant, createPublishedJobPosting(false));
        jobApplicationService.submit(submittedApplicant.getId(), submittedApplicationId);
```
변경 후:
```java
        Long submittedApplicationId = createApplication(submittedApplicant, createPublishedJobPosting(false));
        BasicInfoTestSupport.seedValidBasicInfo(
                basicInfoRepository,
                jobApplicationRepository.findById(submittedApplicationId).orElseThrow());
        jobApplicationService.submit(submittedApplicant.getId(), submittedApplicationId);
```
(그리고 클래스에 `@Autowired ApplicationBasicInfoRepository basicInfoRepository;` 추가. `jobApplicationRepository`는 이 클래스에 이미 있음.)

**마이그레이션 대상 파일 목록(`.submit(` 호출 — 전체 스위트 실패가 사라질 때까지 적용):**
service: `JobApplicationServiceTest`, `ApplicationMilitaryServiceTest`, `ApplicationLanguageServiceTest`, `ApplicationGapPeriodServiceTest`, `ApplicationCertificateServiceTest`, `ApplicationCareerServiceTest`, `ApplicationAwardServiceTest`, `ApplicationAnswerServiceTest`, `ApplicationEducationServiceTest`, `ApplicationAttachmentServiceTest`, `ApplicationAttachmentFileServiceTest`, `ApplicationAttachmentDeleteServiceTest`, `ApplicationAttachmentDownloadServiceTest`, `ApplicationStageResultServiceTest`, `AdminApplicationSectionServiceTest`, `StageServiceTest`, `StageResultServiceTest`, `StageResultCorrectionServiceTest`, `StageAuditInstrumentationTest`, `InterviewServiceTest`, `InterviewSchedulingStabilizationServiceTest`, `InterviewEvaluationStabilizationTest`, `InterviewEvaluationAdminServiceTest`, `InterviewerInterviewServiceTest`, `InterviewerEvaluationServiceTest`, `ApplicantInterviewServiceTest`, `RetentionEligibilityServiceTest`, `RetentionDryRunServiceTest`, `PurgeExecutionServiceTest`
controller: `ApplicationControllerTest`, `ApplicationMilitaryControllerTest`, `ApplicationLanguageControllerTest`, `ApplicationGapPeriodControllerTest`, `ApplicationCertificateControllerTest`, `ApplicationCareerControllerTest`, `ApplicationAwardControllerTest`, `ApplicationAnswerControllerTest`, `ApplicationEducationControllerTest`, `ApplicationAttachmentControllerTest`, `ApplicationStageResultControllerTest`, `StageControllerTest`, `StageResultControllerTest`, `StageResultUploadControllerTest`, `AdminApplicationControllerTest`, `AdminStatisticsControllerTest`, `AdminExportControllerTest`, `AdminDatasetExportControllerTest`, `AdminExportRowCapTest`, `ApplicationPdfControllerTest`, `ApplicationPdfSecurityHardeningTest`, `InterviewAdminControllerTest`, `InterviewerInterviewControllerTest`, `InterviewerEvaluationControllerTest`, `InterviewEvaluationAdminControllerTest`, `ApplicantInterviewControllerTest`
domain: `InterviewParticipantRepositoryTest`, `InterviewEvaluationRepositoryTest`, `InterviewEvaluationTest`

> 컨트롤러 테스트는 보통 `jobApplicationService.submit(...)` 또는 MockMvc submit 호출 전에 시드한다. MockMvc로 submit하는 경우에도 헬퍼는 리포지토리 직접 저장이라 동일하게 적용 가능.
> 일부 테스트는 의도적으로 "제출 실패"를 검증한다(예: 미완성 제출 거부). 그런 케이스는 BasicInfo가 원인이 되지 않도록 주의 — BasicInfo는 시드하고, 원래 의도한 누락 섹션으로 실패하는지 확인한다.

- [ ] **Step 8: 전체 스위트 green 확인**

Run: `gradlew.bat test`
Expected: BUILD SUCCESSFUL (전 테스트 통과)

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat: enforce basic info at submit + migrate submit-dependent tests with shared seeder"
```

---

## Task 8: 완성도 체커 BASIC_INFO 항상 필수 그룹 (M1)

**Files:**
- Modify: `src/main/java/com/shinyoung/recruit/service/ApplicationCompletionReadChecker.java`
- Test: `src/test/java/com/shinyoung/recruit/service/ApplicationCompletionReadCheckerTest.java` (신규)
- Modify(필요 시): `src/test/java/com/shinyoung/recruit/service/ApplicationDashboardServiceTest.java`

- [ ] **Step 1: 실패 테스트 작성**

`ApplicationCompletionReadCheckerTest.java` — `ApplicationBasicInfoServiceTest`의 fixture(`createApplicant`, `createApplication`, `FixedClockConfig`)와 `BasicInfoTestSupport`를 사용. 핵심 테스트:
```java
    @Test
    void basic_info_is_required_group_and_missing_when_absent() {
        Applicant applicant = createApplicant("cmp-missing", "Missing");
        Long applicationId = createApplication(applicant);
        JobApplication application = jobApplicationRepository.findById(applicationId).orElseThrow();

        var result = completionReadChecker.check(application);

        assertThat(result.requiredMissingSections())
                .anyMatch(section -> section.sectionCode().equals("BASIC_INFO"));
    }

    @Test
    void basic_info_not_missing_when_present() {
        Applicant applicant = createApplicant("cmp-present", "Present");
        Long applicationId = createApplication(applicant);
        JobApplication application = jobApplicationRepository.findById(applicationId).orElseThrow();
        BasicInfoTestSupport.seedValidBasicInfo(basicInfoRepository, application);

        var result = completionReadChecker.check(application);

        assertThat(result.requiredMissingSections())
                .noneMatch(section -> section.sectionCode().equals("BASIC_INFO"));
    }
```
(`@Autowired ApplicationCompletionReadChecker completionReadChecker; @Autowired JobApplicationRepository jobApplicationRepository; @Autowired ApplicationBasicInfoRepository basicInfoRepository;` 필요)

- [ ] **Step 2: 실패 확인**

Run: `gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicationCompletionReadCheckerTest"`
Expected: `basic_info_is_required_group_and_missing_when_absent` FAIL (BASIC_INFO 그룹 없음)

- [ ] **Step 3: 체커 수정**

`ApplicationCompletionReadChecker.java`:
1. import 추가:
```java
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.NationalityType;
```
2. 상수 추가:
```java
    private static final String BASIC_INFO = "BASIC_INFO";
```
3. 필드 추가:
```java
    private final ApplicationBasicInfoRepository basicInfoRepository;
```
4. `check()` 시작부(`Long applicationId = application.getId();` 다음 줄)에 호출 추가:
```java
        checkBasicInfo(applicationId, accumulator);
```
> 위치: `ReadinessAccumulator accumulator = new ReadinessAccumulator();` 생성 직후, config 분기보다 먼저.
5. 메서드 추가:
```java
    private void checkBasicInfo(Long applicationId, ReadinessAccumulator accumulator) {
        accumulator.addRequiredGroup(BASIC_INFO);
        ApplicationBasicInfo basicInfo = basicInfoRepository.findByJobApplicationId(applicationId).orElse(null);
        if (basicInfo == null) {
            accumulator.addRequiredIssue(item(BASIC_INFO, "Basic info", true, "MISSING_ROW",
                    "Basic info section is required before submit."));
            return;
        }
        boolean requiredMissing = isBlank(basicInfo.getNameKorean())
                || basicInfo.getBirthDate() == null
                || basicInfo.getNationalityType() == null
                || isBlank(basicInfo.getMobilePhone())
                || isBlank(basicInfo.getEmail())
                || basicInfo.getVeteranStatus() == null
                || basicInfo.getDisabilityStatus() == null
                || (basicInfo.getNationalityType() == NationalityType.FOREIGN && isBlank(basicInfo.getCountryCode()))
                || (basicInfo.getDisabilityStatus() == DisabilityStatus.SUBJECT
                        && (isBlank(basicInfo.getDisabilityGradeCode()) || isBlank(basicInfo.getDisabilityTypeCode())));
        if (requiredMissing) {
            accumulator.addRequiredIssue(item(BASIC_INFO, "Basic info", true, "MISSING_REQUIRED_FIELD",
                    "Basic info required fields are missing before submit."));
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
```

- [ ] **Step 4: 체커 테스트 통과 + 대시보드 테스트 보정**

Run: `gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicationCompletionReadCheckerTest" --tests "com.shinyoung.recruit.service.ApplicationDashboardServiceTest"`
Expected: 체커 테스트 PASS. `ApplicationDashboardServiceTest`가 필수 섹션 개수/완료율을 단언한다면 BASIC_INFO 그룹(+1) 반영해 기대값 보정 후 PASS. (BasicInfo 시드 여부에 따라 완료/미완료가 갈리므로, 대시보드 테스트도 `BasicInfoTestSupport`로 시드하거나 기대값을 수정.)

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: include BASIC_INFO as always-required group in completion checker"
```

---

## Task 9: 관리자 조회 (서비스 + 컨트롤러)

**Files:**
- Modify: `src/main/java/com/shinyoung/recruit/service/AdminApplicationSectionService.java`
- Modify: `src/main/java/com/shinyoung/recruit/controller/AdminApplicationSectionController.java`
- Test: `src/test/java/com/shinyoung/recruit/service/AdminApplicationSectionServiceTest.java` (있으면 보강, 없으면 신규)

- [ ] **Step 1: 서비스 수정**

`AdminApplicationSectionService.java`:
1. import 추가:
```java
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.dto.response.AdminBasicInfoResponse;
```
2. 필드 추가:
```java
    private final ApplicationBasicInfoRepository basicInfoRepository;
```
3. 메서드 추가:
```java
    public AdminBasicInfoResponse getBasicInfo(Long applicationId) {
        validateApplicationExists(applicationId);
        return basicInfoRepository.findByJobApplicationId(applicationId)
                .map(AdminBasicInfoResponse::from)
                .orElse(null);
    }
```

- [ ] **Step 2: 컨트롤러 엔드포인트 추가**

`AdminApplicationSectionController.java`:
1. import 추가:
```java
import com.shinyoung.recruit.dto.response.AdminBasicInfoResponse;
```
2. 엔드포인트 추가(`getMilitary` 옆):
```java
    @GetMapping("/admin/applications/{applicationId}/basic-info")
    public ResponseEntity<ApiResponse<AdminBasicInfoResponse>> getBasicInfo(
            @PathVariable Long applicationId
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminApplicationSectionService.getBasicInfo(applicationId)));
    }
```

- [ ] **Step 3: 테스트 작성/보강**

`AdminApplicationSectionServiceTest`에 추가(또는 신규):
```java
    @Test
    void get_basic_info_returns_persisted_values() {
        // application 생성 + BasicInfoTestSupport.seedValidBasicInfo 시드
        // AdminBasicInfoResponse 가 null 이 아니고 nameKorean == "홍길동" 인지 단언
    }

    @Test
    void get_basic_info_returns_null_when_absent() {
        // 시드하지 않은 application → getBasicInfo == null
    }
```
> 정확한 fixture는 해당 테스트 파일의 기존 헬퍼를 따른다. 시드는 `BasicInfoTestSupport.seedValidBasicInfo(basicInfoRepository, application)`.

- [ ] **Step 4: 테스트 통과 확인**

Run: `gradlew.bat test --tests "com.shinyoung.recruit.service.AdminApplicationSectionServiceTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add admin basic-info read endpoint"
```

---

## Task 10: PDF header BasicInfo 배선 (M2)

**Files:**
- Modify: `src/main/java/com/shinyoung/recruit/service/ApplicationPdfService.java`
- Test: `src/test/java/com/shinyoung/recruit/service/ApplicationPdfServiceTest.java` (없으면 신규)

- [ ] **Step 1: 실패 테스트 작성 (회귀 방지명 포함)**

`ApplicationPdfServiceTest.java`에 추가:
```java
    @Test
    void PDF_BasicInfo_존재시_BasicInfo값을_사용한다() {
        // application 생성 + seedValidBasicInfo (nameKorean=홍길동, mobilePhone=01012345678, email=test@example.com)
        ApplicationPdfDocument doc = applicationPdfService.generate(applicationId);
        // 렌더링된 byte[] 대신, buildHeader 결과를 검증할 수 있도록
        // ApplicationPdfService.generate가 호출하는 경로를 통해 header 값을 확인.
        // (권장: ApplicationPdfRenderer를 stub/spy로 주입해 전달된 ApplicationPdfView.Header를 캡처)
    }

    @Test
    void PDF_BasicInfo_존재하지만_필드_null이면_Applicant로_fallback하지_않는다() {
        // seedValidBasicInfo 후 purgeBasicInfo로 전 필드 null 처리(또는 직접 null BasicInfo 저장)
        // header.phoneNumber()/email()/applicantName() 이 Applicant 의 live 값이 아니라 null 임을 단언
    }
```
> 구현 메모: 헤더 값 검증을 쉽게 하려면 `ApplicationPdfRenderer`를 `@MockBean`으로 주입해 `render(ApplicationPdfView)` 인자를 `ArgumentCaptor`로 캡처하고 `view.header()`를 단언한다. 기존 `ApplicationPdfControllerTest`/`ApplicationPdfSecurityHardeningTest`의 셋업을 참조.

- [ ] **Step 2: 실패 확인**

Run: `gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicationPdfServiceTest"`
Expected: FAIL (현재 header가 BasicInfo를 보지 않음 — phone/email은 Applicant live)

- [ ] **Step 3: 서비스 수정**

`ApplicationPdfService.java`:
1. import 추가:
```java
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
```
2. 필드 추가:
```java
    private final ApplicationBasicInfoRepository basicInfoRepository;
```
3. `buildHeader` 교체:
```java
    private ApplicationPdfView.Header buildHeader(JobApplication application) {
        // BasicInfo row 가 존재하면 그것이 source of truth (파기로 필드가 null 이어도 fallback 하지 않는다).
        ApplicationBasicInfo basicInfo = basicInfoRepository.findByJobApplicationId(application.getId()).orElse(null);
        Applicant applicant = application.getApplicant();

        String name = basicInfo != null ? basicInfo.getNameKorean() : application.getApplicantNameSnapshot();
        String phone = basicInfo != null ? basicInfo.getMobilePhone() : applicant.getPhoneNumber();
        String email = basicInfo != null ? basicInfo.getEmail() : applicant.getEmail();

        return new ApplicationPdfView.Header(
                application.getId(),
                name,
                phone,
                email,
                application.getJobPostingTitleSnapshot(),
                application.getJobPositionNameSnapshot(),
                str(application.getStatus()),
                str(application.getSubmittedAt()));
    }
```

- [ ] **Step 4: 테스트 통과 + 기존 PDF 테스트 확인**

Run: `gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicationPdfServiceTest" --tests "com.shinyoung.recruit.controller.ApplicationPdfControllerTest" --tests "com.shinyoung.recruit.controller.ApplicationPdfSecurityHardeningTest"`
Expected: PASS (BasicInfo 없는 기존 PDF 테스트는 fallback으로 그대로 동작)

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: PDF header uses BasicInfo as source of truth (no fallback when row exists)"
```

---

## Task 11: 파기(Purge) saga 편입

**Files:**
- Modify: `src/main/java/com/shinyoung/recruit/domain/repository/ApplicationPiiPurgeRepository.java`
- Modify: `src/main/java/com/shinyoung/recruit/service/ApplicationPiiPurgeService.java`
- Test: `src/test/java/com/shinyoung/recruit/service/ApplicationPiiPurgeServiceTest.java`

- [ ] **Step 1: 파기 테스트 보강 (실패)**

`ApplicationPiiPurgeServiceTest.인벤토리_분류표대로_관계형_PII가_tombstone_된다()`의 fixture에 BasicInfo 추가:
```java
        basicInfoRepository.save(ApplicationBasicInfo.create(
                application, "홍길동", "Hong", NationalityType.DOMESTIC, null,
                LocalDate.of(1995, 1, 1), "01012345678", "01099998888", "hong@example.com",
                VeteranStatus.NOT_SUBJECT, DisabilityStatus.NOT_SUBJECT, null, null,
                "06236", "서울시 강남구", "101동"));
```
검증 블록에 추가:
```java
        ApplicationBasicInfo purgedBasicInfo = basicInfoRepository.findByJobApplicationId(applicationId).orElseThrow();
        assertThat(purgedBasicInfo.getNameKorean()).isNull();
        assertThat(purgedBasicInfo.getEmail()).isNull();
        assertThat(purgedBasicInfo.getMobilePhone()).isNull();
        assertThat(purgedBasicInfo.getBirthDate()).isNull();
        assertThat(purgedBasicInfo.getNationalityType()).isNull();
        assertThat(purgedBasicInfo.getAddressBasic()).isNull();
        assertThat(purgedBasicInfo.getVeteranStatus()).isNull();
        assertThat(purgedBasicInfo.getDisabilityStatus()).isNull();
```
(`@Autowired ApplicationBasicInfoRepository basicInfoRepository;` + import 추가.)

- [ ] **Step 2: 실패 확인**

Run: `gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicationPiiPurgeServiceTest"`
Expected: FAIL (purge가 BasicInfo를 건드리지 않아 값이 남아 있음)

- [ ] **Step 3: 리포지토리 쿼리 추가**

`ApplicationPiiPurgeRepository.java`에 메서드 추가:
```java
    @Modifying(flushAutomatically = true)
    @Query("""
            update ApplicationBasicInfo b
            set b.nameKorean = null, b.nameEnglish = null, b.email = null,
                b.mobilePhone = null, b.emergencyPhone = null,
                b.birthDate = null, b.nationalityType = null, b.countryCode = null,
                b.veteranStatus = null, b.disabilityStatus = null,
                b.disabilityGradeCode = null, b.disabilityTypeCode = null,
                b.zipCode = null, b.addressBasic = null, b.addressDetail = null,
                b.createdBy = null, b.updatedBy = null
            where b.jobApplication.id = :applicationId""")
    int purgeBasicInfo(@Param("applicationId") Long applicationId);
```

- [ ] **Step 4: 서비스에 호출 추가**

`ApplicationPiiPurgeService.purgeRelationalPii()` 첫 줄(`purgeRepository.purgeAnswers(applicationId);` 위)에 추가:
```java
        purgeRepository.purgeBasicInfo(applicationId);
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicationPiiPurgeServiceTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: purge ApplicationBasicInfo PII (all columns null) in relational purge saga"
```

---

## Task 12: 문서 (md + HTML 리포트 + 이력 + PII 인벤토리)

**Files:**
- Create: `docs/codex/implementation/phase-10-application-basic-info.md`
- Create: `docs/codex/reports/phase-10-application-basic-info.html`
- Modify: `docs/codex/07-implementation-history.md`
- Modify: `docs/codex/`의 `phase-09-pii-field-inventory.md` (정확 경로는 `Glob`으로 확인)

- [ ] **Step 1: PII 인벤토리에 BASIC_INFO 등록**

`phase-09-pii-field-inventory.md`를 찾아(`Glob "**/phase-09-pii-field-inventory.md"`) `ApplicationBasicInfo` 섹션 추가: 전 개인정보 필드 **PURGE(null)**, KEEP_TOMBSTONE 없음, 암호화 컬럼 목록 명시.

- [ ] **Step 2: 구현 문서 작성** (`CLAUDE.md` Implementation Documentation Rules의 11개 항목 포함)

`phase-10-application-basic-info.md`: Phase summary / Implemented scope / Changed files / New classes / Modified classes / Class-by-class explanation(package·type·responsibility·key fields·related classes·notes) / API list(`GET·POST /applications/{id}/basic-info`, `GET /admin/applications/{id}/basic-info`) / Entity relationship / Business rules / Test coverage / Known limitations(사진·Excel export·CommonCode 시드) / Next phase.

- [ ] **Step 3: HTML 리포트 작성** (`docs/codex/templates/human-report-template.md` 준수, self-contained inline CSS)

`phase-10-application-basic-info.html`: Completed scope / API list / Changed files / Domain·Entity 구조 / Validation rules / Test results / Remaining issues / Next phase. 상태 배지 사용. **민감정보(장애·CI·password)는 노출 필드로 표기 금지.**

- [ ] **Step 4: 구현 이력 추가**

`07-implementation-history.md` 최상단에 새 항목(날짜·scope·implemented·APIs·business rules·tests·documentation·deferred·next).

- [ ] **Step 5: 전체 스위트 최종 확인**

Run: `gradlew.bat test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add docs/
git commit -m "docs: add phase-10 ApplicationBasicInfo implementation doc + HTML report + history + PII inventory"
```

---

## Self-Review (작성자 점검 결과)

**Spec coverage:** spec §3 구현 범위 1~14 → Task 1(enum)·2(commoncode)·3(entity/repo)·4(dto)·5(service)·6(controller)·7(submit)·8(completion)·9(admin)·10(pdf)·11(purge)·12(docs)로 전부 매핑됨. M1~M6 + Minor1·2 모두 포함(Minor1 사진은 문서 기준만, Minor2 상수는 Task 5에 고정).

**Placeholder scan:** 신규 production 코드는 전부 완전 코드. Task 7의 58개 테스트 마이그레이션과 Task 9·10·12의 일부 테스트/문서는 "레시피 + 예시 + 대상 목록" 형태 — 대량·반복 변경이거나 기존 파일별 fixture 차이가 커 단일 정답 코드를 박을 수 없는 영역이라 의도적으로 레시피화했고, 각 Task에 "전체 스위트 green까지" 게이트를 두어 완료 기준을 명확히 했다.

**Type consistency:** `ApplicationBasicInfo.create(...)` 16-arg 시그니처(Task 3) ↔ 서비스 `toBasicInfo`/헬퍼/파기 테스트(Task 5·7·11) 인자 순서 일치. `BasicInfoSaveRequest` 15-arg(Task 4) ↔ 서비스·테스트 생성 일치. `existsByGroupCodeAndCodeAndActiveTrue`(Task 2) ↔ 서비스 사용(Task 5) 일치. `BasicInfoResponse.of/prefill`, `AdminBasicInfoResponse.from`(Task 4) ↔ 서비스(Task 5·9) 일치. 완성도 `sectionCode()=="BASIC_INFO"`(Task 8) ↔ 테스트 단언 일치.

**알려진 리스크:** Task 7은 스위트 전반을 건드리므로 가장 크다 — 반드시 `gradlew.bat test` 전체 green을 커밋 게이트로 사용. `ApplicationDashboardServiceTest`(Task 8)·기존 PDF 테스트(Task 10)는 BasicInfo 시드 또는 기대값 보정이 필요할 수 있다.
