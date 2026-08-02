package com.chua.starter.oauth.client.support.protocol;
import jakarta.servlet.http.Cookie;

import com.chua.starter.oauth.client.support.algorithm.TokenCipher;
import com.chua.starter.oauth.client.support.entity.AppKeySecret;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.user.LoginResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 静态协议单元测试
 * <p>静态协议在本地通过 TokenCipher（SM4）解析令牌，不发起网络请求、不校验 x-sign。
 * 覆盖 approve 有效/无效令牌、upgrade 刷新令牌、AppKey 不支持降级。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class StaticProtocolTest {

    private StaticProtocol protocol;

    /**
     * 测试准备
     */
    @BeforeEach
    void setUp() {
        AbstractProtocol.invalidateAllCache();
        AuthClientProperties props = new AuthClientProperties();
        props.setProtocol("static");
        protocol = new StaticProtocol(props);
    }

    /**
     * 测试清理
     */
    @AfterEach
    void tearDown() {
        AbstractProtocol.invalidateAllCache();
    }

    /**
     * 构造并加密一个有效令牌
     *
     * @param username 用户名
     * @return 加密令牌
     */
    private String buildToken(String username) {
        UserResult userResult = new UserResult();
        userResult.setUsername(username);
        userResult.setUserId("1");
        userResult.setLoginType("STATIC");
        userResult.setRoles(Set.of("ADMIN"));
        return TokenCipher.encrypt(userResult);
    }

    /**
     * 有效令牌 approve 返回 OK 且用户信息一致
     */
    @Test
    void approveValidTokenReturnsOk() {
        String token = buildToken("admin");
        AuthenticationInformation result = protocol.approve((Cookie[]) null, token, "WEB");
        assertEquals(Information.OK.getCode(), result.getInformation().getCode());
        assertNotNull(result.getReturnResult());
        assertEquals("admin", result.getReturnResult().getUsername());
        assertEquals("1", result.getReturnResult().getUserId());
    }

    /**
     * 无效令牌 approve 返回 403（未认证）
     */
    @Test
    void approveInvalidTokenReturnsNoAuth() {
        AuthenticationInformation result = protocol.approve((Cookie[]) null, "invalid-token-xxx", "WEB");
        assertEquals(Information.AUTHENTICATION_FAILURE.getCode(), result.getInformation().getCode());
    }

    /**
     * 空令牌 approve 返回 403（未认证）
     */
    @Test
    void approveNullTokenReturnsNoAuth() {
        AuthenticationInformation result = protocol.approve((Cookie[]) null, null, "WEB");
        assertEquals(Information.AUTHENTICATION_FAILURE.getCode(), result.getInformation().getCode());
    }

    /**
     * upgrade（REFRESH）基于旧令牌生成新令牌，新令牌可被解析
     */
    @Test
    void upgradeGeneratesFreshToken() {
        String token = buildToken("admin");
        LoginResult loginResult = protocol.upgrade(null, token, UpgradeType.REFRESH, token);
        assertNotNull(loginResult);
        String refreshed = loginResult.getToken();
        assertNotNull(refreshed);
        assertEquals(refreshed, loginResult.getRefreshToken());

        UserResult decoded = TokenCipher.decryptToUser(refreshed);
        assertNotNull(decoded);
        assertEquals("admin", decoded.getUsername());
    }

    /**
     * upgrade 空令牌返回 null
     */
    @Test
    void upgradeEmptyTokenReturnsNull() {
        assertNull(protocol.upgrade(null, null, UpgradeType.REFRESH, null));
    }

    /**
     * 静态协议不支持 AppKey 认证，返回 403 降级
     */
    @Test
    void authenticationUserCodeNotSupported() {
        AppKeySecret appKeySecret = new AppKeySecret().setUserCode("U-1").setAppId("APP-1");
        AuthenticationInformation result = protocol.authentication(appKeySecret);
        assertEquals(Information.AUTHENTICATION_FAILURE.getCode(), result.getInformation().getCode());
    }

    /**
     * 相同令牌二次 approve 命中本地缓存（返回结果一致）
     */
    @Test
    void approveHitsCacheOnRepeat() {
        String token = buildToken("admin");
        AuthenticationInformation first = protocol.approve((Cookie[]) null, token, "WEB");
        AuthenticationInformation second = protocol.approve((Cookie[]) null, token, "WEB");
        assertEquals(Information.OK.getCode(), first.getInformation().getCode());
        assertEquals(Information.OK.getCode(), second.getInformation().getCode());
        assertEquals(first.getReturnResult().getUsername(), second.getReturnResult().getUsername());
    }
}

