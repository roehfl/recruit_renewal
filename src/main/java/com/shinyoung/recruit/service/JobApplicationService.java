package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPositionRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.response.ApplicationDetailResponse;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobApplicationService {

    private final JobApplicationRepository jobApplicationRepository;
    private final ApplicantRepository applicantRepository;
    private final JobPostingRepository jobPostingRepository;
    private final JobPositionRepository jobPositionRepository;
    private final Clock clock;

    @Transactional
    public Long create(Long applicantId, ApplicationCreateRequest request) {
        Applicant applicant = findApplicant(applicantId);
        JobPosting jobPosting = findJobPosting(request.jobPostingId());
        validateCreatableJobPosting(jobPosting);
        validateApplicationFormConfig(jobPosting);
        validateNotDuplicated(applicantId, jobPosting.getId());

        JobPosition jobPosition = findJobPosition(request.jobPositionId(), jobPosting.getId());
        JobApplication application = JobApplication.create(
                applicant,
                jobPosting,
                jobPosition,
                resolveApplicantNameSnapshot(applicant),
                jobPosting.getTitle(),
                jobPosition.getPositionName()
        );

        return jobApplicationRepository.save(application).getId();
    }

    public ApplicationDetailResponse getApplication(Long applicantId, Long applicationId) {
        JobApplication application = jobApplicationRepository.findByIdAndApplicantId(applicationId, applicantId)
                .orElseThrow(() -> new JobApplicationNotFoundException("지원서를 찾을 수 없습니다. id=" + applicationId));
        return ApplicationDetailResponse.from(application);
    }

    public ApplicationDetailResponse getMyApplicationByJobPosting(Long applicantId, Long jobPostingId) {
        JobApplication application = jobApplicationRepository.findByApplicantIdAndJobPostingId(applicantId, jobPostingId)
                .orElseThrow(() -> new JobApplicationNotFoundException("지원서를 찾을 수 없습니다. jobPostingId=" + jobPostingId));
        return ApplicationDetailResponse.from(application);
    }

    private Applicant findApplicant(Long applicantId) {
        return applicantRepository.findById(applicantId)
                .orElseThrow(() -> new InvalidJobApplicationException("지원자를 찾을 수 없습니다. applicantId=" + applicantId));
    }

    private JobPosting findJobPosting(Long jobPostingId) {
        return jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new JobPostingNotFoundException("채용공고를 찾을 수 없습니다. id=" + jobPostingId));
    }

    private JobPosition findJobPosition(Long jobPositionId, Long jobPostingId) {
        return jobPositionRepository.findByIdAndJobPostingId(jobPositionId, jobPostingId)
                .orElseThrow(() -> new InvalidJobApplicationException("모집분야를 찾을 수 없습니다. jobPositionId=" + jobPositionId));
    }

    private void validateCreatableJobPosting(JobPosting jobPosting) {
        if (jobPosting.getStatus() != JobPostingStatus.PUBLISHED) {
            throw new InvalidJobApplicationException("게시 중인 채용공고에만 지원할 수 있습니다.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isBefore(jobPosting.getReceptionStartDateTime()) || now.isAfter(jobPosting.getReceptionEndDateTime())) {
            throw new InvalidJobApplicationException("접수기간 내에만 지원서를 생성할 수 있습니다.");
        }
    }

    private void validateApplicationFormConfig(JobPosting jobPosting) {
        if (jobPosting.getApplicationFormConfig() == null) {
            throw new InvalidJobApplicationException("지원서 항목 설정이 없는 채용공고에는 지원할 수 없습니다.");
        }
    }

    private void validateNotDuplicated(Long applicantId, Long jobPostingId) {
        if (jobApplicationRepository.existsByApplicantIdAndJobPostingId(applicantId, jobPostingId)) {
            throw new InvalidJobApplicationException("이미 해당 채용공고에 지원서가 존재합니다.");
        }
    }

    private String resolveApplicantNameSnapshot(Applicant applicant) {
        if (applicant.getUserName() != null && !applicant.getUserName().isBlank()) {
            return applicant.getUserName();
        }
        if (applicant.getName() != null && !applicant.getName().isBlank()) {
            return applicant.getName();
        }
        throw new InvalidJobApplicationException("지원자 이름을 확인할 수 없습니다.");
    }
}
