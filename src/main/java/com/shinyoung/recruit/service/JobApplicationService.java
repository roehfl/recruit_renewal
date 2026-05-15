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
import com.shinyoung.recruit.dto.request.ApplicationUpdateRequest;
import com.shinyoung.recruit.dto.response.ApplicationDetailResponse;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
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
        validatePublishedAndAccepting(jobPosting);
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
        JobApplication application = findApplication(applicantId, applicationId);
        return ApplicationDetailResponse.from(application);
    }

    public ApplicationDetailResponse getMyApplicationByJobPosting(Long applicantId, Long jobPostingId) {
        JobApplication application = jobApplicationRepository.findByApplicantIdAndJobPostingId(applicantId, jobPostingId)
                .orElseThrow(() -> new JobApplicationNotFoundException("지원서를 찾을 수 없습니다. jobPostingId=" + jobPostingId));
        return ApplicationDetailResponse.from(application);
    }

    @Transactional
    public Long updateDraft(Long applicantId, Long applicationId, ApplicationUpdateRequest request) {
        JobApplication application = findApplication(applicantId, applicationId);
        validatePublishedAndAccepting(application.getJobPosting());
        validateDraftForUpdate(application);

        JobPosition jobPosition = findJobPosition(request.jobPositionId(), application.getJobPosting().getId());
        application.updateDraft(jobPosition, jobPosition.getPositionName());

        return application.getId();
    }

    @Transactional
    public Long submit(Long applicantId, Long applicationId) {
        JobApplication application = findApplication(applicantId, applicationId);
        validatePublishedAndAccepting(application.getJobPosting());
        validateDraftForSubmit(application);
        validateApplicationFormConfig(application.getJobPosting());
        validateSelectedJobPosition(application);
        // Detailed required-section validation belongs to later Application section phases.
        application.submit(LocalDateTime.now(clock));

        return application.getId();
    }

    @Transactional
    public Long withdraw(Long applicantId, Long applicationId) {
        JobApplication application = findApplication(applicantId, applicationId);
        validatePublishedAndAccepting(application.getJobPosting());
        validateSubmittedForWithdraw(application);
        application.withdraw(LocalDateTime.now(clock));

        return application.getId();
    }

    private JobApplication findApplication(Long applicantId, Long applicationId) {
        return jobApplicationRepository.findByIdAndApplicantId(applicationId, applicantId)
                .orElseThrow(() -> new JobApplicationNotFoundException("지원서를 찾을 수 없습니다. id=" + applicationId));
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

    private void validatePublishedAndAccepting(JobPosting jobPosting) {
        if (jobPosting.getStatus() != JobPostingStatus.PUBLISHED) {
            throw new InvalidJobApplicationException("게시 중인 채용공고에만 지원서를 처리할 수 있습니다.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isBefore(jobPosting.getReceptionStartDateTime()) || now.isAfter(jobPosting.getReceptionEndDateTime())) {
            throw new InvalidJobApplicationException("접수기간 내에만 지원서를 처리할 수 있습니다.");
        }
    }

    private void validateDraftForUpdate(JobApplication application) {
        if (application.getStatus() != JobApplicationStatus.DRAFT) {
            throw new InvalidJobApplicationException("임시저장 상태의 지원서만 수정할 수 있습니다.");
        }
    }

    private void validateDraftForSubmit(JobApplication application) {
        if (application.getStatus() != JobApplicationStatus.DRAFT) {
            throw new InvalidJobApplicationException("임시저장 상태의 지원서만 제출할 수 있습니다.");
        }
    }

    private void validateSubmittedForWithdraw(JobApplication application) {
        if (application.getStatus() != JobApplicationStatus.SUBMITTED) {
            throw new InvalidJobApplicationException("제출된 지원서만 철회할 수 있습니다.");
        }
    }

    private void validateApplicationFormConfig(JobPosting jobPosting) {
        if (jobPosting.getApplicationFormConfig() == null) {
            throw new InvalidJobApplicationException("지원서 항목 설정이 없는 채용공고에는 지원할 수 없습니다.");
        }
    }

    private void validateSelectedJobPosition(JobApplication application) {
        Long jobPositionId = application.getJobPosition().getId();
        Long jobPostingId = application.getJobPosting().getId();
        if (jobPositionRepository.findByIdAndJobPostingId(jobPositionId, jobPostingId).isEmpty()) {
            throw new InvalidJobApplicationException("지원서의 모집분야가 채용공고에 속하지 않습니다.");
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
