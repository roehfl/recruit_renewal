package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.GapPeriodReplaceRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.GapPeriodResponse;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ApplicationGapPeriodService;
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
public class ApplicationGapPeriodController {

    private final ApplicationGapPeriodService applicationGapPeriodService;
    private final CurrentApplicantService currentApplicantService;

    @GetMapping("/applications/{applicationId}/gap-periods")
    public ResponseEntity<ApiResponse<List<GapPeriodResponse>>> getGapPeriods(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        List<GapPeriodResponse> response = applicationGapPeriodService.getGapPeriods(applicantId, applicationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/applications/{applicationId}/gap-periods")
    public ResponseEntity<ApiResponse<List<GapPeriodResponse>>> replaceGapPeriods(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId,
            @Valid @RequestBody GapPeriodReplaceRequest request
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        List<GapPeriodResponse> response = applicationGapPeriodService.replaceGapPeriods(applicantId, applicationId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
