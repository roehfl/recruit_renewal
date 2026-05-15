package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ApplicationSectionAccessService {

    private final JobApplicationRepository jobApplicationRepository;
    private final Clock clock;

    public JobApplication findOwnedApplication(Long applicantId, Long applicationId) {
        return jobApplicationRepository.findByIdAndApplicantId(applicationId, applicantId)
                .orElseThrow(() -> new JobApplicationNotFoundException("Application was not found."));
    }

    public void validateWritable(JobApplication application) {
        if (application.getStatus() != JobApplicationStatus.DRAFT) {
            throw new InvalidJobApplicationException("Application detail can be modified only in DRAFT status.");
        }
        if (application.getJobPosting().getStatus() != JobPostingStatus.PUBLISHED) {
            throw new InvalidJobApplicationException("Application detail can be modified only for a published job posting.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isBefore(application.getJobPosting().getReceptionStartDateTime())
                || now.isAfter(application.getJobPosting().getReceptionEndDateTime())) {
            throw new InvalidJobApplicationException("Application detail can be modified only during the reception period.");
        }
    }

    public void validateEducationEnabled(JobApplication application) {
        ApplicationFormConfig config = application.getJobPosting().getApplicationFormConfig();
        if (config == null || !config.isUseEducation()) {
            throw new InvalidJobApplicationException("Education section is not enabled for this job posting.");
        }
    }

    public void validateCareerEnabled(JobApplication application) {
        ApplicationFormConfig config = application.getJobPosting().getApplicationFormConfig();
        if (config == null || !config.isUseCareer()) {
            throw new InvalidJobApplicationException("Career section is not enabled for this job posting.");
        }
    }

    public void validateCertificateEnabled(JobApplication application) {
        ApplicationFormConfig config = application.getJobPosting().getApplicationFormConfig();
        if (config == null || !config.isUseCertificate()) {
            throw new InvalidJobApplicationException("Certificate section is not enabled for this job posting.");
        }
    }

    public void validateLanguageEnabled(JobApplication application) {
        ApplicationFormConfig config = application.getJobPosting().getApplicationFormConfig();
        if (config == null || !config.isUseLanguage()) {
            throw new InvalidJobApplicationException("Language section is not enabled for this job posting.");
        }
    }

    public void validateMilitaryEnabled(JobApplication application) {
        ApplicationFormConfig config = application.getJobPosting().getApplicationFormConfig();
        if (config == null || !config.isUseMilitary()) {
            throw new InvalidJobApplicationException("Military section is not enabled for this job posting.");
        }
    }

    public void validateAwardEnabled(JobApplication application) {
        ApplicationFormConfig config = application.getJobPosting().getApplicationFormConfig();
        if (config == null || !config.isUseAward()) {
            throw new InvalidJobApplicationException("Award section is not enabled for this job posting.");
        }
    }

    public void validateGapPeriodEnabled(JobApplication application) {
        ApplicationFormConfig config = application.getJobPosting().getApplicationFormConfig();
        if (config == null || !config.isUseGapPeriod()) {
            throw new InvalidJobApplicationException("Gap period section is not enabled for this job posting.");
        }
    }
}
