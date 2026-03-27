package com.chua.starter.strategy.controller;

import com.chua.starter.strategy.config.StrategyProperties;
import com.chua.starter.strategy.support.StrategyConsoleSessionSupport;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyAuthControllerTest {

    @Test
    void shouldReturnAnonymousAuthenticatedStatusWhenEmbeddedAuthDisabled() {
        StrategyProperties properties = new StrategyProperties();
        properties.getWebAuth().setMode("none");
        StrategyAuthController controller = new StrategyAuthController(properties);

        Map<String, Object> data = controller.status(new MockHttpServletRequest()).getData();

        assertThat(data)
                .containsEntry("authEnabled", false)
                .containsEntry("authenticated", true)
                .containsEntry("username", "anonymous");
    }

    @Test
    void shouldCreateAuthenticatedSessionWhenLoginSucceeds() {
        StrategyProperties properties = new StrategyProperties();
        properties.getWebAuth().setUsername("ops");
        properties.getWebAuth().setPassword("secret");
        properties.getWebAuth().setSessionTimeout(90);
        StrategyAuthController controller = new StrategyAuthController(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();

        StrategyAuthController.LoginRequest loginRequest = new StrategyAuthController.LoginRequest();
        loginRequest.setUsername("ops");
        loginRequest.setPassword("secret");

        Map<String, Object> data = controller.login(loginRequest, request).getData();

        assertThat(data)
                .containsEntry("authEnabled", true)
                .containsEntry("authenticated", true)
                .containsEntry("username", "ops")
                .containsKey("token");
        assertThat(StrategyConsoleSessionSupport.isAuthenticated(request.getSession(false))).isTrue();
        assertThat(StrategyConsoleSessionSupport.username(request.getSession(false))).isEqualTo("ops");
        assertThat(request.getSession(false).getAttribute("userId")).isEqualTo("1");
        assertThat(request.getSession(false).getMaxInactiveInterval()).isEqualTo(90);
        assertThat(data.get("token")).isEqualTo(request.getSession(false).getId());
    }

    @Test
    void shouldRejectLoginWhenCredentialsDoNotMatch() {
        StrategyProperties properties = new StrategyProperties();
        StrategyAuthController controller = new StrategyAuthController(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();

        StrategyAuthController.LoginRequest loginRequest = new StrategyAuthController.LoginRequest();
        loginRequest.setUsername("admin");
        loginRequest.setPassword("wrong-password");

        var result = controller.login(loginRequest, request);

        assertThat(result.isOk()).isFalse();
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void shouldResolveUserInfoFromExistingSession() {
        StrategyProperties properties = new StrategyProperties();
        StrategyAuthController controller = new StrategyAuthController(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        StrategyConsoleSessionSupport.markAuthenticated(request.getSession(true), "admin");

        Map<String, Object> data = controller.getUserInfo(request).getData();

        assertThat(data)
                .containsEntry("authEnabled", true)
                .containsEntry("authenticated", true)
                .containsEntry("username", "admin");
    }
}
