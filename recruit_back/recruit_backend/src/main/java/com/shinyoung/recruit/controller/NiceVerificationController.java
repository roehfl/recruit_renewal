package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.NiceRequestRequest;
import com.shinyoung.recruit.dto.request.NiceResultRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.NiceRequestResponse;
import com.shinyoung.recruit.dto.response.NiceResultResponse;
import com.shinyoung.recruit.exception.NiceVerificationException;
import com.shinyoung.recruit.service.nice.NiceVerificationService;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * NICE 체크플러스 본인확인 엔드포인트.
 *
 * <p>{@code /callback} 과 {@code /callback/error} 는 <b>NICE 팝업이 보내는 cross-site 요청</b>이다.
 * NICE 는 결과를 <b>GET 쿼리</b>({@code ?EncodeData=...})로 돌려준다(2026-09-22 실인증 확인 — 설계 초안은
 * 폼 POST 로 가정했다). 레거시 JSP 처럼 메서드를 가리지 않고 GET·POST 둘 다 받는다. 세션 쿠키가 실리지
 * 않으므로(SameSite=Lax) {@code @RequestParam} 으로 받고, 세션 대조는 뒤따르는 {@code /result} 에서 한다.
 */
@RestController
@RequestMapping("/auth/nice")
public class NiceVerificationController {

    private static final Logger log = LoggerFactory.getLogger(NiceVerificationController.class);

    /** 팝업이 로드할 프론트 결과 라우트. 백엔드는 HTML 을 반환하지 않는다. */
    private static final String RESULT_PATH = "/nice-auth/result";

    /** 세션에 인증 결과를 담는 키. */
    public static final String VERIFIED_SESSION_KEY = "NICE_VERIFIED";

    private final NiceVerificationService niceVerificationService;

    public NiceVerificationController(NiceVerificationService niceVerificationService) {
        this.niceVerificationService = niceVerificationService;
    }

    @PostMapping("/request")
    public ResponseEntity<ApiResponse<NiceRequestResponse>> request(
            @Valid @RequestBody NiceRequestRequest request, HttpSession session) {
        String encodeData = niceVerificationService.request(request.purpose(), session.getId());
        return ResponseEntity.ok(ApiResponse.success(new NiceRequestResponse(encodeData)));
    }

    @RequestMapping(value = "/callback", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Void> callback(
            @RequestParam(value = "EncodeData", required = false) String encodeData) {
        String normalized = normalizeEncodeData(encodeData);
        if (normalized == null) {
            return redirectToFailure();
        }
        try {
            return redirectToResult(niceVerificationService.handleCallback(normalized));
        } catch (NiceVerificationException e) {
            return redirectToFailure();
        }
    }

    @RequestMapping(value = "/callback/error", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Void> errorCallback(
            @RequestParam(value = "EncodeData", required = false) String encodeData) {
        String normalized = normalizeEncodeData(encodeData);
        if (normalized == null) {
            return redirectToFailure();
        }
        try {
            return redirectToResult(niceVerificationService.handleErrorCallback(normalized));
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
     * 결과 라우트로 넘긴다. 303 을 쓰는 이유는 콜백이 POST 로 들어왔을 때 302 가 일부 브라우저에서
     * 메서드를 보존해 POST 로 프론트 라우트를 치기 때문이다.
     */
    private ResponseEntity<Void> redirectToResult(String token) {
        return seeOther(RESULT_PATH + "?token=" + token);
    }

    /**
     * 콜백 실패는 토큰 없이 결과 라우트로 보낸다. 콜백은 팝업이 직접 받는 페이지 이동이라, 400 JSON 을 돌려주면
     * 팝업에 원시 JSON 이 렌더되고 부모창에 실패가 전달되지 않는다. 프론트는 토큰이 없으면 FAIL 을 알리고 닫는다.
     *
     * <p>원인은 예외를 던진 서비스·클라이언트가 이미 로그로 남겼다. 여기서 다시 남기지 않는다.
     */
    private ResponseEntity<Void> redirectToFailure() {
        return seeOther(RESULT_PATH);
    }

    /**
     * 쿼리로 온 EncodeData 를 복호화할 수 있는 형태로 되돌린다. 없으면 null.
     *
     * <p>EncodeData 는 Base64 라 '+' 가 섞인다. NICE 가 퍼센트 인코딩 없이 쿼리에 붙이면 서블릿이 '+' 를
     * 공백으로 디코드해 복호화가 깨진다. Base64 에는 공백이 없으므로 공백을 '+' 로 되돌려도 손실이 없다.
     * <b>trim 하지 않는다</b> — 값이 '+' 로 시작하거나 끝나면 양끝 공백이 곧 '+' 다.
     */
    private static String normalizeEncodeData(String encodeData) {
        if (encodeData == null || encodeData.isEmpty()) {
            log.info("NICE 콜백에 EncodeData 가 없습니다.");
            return null;
        }
        return encodeData.replace(' ', '+');
    }

    private ResponseEntity<Void> seeOther(String location) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(URI.create(location))
                .build();
    }
}
