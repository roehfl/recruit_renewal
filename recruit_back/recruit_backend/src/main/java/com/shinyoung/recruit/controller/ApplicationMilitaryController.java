package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.MilitarySaveRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.MilitaryResponse;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ApplicationMilitaryService;
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
public class ApplicationMilitaryController {

    private final ApplicationMilitaryService applicationMilitaryService;
    private final CurrentApplicantService currentApplicantService;

    @GetMapping("/applications/{applicationId}/military")
    public ResponseEntity<ApiResponse<MilitaryResponse>> getMilitary(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        MilitaryResponse response = applicationMilitaryService.getMilitary(applicantId, applicationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/applications/{applicationId}/military")
    public ResponseEntity<ApiResponse<MilitaryResponse>> saveMilitary(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId,
            @Valid @RequestBody MilitarySaveRequest request
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        MilitaryResponse response = applicationMilitaryService.saveMilitary(applicantId, applicationId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
