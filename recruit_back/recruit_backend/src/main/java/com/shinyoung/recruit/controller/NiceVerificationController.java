package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.NiceResultRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.NiceRequestResponse;
import com.shinyoung.recruit.dto.response.NiceResultResponse;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.exception.NiceVerificationException;
import com.shinyoung.recruit.service.nice.NiceVerificationService;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * NICE 체크플러스 본인확인 엔드포인트.
 *
 * <p>{@code /callback} 과 {@code /callback/error} 는 <b>NICE 팝업이 POST 하는 cross-site 요청</b>이다.
 * 세션 쿠키가 실리지 않고(SameSite=Lax) form-urlencoded 로 온다. 그래서 JSON 이 아니라
 * {@code @RequestParam} 으로 받고, 세션 대조는 뒤따르는 {@code /result} 에서 한다.
 */
@RestController
@RequestMapping("/auth/nice")
public class NiceVerificationController {

    /** 팝업이 로드할 프론트 결과 라우트. 백엔드는 HTML 을 반환하지 않는다. */
    private static final String RESULT_PATH = "/nice-auth/result";

    /** 세션에 인증 결과를 담는 키. */
    public static final String VERIFIED_SESSION_KEY = "NICE_VERIFIED";

    private final NiceVerificationService niceVerificationService;

    public NiceVerificationController(NiceVerificationService niceVerificationService) {
        this.niceVerificationService = niceVerificationService;
    }

    @PostMapping("/request")
    public ResponseEntity<ApiResponse<NiceRequestResponse>> request(HttpSession session) {
        String encodeData =
                niceVerificationService.request(NiceVerificationPurpose.SIGNUP, session.getId());
        return ResponseEntity.ok(ApiResponse.success(new NiceRequestResponse(encodeData)));
    }

    @PostMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam("EncodeData") String encodeData) {
        try {
            return redirectToResult(niceVerificationService.handleCallback(encodeData));
        } catch (NiceVerificationException e) {
            return redirectToFailure();
        }
    }

    @PostMapping("/callback/error")
    public ResponseEntity<Void> errorCallback(@RequestParam("EncodeData") String encodeData) {
        try {
            return redirectToResult(niceVerificationService.handleErrorCallback(encodeData));
        } catch (NiceVerificationException e) {
            return redirectToFailure();
        }
    }

    @PostMapping("/result")
    public ResponseEntity<ApiResponse<NiceResultResponse>> result(
            @Valid @RequestBody NiceResultRequest request, HttpSession session) {
        NiceVerifiedIdentity identity =
                niceVerificationService.exchangeResult(request.token(), session.getId());
        session.setAttribute(VERIFIED_SESSION_KEY, identity);
        return ResponseEntity.ok(
                ApiResponse.success(NiceResultResponse.success(identity.name(), identity.phoneNumber())));
    }

    /**
     * POST 로 받아 GET 으로 넘긴다. 303 을 쓰는 이유는 302 가 일부 브라우저에서
     * 메서드를 보존해 POST 로 프론트 라우트를 치기 때문이다.
     */
    private ResponseEntity<Void> redirectToResult(String token) {
        return seeOther(RESULT_PATH + "?token=" + token);
    }

    /**
     * 콜백 실패는 토큰 없이 결과 라우트로 보낸다. 콜백은 팝업이 직접 받는 폼 POST 라, 400 JSON 을 돌려주면
     * 팝업에 원시 JSON 이 렌더되고 부모창에 실패가 전달되지 않는다. 프론트는 토큰이 없으면 FAIL 을 알리고 닫는다.
     *
     * <p>원인은 예외를 던진 서비스·클라이언트가 이미 로그로 남겼다. 여기서 다시 남기지 않는다.
     */
    private ResponseEntity<Void> redirectToFailure() {
        return seeOther(RESULT_PATH);
    }

    private ResponseEntity<Void> seeOther(String location) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(URI.create(location))
                .build();
    }
}
