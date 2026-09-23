package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.ApplicantSignUpRequest;
import com.shinyoung.recruit.dto.request.EmailVerificationConfirmRequest;
import com.shinyoung.recruit.dto.request.EmailVerificationSendRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.ApplicantEmailAvailabilityResponse;
import com.shinyoung.recruit.dto.response.ApplicantSignUpResponse;
import com.shinyoung.recruit.enumeration.EmailVerificationPurpose;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.service.ApplicantSignUpService;
import com.shinyoung.recruit.service.EmailVerificationService;
import com.shinyoung.recruit.service.EmailVerificationState;
import com.shinyoung.recruit.service.nice.NiceVerificationService;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

@Validated
@RestController
@RequestMapping("/auth/applicants")
public class ApplicantSignUpController {

    private static final String SIGNUP_EMAIL_KEY = EmailVerificationService.sessionKey(EmailVerificationPurpose.SIGNUP);

    private final ApplicantSignUpService applicantSignUpService;
    private final NiceVerificationService niceVerificationService;
    private final EmailVerificationService emailVerificationService;

    public ApplicantSignUpController(
            ApplicantSignUpService applicantSignUpService,
            NiceVerificationService niceVerificationService,
            EmailVerificationService emailVerificationService) {
        this.applicantSignUpService = applicantSignUpService;
        this.niceVerificationService = niceVerificationService;
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping("/sign-up")
    public ResponseEntity<ApiResponse<ApplicantSignUpResponse>> signUp(
            @Valid @RequestBody ApplicantSignUpRequest request, HttpSession session) {
        // 세션에서 인증 결과를 꺼내 유효성을 확인한 뒤 서비스에 값으로 넘긴다.
        // 서비스가 HttpSession 을 알면 단위 테스트가 서블릿 컨테이너에 묶인다.
        NiceVerifiedIdentity identity = niceVerificationService.requireFresh(
                (NiceVerifiedIdentity) session.getAttribute(
                        NiceVerificationController.VERIFIED_SESSION_KEY),
                NiceVerificationPurpose.SIGNUP);
        // 가입 이메일은 같은 세션에서 인증번호를 확인한 주소여야 한다(확인 후 10분).
        emailVerificationService.requireVerified(
                (EmailVerificationState) session.getAttribute(SIGNUP_EMAIL_KEY),
                EmailVerificationPurpose.SIGNUP, request.email());

        ApplicantSignUpResponse response = applicantSignUpService.signUp(request, identity);

        // 1회용이다. 남겨 두면 한 번의 인증으로 여러 계정을 만들 수 있다.
        session.removeAttribute(NiceVerificationController.VERIFIED_SESSION_KEY);
        session.removeAttribute(SIGNUP_EMAIL_KEY);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 가입 이메일 인증번호 발송. 발송이 접수된 뒤에만 세션 상태를 바꾼다(실패하면 예외로 끝나 이전 상태가 남는다). */
    @PostMapping("/email-verification/send")
    public ResponseEntity<ApiResponse<Void>> sendEmailVerification(
            @Valid @RequestBody EmailVerificationSendRequest request, HttpSession session) {
        EmailVerificationState state = applicantSignUpService.sendEmailVerification(
                (EmailVerificationState) session.getAttribute(SIGNUP_EMAIL_KEY), request.email());
        session.setAttribute(SIGNUP_EMAIL_KEY, state);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/email-verification/verify")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @Valid @RequestBody EmailVerificationConfirmRequest request, HttpSession session) {
        // 같은 세션의 동시 요청이 실패 수 확인·증가 사이에 끼면 5회 제한을 넘겨 추측할 수 있다. 세션 단위로 직렬화한다.
        synchronized (WebUtils.getSessionMutex(session)) {
            EmailVerificationState state = (EmailVerificationState) session.getAttribute(SIGNUP_EMAIL_KEY);
            try {
                emailVerificationService.verify(state, EmailVerificationPurpose.SIGNUP, request.email(), request.code());
            } finally {
                // 실패 수·확인 시각은 상태 객체 안에서 바뀐다. 세션 저장소가 바뀐 값을 알도록 다시 넣는다.
                if (state != null) {
                    session.setAttribute(SIGNUP_EMAIL_KEY, state);
                }
            }
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 가입 화면용 advisory 이메일 중복체크. email 입력값이 있을 때만 호출한다.
     */
    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse<ApplicantEmailAvailabilityResponse>> checkEmail(
            @RequestParam
            @NotBlank(message = "email은 필수입니다.")
            @Email(message = "유효한 이메일 형식이어야 합니다.")
            @Size(max = 255, message = "email은 255자 이하여야 합니다.")
            String email) {
        return ResponseEntity.ok(ApiResponse.success(applicantSignUpService.checkEmailAvailability(email)));
    }
}
