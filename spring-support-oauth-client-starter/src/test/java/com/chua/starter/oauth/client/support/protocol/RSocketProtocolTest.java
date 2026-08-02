package com.chua.starter.oauth.client.support.protocol;
import jakarta.servlet.http.Cookie;

import com.chua.starter.common.support.algorithm.crypto.Codec;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * RSocket 协议单元测试
 * <p>RSocket 协议通过元数据携带 x-oauth-sign 签名与 x-oauth-encode 开关，请求体经
 * createData 加密。此处覆盖连接地址解析（host/port）、请求体加密能力、以及未配置
 * 地址时的异常降级。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class RSocketProtocolTest {

    private AuthClientProperties props;

    /**
     * 测试准备
     */
    @BeforeEach
    void setUp() {
        props = new AuthClientProperties();
        props.setProtocol("rsocket");
        props.setAddress("rsocket://192.168.1.11:7000");
        props.setOauthUrl("/verify");
    }

    /**
     * 解析 rsocket 协议地址主机名
     */
    @Test
    void extractHostFromRsocketUrl() throws Exception {
        Method method = RSocketProtocol.class.getDeclaredMethod("extractHost", String.class);
        method.setAccessible(true);
        RSocketProtocol protocol = new RSocketProtocol(props);
        assertEquals("192.168.1.11", method.invoke(protocol, "rsocket://192.168.1.11:7000"));
    }

    /**
     * 解析 http 协议地址主机名
     */
    @Test
    void extractHostFromHttpUrl() throws Exception {
        Method method = RSocketProtocol.class.getDeclaredMethod("extractHost", String.class);
        method.setAccessible(true);
        RSocketProtocol protocol = new RSocketProtocol(props);
        assertEquals("10.0.0.5", method.invoke(protocol, "http://10.0.0.5:8080"));
    }

    /**
     * 解析端口号
     */
    @Test
    void extractPort() throws Exception {
        Method method = RSocketProtocol.class.getDeclaredMethod("extractPort", String.class, int.class);
        method.setAccessible(true);
        RSocketProtocol protocol = new RSocketProtocol(props);
        assertEquals(7000, method.invoke(protocol, "rsocket://192.168.1.11:7000", 7000));
        assertEquals(9000, method.invoke(protocol, "rsocket://192.168.1.11:9000", 7000));
    }

    /**
     * 无端口时使用默认端口
     */
    @Test
    void extractPortDefault() throws Exception {
        Method method = RSocketProtocol.class.getDeclaredMethod("extractPort", String.class, int.class);
        method.setAccessible(true);
        RSocketProtocol protocol = new RSocketProtocol(props);
        assertEquals(7000, method.invoke(protocol, "http://10.0.0.5", 7000));
    }

    /**
     * 开启加密时 createData 输出十六进制密文（与 HTTP/Armeria 共享同一加密实现）
     */
    @Test
    void createDataEncryptedWhenEnabled() {
        props.setEnableEncryption(true);
        props.setEncryption("aes");
        RSocketProtocol protocol = new RSocketProtocol(props);

        String out = protocol.createData("hello", "rkey");
        assertNotEquals("hello", out, "加密后不应是明文");
        assertEquals("hello", Codec.build("aes", "rkey").decodeHex(out));
    }

    /**
     * 未配置地址时 approve 抛出地址不存在异常
     */
    @Test
    void approveWithoutAddressThrows() {
        props.setAddress(null);
        RSocketProtocol protocol = new RSocketProtocol(props);
        assertThrows(IllegalStateException.class, () -> protocol.approve((Cookie[]) null, "AT-1", "WEB"));
    }
}

