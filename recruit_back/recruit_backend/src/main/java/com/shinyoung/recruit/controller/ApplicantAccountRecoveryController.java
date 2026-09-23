package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.ApplicantPasswordResetRequest;
import com.shinyoung.recruit.dto.request.EmailVerificationConfirmRequest;
import com.shinyoung.recruit.dto.request.EmailVerificationSendRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.ApplicantFindEmailResponse;
import com.shinyoung.recruit.enumeration.EmailVerificationPurpose;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.service.ApplicantAccountRecoveryService;
import com.shinyoung.recruit.service.EmailVerificationService;
import com.shinyoung.recruit.service.EmailVerificationState;
import com.shinyoung.recruit.service.nice.NiceVerificationService;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

/** 로그인 전 계정 복구: 아이디(이메일) 찾기, 비밀번호 재설정(가입 이메일 인증번호). */
@RestController
@RequestMapping("/auth/applicants")
public class ApplicantAccountRecoveryController {

    private static final String RESET_EMAIL_KEY = EmailVerificationService.sessionKey(EmailVerificationPurpose.PASSWORD_RESET);

    private final ApplicantAccountRecoveryService applicantAccountRecoveryService;
    private final NiceVerificationService niceVerificationService;
    private final EmailVerificationService emailVerificationService;

    public ApplicantAccountRecoveryController(
            ApplicantAccountRecoveryService applicantAccountRecoveryService,
            NiceVerificationService niceVerificationService,
            EmailVerificationService emailVerificationService) {
        this.applicantAccountRecoveryService = applicantAccountRecoveryService;
        this.niceVerificationService = niceVerificationService;
        this.emailVerificationService = emailVerificationService;
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

    /** 비밀번호 재설정 인증번호 발송. 발송이 접수된 뒤에만 세션 상태를 바꾼다. */
    @PostMapping("/password-reset/send")
    public ResponseEntity<ApiResponse<Void>> sendPasswordResetCode(
            @Valid @RequestBody EmailVerificationSendRequest request, HttpSession session) {
        EmailVerificationState state = applicantAccountRecoveryService.sendPasswordResetCode(
                (EmailVerificationState) session.getAttribute(RESET_EMAIL_KEY), request.email());
        session.setAttribute(RESET_EMAIL_KEY, state);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/password-reset/verify")
    public ResponseEntity<ApiResponse<Void>> verifyPasswordResetCode(
            @Valid @RequestBody EmailVerificationConfirmRequest request, HttpSession session) {
        // 같은 세션의 동시 요청이 실패 수 확인·증가 사이에 끼면 5회 제한을 넘겨 추측할 수 있다. 세션 단위로 직렬화한다.
        synchronized (WebUtils.getSessionMutex(session)) {
            EmailVerificationState state = (EmailVerificationState) session.getAttribute(RESET_EMAIL_KEY);
            try {
                emailVerificationService.verify(state, EmailVerificationPurpose.PASSWORD_RESET, request.email(), request.code());
            } finally {
                // 실패 수·확인 시각은 상태 객체 안에서 바뀐다. 세션 저장소가 바뀐 값을 알도록 다시 넣는다.
                if (state != null) {
                    session.setAttribute(RESET_EMAIL_KEY, state);
                }
            }
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/password-reset")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ApplicantPasswordResetRequest request, HttpSession session) {
        EmailVerificationState state = (EmailVerificationState) session.getAttribute(RESET_EMAIL_KEY);
        emailVerificationService.requireVerified(state, EmailVerificationPurpose.PASSWORD_RESET, request.email());
        // 대소문자를 무시하고 비교해 통과시켰으므로, 조회는 발송 때 DB 에서 읽어 둔 원래 이메일로 한다.
        applicantAccountRecoveryService.resetPassword(state.getEmail(), request.newPassword());
        // 1회용이다. 남겨 두면 10분 안에 같은 인증으로 여러 번 바꿀 수 있다.
        session.removeAttribute(RESET_EMAIL_KEY);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
