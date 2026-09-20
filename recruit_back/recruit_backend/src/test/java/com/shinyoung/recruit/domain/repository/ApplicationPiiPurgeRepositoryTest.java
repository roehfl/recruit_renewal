package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.common.crypto.CryptoHolder;
import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.config.CryptoConfig;
import com.shinyoung.recruit.config.JpaConfig;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code countUnpurgedByApplicantId} 검증(Phase 10 강제 파기 리뷰 대응). {@link ForcedPurgeService} 가
 * 엔티티 재조회 대신 이 집계 쿼리로 {@code allCleared} 를 판정하는 이유(OSIV 1차 캐시 stale 회피)는
 * 호출부 문제이므로 여기서는 쿼리 자체의 결과값만 검증한다.
 */
@DataJpaTest
@Import({CryptoConfig.class, JpaConfig.class, CryptoHolder.class})
class ApplicationPiiPurgeRepositoryTest {

    @Autowired private ApplicationPiiPurgeRepository applicationPiiPurgeRepository;
    @Autowired private ApplicantRepository applicantRepository;
    @Autowired private JobPostingRepository jobPostingRepository;
    @Autowired private JobApplicationRepository jobApplicationRepository;

    @Test
    void countUnpurgedByApplicantId는_미파기_지원서_수만_센다() {
        Applicant applicant = saveApplicant();

        // 지원 이력 0건 — 0 (강제 파기의 "지원 이력 0건 계정" 경로가 실제로 타는 값)
        assertThat(applicationPiiPurgeRepository.countUnpurgedByApplicantId(applicant.getId())).isZero();

        JobApplication app1 = saveApplication(applicant, saveJobPosting());
        JobApplication app2 = saveApplication(applicant, saveJobPosting());

        // 지원서 2건 모두 미파기 — 2
        assertThat(applicationPiiPurgeRepository.countUnpurgedByApplicantId(applicant.getId())).isEqualTo(2);

        app1.markPurged(1L, LocalDateTime.now());
        jobApplicationRepository.saveAndFlush(app1);

        // 1건만 파기 — 1
        assertThat(applicationPiiPurgeRepository.countUnpurgedByApplicantId(applicant.getId())).isEqualTo(1);

        app2.markPurged(1L, LocalDateTime.now());
        jobApplicationRepository.saveAndFlush(app2);

        // 전부 파기 — 0
        assertThat(applicationPiiPurgeRepository.countUnpurgedByApplicantId(applicant.getId())).isZero();
    }

    private Applicant saveApplicant() {
        String ci = "test-ci-" + UUID.randomUUID();
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId("applicant-" + UUID.randomUUID());
        applicant.setName("지원자");
        applicant.setEmail(UUID.randomUUID() + "@example.com");
        applicant.setUserName("지원자");
        applicant.setPhoneNumber("01000000000");
        return applicantRepository.saveAndFlush(applicant);
    }

    private JobPosting saveJobPosting() {
        JobPosting jobPosting = JobPosting.create("공고", "내용", start().minusDays(1), start().plusDays(10));
        jobPosting.replaceJobPositions(List.of(JobPosition.create("본사영업", 1)));
        return jobPostingRepository.saveAndFlush(jobPosting);
    }

    private JobApplication saveApplication(Applicant applicant, JobPosting jobPosting) {
        JobPosition jobPosition = jobPosting.getJobPositions().get(0);
        JobApplication application = JobApplication.create(
                applicant, jobPosting, jobPosition, "지원자", jobPosting.getTitle(), jobPosition.getPositionName());
        application.submit(start().minusDays(1));
        return jobApplicationRepository.saveAndFlush(application);
    }

    private LocalDateTime start() {
        return LocalDateTime.of(2026, 6, 1, 10, 0);
    }
}
