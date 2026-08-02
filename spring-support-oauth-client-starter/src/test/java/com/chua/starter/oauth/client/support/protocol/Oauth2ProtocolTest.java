package com.chua.starter.oauth.client.support.protocol;
import jakarta.servlet.http.Cookie;

import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OAuth2 协议单元测试
 * <p>OAuth2 遵循 RFC 6749 标准，使用 Basic 认证而非 x-sign 签名与请求体加密。
 * 覆盖 Basic 认证头、空令牌内省降级、不支持的扩展能力、地址缺失时的降级。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class Oauth2ProtocolTest {

    private AuthClientProperties props;
    private Oauth2Protocol protocol;

    /**
     * 测试准备
     */
    @BeforeEach
    void setUp() {
        AbstractProtocol.invalidateAllCache();
        props = new AuthClientProperties();
        props.setProtocol("oauth2");
        props.getKey().setAccessKey("client-id");
        props.getKey().setSecretKey("client-secret");
        protocol = new Oauth2Protocol(props);
    }

    /**
     * Basic 认证头为 base64(clientId:clientSecret)
     */
    @Test
    void buildBasicAuthEncodesCredentials() throws Exception {
        Method method = Oauth2Protocol.class.getDeclaredMethod("buildBasicAuth");
        method.setAccessible(true);
        String basic = (String) method.invoke(protocol);
        String expected = "Basic " + Base64.getEncoder()
                .encodeToString("client-id:client-secret".getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, basic);
    }

    /**
     * 空令牌内省返回 403，不发网络请求
     */
    @Test
    void introspectBlankTokenReturnsNoAuth() {
        AuthenticationInformation result = protocol.introspectToken("");
        assertEquals(Information.AUTHENTICATION_FAILURE.getCode(), result.getInformation().getCode());
    }

    /**
     * null 令牌内省返回 403
     */
    @Test
    void introspectNullTokenReturnsNoAuth() {
        AuthenticationInformation result = protocol.introspectToken(null);
        assertEquals(Information.AUTHENTICATION_FAILURE.getCode(), result.getInformation().getCode());
    }

    /**
     * OAuth2 标准不支持临时令牌，返回 501
     */
    @Test
    void createTemporaryTokenNotSupported() {
        assertEquals(501, protocol.createTemporaryToken("src-token", null).getCode());
    }

    /**
     * OAuth2 标准不包含在线状态，返回默认状态
     */
    @Test
    void onlineStatusDefaults() {
        assertEquals("MULTIPLE", protocol.getOnlineStatus("1").getOnlineMode());
        assertTrue(protocol.getOnlineStatus("1").isMultipleMode());
    }

    /**
     * OAuth2 标准不包含在线用户列表，返回空列表
     */
    @Test
    void onlineUsersEmpty() {
        assertNotNull(protocol.getOnlineUsers(null).getUsers());
        assertTrue(protocol.getOnlineUsers(null).getUsers().isEmpty());
    }

    /**
     * 未配置鉴权地址时 client_credentials 授权降级为服务器异常
     */
    @Test
    void clientCredentialsNoAddressFallsBack() {
        props.setAddress(null);
        protocol = new Oauth2Protocol(props);
        AuthenticationInformation result = protocol.clientCredentialsGrant("c", "s", null);
        assertEquals(Information.AUTHENTICATION_SERVER_EXCEPTION.getCode(), result.getInformation().getCode());
    }

    /**
     * 未配置鉴权地址时 approve 降级为服务器异常
     */
    @Test
    void approveNoAddressFallsBack() {
        props.setAddress(null);
        protocol = new Oauth2Protocol(props);
        AuthenticationInformation result = protocol.approve((Cookie[]) null, "AT-123", null);
        assertEquals(Information.AUTHENTICATION_SERVER_EXCEPTION.getCode(), result.getInformation().getCode());
    }

    /**
     * 标准 OAuth2 登录路径（getAccessToken）未配置地址时返回 500
     */
    @Test
    void getAccessTokenNoAddressFallsBack() {
        props.setAddress(null);
        protocol = new Oauth2Protocol(props);
        assertEquals(500, protocol.getAccessToken("admin", "123456", null, null).getCode());
    }

    /**
     * 标准 OAuth2 登出未配置地址时抛出异常
     */
    @Test
    void logoutNoAddressThrows() {
        props.setAddress(null);
        protocol = new Oauth2Protocol(props);
        assertThrows(Exception.class, () -> protocol.logout("1", null, null));
    }
}

