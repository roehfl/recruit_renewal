package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.ApplicantStageResultResponse;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ApplicationStageResultService;
import com.shinyoung.recruit.service.CurrentApplicantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ApplicationStageResultController {

    private final ApplicationStageResultService applicationStageResultService;
    private final CurrentApplicantService currentApplicantService;

    @GetMapping("/applications/{applicationId}/stage-results")
    public ResponseEntity<ApiResponse<List<ApplicantStageResultResponse>>> getStageResults(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(
                applicationStageResultService.getApplicantStageResults(applicantId, applicationId)
        ));
    }
}
