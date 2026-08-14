package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CertificateRequest(
        @NotBlank(message = "Certificate name is required.")
        String certificateName,

        @NotBlank(message = "Issuing organization is required.")
        String issuingOrganization,

        @NotNull(message = "Acquired date is required.")
        LocalDate acquiredDate,

        String certificateNumber,

        LocalDate expiredDate,

        String scoreOrGrade,

        @NotNull(message = "Sort order is required.")
        @Min(value = 0, message = "Sort order must be greater than or equal to 0.")
        Integer sortOrder
) {
}
