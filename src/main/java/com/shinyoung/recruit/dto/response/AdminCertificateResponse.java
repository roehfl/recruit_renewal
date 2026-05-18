package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.ApplicationCertificate;

import java.time.LocalDate;

public record AdminCertificateResponse(
        Long certificateId,
        String certificateName,
        String issuingOrganization,
        LocalDate acquiredDate,
        String certificateNumberMasked,
        LocalDate expiredDate,
        String scoreOrGrade,
        Integer sortOrder
) {

    public static AdminCertificateResponse from(ApplicationCertificate certificate, String certificateNumberMasked) {
        return new AdminCertificateResponse(
                certificate.getId(),
                certificate.getCertificateName(),
                certificate.getIssuingOrganization(),
                certificate.getAcquiredDate(),
                certificateNumberMasked,
                certificate.getExpiredDate(),
                certificate.getScoreOrGrade(),
                certificate.getSortOrder()
        );
    }
}
