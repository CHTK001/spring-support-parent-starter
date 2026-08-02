package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.utils.DigestUtils;
import com.chua.starter.common.support.algorithm.crypto.Codec;
import com.chua.starter.oauth.client.support.entity.AppKeySecret;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.oauth.client.support.user.UserResult;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 协议基类单元测试
 * <p>覆盖影响所有协议的公共能力：请求体加密（createData）、是否加密开关（isEncode）、
 * AK/SK 二次加密、缓存 key 计算（SHA-256）、缓存命中与失效、AppKey 缓存 key 优先级。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class AbstractProtocolTest {

    /**
     * 测试协议子类：暴露 protected 方法并统计调用次数
     */
    static class TestProtocol extends AbstractProtocol {
        int approveCallCount = 0;
        int authenticationCallCount = 0;
        AuthenticationInformation result = AuthenticationInformation.noAuth();

        TestProtocol(AuthClientProperties p) {
            super(p);
        }

        @Override
        protected AuthenticationInformation approve(Cookie cookie, String token, String subProtocol) {
            approveCallCount++;
            return result;
        }

        @Override
        protected AuthenticationInformation authenticationUserCode(AppKeySecret appKeySecret) {
            authenticationCallCount++;
            return result;
        }

        @Override
        protected AuthenticationInformation upgradeInformation(Cookie cookie, String token, UpgradeType upgradeType, String refreshToken) {
            return result;
        }

        @Override
        public LoginAuthResult getAccessToken(String username, String password, AuthType authType, Map<String, Object> ext) {
            return null;
        }

        @Override
        public LoginAuthResult logout(String uid, LogoutType logoutType, UserResult userResult) {
            return null;
        }

        String data(String value, String key) {
            return createData(value, key);
        }

        boolean encode() {
            return isEncode();
        }

        String accessKey() {
            return getAccessKey();
        }

        String key(Cookie[] cookies, String token) {
            return getCacheKey(cookies, token);
        }
    }

    private AuthClientProperties props;
    private TestProtocol protocol;

    /**
     * 测试准备
     */
    @BeforeEach
    void setUp() {
        AbstractProtocol.invalidateAllCache();
        props = new AuthClientProperties();
        props.setProtocol("test");
        protocol = new TestProtocol(props);
    }

    /**
     * 测试清理
     */
    @AfterEach
    void tearDown() {
        AbstractProtocol.invalidateAllCache();
    }

    // ==================== 加密能力 ====================

    /**
     * 关闭加密时 createData 原样返回明文
     */
    @Test
    void encryptDisabledReturnsPlain() {
        props.setEnableEncryption(false);
        protocol = new TestProtocol(props);
        assertEquals("hello", protocol.data("hello", "key"));
    }

    /**
     * 开启加密时 createData 输出十六进制密文（非明文）
     */
    @Test
    void encryptEnabledReturnsHexCipher() {
        props.setEnableEncryption(true);
        props.setEncryption("aes");
        protocol = new TestProtocol(props);
        String out = protocol.data("hello", "key");
        assertNotEquals("hello", out, "加密后不应是明文");
        assertEquals("hello", Codec.build("aes", "key").decodeHex(out),
                "使用同一密钥应可解密还原");
    }

    /**
     * 开启加密且启用 AK/SK 时，数据先按 SecretKey 二次加密再按随机密钥加密
     */
    @Test
    void encryptWithAkskDoubleEncryption() {
        props.setEnableEncryption(true);
        props.setEncryption("aes");
        props.getKey().setEnabled(true);
        props.getKey().setAccessKey("ak-1");
        props.getKey().setSecretKey("sk-1");
        protocol = new TestProtocol(props);

        String out = protocol.data("hello", "key");
        // 外层解密后仍为内层（SK 加密）的十六进制字符串
        String outerDecoded = Codec.build("aes", "key").decodeHex(out);
        assertNotEquals("hello", outerDecoded, "外层解密后不应直接是明文");
        // 内层按 SecretKey 解密还原明文
        assertEquals("hello", Codec.build("aes", "sk-1").decodeHex(outerDecoded));
    }

    /**
     * isEncode 反映 enableEncryption 配置
     */
    @Test
    void isEncodeReflectsFlag() {
        props.setEnableEncryption(true);
        protocol = new TestProtocol(props);
        assertEquals(true, protocol.encode());

        props.setEnableEncryption(false);
        protocol = new TestProtocol(props);
        assertEquals(false, protocol.encode());
    }

    /**
     * 启用 AK/SK 时返回 accessKey
     */
    @Test
    void getAccessKeyEnabled() {
        props.getKey().setEnabled(true);
        props.getKey().setAccessKey("ak-2");
        protocol = new TestProtocol(props);
        assertEquals("ak-2", protocol.accessKey());
    }

    /**
     * 未启用 AK/SK 时返回 null
     */
    @Test
    void getAccessKeyDisabled() {
        props.getKey().setEnabled(false);
        props.getKey().setAccessKey("ak-3");
        protocol = new TestProtocol(props);
        assertNull(protocol.accessKey());
    }

    // ==================== 缓存 key ====================

    /**
     * 存在 token 时缓存 key 为 token 的 SHA-256
     */
    @Test
    void cacheKeyUsesTokenSha256() {
        assertEquals(DigestUtils.sha256("AT-123"), protocol.key(null, "AT-123"));
    }

    /**
     * 无 token 时回退到 x-oauth-cookie 的 SHA-256
     */
    @Test
    void cacheKeyFallsBackToCookie() {
        Cookie[] cookies = {new Cookie("x-oauth-cookie", "cookie-val")};
        assertEquals(DigestUtils.sha256("cookie-val"), protocol.key(cookies, null));
    }

    /**
     * 无 token 也无有效 cookie 时返回 null
     */
    @Test
    void cacheKeyNullWhenNoCredential() {
        assertNull(protocol.key(null, null));
        assertNull(protocol.key(new Cookie[]{new Cookie("other", "v")}, ""));
    }

    // ==================== 缓存行为 ====================

    /**
     * 首次 approve 走协议，二次命中缓存不再走协议
     */
    @Test
    void approveHitsCacheOnSecondCall() {
        protocol.result = ok("admin");
        protocol.approve((Cookie[]) null, "t-cache-1", "WEB");
        assertEquals(1, protocol.approveCallCount);
        protocol.approve((Cookie[]) null, "t-cache-1", "WEB");
        assertEquals(1, protocol.approveCallCount, "二次调用应命中缓存");
    }

    /**
     * 鉴权失败结果不缓存，每次都重新走协议
     */
    @Test
    void approveDoesNotCacheFailure() {
        protocol.result = AuthenticationInformation.noAuth();
        protocol.approve((Cookie[]) null, "t-fail-1", "WEB");
        protocol.approve((Cookie[]) null, "t-fail-1", "WEB");
        assertEquals(2, protocol.approveCallCount, "失败结果不应命中缓存");
    }

    /**
     * invalidateCache 清除后重新走协议
     */
    @Test
    void invalidateCacheClears() {
        protocol.result = ok("admin");
        protocol.approve((Cookie[]) null, "t-inv-1", "WEB");
        AbstractProtocol.invalidateCache("t-inv-1");
        protocol.approve((Cookie[]) null, "t-inv-1", "WEB");
        assertEquals(2, protocol.approveCallCount, "失效缓存后应重新鉴权");
    }

    /**
     * AppKey 认证缓存 key 优先使用 userCode，body 变化不影响命中
     */
    @Test
    void authenticationKeyPrefersUserCode() {
        protocol.result = ok("admin");
        protocol.authentication(new AppKeySecret().setUserCode("U-100").setBody("{\"x\":1}"));
        assertEquals(1, protocol.authenticationCallCount);
        protocol.authentication(new AppKeySecret().setUserCode("U-100").setBody("{\"x\":2}"));
        assertEquals(1, protocol.authenticationCallCount, "相同 userCode 应命中缓存");
    }

    /**
     * 构造成功认证信息
     *
     * @param username 用户名
     * @return 认证信息
     */
    static AuthenticationInformation ok(String username) {
        UserResume userResume = new UserResume();
        userResume.setUsername(username);
        return new AuthenticationInformation(Information.OK, userResume);
    }
}

