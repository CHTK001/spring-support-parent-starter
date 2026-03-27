package com.chua.starter.oauth.client.support.web;

import com.chua.common.support.crypto.Codec;
import com.chua.common.support.text.json.Json;
import com.chua.spring.support.configuration.SpringBeanUtils;
import com.chua.starter.oauth.client.support.execute.AuthClientExecute;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.user.UserResult;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.context.support.StaticApplicationContext;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WebRequestStaticProtocolTest {

    @Test
    void shouldAuthenticateViaStaticProtocolToken() {
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.refresh();
        SpringBeanUtils.setApplicationContext(applicationContext);

        AuthClientProperties properties = new AuthClientProperties();
        properties.setProtocol("Static");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/secure/profile");
        request.addHeader(properties.getTokenName(), encodeToken());

        WebRequest webRequest = new WebRequest(properties, request, null);
        AuthenticationInformation information = webRequest.authentication();

        assertEquals(Information.OK.getCode(), information.getInformation().getCode());
        assertNotNull(information.getReturnResult());
        assertEquals("tester", information.getReturnResult().getUsername());
        assertEquals("1001", information.getReturnResult().getUserId());
        assertEquals("tenant-a", information.getReturnResult().getTenantId());

        applicationContext.close();
    }

    @Test
    void shouldResolveBearerAuthorizationHeader() {
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.refresh();
        SpringBeanUtils.setApplicationContext(applicationContext);

        AuthClientProperties properties = new AuthClientProperties();
        properties.setProtocol("Static");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/secure/bearer");
        request.addHeader("Authorization", "Bearer " + encodeToken());

        WebRequest webRequest = new WebRequest(properties, request, null);
        AuthenticationInformation information = webRequest.authentication();

        assertEquals(Information.OK.getCode(), information.getInformation().getCode());
        assertEquals("tester", information.getReturnResult().getUsername());

        applicationContext.close();
    }

    private String encodeToken() {
        UserResult userResult = new UserResult();
        userResult.setUid("uid-1001");
        userResult.setUserId("1001");
        userResult.setUsername("tester");
        userResult.setNickName("Tester");
        userResult.setTenantId("tenant-a");
        userResult.setDeptId("dept-a");
        userResult.setRoles(Set.of("ADMIN"));
        userResult.setPermission(Set.of("USER_READ"));
        userResult.setFingerprint("fingerprint-001");
        return Codec.build("SM4", AuthClientExecute.DEFAULT_KEY).encodeHex(Json.toJson(userResult));
    }
}
