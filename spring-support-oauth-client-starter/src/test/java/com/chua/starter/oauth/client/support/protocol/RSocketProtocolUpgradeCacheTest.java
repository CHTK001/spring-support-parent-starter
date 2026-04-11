package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.task.cache.CacheProvider;
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

class RSocketProtocolUpgradeCacheTest {

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
        TestRSocketProtocol protocol = new TestRSocketProtocol();

        protocol.upgrade(new Cookie("x-oauth-cookie", "legacy-cookie"), "access-token", UpgradeType.REFRESH, "refresh-token");

        assertThat(protocol.clearedKeys).containsExactly("access-token");
    }

    @Test
    void shouldClearOldCacheWhenVersionUpgradeSucceeds() {
        TestRSocketProtocol protocol = new TestRSocketProtocol();

        protocol.upgrade(new Cookie("x-oauth-cookie", "legacy-cookie"), "access-token", UpgradeType.VERSION, "refresh-token");

        assertThat(protocol.clearedKeys).containsExactly("access-token");
    }

    private static final class TestRSocketProtocol extends RSocketProtocol {
        private final List<String> clearedKeys = new ArrayList<>();

        private TestRSocketProtocol() {
            super(new AuthClientProperties());
        }

        private AuthenticationInformation upgrade(Cookie cookie, String token, UpgradeType upgradeType, String refreshToken) {
            return super.upgradeInformation(cookie, token, upgradeType, refreshToken);
        }

        @Override
        protected AuthenticationInformation sendRSocketRequest(String route, com.chua.common.support.text.json.JsonObject jsonObject, UpgradeType upgradeType) {
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
