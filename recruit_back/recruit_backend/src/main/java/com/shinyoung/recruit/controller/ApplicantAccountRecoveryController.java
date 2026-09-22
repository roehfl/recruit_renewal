package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.ApplicantFindEmailResponse;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.service.ApplicantAccountRecoveryService;
import com.shinyoung.recruit.service.nice.NiceVerificationService;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인 전 계정 복구. 지금은 아이디(이메일) 찾기뿐이다. */
@RestController
@RequestMapping("/auth/applicants")
public class ApplicantAccountRecoveryController {

    private final ApplicantAccountRecoveryService applicantAccountRecoveryService;
    private final NiceVerificationService niceVerificationService;

    public ApplicantAccountRecoveryController(
            ApplicantAccountRecoveryService applicantAccountRecoveryService,
            NiceVerificationService niceVerificationService) {
        this.applicantAccountRecoveryService = applicantAccountRecoveryService;
        this.niceVerificationService = niceVerificationService;
    }

    /**
     * 아이디(이메일) 찾기. 요청 본문은 없다 — 세션에 담긴 NICE 인증 결과(용도 {@code FIND_EMAIL})로 찾는다.
     *
     * <p>인증 결과는 <b>조회 전에</b> 세션에서 지운다. 인증 1회로 조회 1회만 된다. 계정이 없어도 소비한다
     * — 조회는 결정적이라 같은 인증으로 다시 시도할 이유가 없다.
     */
    @PostMapping("/find-email")
    public ResponseEntity<ApiResponse<ApplicantFindEmailResponse>> findEmail(HttpSession session) {
        NiceVerifiedIdentity identity = niceVerificationService.requireFresh(
                (NiceVerifiedIdentity) session.getAttribute(NiceVerificationController.VERIFIED_SESSION_KEY),
                NiceVerificationPurpose.FIND_EMAIL);
        session.removeAttribute(NiceVerificationController.VERIFIED_SESSION_KEY);

        return ResponseEntity.ok(ApiResponse.success(applicantAccountRecoveryService.findEmail(identity)));
    }
}
