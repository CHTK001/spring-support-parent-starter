package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.task.cache.CacheProvider;
import com.chua.common.support.text.json.JsonObject;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.http.Cookie;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HttpProtocolUpgradeCacheTest {

    @BeforeEach
    void prepareCacheProvider() {
        ReflectionTestUtils.setField(
                AbstractProtocol.class,
                "CACHEABLE",
                Mockito.mock(CacheProvider.class)
        );
    }

    @Test
    void shouldClearOldCacheWhenRefreshUpgradeSucceeds() {
        TestHttpProtocol protocol = new TestHttpProtocol();

        protocol.upgrade(new Cookie("x-oauth-cookie", "legacy-cookie"), "access-token", UpgradeType.REFRESH, "refresh-token");

        assertThat(protocol.clearedKeys).containsExactly("access-token");
    }

    @Test
    void shouldClearOldCacheWhenVersionUpgradeSucceeds() {
        TestHttpProtocol protocol = new TestHttpProtocol();

        protocol.upgrade(new Cookie("x-oauth-cookie", "legacy-cookie"), "access-token", UpgradeType.VERSION, "refresh-token");

        assertThat(protocol.clearedKeys).containsExactly("access-token");
    }

    private static final class TestHttpProtocol extends HttpProtocol {
        private final List<String> clearedKeys = new ArrayList<>();

        private TestHttpProtocol() {
            super(new AuthClientProperties());
        }

        private AuthenticationInformation upgrade(Cookie cookie, String token, UpgradeType upgradeType, String refreshToken) {
            return super.upgradeInformation(cookie, token, upgradeType, refreshToken);
        }

        @Override
        protected AuthenticationInformation createAuthenticationInformation(
                JsonObject jsonObject, UpgradeType upgradeType, String path) {
            return new AuthenticationInformation(Information.OK, null);
        }

        @Override
        protected boolean hasCache(String cacheKey) {
            return true;
        }

        @Override
        protected void clearAuthenticationInformation(String cacheKey) {
            clearedKeys.add(cacheKey);
        }
    }
}
