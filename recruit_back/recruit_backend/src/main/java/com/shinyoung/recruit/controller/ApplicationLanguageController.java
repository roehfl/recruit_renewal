package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.LanguageReplaceRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.LanguageResponse;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ApplicationLanguageService;
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
public class ApplicationLanguageController {

    private final ApplicationLanguageService applicationLanguageService;
    private final CurrentApplicantService currentApplicantService;

    @GetMapping("/applications/{applicationId}/languages")
    public ResponseEntity<ApiResponse<List<LanguageResponse>>> getLanguages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        List<LanguageResponse> response = applicationLanguageService.getLanguages(applicantId, applicationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/applications/{applicationId}/languages")
    public ResponseEntity<ApiResponse<List<LanguageResponse>>> replaceLanguages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId,
            @Valid @RequestBody LanguageReplaceRequest request
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        List<LanguageResponse> response = applicationLanguageService.replaceLanguages(applicantId, applicationId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
