package com.shinyoung.recruit.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CSRF 헤더 검사. 테스트 yaml 은 이 필터를 끄므로(MockMvc 요청이 헤더를 붙이지 않는다) 여기서 직접 검증한다.
 */
class CsrfHeaderFilterTest {

    private static final String NICE_CALLBACK = "/api/auth/nice/callback";

    private final CsrfHeaderFilter filter = new CsrfHeaderFilter(
            Set.of(NICE_CALLBACK),
            (request, response, exception) -> response.setStatus(403)
    );

    private MockHttpServletResponse run(MockHttpServletRequest request, MockFilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void 헤더_없는_API_POST는_403으로_막는다() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(new MockHttpServletRequest("POST", "/api/applications/1/withdraw"), chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).as("다음 필터로 넘기지 않는다").isNull();
    }

    @Test
    void 헤더가_있으면_통과한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/applications/1/withdraw");
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(request, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void 조회_요청과_API_밖_경로와_NICE_콜백은_검사하지_않는다() throws Exception {
        for (MockHttpServletRequest request : new MockHttpServletRequest[]{
                new MockHttpServletRequest("GET", "/api/applications/1"),
                new MockHttpServletRequest("OPTIONS", "/api/applications/1/withdraw"),
                new MockHttpServletRequest("POST", "/h2-console/login.do"),
                new MockHttpServletRequest("POST", NICE_CALLBACK)
        }) {
            MockFilterChain chain = new MockFilterChain();

            MockHttpServletResponse response = run(request, chain);

            assertThat(response.getStatus()).as(request.getMethod() + " " + request.getRequestURI()).isEqualTo(200);
            assertThat(chain.getRequest()).isSameAs(request);
        }
    }
}
