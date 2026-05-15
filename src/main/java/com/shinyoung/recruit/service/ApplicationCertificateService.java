package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationCertificate;
import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.repository.ApplicationCertificateRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.dto.request.CertificateReplaceRequest;
import com.shinyoung.recruit.dto.request.CertificateRequest;
import com.shinyoung.recruit.dto.response.CertificateResponse;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ApplicationCertificateService {

    private final JobApplicationRepository jobApplicationRepository;
    private final ApplicationCertificateRepository certificateRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<CertificateResponse> getCertificates(Long applicantId, Long applicationId) {
        findApplication(applicantId, applicationId);
        return getCertificateResponses(applicationId);
    }

    @Transactional
    public List<CertificateResponse> replaceCertificates(
            Long applicantId,
            Long applicationId,
            CertificateReplaceRequest request
    ) {
        JobApplication application = findApplication(applicantId, applicationId);
        validateCertificateWritable(application);
        validateRequest(request);

        certificateRepository.deleteByJobApplicationId(applicationId);
        List<ApplicationCertificate> certificates = request.certificates().stream()
                .map(certificate -> toCertificate(application, certificate))
                .toList();
        certificateRepository.saveAll(certificates);

        return getCertificateResponses(applicationId);
    }

    private JobApplication findApplication(Long applicantId, Long applicationId) {
        return jobApplicationRepository.findByIdAndApplicantId(applicationId, applicantId)
                .orElseThrow(() -> new JobApplicationNotFoundException("Application was not found."));
    }

    private void validateCertificateWritable(JobApplication application) {
        if (application.getStatus() != JobApplicationStatus.DRAFT) {
            throw new InvalidJobApplicationException("Certificate can be modified only in DRAFT status.");
        }
        if (application.getJobPosting().getStatus() != JobPostingStatus.PUBLISHED) {
            throw new InvalidJobApplicationException("Certificate can be modified only for a published job posting.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isBefore(application.getJobPosting().getReceptionStartDateTime())
                || now.isAfter(application.getJobPosting().getReceptionEndDateTime())) {
            throw new InvalidJobApplicationException("Certificate can be modified only during the reception period.");
        }

        ApplicationFormConfig config = application.getJobPosting().getApplicationFormConfig();
        if (config == null || !config.isUseCertificate()) {
            throw new InvalidJobApplicationException("Certificate section is not enabled for this job posting.");
        }
    }

    private void validateRequest(CertificateReplaceRequest request) {
        if (request == null || request.certificates() == null) {
            throw new InvalidJobApplicationException("Certificate list is required.");
        }

        Set<Integer> sortOrders = new HashSet<>();
        for (CertificateRequest certificate : request.certificates()) {
            validateCertificateRequiredFields(certificate);
            if (!sortOrders.add(certificate.sortOrder())) {
                throw new InvalidJobApplicationException("Certificate sort order must be unique.");
            }
        }
    }

    private void validateCertificateRequiredFields(CertificateRequest certificate) {
        if (certificate == null) {
            throw new InvalidJobApplicationException("Certificate item is required.");
        }
        if (certificate.certificateName() == null || certificate.certificateName().isBlank()) {
            throw new InvalidJobApplicationException("Certificate name is required.");
        }
        if (certificate.issuingOrganization() == null || certificate.issuingOrganization().isBlank()) {
            throw new InvalidJobApplicationException("Issuing organization is required.");
        }
        if (certificate.acquiredDate() == null) {
            throw new InvalidJobApplicationException("Acquired date is required.");
        }
        if (certificate.sortOrder() == null || certificate.sortOrder() < 0) {
            throw new InvalidJobApplicationException("Sort order must be greater than or equal to 0.");
        }
        if (certificate.expiredDate() != null && certificate.acquiredDate().isAfter(certificate.expiredDate())) {
            throw new InvalidJobApplicationException("Acquired date cannot be after expired date.");
        }
    }

    private ApplicationCertificate toCertificate(JobApplication application, CertificateRequest request) {
        return ApplicationCertificate.create(
                application,
                request.certificateName(),
                request.issuingOrganization(),
                request.acquiredDate(),
                request.certificateNumber(),
                request.expiredDate(),
                request.scoreOrGrade(),
                request.sortOrder()
        );
    }

    private List<CertificateResponse> getCertificateResponses(Long applicationId) {
        return certificateRepository.findByJobApplicationIdOrderBySortOrderAscIdAsc(applicationId)
                .stream()
                .map(CertificateResponse::from)
                .toList();
    }
}
