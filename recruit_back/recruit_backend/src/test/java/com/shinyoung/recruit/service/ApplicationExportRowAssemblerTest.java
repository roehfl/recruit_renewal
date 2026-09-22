package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationAward;
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.ApplicationCareer;
import com.shinyoung.recruit.domain.entity.ApplicationCertificate;
import com.shinyoung.recruit.domain.entity.ApplicationEducation;
import com.shinyoung.recruit.domain.entity.ApplicationGapPeriod;
import com.shinyoung.recruit.domain.entity.ApplicationLanguage;
import com.shinyoung.recruit.domain.entity.ApplicationMilitary;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAwardRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCareerRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCertificateRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.domain.repository.ApplicationGapPeriodRepository;
import com.shinyoung.recruit.domain.repository.ApplicationLanguageRepository;
import com.shinyoung.recruit.domain.repository.ApplicationMilitaryRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.response.ApplicationExportRow;
import com.shinyoung.recruit.enumeration.CampusType;
import com.shinyoung.recruit.enumeration.DayNightType;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.enumeration.EmploymentType;
import com.shinyoung.recruit.enumeration.GapType;
import com.shinyoung.recruit.enumeration.GraduationStatus;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPositionApplicationType;
import com.shinyoung.recruit.enumeration.MilitaryBranch;
import com.shinyoung.recruit.enumeration.MilitaryRank;
import com.shinyoung.recruit.enumeration.MilitaryServiceType;
import com.shinyoung.recruit.enumeration.MilitarySubjectType;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.enumeration.VeteranStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.shinyoung.recruit.service.ApplicationExportColumn.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 셀 값 표기를 실제 JPA 조회로 고정한다. 표기 규칙은 PDF(ApplicationPdfService)와 같아야 한다.
 * 공통코드는 테스트 DB에 등록돼 있지 않으므로 코드값 fallback 으로 검증한다(표시명 변환은 CommonCodeNamesTest).
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicationExportRowAssemblerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), ZoneId.of("UTC"));

    @Autowired private ApplicationExportRowAssembler assembler;
    @Autowired private JobPostingService jobPostingService;
    @Autowired private JobPostingRepository jobPostingRepository;
    @Autowired private ApplicantRepository applicantRepository;
    @Autowired private JobApplicationRepository jobApplicationRepository;
    @Autowired private ApplicationBasicInfoRepository basicInfoRepository;
    @Autowired private ApplicationMilitaryRepository militaryRepository;
    @Autowired private ApplicationEducationRepository educationRepository;
    @Autowired private ApplicationCareerRepository careerRepository;
    @Autowired private ApplicationCertificateRepository certificateRepository;
    @Autowired private ApplicationLanguageRepository languageRepository;
    @Autowired private ApplicationAwardRepository awardRepository;
    @Autowired private ApplicationGapPeriodRepository gapPeriodRepository;
    @Autowired private StageRepository stageRepository;
    @Autowired private StageResultRepository stageResultRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void default_columns_use_basic_info_final_education_and_latest_stage_result() {
        JobApplication application = persistApplication("asm-default");
        basicInfoRepository.save(ApplicationBasicInfo.create(
                application, "기본이름", null, NationalityType.DOMESTIC, null, LocalDate.of(1995, 1, 1),
                "01099990000", null, "basic@example.com", VeteranStatus.NOT_SUBJECT, null,
                DisabilityStatus.NOT_SUBJECT, null, null, null, null, null, null));
        // 저장 순서(= sortOrder/삽입 순서) 가 아니라 규칙(레벨·전형 순서)으로 선택되는지를 판별하도록 역순으로 시딩한다.
        educationRepository.save(education(application, EducationLevel.UNIVERSITY, "한국대", LocalDate.of(2020, 2, 20), 0));
        educationRepository.save(education(application, EducationLevel.HIGH_SCHOOL, "한국고", LocalDate.of(2014, 2, 10), 1));
        decide(application, "1차면접", StageType.FIRST_INTERVIEW, 2, StageResultStatus.FAILED);
        decide(application, "서류전형", StageType.DOCUMENT, 1, StageResultStatus.PASSED);

        Map<ApplicationExportColumn, String> values = assembleOne(application, ApplicationExportColumn.defaults());

        assertThat(values).hasSize(14)
                .containsEntry(APPLICATION_ID, String.valueOf(application.getId()))
                .containsEntry(JOB_POSITION_NAME, "Backend")
                .containsEntry(WORK_LOCATION, "")
                .containsEntry(STATUS, "제출 완료")
                .containsEntry(SUBMITTED_AT, "2026-05-10 10:00")
                .containsEntry(LATEST_STAGE_RESULT, "1차면접 불합격")
                .containsEntry(NAME, "기본이름")
                .containsEntry(BIRTH_DATE, "1995-01-01")
                // FIXED_CLOCK = 2026-06-15 기준 만 나이(목록과 같은 계산)
                .containsEntry(AGE, "31")
                .containsEntry(MOBILE_PHONE, "01099990000")
                .containsEntry(EMAIL, "basic@example.com")
                .containsEntry(FINAL_EDUCATION_LEVEL, "대학교")
                .containsEntry(FINAL_SCHOOL_NAME, "한국대")
                .containsEntry(FINAL_GRADUATION_DATE, "2020-02");
    }

    @Test
    void name_and_contact_fall_back_to_snapshot_and_account_without_basic_info() {
        JobApplication application = persistApplication("asm-fallback");

        Map<ApplicationExportColumn, String> values = assembleOne(application,
                List.of(NAME, MOBILE_PHONE, EMAIL, BIRTH_DATE, AGE, LATEST_STAGE_RESULT, FINAL_SCHOOL_NAME));

        assertThat(values)
                .containsEntry(NAME, "스냅샷")
                .containsEntry(MOBILE_PHONE, "01000000000")
                .containsEntry(EMAIL, "acct@example.com")
                .containsEntry(BIRTH_DATE, "")
                .containsEntry(AGE, "")
                .containsEntry(LATEST_STAGE_RESULT, "")
                .containsEntry(FINAL_SCHOOL_NAME, "");
    }

    @Test
    void basic_info_detail_columns_follow_pdf_notation() {
        JobApplication application = persistApplication("asm-basic");
        basicInfoRepository.save(ApplicationBasicInfo.create(
                application, "홍길동", "HONG GILDONG", NationalityType.FOREIGN, "ZZ", LocalDate.of(1990, 3, 3),
                "01011112222", "01033334444", "hong@example.com", VeteranStatus.SUBJECT, "국가유공자",
                DisabilityStatus.SUBJECT, "G9", "T9", "04524", "서울시 중구 세종대로 1", "101호", "ROUTE_X"));

        Map<ApplicationExportColumn, String> values = assembleOne(application,
                List.of(NAME_ENGLISH, NATIONALITY, EMERGENCY_PHONE, ADDRESS, VETERAN, DISABILITY, APPLICATION_ROUTE));

        assertThat(values)
                .containsEntry(NAME_ENGLISH, "HONG GILDONG")
                .containsEntry(NATIONALITY, "외국인 (ZZ)")
                .containsEntry(EMERGENCY_PHONE, "01033334444")
                .containsEntry(ADDRESS, "(04524) 서울시 중구 세종대로 1, 101호")
                .containsEntry(VETERAN, "대상 (국가유공자)")
                .containsEntry(DISABILITY, "대상 (등급: G9 / 유형: T9)")
                .containsEntry(APPLICATION_ROUTE, "ROUTE_X");
    }

    @Test
    void military_shows_service_detail_only_when_completed_and_never_the_exemption_reason() {
        JobApplication completed = persistApplication("asm-mil-done");
        militaryRepository.save(ApplicationMilitary.create(
                completed, MilitarySubjectType.COMPLETED, MilitaryServiceType.ACTIVE_DUTY, MilitaryBranch.ARMY,
                MilitaryRank.SERGEANT, LocalDate.of(2015, 3, 2), LocalDate.of(2016, 12, 1), null));
        JobApplication exempted = persistApplication("asm-mil-exempt");
        militaryRepository.save(ApplicationMilitary.create(
                exempted, MilitarySubjectType.EXEMPTED, null, null, null, null, null, "건강 사유"));
        entityManager.flush();

        List<Map<ApplicationExportColumn, String>> values = assembler.assemble(
                List.of(row(completed.getId()), row(exempted.getId())), List.of(MILITARY), assembler.newCodeNames());

        assertThat(values.get(0)).containsEntry(MILITARY, "필 / 육군 현역복무 / 병장 / 2015-03-02 ~ 2016-12-01");
        // 미필·면제 사유는 화면·PDF 에서도 마스킹되는 민감정보 — 엑셀에 넣지 않는다.
        assertThat(values.get(1)).containsEntry(MILITARY, "면제");
    }

    @Test
    void final_education_detail_columns_and_education_summary() {
        JobApplication application = persistApplication("asm-edu");
        educationRepository.save(ApplicationEducation.create(
                application, EducationLevel.HIGH_SCHOOL, "한국고", null, null, null, null,
                LocalDate.of(2011, 3, 2), LocalDate.of(2014, 2, 10), GraduationStatus.GRADUATED,
                DayNightType.DAY, null, false, null, 0));
        educationRepository.save(ApplicationEducation.create(
                application, EducationLevel.UNIVERSITY, "한국대", "경영학", "MT_X", "경제학", null,
                LocalDate.of(2014, 3, 2), LocalDate.of(2020, 2, 20), GraduationStatus.GRADUATED,
                DayNightType.NIGHT, CampusType.BRANCH, true, "ZZ", null, null,
                new BigDecimal("3.80"), new BigDecimal("4.50"), new BigDecimal("4.00"), new BigDecimal("4.50"), 1));

        Map<ApplicationExportColumn, String> values = assembleOne(application, List.of(
                FINAL_EDUCATION_LEVEL, FINAL_MAJOR, FINAL_ADDITIONAL_MAJOR, FINAL_GRADUATION_STATUS,
                FINAL_ADMISSION_DATE, FINAL_GRADUATION_DATE, FINAL_GPA, FINAL_MAJOR_GPA,
                FINAL_SCHOOL_LOCATION, FINAL_SCHOOL_TYPE, EDUCATIONS));

        assertThat(values)
                .containsEntry(FINAL_EDUCATION_LEVEL, "대학교")
                .containsEntry(FINAL_MAJOR, "경영학")
                .containsEntry(FINAL_ADDITIONAL_MAJOR, "MT_X: 경제학")
                .containsEntry(FINAL_GRADUATION_STATUS, "졸업")
                .containsEntry(FINAL_ADMISSION_DATE, "2014-03")
                .containsEntry(FINAL_GRADUATION_DATE, "2020-02")
                .containsEntry(FINAL_GPA, "3.80 / 4.50")
                .containsEntry(FINAL_MAJOR_GPA, "4.00 / 4.50")
                .containsEntry(FINAL_SCHOOL_LOCATION, "해외 (ZZ)")
                .containsEntry(FINAL_SCHOOL_TYPE, "편입, 분교, 야간")
                .containsEntry(EDUCATIONS,
                        "고등학교 / 한국고 / 2011-03-02 ~ 2014-02-10 / 졸업\n"
                                + "대학교 / 한국대 / 경영학 / 2014-03-02 ~ 2020-02-20 / 졸업 / 3.80 / 4.50");
    }

    @Test
    void one_to_many_sections_are_summarized_line_by_line_in_sort_order() {
        JobApplication application = persistApplication("asm-summary");
        careerRepository.save(ApplicationCareer.create(application, "B사", "영업팀", "대리", EmploymentType.FULL_TIME,
                LocalDate.of(2022, 1, 3), null, null, true, 5000, null, 1));
        careerRepository.save(ApplicationCareer.create(application, "A사", "개발팀", "사원", EmploymentType.CONTRACT,
                LocalDate.of(2019, 1, 2), LocalDate.of(2021, 12, 31), null, false, 3000, "계약만료", 0));
        certificateRepository.save(ApplicationCertificate.create(application, "정보처리기사", "한국산업인력공단",
                LocalDate.of(2019, 5, 10), "12345678", null, null, 0));
        languageRepository.save(ApplicationLanguage.create(application, "EN", "영어", "TOEIC", "TOEIC", "900", "상",
                LocalDate.of(2023, 1, 15), null, null, null, 0));
        awardRepository.save(ApplicationAward.create(application, "우수상", "한국대", LocalDate.of(2019, 11, 20), null, 0));
        gapPeriodRepository.save(ApplicationGapPeriod.create(application, LocalDate.of(2020, 3, 1),
                LocalDate.of(2020, 12, 31), GapType.OTHER, "어학연수", null, 0));
        // 삽입 순서가 아니라 stageOrder 로 정렬되는지를 판별하도록 역순으로 시딩한다.
        decide(application, "1차면접", StageType.FIRST_INTERVIEW, 2, StageResultStatus.HOLD);
        decide(application, "서류전형", StageType.DOCUMENT, 1, StageResultStatus.PASSED);

        Map<ApplicationExportColumn, String> values = assembleOne(application,
                List.of(STAGE_RESULTS, CAREERS, CURRENT_SALARY, CERTIFICATES, LANGUAGES, AWARDS, GAP_PERIODS));

        assertThat(values)
                .containsEntry(STAGE_RESULTS, "서류전형: 합격\n1차면접: 보류")
                .containsEntry(CAREERS,
                        "A사 / 개발팀 / 사원 / 계약 / 2019-01-02 ~ 2021-12-31 / 계약만료\n"
                                + "B사 / 영업팀 / 대리 / 정규직 / 2022-01-03 ~ 재직중")
                .containsEntry(CURRENT_SALARY, "B사: 5,000")
                // 자격증번호는 화면·PDF 에서 마스킹되는 값이라 넣지 않는다.
                .containsEntry(CERTIFICATES, "정보처리기사 / 한국산업인력공단 / 2019-05-10")
                .containsEntry(LANGUAGES, "영어 / TOEIC / 900 / 상 / 2023-01-15")
                .containsEntry(AWARDS, "우수상 / 한국대 / 2019-11")
                .containsEntry(GAP_PERIODS, "2020-03 ~ 2020-12 / 기타 / 어학연수");
    }

    @Test
    void one_to_many_values_are_grouped_per_application_within_a_page() {
        JobApplication a = persistApplication("asm-page-a");
        JobApplication b = persistApplication("asm-page-b");
        careerRepository.save(ApplicationCareer.create(a, "A사", "개발팀", "사원", EmploymentType.FULL_TIME,
                LocalDate.of(2020, 1, 1), null, null, true, 4000, null, 0));
        careerRepository.save(ApplicationCareer.create(b, "B사", "영업팀", "대리", EmploymentType.FULL_TIME,
                LocalDate.of(2021, 1, 1), null, null, true, 5000, null, 0));
        entityManager.flush();

        List<Map<ApplicationExportColumn, String>> values = assembler.assemble(
                List.of(row(a.getId()), row(b.getId())), List.of(CAREERS), assembler.newCodeNames());

        assertThat(values.get(0)).containsEntry(CAREERS, "A사 / 개발팀 / 사원 / 정규직 / 2020-01-01 ~ 재직중");
        assertThat(values.get(1)).containsEntry(CAREERS, "B사 / 영업팀 / 대리 / 정규직 / 2021-01-01 ~ 재직중");
    }

    private Map<ApplicationExportColumn, String> assembleOne(JobApplication application, List<ApplicationExportColumn> columns) {
        // assembler 는 조립 후 영속성 컨텍스트를 비운다 — 그 전에 시드를 DB 로 내보낸다.
        entityManager.flush();
        return assembler.assemble(List.of(row(application.getId())), columns, assembler.newCodeNames()).get(0);
    }

    /** base projection 값은 테스트 상수로 고정한다(쿼리는 Task 6/10 이 검증). */
    private static ApplicationExportRow row(Long applicationId) {
        return new ApplicationExportRow(
                applicationId, "스냅샷", "01000000000", "acct@example.com", "공고",
                JobPositionApplicationType.NEW_GRADUATE, "Backend", null, null, JobApplicationStatus.SUBMITTED,
                LocalDateTime.of(2026, 5, 10, 10, 0), null,
                LocalDateTime.of(2026, 5, 1, 9, 0), LocalDateTime.of(2026, 5, 10, 10, 0));
    }

    private JobApplication persistApplication(String loginId) {
        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                loginId + " 공고",
                "<p>content</p>",
                LocalDateTime.of(2026, 5, 1, 9, 0),
                LocalDateTime.of(2026, 5, 30, 18, 0),
                List.of(new JobPositionRequest("Backend", 1)),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)));
        JobPosting jobPosting = jobPostingRepository.findDetailById(jobPostingId).orElseThrow();
        JobPosition jobPosition = jobPosting.getJobPositions().stream()
                .min(Comparator.comparing(JobPosition::getSortOrder))
                .orElseThrow();

        String ci = loginId + "-ci";
        Applicant applicant = new Applicant(HashUtil.sha256(ci));
        applicant.setLoginId(loginId);
        applicant.setName("스냅샷");
        applicant.setUserName("스냅샷");
        applicant.setPassword("encoded-password");
        applicant.setPhoneNumber("01000000000");
        applicant.setEmail(loginId + "@example.com");
        applicantRepository.save(applicant);

        JobApplication application = JobApplication.create(
                applicant, jobPosting, jobPosition, "스냅샷", jobPosting.getTitle(), jobPosition.getPositionName());
        application.submit(LocalDateTime.of(2026, 5, 10, 10, 0));
        return jobApplicationRepository.save(application);
    }

    private static ApplicationEducation education(
            JobApplication application, EducationLevel level, String schoolName, LocalDate graduationDate, int sortOrder) {
        return ApplicationEducation.create(application, level, schoolName, null, null, null, null,
                null, graduationDate, GraduationStatus.GRADUATED, DayNightType.DAY, null, false, null, sortOrder);
    }

    private void decide(JobApplication application, String stageName, StageType type, int order, StageResultStatus status) {
        Stage stage = stageRepository.save(Stage.create(
                application.getJobPosting(), stageName, type, order, LocalDateTime.of(2026, 7, 1, 10, 0), false));
        StageResult result = StageResult.initialize(stage, application);
        result.updateResult(status, null, null, LocalDateTime.of(2026, 6, 1, 10, 0), "tester");
        stageResultRepository.save(result);
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
