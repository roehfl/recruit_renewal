package com.shinyoung.recruit.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CertificateReplaceRequest(
        @NotNull(message = "Certificate list is required.")
        List<@Valid CertificateRequest> certificates
) {
}
