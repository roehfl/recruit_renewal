package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.EducationReplaceRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.EducationResponse;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ApplicationEducationService;
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
public class ApplicationEducationController {

    private final ApplicationEducationService applicationEducationService;
    private final CurrentApplicantService currentApplicantService;

    @GetMapping("/applications/{applicationId}/educations")
    public ResponseEntity<ApiResponse<List<EducationResponse>>> getEducations(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        List<EducationResponse> response = applicationEducationService.getEducations(applicantId, applicationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/applications/{applicationId}/educations")
    public ResponseEntity<ApiResponse<List<EducationResponse>>> replaceEducations(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId,
            @Valid @RequestBody EducationReplaceRequest request
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        List<EducationResponse> response = applicationEducationService.replaceEducations(applicantId, applicationId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
