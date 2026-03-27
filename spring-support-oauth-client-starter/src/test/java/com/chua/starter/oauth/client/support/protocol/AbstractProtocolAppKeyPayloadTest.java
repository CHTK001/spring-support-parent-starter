package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.text.json.JsonObject;
import com.chua.starter.oauth.client.support.entity.AppKeySecret;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbstractProtocolAppKeyPayloadTest {

    @Test
    void shouldBuildAppKeyPayloadFromPlainCodeBody() {
        TestProtocol protocol = new TestProtocol(new AuthClientProperties());
        AppKeySecret appKeySecret = new AppKeySecret()
                .setUserCode("test-app-key-001")
                .setAppId("system-app-id")
                .setBody("dummy-code");

        JsonObject payload = protocol.buildPayload(appKeySecret);

        assertEquals("test-app-key-001", payload.getString("x-oauth-user-code"));
        assertEquals("system-app-id", payload.getString("x-oauth-app-id"));
        assertEquals("dummy-code", payload.getString("code"));
    }

    @Test
    void shouldMergeJsonBodyIntoAppKeyPayload() {
        TestProtocol protocol = new TestProtocol(new AuthClientProperties());
        AppKeySecret appKeySecret = new AppKeySecret()
                .setUserCode("test-app-key-001")
                .setBody("{\"code\":\"dummy-code\",\"scope\":\"read\",\"tenantId\":\"tenant-a\"}");

        JsonObject payload = protocol.buildPayload(appKeySecret);

        assertEquals("test-app-key-001", payload.getString("x-oauth-user-code"));
        assertEquals("dummy-code", payload.getString("code"));
        assertEquals("read", payload.getString("scope"));
        assertEquals("tenant-a", payload.getString("tenantId"));
    }

    private static final class TestProtocol extends AbstractProtocol {

        private TestProtocol(AuthClientProperties authClientProperties) {
            super(authClientProperties);
        }

        private JsonObject buildPayload(AppKeySecret appKeySecret) {
            return createAppKeyAuthenticationData(appKeySecret);
        }

        @Override
        protected AuthenticationInformation approve(jakarta.servlet.http.Cookie cookies, String token, String subProtocol) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected AuthenticationInformation authenticationUserCode(AppKeySecret appKeySecret) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected AuthenticationInformation upgradeInformation(jakarta.servlet.http.Cookie cookie, String token, UpgradeType upgradeType, String refreshToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LoginAuthResult getAccessToken(String username, String password, AuthType authType, Map<String, Object> ext) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LoginAuthResult logout(String uid, LogoutType logoutType, UserResult userResult) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LoginAuthResult createTemporaryToken(String sourceToken, Map<String, Object> ext) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OnlineStatus getOnlineStatus(String uid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OnlineUserResult getOnlineUsers(OnlineUserQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FingerprintVerifyResult verifyFingerprint(String token, String fingerprint) {
            throw new UnsupportedOperationException();
        }
    }
}
