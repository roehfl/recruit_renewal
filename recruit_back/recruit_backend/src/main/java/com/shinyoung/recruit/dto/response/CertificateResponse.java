package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.ApplicationCertificate;

import java.time.LocalDate;

public record CertificateResponse(
        Long certificateId,
        String certificateName,
        String issuingOrganization,
        LocalDate acquiredDate,
        String certificateNumber,
        LocalDate expiredDate,
        String scoreOrGrade,
        Integer sortOrder
) {

    public static CertificateResponse from(ApplicationCertificate certificate) {
        return new CertificateResponse(
                certificate.getId(),
                certificate.getCertificateName(),
                certificate.getIssuingOrganization(),
                certificate.getAcquiredDate(),
                certificate.getCertificateNumber(),
                certificate.getExpiredDate(),
                certificate.getScoreOrGrade(),
                certificate.getSortOrder()
        );
    }
}
