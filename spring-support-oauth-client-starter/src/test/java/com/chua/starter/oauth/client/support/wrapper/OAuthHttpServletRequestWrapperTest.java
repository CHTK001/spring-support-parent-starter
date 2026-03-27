package com.chua.starter.oauth.client.support.wrapper;

import com.chua.starter.oauth.client.support.user.UserResume;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OAuthHttpServletRequestWrapperTest {

    @Test
    void shouldExposePrincipalAndClearSessionOnLogout() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/secure/admin");
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);

        UserResume userResume = new UserResume();
        userResume.setUserId("2001");
        userResume.setUsername("admin");
        userResume.setNickName("管理员");
        userResume.setTenantId("tenant-x");
        userResume.setDeptId("dept-x");
        userResume.setRoles(Set.of("ADMIN"));
        userResume.setPermission(Set.of("USER_READ", "USER_WRITE"));
        userResume.setExt(Map.of("userId", "2001"));

        OAuthHttpServletRequestWrapper wrapper = OAuthHttpServletRequestWrapper.authenticated(request, userResume, "OAUTH_HTTP");

        assertEquals("admin", wrapper.getRemoteUser());
        assertEquals("OAUTH_HTTP", wrapper.getAuthType());
        assertNotNull(wrapper.getUserPrincipal());
        assertTrue(wrapper.isUserInRole("ADMIN"));
        assertTrue(wrapper.hasPermission("USER_READ"));
        assertTrue(wrapper.isAuthenticated());

        wrapper.logout();
        assertTrue(session.isInvalid());
    }
}
