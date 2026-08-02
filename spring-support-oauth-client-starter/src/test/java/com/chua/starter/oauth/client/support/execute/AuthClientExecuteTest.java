package com.chua.starter.oauth.client.support.execute;

import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.ratelimit.RateLimitService;
import com.chua.starter.oauth.client.support.runtime.OauthClientRuntimeContext;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 鉴权客户端便捷登录方法单元测试
 *
 * <p>验证三方登录 {@link AuthClientExecute#thirdPartyLogin} 与手机号登录
 * {@link AuthClientExecute#smsLogin} 两个语义化入口：委托 {@code getAccessToken}
 * 签发令牌，并自动继承账号锁定限流埋点。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class AuthClientExecuteTest {

    /**
     * 手机号账号（static 协议临时账号，单值匹配）
     */
    private static final String PHONE = "13800000000";

    /**
     * 未配置账号（用于失败锁定测试）
     */
    private static final String UNKNOWN = "99999999999";

    /**
     * 限流服务（单例，需在用例间恢复）
     */
    private RateLimitService rateLimitService;

    /**
     * 测试准备：配置 static 协议 + 临时账号 + 限流阈值
     */
    @BeforeEach
    void setUp() {
        rateLimitService = RateLimitService.getInstance();
        rateLimitService.resetAccount(PHONE);
        rateLimitService.resetAccount(UNKNOWN);

        AuthClientProperties props = new AuthClientProperties();
        props.setProtocol("static");
        props.getTemp().setUser(PHONE);
        AuthClientProperties.RateLimit rateLimit = new AuthClientProperties.RateLimit();
        rateLimit.setEnable(true);
        AuthClientProperties.RateLimit.LoginLimit login = new AuthClientProperties.RateLimit.LoginLimit();
        login.setEnable(true);
        login.setAccountFailLimit(3);
        login.setAccountLockMinutes(1);
        rateLimit.setLogin(login);
        props.setRateLimit(rateLimit);
        OauthClientRuntimeContext.setAuthClientProperties(props);
    }

    /**
     * 测试清理：恢复限流计数与运行时上下文
     */
    @AfterEach
    void tearDown() {
        rateLimitService.resetAccount(PHONE);
        rateLimitService.resetAccount(UNKNOWN);
        OauthClientRuntimeContext.setAuthClientProperties(null);
    }

    /**
     * 三方登录：身份确认后按手机号签发令牌
     */
    @Test
    void thirdPartyLoginShouldIssueToken() {
        Map<String, Object> ext = new HashMap<>();
        ext.put("thirdPartyType", "wx_mini_app");
        ext.put("openid", "openid-xxx");

        AuthClientExecute client = new AuthClientExecute();
        LoginAuthResult result = client.thirdPartyLogin(PHONE, PHONE, AuthType.WECHAT, ext);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertNotNull(result.getToken());
        assertTrue(result.getToken().length() > 20);
    }

    /**
     * 手机号登录：委托 getAccessToken 执行（static 协议下凭证为空不匹配）
     */
    @Test
    void smsLoginShouldDelegateToGetAccessToken() {
        Map<String, Object> ext = new HashMap<>();
        ext.put("loginType", "sms");
        ext.put("phone", PHONE);

        AuthClientExecute client = new AuthClientExecute();
        LoginAuthResult result = client.smsLogin(PHONE, ext);

        assertNotNull(result);
        assertEquals(403, result.getCode());
    }

    /**
     * 三方登录失败：连续失败达到阈值后账号被锁定
     */
    @Test
    void thirdPartyLoginFailureShouldLockAccount() {
        AuthClientExecute client = new AuthClientExecute();
        Map<String, Object> ext = new HashMap<>();

        // 前 3 次失败累计计数，第 3 次达阈值触发锁定
        for (int i = 0; i < 3; i++) {
            LoginAuthResult result = client.thirdPartyLogin(UNKNOWN, "wrong", AuthType.WECHAT, ext);
            assertEquals(403, result.getCode());
        }

        // 第 4 次应命中账号锁定
        LoginAuthResult locked = client.thirdPartyLogin(UNKNOWN, "wrong", AuthType.WECHAT, ext);
        assertEquals(403, locked.getCode());
        assertTrue(locked.getMessage().contains("锁定"));
    }

    /**
     * 用刷新令牌换临时令牌：空刷新令牌返回参数错误
     */
    @Test
    void createTemporaryTokenByRefreshTokenRejectsBlank() {
        AuthClientExecute client = new AuthClientExecute();
        LoginAuthResult result = client.createTemporaryTokenByRefreshToken("", 60, null);
        assertNotNull(result);
        assertEquals(400, result.getCode());
    }

    /**
     * 用刷新令牌换临时令牌：委托到协议层（static 协议默认不支持返回 501）
     */
    @Test
    void createTemporaryTokenByRefreshTokenDelegatesToProtocol() {
        AuthClientExecute client = new AuthClientExecute();
        LoginAuthResult result = client.createTemporaryTokenByRefreshToken("refresh-token-xxx", 60, null);
        assertNotNull(result);
        assertEquals(501, result.getCode());
    }
}
