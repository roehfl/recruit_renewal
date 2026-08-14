package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.CertificateReplaceRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.CertificateResponse;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ApplicationCertificateService;
import com.shinyoung.recruit.service.CurrentApplicantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ApplicationCertificateController {

    private final ApplicationCertificateService applicationCertificateService;
    private final CurrentApplicantService currentApplicantService;

    @GetMapping("/applications/{applicationId}/certificates")
    public ResponseEntity<ApiResponse<List<CertificateResponse>>> getCertificates(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        List<CertificateResponse> response = applicationCertificateService.getCertificates(applicantId, applicationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/applications/{applicationId}/certificates")
    public ResponseEntity<ApiResponse<List<CertificateResponse>>> replaceCertificates(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId,
            @Valid @RequestBody CertificateReplaceRequest request
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        List<CertificateResponse> response = applicationCertificateService.replaceCertificates(applicantId, applicationId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
