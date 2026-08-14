package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.CareerReplaceRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.CareerResponse;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ApplicationCareerService;
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

@RestController
@RequiredArgsConstructor
public class ApplicationCareerController {

    private final ApplicationCareerService applicationCareerService;
    private final CurrentApplicantService currentApplicantService;

    @GetMapping("/applications/{applicationId}/careers")
    public ResponseEntity<ApiResponse<CareerResponse>> getCareers(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        CareerResponse response = applicationCareerService.getCareers(applicantId, applicationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/applications/{applicationId}/careers")
    public ResponseEntity<ApiResponse<CareerResponse>> replaceCareers(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId,
            @Valid @RequestBody CareerReplaceRequest request
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        CareerResponse response = applicationCareerService.replaceCareers(applicantId, applicationId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
