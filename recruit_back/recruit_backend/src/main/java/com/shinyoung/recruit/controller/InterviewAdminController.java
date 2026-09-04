package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.InterviewCreateRequest;
import com.shinyoung.recruit.dto.request.InterviewParticipantReplaceRequest;
import com.shinyoung.recruit.dto.request.InterviewUpdateRequest;
import com.shinyoung.recruit.dto.response.AdminInterviewDetailResponse;
import com.shinyoung.recruit.dto.response.AdminInterviewSummaryResponse;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.service.InterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin")
public class InterviewAdminController {

    private final InterviewService interviewService;

    @GetMapping("/job-postings/{jobPostingId}/interviews")
    public ResponseEntity<ApiResponse<List<AdminInterviewSummaryResponse>>> getInterviews(
            @PathVariable Long jobPostingId,
            @RequestParam(required = false) Long stageId,
            @RequestParam(required = false) InterviewStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                interviewService.getAdminInterviews(jobPostingId, stageId, status, from, to)
        ));
    }

    @GetMapping("/interviews/{interviewId}")
    public ResponseEntity<ApiResponse<AdminInterviewDetailResponse>> getInterview(
            @PathVariable Long interviewId
    ) {
        return ResponseEntity.ok(ApiResponse.success(interviewService.getAdminInterview(interviewId)));
    }

    @PostMapping("/job-postings/{jobPostingId}/interviews")
    public ResponseEntity<ApiResponse<Long>> create(
            @PathVariable Long jobPostingId,
            @Valid @RequestBody InterviewCreateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(interviewService.createDraft(jobPostingId, request)));
    }

    @PostMapping("/interviews/{interviewId}")
    public ResponseEntity<ApiResponse<Long>> update(
            @PathVariable Long interviewId,
            @Valid @RequestBody InterviewUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(interviewService.updateDraft(interviewId, request)));
    }

    @PostMapping("/interviews/{interviewId}/participants")
    public ResponseEntity<ApiResponse<Long>> replaceParticipants(
            @PathVariable Long interviewId,
            @Valid @RequestBody InterviewParticipantReplaceRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(interviewService.replaceParticipants(interviewId, request)));
    }

    @PostMapping("/interviews/{interviewId}/confirm")
    public ResponseEntity<ApiResponse<Long>> confirm(
            @PathVariable Long interviewId
    ) {
        return ResponseEntity.ok(ApiResponse.success(interviewService.confirm(interviewId)));
    }

    @PostMapping("/interviews/{interviewId}/cancel")
    public ResponseEntity<ApiResponse<Long>> cancel(
            @PathVariable Long interviewId
    ) {
        return ResponseEntity.ok(ApiResponse.success(interviewService.cancel(interviewId)));
    }

    @PostMapping("/interviews/{interviewId}/delete")
    public ResponseEntity<ApiResponse<Long>> delete(
            @PathVariable Long interviewId
    ) {
        return ResponseEntity.ok(ApiResponse.success(interviewService.delete(interviewId)));
    }
}
