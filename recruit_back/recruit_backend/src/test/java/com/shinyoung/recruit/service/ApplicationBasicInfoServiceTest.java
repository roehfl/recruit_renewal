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
                        VeteranStatus.SUBJECT, "국가유공자", DisabilityStatus.NOT_SUBJECT, null, null, null, null, null, null));

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
                        VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.NOT_SUBJECT, null, null, null, null, null, null)))
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
                        VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.NOT_SUBJECT, null, null, null, null, null, null)))
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

    @Test
    void disability_not_subject_with_codes_is_rejected() {
        Applicant applicant = createApplicant("bi-disability-forbidden", "DisabilityForbidden");
        Long applicationId = createApplication(applicant);

        assertThatThrownBy(() -> basicInfoService.saveBasicInfo(applicant.getId(), applicationId,
                new BasicInfoSaveRequest("홍길동", null, NationalityType.DOMESTIC, null,
                        LocalDate.of(1995, 1, 1), "01012345678", null, "hong@example.com",
                        VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.NOT_SUBJECT, "G1", "T1", null, null, null, null)))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void veteran_subject_requires_type_and_persists_it() {
        Applicant applicant = createApplicant("bi-veteran", "Veteran");
        Long applicationId = createApplication(applicant);

        // SUBJECT인데 종류 누락 → 거부
        assertThatThrownBy(() -> basicInfoService.saveBasicInfo(applicant.getId(), applicationId,
                new BasicInfoSaveRequest("홍길동", null, NationalityType.DOMESTIC, null,
                        LocalDate.of(1995, 1, 1), "01012345678", null, "hong@example.com",
                        VeteranStatus.SUBJECT, null, DisabilityStatus.NOT_SUBJECT, null, null, null, null, null, null)))
                .isInstanceOf(InvalidJobApplicationException.class);

        // SUBJECT + 종류 → 성공 + 평문 라운드트립
        BasicInfoResponse ok = basicInfoService.saveBasicInfo(applicant.getId(), applicationId,
                new BasicInfoSaveRequest("홍길동", null, NationalityType.DOMESTIC, null,
                        LocalDate.of(1995, 1, 1), "01012345678", null, "hong@example.com",
                        VeteranStatus.SUBJECT, "국가유공자", DisabilityStatus.NOT_SUBJECT, null, null, null, null, null, null));
        assertThat(ok.veteranType()).isEqualTo("국가유공자");
    }

    @Test
    void veteran_not_subject_with_type_is_rejected() {
        Applicant applicant = createApplicant("bi-veteran-forbidden", "VeteranForbidden");
        Long applicationId = createApplication(applicant);

        assertThatThrownBy(() -> basicInfoService.saveBasicInfo(applicant.getId(), applicationId,
                new BasicInfoSaveRequest("홍길동", null, NationalityType.DOMESTIC, null,
                        LocalDate.of(1995, 1, 1), "01012345678", null, "hong@example.com",
                        VeteranStatus.NOT_SUBJECT, "국가유공자", DisabilityStatus.NOT_SUBJECT, null, null, null, null, null, null)))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void birth_date_exactly_14_is_accepted_and_one_day_short_is_rejected() {
        Applicant applicant = createApplicant("bi-age-boundary", "AgeBoundary");
        Long applicationId = createApplication(applicant);

        // exactly 14 today (clock 2026-06-15) → accepted
        BasicInfoResponse ok = basicInfoService.saveBasicInfo(applicant.getId(), applicationId,
                ageRequest(LocalDate.of(2012, 6, 15)));
        assertThat(ok.persisted()).isTrue();

        // 13 years 364 days (born one day later) → rejected
        assertThatThrownBy(() -> basicInfoService.saveBasicInfo(
                applicant.getId(), applicationId, ageRequest(LocalDate.of(2012, 6, 16))))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void application_route_requires_active_common_code() {
        Applicant applicant = createApplicant("bi-route", "Route");
        Long applicationId = createApplication(applicant);

        // 미등록/비활성 코드 → 거부
        assertThatThrownBy(() -> basicInfoService.saveBasicInfo(applicant.getId(), applicationId,
                applicationRouteRequest("ZZ"))).isInstanceOf(InvalidJobApplicationException.class);

        // 활성 코드 → 성공
        commonCodeRepository.save(CommonCode.create("APPLICATION_ROUTE", "HOMEPAGE", "채용 홈페이지", 1, true, null));
        BasicInfoResponse ok = basicInfoService.saveBasicInfo(applicant.getId(), applicationId,
                applicationRouteRequest("HOMEPAGE"));
        assertThat(ok.applicationRouteCode()).isEqualTo("HOMEPAGE");

        // 미입력(선택 항목) → 성공
        BasicInfoResponse blank = basicInfoService.saveBasicInfo(applicant.getId(), applicationId,
                applicationRouteRequest(null));
        assertThat(blank.applicationRouteCode()).isNull();
    }

    // ---- fixtures ----

    private BasicInfoSaveRequest domesticRequest() {
        return new BasicInfoSaveRequest("홍길동", "Hong", NationalityType.DOMESTIC, null,
                LocalDate.of(1995, 1, 1), "01012345678", null, "hong@example.com",
                VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.NOT_SUBJECT, null, null, "06236", "서울", "101호", null);
    }

    private BasicInfoSaveRequest foreignRequest(String countryCode) {
        return new BasicInfoSaveRequest("홍길동", null, NationalityType.FOREIGN, countryCode,
                LocalDate.of(1995, 1, 1), "01012345678", null, "hong@example.com",
                VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.NOT_SUBJECT, null, null, null, null, null, null);
    }

    private BasicInfoSaveRequest disabilityRequest(String grade, String type) {
        return new BasicInfoSaveRequest("홍길동", null, NationalityType.DOMESTIC, null,
                LocalDate.of(1995, 1, 1), "01012345678", null, "hong@example.com",
                VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.SUBJECT, grade, type, null, null, null, null);
    }

    private BasicInfoSaveRequest ageRequest(LocalDate birthDate) {
        return new BasicInfoSaveRequest("홍길동", null, NationalityType.DOMESTIC, null,
                birthDate, "01012345678", null, "hong@example.com",
                VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.NOT_SUBJECT, null, null, null, null, null, null);
    }

    private BasicInfoSaveRequest applicationRouteRequest(String applicationRouteCode) {
        return new BasicInfoSaveRequest("홍길동", null, NationalityType.DOMESTIC, null,
                LocalDate.of(1995, 1, 1), "01012345678", null, "hong@example.com",
                VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.NOT_SUBJECT, null, null, null, null, null,
                applicationRouteCode);
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
