package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.ApplicantSignUpRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.ApplicantSignUpResponse;
import com.shinyoung.recruit.service.ApplicantSignUpService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/applicants")
public class ApplicantSignUpController {

    private final ApplicantSignUpService applicantSignUpService;

    public ApplicantSignUpController(ApplicantSignUpService applicantSignUpService) {
        this.applicantSignUpService = applicantSignUpService;
    }

    @PostMapping("/sign-up")
    public ResponseEntity<ApiResponse<ApplicantSignUpResponse>> signUp(
            @Valid @RequestBody ApplicantSignUpRequest request) {
        ApplicantSignUpResponse response = applicantSignUpService.signUp(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
