package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationCertificate;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.repository.ApplicationCertificateRepository;
import com.shinyoung.recruit.dto.request.CertificateReplaceRequest;
import com.shinyoung.recruit.dto.request.CertificateRequest;
import com.shinyoung.recruit.dto.response.CertificateResponse;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ApplicationCertificateService {

    private final ApplicationSectionAccessService sectionAccessService;
    private final ApplicationCertificateRepository certificateRepository;

    @Transactional(readOnly = true)
    public List<CertificateResponse> getCertificates(Long applicantId, Long applicationId) {
        sectionAccessService.findOwnedApplication(applicantId, applicationId);
        return getCertificateResponses(applicationId);
    }

    @Transactional
    public List<CertificateResponse> replaceCertificates(
            Long applicantId,
            Long applicationId,
            CertificateReplaceRequest request
    ) {
        JobApplication application = sectionAccessService.findOwnedApplication(applicantId, applicationId);
        sectionAccessService.validateWritable(application);
        sectionAccessService.validateCertificateEnabled(application);
        validateRequest(request);

        certificateRepository.deleteByJobApplicationId(applicationId);
        List<ApplicationCertificate> certificates = request.certificates().stream()
                .map(certificate -> toCertificate(application, certificate))
                .toList();
        certificateRepository.saveAll(certificates);

        return getCertificateResponses(applicationId);
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
