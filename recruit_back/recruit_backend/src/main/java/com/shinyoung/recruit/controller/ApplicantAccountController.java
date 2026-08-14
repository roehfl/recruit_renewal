package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.ApplicantPasswordChangeRequest;
import com.shinyoung.recruit.dto.request.ApplicantPhoneNumberChangeRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ApplicantAccountService;
import com.shinyoung.recruit.service.CurrentApplicantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/applicant/account")
public class ApplicantAccountController {

    private final ApplicantAccountService applicantAccountService;
    private final CurrentApplicantService currentApplicantService;

    @PostMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ApplicantPasswordChangeRequest request) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        applicantAccountService.changePassword(applicantId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/phone-number")
    public ResponseEntity<ApiResponse<Void>> changePhoneNumber(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ApplicantPhoneNumberChangeRequest request) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        applicantAccountService.changePhoneNumber(applicantId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
