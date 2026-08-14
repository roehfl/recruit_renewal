package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.ApplicationAnswerReplaceRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.ApplicationQuestionResponse;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ApplicationAnswerService;
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
public class ApplicationAnswerController {

    private final ApplicationAnswerService applicationAnswerService;
    private final CurrentApplicantService currentApplicantService;

    @GetMapping("/applications/{applicationId}/questions")
    public ResponseEntity<ApiResponse<List<ApplicationQuestionResponse>>> getQuestions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        List<ApplicationQuestionResponse> response = applicationAnswerService.getQuestions(applicantId, applicationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/applications/{applicationId}/answers")
    public ResponseEntity<ApiResponse<List<ApplicationQuestionResponse>>> replaceAnswers(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId,
            @Valid @RequestBody ApplicationAnswerReplaceRequest request
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        List<ApplicationQuestionResponse> response = applicationAnswerService.replaceAnswers(applicantId, applicationId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
