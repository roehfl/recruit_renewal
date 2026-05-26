package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.ApplicantInterviewDetailResponse;
import com.shinyoung.recruit.dto.response.ApplicantInterviewSummaryResponse;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ApplicantInterviewService;
import com.shinyoung.recruit.service.CurrentApplicantService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ApplicantInterviewController {

    private final ApplicantInterviewService applicantInterviewService;
    private final CurrentApplicantService currentApplicantService;

    @GetMapping("/applicant/interviews")
    public ResponseEntity<ApiResponse<List<ApplicantInterviewSummaryResponse>>> getMyInterviews(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) InterviewStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(
                applicantInterviewService.getMyInterviews(applicantId, status, from, to)
        ));
    }

    @GetMapping("/applicant/applications/{applicationId:[0-9]+}/interviews")
    public ResponseEntity<ApiResponse<List<ApplicantInterviewSummaryResponse>>> getMyApplicationInterviews(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId,
            @RequestParam(required = false) InterviewStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(
                applicantInterviewService.getMyApplicationInterviews(applicantId, applicationId, status, from, to)
        ));
    }

    @GetMapping("/applicant/interviews/{interviewId:[0-9]+}")
    public ResponseEntity<ApiResponse<ApplicantInterviewDetailResponse>> getMyInterviewDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long interviewId
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(
                applicantInterviewService.getMyInterviewDetail(applicantId, interviewId)
        ));
    }
}
