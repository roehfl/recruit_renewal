package com.shinyoung.recruit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationCertificate;
import com.shinyoung.recruit.domain.entity.ApplicationEducation;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.School;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCertificateRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.SchoolRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.response.DimensionFunnelResponse;
import com.shinyoung.recruit.dto.response.FunnelResponse;
import com.shinyoung.recruit.dto.response.StageFunnelResponse;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.enumeration.GraduationStatus;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class AdminStatisticsControllerTest {

    @Autowired
    private WebApplicationContext context;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private StageRepository stageRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private StageResultRepository stageResultRepository;

    @Autowired
    private ApplicationEducationRepository educationRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private ApplicationCertificateRepository certificateRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void overall_funnel_returns_population_distribution_and_sequential_rates() throws Exception {
        JobPosting jobPosting = saveJobPosting("Backend");
        JobPosition backend = position(jobPosting, "Backend");
        Stage stage1 = saveStage(jobPosting, "Document", StageType.DOCUMENT, 1);
        Stage stage2 = saveStage(jobPosting, "Interview", StageType.FIRST_INTERVIEW, 2);

        JobApplication app1 = submittedApplication(jobPosting, backend, "App1");
        JobApplication app2 = submittedApplication(jobPosting, backend, "App2");
        JobApplication app3 = submittedApplication(jobPosting, backend, "App3");
        submittedApplication(jobPosting, backend, "App4"); // no results -> NO_RESULT
        JobApplication app5 = withdrawnApplication(jobPosting, backend, "App5");

        result(stage1, app1, StageResultStatus.PASSED);
        result(stage2, app1, StageResultStatus.PASSED);
        result(stage1, app2, StageResultStatus.PASSED);
        result(stage2, app2, StageResultStatus.FAILED);
        result(stage1, app3, StageResultStatus.FAILED);
        result(stage1, app5, StageResultStatus.WITHDRAWN);

        FunnelResponse funnel = getFunnel(jobPosting.getId(), null);

        assertThat(funnel.dimension()).isNull();
        assertThat(funnel.dimensions()).isEmpty();
        assertThat(funnel.population().p()).isEqualTo(5);
        assertThat(funnel.population().currentlySubmittedCount()).isEqualTo(4);
        assertThat(funnel.population().withdrawnCount()).isEqualTo(1);
        assertThat(funnel.stages()).hasSize(2);

        StageFunnelResponse first = funnel.stages().get(0);
        assertThat(first.distribution().passed()).isEqualTo(2);
        assertThat(first.distribution().failed()).isEqualTo(1);
        assertThat(first.distribution().withdrawn()).isEqualTo(1);
        assertThat(first.distribution().noResult()).isEqualTo(1);
        assertThat(first.distribution().pending()).isZero();
        assertThat(first.distribution().absent()).isZero();
        assertThat(first.distribution().hold()).isZero();
        assertThat(distributionSum(first)).isEqualTo(5L); // sum == |P|
        assertThat(first.funnelPassedCount()).isEqualTo(2);
        assertThat(first.cumulativeRate()).isCloseTo(0.4, within(1e-9));
        assertThat(first.stepConversionRate()).isCloseTo(0.4, within(1e-9));

        StageFunnelResponse second = funnel.stages().get(1);
        assertThat(second.distribution().passed()).isEqualTo(1);
        assertThat(second.distribution().failed()).isEqualTo(1);
        assertThat(second.distribution().noResult()).isEqualTo(3);
        assertThat(distributionSum(second)).isEqualTo(5L);
        assertThat(second.funnelPassedCount()).isEqualTo(1);
        assertThat(second.cumulativeRate()).isCloseTo(0.2, within(1e-9));
        assertThat(second.stepConversionRate()).isCloseTo(0.5, within(1e-9)); // |S2|/|S1| = 1/2
    }

    @Test
    void position_dimension_returns_funnel_per_position_group() throws Exception {
        JobPosting jobPosting = saveJobPosting("Backend", "Frontend");
        JobPosition backend = position(jobPosting, "Backend");
        JobPosition frontend = position(jobPosting, "Frontend");
        Stage stage1 = saveStage(jobPosting, "Document", StageType.DOCUMENT, 1);

        JobApplication backend1 = submittedApplication(jobPosting, backend, "B1");
        JobApplication backend2 = submittedApplication(jobPosting, backend, "B2");
        JobApplication frontend1 = submittedApplication(jobPosting, frontend, "F1");
        result(stage1, backend1, StageResultStatus.PASSED);
        result(stage1, backend2, StageResultStatus.FAILED);
        result(stage1, frontend1, StageResultStatus.PASSED);

        FunnelResponse funnel = getFunnel(jobPosting.getId(), "POSITION");

        assertThat(funnel.dimension().name()).isEqualTo("POSITION");
        assertThat(funnel.population().p()).isEqualTo(3);
        assertThat(funnel.dimensions()).hasSize(2);

        DimensionFunnelResponse backendGroup = funnel.dimensions().stream()
                .filter(group -> group.groupName().equals("Backend"))
                .findFirst().orElseThrow();
        assertThat(backendGroup.population().p()).isEqualTo(2);
        assertThat(backendGroup.stages().get(0).distribution().passed()).isEqualTo(1);
        assertThat(backendGroup.stages().get(0).distribution().failed()).isEqualTo(1);
        assertThat(backendGroup.stages().get(0).funnelPassedCount()).isEqualTo(1);
        assertThat(backendGroup.stages().get(0).cumulativeRate()).isCloseTo(0.5, within(1e-9));

        DimensionFunnelResponse frontendGroup = funnel.dimensions().stream()
                .filter(group -> group.groupName().equals("Frontend"))
                .findFirst().orElseThrow();
        assertThat(frontendGroup.population().p()).isEqualTo(1);
        assertThat(frontendGroup.stages().get(0).funnelPassedCount()).isEqualTo(1);
        assertThat(frontendGroup.stages().get(0).cumulativeRate()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void rates_use_sequential_passed_set_not_raw_distribution_passed() throws Exception {
        JobPosting jobPosting = saveJobPosting("Backend");
        JobPosition backend = position(jobPosting, "Backend");
        Stage stage1 = saveStage(jobPosting, "Document", StageType.DOCUMENT, 1);
        Stage stage2 = saveStage(jobPosting, "Interview", StageType.FIRST_INTERVIEW, 2);

        JobApplication app1 = submittedApplication(jobPosting, backend, "A1");
        JobApplication app2 = submittedApplication(jobPosting, backend, "A2");
        JobApplication app3 = submittedApplication(jobPosting, backend, "A3");

        result(stage1, app1, StageResultStatus.PASSED);
        result(stage1, app2, StageResultStatus.FAILED);
        // app3 stage1: no result (NO_RESULT)
        result(stage2, app1, StageResultStatus.PASSED);
        result(stage2, app2, StageResultStatus.PASSED); // 보정 데이터: 이전 FAILED인데 후속 PASSED
        result(stage2, app3, StageResultStatus.PASSED); // 보정 데이터: 이전 NO_RESULT인데 후속 PASSED

        FunnelResponse funnel = getFunnel(jobPosting.getId(), null);

        StageFunnelResponse second = funnel.stages().get(1);
        assertThat(second.distribution().passed()).isEqualTo(3);      // raw 분포
        assertThat(second.funnelPassedCount()).isEqualTo(1);          // 순차 통과(app1만 stage1 생존)
        assertThat(second.stepConversionRate()).isCloseTo(1.0, within(1e-9)); // |S2|/|S1| = 1/1
        assertThat(second.cumulativeRate()).isCloseTo(1.0 / 3.0, within(1e-9)); // |S2|/|P| = 1/3
    }

    @Test
    void distribution_distinguishes_pending_absent_hold_and_no_result() throws Exception {
        JobPosting jobPosting = saveJobPosting("Backend");
        JobPosition backend = position(jobPosting, "Backend");
        Stage stage1 = saveStage(jobPosting, "Document", StageType.DOCUMENT, 1);

        JobApplication app1 = submittedApplication(jobPosting, backend, "A1");
        JobApplication app2 = submittedApplication(jobPosting, backend, "A2");
        JobApplication app3 = submittedApplication(jobPosting, backend, "A3");
        submittedApplication(jobPosting, backend, "A4"); // 결과 row 없음 -> NO_RESULT

        result(stage1, app1, StageResultStatus.PENDING); // row 있음, 결정 전
        result(stage1, app2, StageResultStatus.ABSENT);
        result(stage1, app3, StageResultStatus.HOLD);

        FunnelResponse funnel = getFunnel(jobPosting.getId(), null);

        StageFunnelResponse first = funnel.stages().get(0);
        assertThat(first.distribution().pending()).isEqualTo(1);
        assertThat(first.distribution().absent()).isEqualTo(1);
        assertThat(first.distribution().hold()).isEqualTo(1);
        assertThat(first.distribution().noResult()).isEqualTo(1); // PENDING과 구분
        assertThat(first.distribution().passed()).isZero();
        assertThat(distributionSum(first)).isEqualTo(4L); // = |P|
        assertThat(funnel.population().p()).isEqualTo(4);
    }

    @Test
    void population_withdrawn_count_is_independent_of_stage_result_withdrawn() throws Exception {
        JobPosting jobPosting = saveJobPosting("Backend");
        JobPosition backend = position(jobPosting, "Backend");
        Stage stage1 = saveStage(jobPosting, "Document", StageType.DOCUMENT, 1);

        JobApplication withdrawnApp = withdrawnApplication(jobPosting, backend, "W1"); // application status WITHDRAWN
        JobApplication submittedApp = submittedApplication(jobPosting, backend, "S1"); // application status SUBMITTED

        result(stage1, withdrawnApp, StageResultStatus.PASSED);    // 철회 지원서의 stage 결과는 PASSED
        result(stage1, submittedApp, StageResultStatus.WITHDRAWN); // 제출 지원서의 stage 결과는 WITHDRAWN

        FunnelResponse funnel = getFunnel(jobPosting.getId(), null);

        assertThat(funnel.population().p()).isEqualTo(2);
        assertThat(funnel.population().withdrawnCount()).isEqualTo(1);        // application-level (withdrawnApp)
        assertThat(funnel.population().currentlySubmittedCount()).isEqualTo(1);

        StageFunnelResponse first = funnel.stages().get(0);
        assertThat(first.distribution().withdrawn()).isEqualTo(1); // stage result status (submittedApp)
        assertThat(first.distribution().passed()).isEqualTo(1);    // withdrawnApp의 stage 결과
    }

    @Test
    void draft_application_is_excluded_from_population() throws Exception {
        JobPosting jobPosting = saveJobPosting("Backend");
        JobPosition backend = position(jobPosting, "Backend");
        Stage stage1 = saveStage(jobPosting, "Document", StageType.DOCUMENT, 1);

        submittedApplication(jobPosting, backend, "Submitted");
        withdrawnApplication(jobPosting, backend, "Withdrawn"); // submittedAt 있음 -> P 포함
        draftApplication(jobPosting, backend, "Draft");         // submittedAt 없음 -> P 제외

        FunnelResponse funnel = getFunnel(jobPosting.getId(), null);

        assertThat(funnel.population().p()).isEqualTo(2);
        assertThat(distributionSum(funnel.stages().get(0))).isEqualTo(2L);
    }

    @Test
    void unsupported_dimension_returns_bad_request() throws Exception {
        JobPosting jobPosting = saveJobPosting("Backend");

        // POSITION/SCHOOL/CERTIFICATE 외 잘못된 dimension 값은 400(SCHOOL=08d, CERTIFICATE=08e 로 지원됨).
        mockMvc.perform(get("/api/admin/job-postings/{jobPostingId}/statistics/funnel", jobPosting.getId())
                        .param("dimension", "NOT_A_DIMENSION")
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void certificate_dimension_counts_distinct_holders_per_normalized_name_with_overlap() throws Exception {
        JobPosting jobPosting = saveJobPosting("Backend");
        JobPosition backend = position(jobPosting, "Backend");
        saveStage(jobPosting, "Document", StageType.DOCUMENT, 1);

        JobApplication app1 = submittedApplication(jobPosting, backend, "A1");
        certificate(app1, "정보처리기사");
        certificate(app1, "TOEIC"); // app1은 두 그룹에 중복 집계
        JobApplication app2 = submittedApplication(jobPosting, backend, "A2");
        certificate(app2, "정보처리기사 "); // 정규화(trim) → 정보처리기사로 병합
        JobApplication app3 = submittedApplication(jobPosting, backend, "A3");
        certificate(app3, "TOEIC");
        submittedApplication(jobPosting, backend, "A4"); // 자격 없음 → 어느 그룹에도 미포함

        FunnelResponse funnel = getFunnel(jobPosting.getId(), "CERTIFICATE", null);

        assertThat(funnel.dimension().name()).isEqualTo("CERTIFICATE");
        assertThat(funnel.population().p()).isEqualTo(4);
        assertThat(group(funnel, "정보처리기사").population().p()).isEqualTo(2); // app1, app2(정규화)
        assertThat(group(funnel, "TOEIC").population().p()).isEqualTo(2);       // app1, app3 (app1 중복)
    }

    @Test
    void certificate_dimension_topN_folds_overflow_into_other() throws Exception {
        JobPosting jobPosting = saveJobPosting("Backend");
        JobPosition backend = position(jobPosting, "Backend");
        saveStage(jobPosting, "Document", StageType.DOCUMENT, 1);

        certificate(submittedApplication(jobPosting, backend, "A1"), "Common");
        certificate(submittedApplication(jobPosting, backend, "A2"), "Common");
        certificate(submittedApplication(jobPosting, backend, "A3"), "Common");
        certificate(submittedApplication(jobPosting, backend, "A4"), "Rare1");
        certificate(submittedApplication(jobPosting, backend, "A5"), "Rare2");

        FunnelResponse funnel = getFunnel(jobPosting.getId(), "CERTIFICATE", 1); // topN=1 → Common만 개별

        assertThat(funnel.dimensions()).hasSize(2);
        assertThat(group(funnel, "Common").population().p()).isEqualTo(3);
        assertThat(group(funnel, "기타").population().p()).isEqualTo(2); // Rare1 보유자 + Rare2 보유자 distinct
    }

    @Test
    void school_dimension_groups_by_final_school_with_other_bucket() throws Exception {
        JobPosting jobPosting = saveJobPosting("Backend");
        JobPosition backend = position(jobPosting, "Backend");
        Stage stage1 = saveStage(jobPosting, "Document", StageType.DOCUMENT, 1);
        School alpha = saveSchool("Alpha University");
        School beta = saveSchool("Beta University");

        JobApplication app1 = submittedApplication(jobPosting, backend, "A1");
        education(app1, EducationLevel.HIGH_SCHOOL, null, 0);   // 최종학력 아님
        education(app1, EducationLevel.UNIVERSITY, alpha.getId(), 1); // 최종학력 = Alpha
        JobApplication app2 = submittedApplication(jobPosting, backend, "A2");
        education(app2, EducationLevel.UNIVERSITY, alpha.getId(), 0);
        JobApplication app3 = submittedApplication(jobPosting, backend, "A3");
        education(app3, EducationLevel.UNIVERSITY, beta.getId(), 0);
        JobApplication app4 = submittedApplication(jobPosting, backend, "A4");
        education(app4, EducationLevel.UNIVERSITY, null, 0);    // 직접입력 → 미매칭
        submittedApplication(jobPosting, backend, "A5");        // 학력 없음 → 미매칭

        result(stage1, app1, StageResultStatus.PASSED);
        result(stage1, app2, StageResultStatus.FAILED);
        result(stage1, app3, StageResultStatus.PASSED);

        FunnelResponse funnel = getFunnel(jobPosting.getId(), "SCHOOL", null);

        assertThat(funnel.dimension().name()).isEqualTo("SCHOOL");
        assertThat(funnel.population().p()).isEqualTo(5);

        DimensionFunnelResponse alphaGroup = group(funnel, "Alpha University");
        assertThat(alphaGroup.groupId()).isEqualTo(alpha.getId());
        assertThat(alphaGroup.population().p()).isEqualTo(2);
        assertThat(alphaGroup.stages().get(0).funnelPassedCount()).isEqualTo(1); // app1 PASSED, app2 FAILED

        DimensionFunnelResponse betaGroup = group(funnel, "Beta University");
        assertThat(betaGroup.population().p()).isEqualTo(1);

        DimensionFunnelResponse other = group(funnel, "기타");
        assertThat(other.groupId()).isNull();
        assertThat(other.population().p()).isEqualTo(2); // app4(미매칭) + app5(학력없음)
    }

    @Test
    void school_dimension_topN_folds_overflow_and_unmatched_into_other() throws Exception {
        JobPosting jobPosting = saveJobPosting("Backend");
        JobPosition backend = position(jobPosting, "Backend");
        saveStage(jobPosting, "Document", StageType.DOCUMENT, 1);
        School alpha = saveSchool("Alpha University");
        School beta = saveSchool("Beta University");

        education(submittedApplication(jobPosting, backend, "A1"), EducationLevel.UNIVERSITY, alpha.getId(), 0);
        education(submittedApplication(jobPosting, backend, "A2"), EducationLevel.UNIVERSITY, alpha.getId(), 0);
        education(submittedApplication(jobPosting, backend, "A3"), EducationLevel.UNIVERSITY, beta.getId(), 0);
        submittedApplication(jobPosting, backend, "A4"); // 미매칭

        FunnelResponse funnel = getFunnel(jobPosting.getId(), "SCHOOL", 1); // topN=1 → Alpha만 개별

        // 개별 학교는 Alpha(인원 2) 1개, 나머지(Beta 1 + 미매칭 1)는 '기타'로 합산.
        assertThat(funnel.dimensions()).hasSize(2);
        assertThat(group(funnel, "Alpha University").population().p()).isEqualTo(2);
        assertThat(group(funnel, "기타").population().p()).isEqualTo(2);
    }

    @Test
    void school_dimension_tie_break_prefers_education_with_school_id_at_same_level() throws Exception {
        JobPosting jobPosting = saveJobPosting("Backend");
        JobPosition backend = position(jobPosting, "Backend");
        saveStage(jobPosting, "Document", StageType.DOCUMENT, 1);
        School alpha = saveSchool("Alpha University");

        JobApplication app = submittedApplication(jobPosting, backend, "A1");
        education(app, EducationLevel.UNIVERSITY, null, 0);          // 같은 레벨, schoolId 없음
        education(app, EducationLevel.UNIVERSITY, alpha.getId(), 1); // 같은 레벨, schoolId=Alpha → 우선

        FunnelResponse funnel = getFunnel(jobPosting.getId(), "SCHOOL", null);

        assertThat(funnel.dimensions()).hasSize(1);
        assertThat(group(funnel, "Alpha University").population().p()).isEqualTo(1); // 기타 아님
    }

    @Test
    void school_dimension_topN_zero_uses_default_and_does_not_fold() throws Exception {
        JobPosting jobPosting = saveJobPosting("Backend");
        JobPosition backend = position(jobPosting, "Backend");
        saveStage(jobPosting, "Document", StageType.DOCUMENT, 1);
        School alpha = saveSchool("Alpha University");
        School beta = saveSchool("Beta University");

        education(submittedApplication(jobPosting, backend, "A1"), EducationLevel.UNIVERSITY, alpha.getId(), 0);
        education(submittedApplication(jobPosting, backend, "A2"), EducationLevel.UNIVERSITY, beta.getId(), 0);

        // topN=0 → limit(0)이 아니라 기본 10으로 clamp → 두 학교 모두 개별 노출, '기타' 없음.
        FunnelResponse funnel = getFunnel(jobPosting.getId(), "SCHOOL", 0);

        assertThat(funnel.dimensions()).hasSize(2);
        assertThat(group(funnel, "Alpha University").population().p()).isEqualTo(1);
        assertThat(group(funnel, "Beta University").population().p()).isEqualTo(1);
    }

    @Test
    void school_dimension_dangling_school_id_folds_into_other() throws Exception {
        JobPosting jobPosting = saveJobPosting("Backend");
        JobPosition backend = position(jobPosting, "Backend");
        saveStage(jobPosting, "Document", StageType.DOCUMENT, 1);

        // School 테이블에 없는 schoolId(dangling) → 개별 그룹이 아니라 '기타'로 합산되어야 한다.
        education(submittedApplication(jobPosting, backend, "A1"), EducationLevel.UNIVERSITY, 999999L, 0);

        FunnelResponse funnel = getFunnel(jobPosting.getId(), "SCHOOL", null);

        assertThat(funnel.dimensions()).hasSize(1);
        assertThat(group(funnel, "기타").population().p()).isEqualTo(1);
        assertThat(funnel.dimensions()).allSatisfy(g -> assertThat(g.groupName()).isNotNull());
        assertThat(funnel.dimensions()).noneSatisfy(g -> assertThat(g.groupId()).isEqualTo(999999L));
    }

    @Test
    void unknown_job_posting_returns_not_found() throws Exception {
        mockMvc.perform(get("/api/admin/job-postings/{jobPostingId}/statistics/funnel", 999999L)
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isNotFound());
    }

    @Test
    void funnel_blocks_applicant_and_anonymous() throws Exception {
        JobPosting jobPosting = saveJobPosting("Backend");
        Applicant applicant = saveApplicant("blocked");

        mockMvc.perform(get("/api/admin/job-postings/{jobPostingId}/statistics/funnel", jobPosting.getId())
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/job-postings/{jobPostingId}/statistics/funnel", jobPosting.getId())
                        .with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private FunnelResponse getFunnel(Long jobPostingId, String dimension) throws Exception {
        return getFunnel(jobPostingId, dimension, null);
    }

    private FunnelResponse getFunnel(Long jobPostingId, String dimension, Integer topN) throws Exception {
        var request = get("/api/admin/job-postings/{jobPostingId}/statistics/funnel", jobPostingId)
                .with(authentication(adminAuthentication()));
        if (dimension != null) {
            request = request.param("dimension", dimension);
        }
        if (topN != null) {
            request = request.param("topN", String.valueOf(topN));
        }
        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).get("data");
        return objectMapper.treeToValue(data, FunnelResponse.class);
    }

    private DimensionFunnelResponse group(FunnelResponse funnel, String groupName) {
        return funnel.dimensions().stream()
                .filter(group -> groupName.equals(group.groupName()))
                .findFirst().orElseThrow();
    }

    private School saveSchool(String name) {
        return schoolRepository.saveAndFlush(
                School.create(null, name, "UNIVERSITY", null, "Seoul", null, "KR", true));
    }

    private void education(JobApplication application, EducationLevel level, Long schoolId, int sortOrder) {
        educationRepository.saveAndFlush(ApplicationEducation.create(
                application, level, "School-" + sortOrder, null, null, null, null,
                GraduationStatus.GRADUATED, null, null, false, "KR", schoolId, sortOrder));
    }

    private void certificate(JobApplication application, String certificateName) {
        certificateRepository.saveAndFlush(ApplicationCertificate.create(
                application, certificateName, "Org", LocalDate.of(2023, 1, 1), null, null, null, 0));
    }

    private long distributionSum(StageFunnelResponse stage) {
        return stage.distribution().passed()
                + stage.distribution().failed()
                + stage.distribution().absent()
                + stage.distribution().hold()
                + stage.distribution().pending()
                + stage.distribution().withdrawn()
                + stage.distribution().noResult();
    }

    private JobPosting saveJobPosting(String... positionNames) {
        JobPosting jobPosting = JobPosting.create(
                "Posting " + UUID.randomUUID(),
                "Content",
                LocalDateTime.of(2026, 5, 1, 9, 0),
                LocalDateTime.of(2026, 12, 31, 18, 0)
        );
        int sortOrder = 1;
        List<JobPosition> positions = new java.util.ArrayList<>();
        for (String name : positionNames) {
            positions.add(JobPosition.create(name, sortOrder++));
        }
        jobPosting.replaceJobPositions(positions);
        return jobPostingRepository.saveAndFlush(jobPosting);
    }

    private JobPosition position(JobPosting jobPosting, String name) {
        return jobPosting.getJobPositions().stream()
                .filter(jobPosition -> jobPosition.getPositionName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private Stage saveStage(JobPosting jobPosting, String name, StageType type, int order) {
        return stageRepository.saveAndFlush(Stage.create(jobPosting, name, type, order, null, false));
    }

    private JobApplication submittedApplication(JobPosting jobPosting, JobPosition jobPosition, String name) {
        JobApplication application = newApplication(jobPosting, jobPosition, name);
        application.submit(LocalDateTime.of(2026, 5, 10, 10, 0));
        return jobApplicationRepository.saveAndFlush(application);
    }

    private JobApplication withdrawnApplication(JobPosting jobPosting, JobPosition jobPosition, String name) {
        JobApplication application = newApplication(jobPosting, jobPosition, name);
        application.submit(LocalDateTime.of(2026, 5, 10, 10, 0));
        application.withdraw(LocalDateTime.of(2026, 5, 12, 10, 0));
        return jobApplicationRepository.saveAndFlush(application);
    }

    private JobApplication draftApplication(JobPosting jobPosting, JobPosition jobPosition, String name) {
        // submit하지 않아 submittedAt == null, status == DRAFT -> 모집단 P에서 제외되어야 한다.
        JobApplication application = newApplication(jobPosting, jobPosition, name);
        return jobApplicationRepository.saveAndFlush(application);
    }

    private JobApplication newApplication(JobPosting jobPosting, JobPosition jobPosition, String name) {
        Applicant applicant = saveApplicant(name);
        return JobApplication.create(
                applicant,
                jobPosting,
                jobPosition,
                name,
                jobPosting.getTitle(),
                jobPosition.getPositionName()
        );
    }

    private void result(Stage stage, JobApplication application, StageResultStatus status) {
        StageResult stageResult = StageResult.initialize(stage, application);
        if (status != StageResultStatus.PENDING) {
            stageResult.updateResult(status, null, null, LocalDateTime.of(2026, 6, 1, 12, 0), "tester");
        }
        stageResultRepository.saveAndFlush(stageResult);
    }

    private Applicant saveApplicant(String name) {
        String ci = "ci-" + UUID.randomUUID();
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId("applicant-" + UUID.randomUUID());
        applicant.setName(name);
        applicant.setUserName(name);
        applicant.setEmail(UUID.randomUUID() + "@example.com");
        applicant.setPhoneNumber("01000000000");
        return applicantRepository.saveAndFlush(applicant);
    }

    private Authentication adminAuthentication() {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "stats-admin-" + UUID.randomUUID(),
                "Recruit",
                "Stats Admin",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    private Authentication applicantAuthentication(Applicant applicant) {
        CustomUserDetails userDetails = CustomUserDetails.fromUser(
                applicant,
                List.of(new SimpleGrantedAuthority("ROLE_APPLICANT"))
        );
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}
