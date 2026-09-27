package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.ApplicantPasswordChangeRequest;
import com.shinyoung.recruit.dto.request.ApplicantPhoneNumberChangeRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ApplicantAccountService;
import com.shinyoung.recruit.service.CurrentApplicantService;
import com.shinyoung.recruit.service.nice.NiceVerificationService;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import jakarta.servlet.http.HttpSession;
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
    private final NiceVerificationService niceVerificationService;

    @PostMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ApplicantPasswordChangeRequest request,
            HttpSession session) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        applicantAccountService.changePassword(applicantId, request, session.getId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/phone-number")
    public ResponseEntity<ApiResponse<Void>> changePhoneNumber(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ApplicantPhoneNumberChangeRequest request,
            HttpSession session) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        // 새 번호는 세션의 NICE 결과(용도 PHONE_CHANGE)에서 꺼낸다. 요청 본문의 번호는 받지 않는다.
        NiceVerifiedIdentity identity = niceVerificationService.requireFresh(
                (NiceVerifiedIdentity) session.getAttribute(NiceVerificationController.VERIFIED_SESSION_KEY),
                NiceVerificationPurpose.PHONE_CHANGE);
        applicantAccountService.changePhoneNumber(applicantId, request, identity);
        // 1회용이다. 남겨 두면 한 번의 인증으로 여러 번 바꿀 수 있다.
        session.removeAttribute(NiceVerificationController.VERIFIED_SESSION_KEY);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
