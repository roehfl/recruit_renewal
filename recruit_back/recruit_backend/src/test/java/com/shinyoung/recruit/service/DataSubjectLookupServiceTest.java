package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingImageRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.RetentionAnchorRequest;
import com.shinyoung.recruit.dto.request.RetentionPolicyRequest;
import com.shinyoung.recruit.dto.response.DataSubjectDetailResponse;
import com.shinyoung.recruit.dto.response.DataSubjectSummaryResponse;
import com.shinyoung.recruit.enumeration.AuditReasonCode;
import com.shinyoung.recruit.enumeration.RetentionBaselineType;
import com.shinyoung.recruit.exception.ApplicantNotFoundException;
import com.shinyoung.recruit.exception.InvalidRetentionRequestException;
import com.shinyoung.recruit.support.BasicInfoTestSupport;
import com.shinyoung.recruit.support.JobPostingImageTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 파기 대상자 검색·상세 조회 통합 검증(Phase 10 S1 Task 4). 검색은 조건 1개 이상 필수 +
 * 상한 50건, 상세는 지원서별 eligibility 판정을 함께 준다.
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class DataSubjectLookupServiceTest {

    @Autowired
    private DataSubjectLookupService dataSubjectLookupService;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private JobPostingImageRepository jobPostingImageRepository;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ApplicationBasicInfoRepository basicInfoRepository;

    @Autowired
    private RetentionPolicyService retentionPolicyService;

    @Autowired
    private RetentionAnchorService retentionAnchorService;

    @Test
    @DisplayName("검색 조건이 모두 비면 400")
    void searchRequiresAtLeastOneCondition() {
        assertThatThrownBy(() -> dataSubjectLookupService.search(null, "  ", null))
                .isInstanceOf(InvalidRetentionRequestException.class);
    }

    @Test
    @DisplayName("휴대폰은 하이픈을 제거하고 부분 일치로 찾는다")
    void searchNormalizesPhone() {
        Applicant applicant = createApplicant("lookup-phone");
        applicant.setPhoneNumber("01012345678");
        applicantRepository.save(applicant);

        List<DataSubjectSummaryResponse> found = dataSubjectLookupService.search(null, "010-1234-5678", null);

        assertThat(found).extracting(DataSubjectSummaryResponse::applicantId).contains(applicant.getId());
    }

    @Test
    @DisplayName("이미 익명화된 계정은 결과에서 제외한다")
    void searchExcludesPurgedAccounts() {
        // purgePersonalData() 는 userName/email/phoneNumber 까지 null 로 지워 ciHash 필터 없이도
        // LIKE 매칭이 실패해 이 테스트가 거짓 통과한다. ciHash 만 sentinel 로 바꿔 필터를 격리 검증한다.
        Applicant applicant = createApplicant("lookup-purged");
        applicant.setUserName("홍길동");
        applicant.setCiHash("PURGED:" + UUID.randomUUID());
        applicantRepository.save(applicant);

        assertThat(dataSubjectLookupService.search("홍길동", null, null)).isEmpty();
    }

    @Test
    @DisplayName("상세는 지원서별 적격성 판정을 함께 돌려준다")
    void detailIncludesEligibility() {
        Long jobPostingId = createPosting();
        JobPostingImageTestSupport.attachContentImage(jobPostingRepository, jobPostingImageRepository, jobPostingId);
        jobPostingService.publish(jobPostingId);

        Applicant applicant = createApplicant("lookup-detail");
        applicantRepository.save(applicant);
        createSubmittedApplication(applicant, jobPostingId);

        // 정책(HIRING_ENDED_AT, 30일) + anchor 5일 전 확정 → 보존기간 미도래(RETENTION_NOT_DUE).
        retentionPolicyService.create(new RetentionPolicyRequest(
                null, 30, RetentionBaselineType.HIRING_ENDED_AT, true, null, null), "privacy01");
        retentionAnchorService.fixAnchor(jobPostingId,
                new RetentionAnchorRequest(LocalDateTime.now().minusDays(5)), "privacy01");

        DataSubjectDetailResponse detail = dataSubjectLookupService.getDetail(applicant.getId());

        assertThat(detail.applications()).hasSize(1);
        assertThat(detail.applications().get(0).reasonCode()).isEqualTo(AuditReasonCode.RETENTION_NOT_DUE.name());
        assertThat(detail.hasActiveHold()).isFalse();
    }

    @Test
    @DisplayName("없는 지원자는 404")
    void detailNotFound() {
        assertThatThrownBy(() -> dataSubjectLookupService.getDetail(999_999L))
                .isInstanceOf(ApplicantNotFoundException.class);
    }

    private Long createPosting() {
        return jobPostingService.create(new JobPostingCreateRequest(
                "data subject lookup recruitment",
                "<p>content</p>",
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30),
                List.of(new JobPositionRequest("Backend", 1)),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)
        ));
    }

    private void createSubmittedApplication(Applicant applicant, Long jobPostingId) {
        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );
        BasicInfoTestSupport.seedValidBasicInfo(basicInfoRepository, jobApplicationRepository.findById(applicationId).orElseThrow());
        jobApplicationService.submit(applicant.getId(), applicationId);
    }

    private Long firstJobPositionId(Long jobPostingId) {
        JobPosting jobPosting = jobPostingRepository.findDetailById(jobPostingId).orElseThrow();
        return jobPosting.getJobPositions().stream()
                .sorted(Comparator.comparing(JobPosition::getSortOrder).thenComparing(JobPosition::getId))
                .map(JobPosition::getId)
                .findFirst()
                .orElseThrow();
    }

    private Applicant createApplicant(String loginId) {
        String ci = loginId + "-ci";
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId(loginId);
        applicant.setName("User " + loginId);
        applicant.setUserName("Applicant " + loginId);
        applicant.setPassword("encoded-password");
        applicant.setEmail(loginId + "@example.com");
        applicant.setPhoneNumber("01000000000");
        return applicantRepository.save(applicant);
    }
}
