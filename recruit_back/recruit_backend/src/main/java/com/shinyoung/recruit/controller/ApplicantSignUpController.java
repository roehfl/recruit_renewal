package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.ApplicantSignUpRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.ApplicantEmailAvailabilityResponse;
import com.shinyoung.recruit.dto.response.ApplicantSignUpResponse;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.service.ApplicantSignUpService;
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

@Validated
@RestController
@RequestMapping("/auth/applicants")
public class ApplicantSignUpController {

    private final ApplicantSignUpService applicantSignUpService;
    private final NiceVerificationService niceVerificationService;

    public ApplicantSignUpController(
            ApplicantSignUpService applicantSignUpService,
            NiceVerificationService niceVerificationService) {
        this.applicantSignUpService = applicantSignUpService;
        this.niceVerificationService = niceVerificationService;
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

        ApplicantSignUpResponse response = applicantSignUpService.signUp(request, identity);

        // 1회용이다. 남겨 두면 한 번의 인증으로 여러 계정을 만들 수 있다.
        session.removeAttribute(NiceVerificationController.VERIFIED_SESSION_KEY);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 가입 화면용 advisory 이메일 중복체크. email 입력값이 있을 때만 호출한다(가입 email optional 정책).
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
