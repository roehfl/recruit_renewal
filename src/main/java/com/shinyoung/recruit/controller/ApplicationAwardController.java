package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.AwardReplaceRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.AwardResponse;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ApplicationAwardService;
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
public class ApplicationAwardController {

    private final ApplicationAwardService applicationAwardService;
    private final CurrentApplicantService currentApplicantService;

    @GetMapping("/applications/{applicationId}/awards")
    public ResponseEntity<ApiResponse<List<AwardResponse>>> getAwards(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        List<AwardResponse> response = applicationAwardService.getAwards(applicantId, applicationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/applications/{applicationId}/awards")
    public ResponseEntity<ApiResponse<List<AwardResponse>>> replaceAwards(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId,
            @Valid @RequestBody AwardReplaceRequest request
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        List<AwardResponse> response = applicationAwardService.replaceAwards(applicantId, applicationId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
