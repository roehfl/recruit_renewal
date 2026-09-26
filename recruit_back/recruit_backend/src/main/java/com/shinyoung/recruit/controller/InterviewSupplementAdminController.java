package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.InterviewSupplementQuestionReorderRequest;
import com.shinyoung.recruit.dto.request.InterviewSupplementQuestionSaveRequest;
import com.shinyoung.recruit.dto.request.InterviewSupplementWindowResetRequest;
import com.shinyoung.recruit.dto.request.InterviewSupplementWindowSaveRequest;
import com.shinyoung.recruit.dto.response.AdminInterviewSupplementAnswerDetailResponse;
import com.shinyoung.recruit.dto.response.AdminInterviewSupplementCandidateResponse;
import com.shinyoung.recruit.dto.response.AdminInterviewSupplementResponse;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.service.InterviewSupplementAdminService;
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
@RequestMapping("/admin/stages/{stageId:[0-9]+}/interview-supplement")
public class InterviewSupplementAdminController {

    private final InterviewSupplementAdminService interviewSupplementAdminService;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminInterviewSupplementResponse>> getSupplement(@PathVariable Long stageId) {
        return ResponseEntity.ok(ApiResponse.success(interviewSupplementAdminService.getSupplement(stageId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminInterviewSupplementResponse>> enable(@PathVariable Long stageId) {
        return ResponseEntity.ok(ApiResponse.success(interviewSupplementAdminService.enable(stageId)));
    }

    @PostMapping("/delete")
    public ResponseEntity<ApiResponse<AdminInterviewSupplementResponse>> disable(@PathVariable Long stageId) {
        return ResponseEntity.ok(ApiResponse.success(interviewSupplementAdminService.disable(stageId)));
    }

    @PostMapping("/questions")
    public ResponseEntity<ApiResponse<AdminInterviewSupplementResponse>> addQuestion(
            @PathVariable Long stageId,
            @Valid @RequestBody InterviewSupplementQuestionSaveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(interviewSupplementAdminService.addQuestion(stageId, request)));
    }

    @PostMapping("/questions/reorder")
    public ResponseEntity<ApiResponse<AdminInterviewSupplementResponse>> reorderQuestions(
            @PathVariable Long stageId,
            @Valid @RequestBody InterviewSupplementQuestionReorderRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                interviewSupplementAdminService.reorderQuestions(stageId, request)
        ));
    }

    @PostMapping("/questions/{questionId:[0-9]+}")
    public ResponseEntity<ApiResponse<AdminInterviewSupplementResponse>> updateQuestion(
            @PathVariable Long stageId,
            @PathVariable Long questionId,
            @Valid @RequestBody InterviewSupplementQuestionSaveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                interviewSupplementAdminService.updateQuestion(stageId, questionId, request)
        ));
    }

    @PostMapping("/questions/{questionId:[0-9]+}/delete")
    public ResponseEntity<ApiResponse<AdminInterviewSupplementResponse>> deleteQuestion(
            @PathVariable Long stageId,
            @PathVariable Long questionId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                interviewSupplementAdminService.deleteQuestion(stageId, questionId)
        ));
    }

    @GetMapping("/candidates")
    public ResponseEntity<ApiResponse<List<AdminInterviewSupplementCandidateResponse>>> getCandidates(
            @PathVariable Long stageId
    ) {
        return ResponseEntity.ok(ApiResponse.success(interviewSupplementAdminService.getCandidates(stageId)));
    }

    @PostMapping("/windows")
    public ResponseEntity<ApiResponse<List<AdminInterviewSupplementCandidateResponse>>> saveWindows(
            @PathVariable Long stageId,
            @Valid @RequestBody InterviewSupplementWindowSaveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(interviewSupplementAdminService.saveWindows(stageId, request)));
    }

    @PostMapping("/windows/reset")
    public ResponseEntity<ApiResponse<List<AdminInterviewSupplementCandidateResponse>>> resetWindows(
            @PathVariable Long stageId,
            @Valid @RequestBody InterviewSupplementWindowResetRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(interviewSupplementAdminService.resetWindows(stageId, request)));
    }

    @GetMapping("/candidates/{jobApplicationId:[0-9]+}/answers")
    public ResponseEntity<ApiResponse<AdminInterviewSupplementAnswerDetailResponse>> getAnswers(
            @PathVariable Long stageId,
            @PathVariable Long jobApplicationId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                interviewSupplementAdminService.getAnswers(stageId, jobApplicationId)
        ));
    }
}
