package com.chua.payment.support.support;

import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.oauth.client.support.wrapper.OAuthHttpServletRequestWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentAccountPrincipalResolverTest {

    private final PaymentAccountPrincipalResolver resolver = new PaymentAccountPrincipalResolver();

    @Test
    void shouldResolveUserFromOAuthWrapper() {
        UserResume userResume = new UserResume();
        userResume.setUserId("101");
        userResume.setUsername("demo");
        userResume.setRoles(Set.of("SUPER_ADMIN"));

        OAuthHttpServletRequestWrapper request = OAuthHttpServletRequestWrapper.authenticated(new MockHttpServletRequest(), userResume, "TEST");

        assertEquals(101L, resolver.resolveUserId(request));
        assertTrue(resolver.isAdmin(userResume));
        assertEquals("demo", resolver.resolve(request).getUsername());
    }
}
