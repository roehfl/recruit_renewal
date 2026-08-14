package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.JobPostingQuestionCreateRequest;
import com.shinyoung.recruit.dto.request.JobPostingQuestionReorderRequest;
import com.shinyoung.recruit.dto.request.JobPostingQuestionUpdateRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.JobPostingQuestionResponse;
import com.shinyoung.recruit.service.JobPostingQuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/job-postings/{jobPostingId}/questions")
public class JobPostingQuestionController {

    private final JobPostingQuestionService jobPostingQuestionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<JobPostingQuestionResponse>>> getQuestions(@PathVariable Long jobPostingId) {
        return ResponseEntity.ok(ApiResponse.success(jobPostingQuestionService.getQuestions(jobPostingId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<JobPostingQuestionResponse>> createQuestion(
            @PathVariable Long jobPostingId,
            @Valid @RequestBody JobPostingQuestionCreateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(jobPostingQuestionService.createQuestion(jobPostingId, request)));
    }

    @PostMapping("/{questionId}")
    public ResponseEntity<ApiResponse<JobPostingQuestionResponse>> updateQuestion(
            @PathVariable Long jobPostingId,
            @PathVariable Long questionId,
            @Valid @RequestBody JobPostingQuestionUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                jobPostingQuestionService.updateQuestion(jobPostingId, questionId, request)
        ));
    }

    @PostMapping("/reorder")
    public ResponseEntity<ApiResponse<List<JobPostingQuestionResponse>>> reorderQuestions(
            @PathVariable Long jobPostingId,
            @Valid @RequestBody JobPostingQuestionReorderRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(jobPostingQuestionService.reorderQuestions(jobPostingId, request)));
    }

    @PostMapping("/{questionId}/delete")
    public ResponseEntity<ApiResponse<JobPostingQuestionResponse>> deactivateQuestion(
            @PathVariable Long jobPostingId,
            @PathVariable Long questionId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                jobPostingQuestionService.deactivateQuestion(jobPostingId, questionId)
        ));
    }
}
