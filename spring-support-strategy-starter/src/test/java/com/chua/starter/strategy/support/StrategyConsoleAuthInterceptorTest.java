package com.chua.starter.strategy.support;

import com.chua.starter.strategy.config.StrategyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyConsoleAuthInterceptorTest {

    @Test
    void shouldAllowAuthenticatedConsoleRequest() throws Exception {
        StrategyConsoleAuthInterceptor interceptor = new StrategyConsoleAuthInterceptor(new StrategyProperties());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/strategy-console/index.html");
        MockHttpServletResponse response = new MockHttpServletResponse();
        StrategyConsoleSessionSupport.markAuthenticated(request.getSession(true), "admin");

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldWriteUnauthorizedJsonForConsoleApiWhenNotLoggedIn() throws Exception {
        StrategyConsoleAuthInterceptor interceptor = new StrategyConsoleAuthInterceptor(new StrategyProperties());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v2/strategy/metrics");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"401\"").contains("请先登录控制台");
    }

    @Test
    void shouldRedirectConsolePageToLoginWhenNotLoggedIn() throws Exception {
        StrategyConsoleAuthInterceptor interceptor = new StrategyConsoleAuthInterceptor(new StrategyProperties());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/strategy-console/index.html");
        request.setContextPath("/demo");
        request.setRequestURI("/demo/strategy-console/index.html");
        request.setQueryString("tab=metrics");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getRedirectedUrl())
                .isEqualTo("/demo/strategy-console/login.html?redirect=%2Fdemo%2Fstrategy-console%2Findex.html%3Ftab%3Dmetrics");
    }
}
