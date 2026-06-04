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
}
