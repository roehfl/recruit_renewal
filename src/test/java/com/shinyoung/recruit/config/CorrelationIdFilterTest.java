package com.shinyoung.recruit.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void X_Request_Id_헤더가_있으면_재사용하고_응답에_echo한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "given-correlation-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> duringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> duringChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(duringChain.get()).isEqualTo("given-correlation-id");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("given-correlation-id");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull(); // finally 에서 제거
    }

    @Test
    void 헤더가_없으면_UUID를_생성해_MDC와_응답에_넣는다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> duringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> duringChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(duringChain.get()).isNotBlank();
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(duringChain.get());
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void blank_헤더면_UUID를_생성한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> duringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> duringChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(duringChain.get()).isNotBlank().isNotEqualTo("   ");
    }

    @Test
    void 길이_100_초과_헤더는_재사용하지_않고_UUID로_대체한다() throws Exception {
        String tooLong = "x".repeat(CorrelationIdFilter.MAX_CORRELATION_ID + 1);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, tooLong);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> duringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> duringChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(duringChain.get())
                .isNotBlank()
                .isNotEqualTo(tooLong)
                .hasSizeLessThanOrEqualTo(CorrelationIdFilter.MAX_CORRELATION_ID);
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(duringChain.get());
    }

    @Test
    void 길이_100_이하_헤더는_그대로_재사용한다() throws Exception {
        String maxAllowed = "y".repeat(CorrelationIdFilter.MAX_CORRELATION_ID);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, maxAllowed);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> duringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> duringChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(duringChain.get()).isEqualTo(maxAllowed);
    }

    @Test
    void CR_LF_포함_헤더는_재사용하지_않고_UUID로_대체한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "evil\r\nX-Injected: 1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> duringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> duringChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(duringChain.get())
                .isNotBlank()
                .doesNotContain("\r")
                .doesNotContain("\n")
                .doesNotContain("evil");
    }

    @Test
    void CR_LF_외_제어문자_포함_헤더도_재사용하지_않고_UUID로_대체한다() throws Exception {
        // 값 중간의 TAB/그 외 ISO 제어문자 — 응답 헤더 echo 시 WAS reject 방지(2차 리뷰 보완)
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "corr\tid-withcontrol");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> duringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> duringChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(duringChain.get())
                .isNotBlank()
                .doesNotContain("\t")
                .doesNotContain("corr");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(duringChain.get());
    }
}
