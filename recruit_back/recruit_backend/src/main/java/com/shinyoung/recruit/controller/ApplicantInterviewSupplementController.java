package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.InterviewSupplementAnswerSaveRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.ApplicantInterviewSupplementFormResponse;
import com.shinyoung.recruit.dto.response.ApplicantInterviewSupplementSaveResponse;
import com.shinyoung.recruit.dto.response.ApplicantInterviewSupplementSummaryResponse;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ApplicantInterviewSupplementService;
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
public class ApplicantInterviewSupplementController {

    private final ApplicantInterviewSupplementService applicantInterviewSupplementService;
    private final CurrentApplicantService currentApplicantService;

    @GetMapping("/applicant/interview-supplements")
    public ResponseEntity<ApiResponse<List<ApplicantInterviewSupplementSummaryResponse>>> getMySupplements(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(applicantInterviewSupplementService.getMySupplements(applicantId)));
    }

    @GetMapping("/applicant/applications/{applicationId:[0-9]+}/interview-supplements/{stageId:[0-9]+}")
    public ResponseEntity<ApiResponse<ApplicantInterviewSupplementFormResponse>> getForm(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId,
            @PathVariable Long stageId
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(
                applicantInterviewSupplementService.getForm(applicantId, applicationId, stageId)
        ));
    }

    @PostMapping("/applicant/applications/{applicationId:[0-9]+}/interview-supplements/{stageId:[0-9]+}/answers")
    public ResponseEntity<ApiResponse<ApplicantInterviewSupplementSaveResponse>> saveAnswers(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId,
            @PathVariable Long stageId,
            @Valid @RequestBody InterviewSupplementAnswerSaveRequest request
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(
                applicantInterviewSupplementService.saveAnswers(applicantId, applicationId, stageId, request)
        ));
    }
}
