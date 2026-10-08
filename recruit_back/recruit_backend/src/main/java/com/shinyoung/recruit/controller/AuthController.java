package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.config.NiceProperties;
import com.shinyoung.recruit.dto.request.LoginRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.LoginOptionsResponse;
import com.shinyoung.recruit.dto.response.LoginUserResponse;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.exception.LoginSecondFactorException;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionRegistry sessionRegistry;
    private final NiceProperties niceProperties;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginUserResponse>> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request, HttpServletResponse response) {
        // 지원자 2차 인증: 로그인 전 세션에 담긴 NICE 결과(용도 LOGIN)를 요청에 실어 인증기에 넘긴다.
        // 소비는 성공·2차 인증 실패 때만 한다. 비밀번호 실패는 남겨 두어 재입력에 NICE를 다시 하지 않게 한다.
        HttpSession preLoginSession = request.getSession(false);
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(loginRequest.getLoginId(), loginRequest.getPassword());
        token.setDetails(loginNiceResult(preLoginSession));

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(token);
        } catch (LoginSecondFactorException e) {
            consumeLoginNiceResult(preLoginSession);
            throw e;
        }
        consumeLoginNiceResult(preLoginSession);
        // 이름·생년월일·성별이 든 NICE 결과가 세션의 SecurityContext에 남지 않게 한다(원래 details는 비어 있었다).
        if (authentication instanceof AbstractAuthenticationToken authenticationToken) {
            authenticationToken.setDetails(null);
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(true);
//        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        request.changeSessionId();

        securityContextRepository.saveContext(context, request, response);
        // 비밀번호 변경 시 이 계정의 다른 세션을 만료할 수 있도록 등록한다(UserSessionRevoker).
        sessionRegistry.registerNewSession(request.getSession().getId(), authentication.getPrincipal());

        LoginUserResponse loginUser = toLoginUserResponse(authentication);

        return ResponseEntity.ok(ApiResponse.success(loginUser));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<LoginUserResponse>> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.fail("로그인이 필요합니다."));
        }

        LoginUserResponse loginUser = toLoginUserResponse(authentication);

        return ResponseEntity.ok(ApiResponse.success(loginUser));
    }

    /** 로그인 화면이 NICE 팝업을 띄울지 정하는 데 쓴다. 서버는 이 값과 무관하게 설정이 켜져 있으면 지원자에게 강제한다. */
    @GetMapping("/login-options")
    public ResponseEntity<ApiResponse<LoginOptionsResponse>> loginOptions() {
        return ResponseEntity.ok(ApiResponse.success(new LoginOptionsResponse(niceProperties.isLoginTwoFactorEnabled())));
    }

    /** 세션의 NICE 결과가 로그인 용도일 때만 돌려준다. 다른 용도(가입 등)의 결과는 쓰지도 지우지도 않는다. */
    private static NiceVerifiedIdentity loginNiceResult(HttpSession session) {
        if (session != null
                && session.getAttribute(NiceVerificationController.VERIFIED_SESSION_KEY) instanceof NiceVerifiedIdentity identity
                && identity.purpose() == NiceVerificationPurpose.LOGIN) {
            return identity;
        }
        return null;
    }

    private static void consumeLoginNiceResult(HttpSession session) {
        if (loginNiceResult(session) != null) {
            session.removeAttribute(NiceVerificationController.VERIFIED_SESSION_KEY);
        }
    }

    private LoginUserResponse toLoginUserResponse(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        List<String> roles = userDetails.getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority())
                .toList();

        return new LoginUserResponse(userDetails.getUsername(), userDetails.getName(), userDetails.getDeptName(), userDetails.getUserType(), roles);
    }
}
