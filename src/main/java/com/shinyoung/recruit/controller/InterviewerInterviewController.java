package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.InterviewerInterviewDetailResponse;
import com.shinyoung.recruit.dto.response.InterviewerInterviewSummaryResponse;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.CurrentEmployeeService;
import com.shinyoung.recruit.service.InterviewerInterviewService;
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
public class InterviewerInterviewController {

    private final InterviewerInterviewService interviewerInterviewService;
    private final CurrentEmployeeService currentEmployeeService;

    @GetMapping("/interviewer/interviews")
    public ResponseEntity<ApiResponse<List<InterviewerInterviewSummaryResponse>>> getMyInterviews(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) InterviewStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        Long employeeId = currentEmployeeService.getCurrentEmployeeId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(
                interviewerInterviewService.getMyInterviews(employeeId, status, from, to)
        ));
    }

    @GetMapping("/interviewer/interviews/{interviewId:[0-9]+}")
    public ResponseEntity<ApiResponse<InterviewerInterviewDetailResponse>> getMyInterviewDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long interviewId
    ) {
        Long employeeId = currentEmployeeService.getCurrentEmployeeId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(
                interviewerInterviewService.getMyInterviewDetail(employeeId, interviewId)
        ));
    }
}
