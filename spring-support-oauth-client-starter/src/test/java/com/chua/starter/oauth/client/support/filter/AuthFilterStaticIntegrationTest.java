package com.chua.starter.oauth.client.support.filter;

import com.chua.common.support.crypto.Codec;
import com.chua.common.support.text.json.Json;
import com.chua.spring.support.configuration.SpringBeanUtils;
import com.chua.starter.oauth.client.support.annotation.VerifyFingerprint;
import com.chua.starter.oauth.client.support.execute.AuthClientExecute;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.client.support.wrapper.OAuthHttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuthFilterStaticIntegrationTest {

    @Test
    void shouldWrapAuthenticatedRequestAndExposeUserAttributes() throws Exception {
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.refresh();
        SpringBeanUtils.setApplicationContext(applicationContext);

        AuthClientProperties properties = new AuthClientProperties();
        properties.setProtocol("Static");
        properties.setWhitelist(java.util.List.of());

        AuthFilter filter = new AuthFilter(
                new com.chua.starter.oauth.client.support.web.WebRequest(properties),
                Mockito.mock(RequestMappingHandlerMapping.class));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/secure/hello");
        request.addHeader(properties.getTokenName(), encodeToken());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        HttpServletRequest filteredRequest = (HttpServletRequest) chain.getRequest();
        OAuthHttpServletRequestWrapper wrapper = assertInstanceOf(OAuthHttpServletRequestWrapper.class, filteredRequest);
        assertEquals("tester", wrapper.getRemoteUser());
        assertEquals("1001", filteredRequest.getAttribute("userId"));
        assertNotNull(wrapper.getUserPrincipal());

        applicationContext.close();
    }

    @Test
    void shouldRejectFingerprintMismatchOnAnnotatedEndpoint() throws Exception {
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.refresh();
        SpringBeanUtils.setApplicationContext(applicationContext);

        AuthClientProperties properties = new AuthClientProperties();
        properties.setProtocol("Static");
        properties.setWhitelist(java.util.List.of());

        RequestMappingHandlerMapping handlerMapping = Mockito.mock(RequestMappingHandlerMapping.class);
        HandlerMethod handlerMethod = new HandlerMethod(new FingerprintController(), FingerprintController.class.getMethod("secured"));
        Mockito.when(handlerMapping.getHandler(Mockito.any())).thenReturn(new HandlerExecutionChain(handlerMethod));

        AuthFilter filter = new AuthFilter(new com.chua.starter.oauth.client.support.web.WebRequest(properties), handlerMapping);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/secure/fingerprint");
        request.addHeader(properties.getTokenName(), encodeToken());
        request.addHeader("x-oauth-fingerprint", "fingerprint-mismatch");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(40301, response.getStatus());
        assertNull(chain.getRequest());

        applicationContext.close();
    }

    static class FingerprintController {
        @VerifyFingerprint
        public void secured() {
        }
    }

    private String encodeToken() {
        UserResult userResult = new UserResult();
        userResult.setUid("uid-1001");
        userResult.setUserId("1001");
        userResult.setUsername("tester");
        userResult.setNickName("Tester");
        userResult.setRoles(Set.of("ADMIN"));
        userResult.setPermission(Set.of("USER_READ"));
        userResult.setFingerprint("fingerprint-001");
        return Codec.build("SM4", AuthClientExecute.DEFAULT_KEY).encodeHex(Json.toJson(userResult));
    }
}
