package com.chua.starter.oauth.client.support.protocol;
import jakarta.servlet.http.Cookie;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.starter.common.support.algorithm.crypto.Codec;
import com.chua.starter.common.support.utils.SignUtils;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Armeria 协议端到端测试
 * <p>通过本地 HttpServer 模拟认证服务器，验证 Armeria WebClient 请求的
 * 加密开关（x-oauth-encode）、x-sign 签名携带与一致性、响应解析。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class ArmeriaProtocolTest {

    private HttpServer server;
    private AuthClientProperties props;

    private final AtomicReference<Map<String, String>> capturedHeaders = new AtomicReference<>();
    private final AtomicReference<String> capturedBody = new AtomicReference<>();
    private volatile String responseBody = "";
    private volatile int responseCode = 200;
    private volatile String responseSerial = null;
    private volatile boolean verifySignOnServer = false;
    private volatile boolean tamperRejected = false;

    /**
     * 启动本地认证服务器
     */
    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/verify", this::handle);
        server.start();
        int port = server.getAddress().getPort();

        props = new AuthClientProperties();
        props.setProtocol("armeria");
        props.setAddress("127.0.0.1:" + port);
        props.setOauthUrl("/verify");
    }

    /**
     * 处理认证请求
     */
    private void handle(HttpExchange exchange) throws IOException {
        Map<String, String> headers = new HashMap<>();
        exchange.getRequestHeaders().forEach((k, v) ->
                headers.put(k.toLowerCase(), v.isEmpty() ? null : v.get(0)));
        capturedHeaders.set(headers);
        capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        // 模拟服务端校验 x-sign：篡改请求参数后复算签名，若与请求头不一致则判定请求被篡改
        if (verifySignOnServer) {
            String rawBody = capturedBody.get();
            try {
                String dataJson = Json.fromJson(rawBody, JsonObject.class).getObject("data", String.class);
                JsonObject parsed = Json.fromJson(dataJson, JsonObject.class);
                String token = String.valueOf(parsed.get("x-oauth-token"));
                parsed.put("x-oauth-token", token + "-tampered");
                String tamperedSign = SignUtils.generateSignFromMap(parsed);
                tamperRejected = !tamperedSign.equals(headers.get("x-oauth-sign"));
            } catch (Exception ignored) {
                // 请求体非预期格式时忽略，由响应路径处理
            }
        }

        if (responseSerial != null) {
            exchange.getResponseHeaders().add("x-oauth-response-serial", responseSerial);
        }
        byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(responseCode, out.length);
        exchange.getResponseBody().write(out);
        exchange.close();
    }

    /**
     * 停止服务器
     */
    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        AbstractProtocol.invalidateAllCache();
    }

    private AuthenticationInformation approve() {
        ArmeriaProtocol protocol = new ArmeriaProtocol(props);
        return protocol.approve((Cookie[]) null, "AT-token-123", "WEB");
    }

    /**
     * 加密关闭：明文请求体 + x-sign 签名，响应解析成功
     */
    @Test
    void approveSuccessWithEncryptionDisabled() {
        props.setEnableEncryption(false);
        responseBody = "{\"code\":\"00000\",\"data\":\"{\\\"username\\\":\\\"admin\\\",\\\"userId\\\":\\\"1\\\"}\"}";

        AuthenticationInformation result = approve();

        assertEquals(Information.OK.getCode(), result.getInformation().getCode());
        assertEquals("admin", result.getReturnResult().getUsername());

        Map<String, String> headers = capturedHeaders.get();
        assertEquals("false", headers.get("x-oauth-encode"));
        assertNotNull(headers.get("x-oauth-sign"));
        assertNotNull(headers.get("x-oauth-serial"));

        String body = capturedBody.get();
        assertTrue(body.contains("x-oauth-token"), "明文请求体应包含 x-oauth-token");

        String dataJson = Json.fromJson(body, JsonObject.class).getObject("data", String.class);
        JsonObject jsonObject = Json.fromJson(dataJson, JsonObject.class);
        assertEquals(SignUtils.generateSignFromMap(jsonObject), headers.get("x-oauth-sign"),
                "x-oauth-sign 应等于请求参数的 MD5 签名");
    }

    /**
     * 加密开启：密文请求体 + x-sign 签名，响应按 x-oauth-response-serial 解密
     */
    @Test
    void approveSuccessWithEncryptionEnabled() {
        props.setEnableEncryption(true);
        props.setEncryption("aes");
        responseSerial = "armeria-serial-001";
        String userJson = "{\"username\":\"admin\",\"userId\":\"1\"}";
        String dataHex = Codec.build("aes", responseSerial).encodeHex(userJson);
        responseBody = "{\"code\":\"00000\",\"data\":\"" + dataHex + "\"}";

        AuthenticationInformation result = approve();

        assertEquals(Information.OK.getCode(), result.getInformation().getCode());
        assertEquals("admin", result.getReturnResult().getUsername());

        Map<String, String> headers = capturedHeaders.get();
        assertEquals("true", headers.get("x-oauth-encode"));
        assertNotNull(headers.get("x-oauth-sign"));

        String body = capturedBody.get();
        assertFalse(body.contains("x-oauth-token"), "加密请求体不应包含明文 token");

        String dataHexBody = Json.fromJson(body, JsonObject.class).getObject("data", String.class);
        String key1 = headers.get("x-oauth-uuid");
        String key = Codec.build("aes", key1).decodeHex(headers.get("x-oauth-serial"));
        String dataJson = Codec.build("aes", key).decodeHex(dataHexBody);
        JsonObject jsonObject = Json.fromJson(dataJson, JsonObject.class);
        assertEquals(SignUtils.generateSignFromMap(jsonObject), headers.get("x-oauth-sign"),
                "解密后参数计算出的签名应与请求头一致");
    }

    /**
     * 服务器返回 5xx 时降级为认证服务器不存在
     */
    @Test
    void approveServerErrorFallsBack() {
        responseCode = 500;
        responseBody = "{\"code\":\"50000\",\"msg\":\"internal error\"}";

        AuthenticationInformation result = approve();
        assertEquals(Information.AUTHENTICATION_SERVER_NO_EXIST.getCode(), result.getInformation().getCode());
    }

    /**
     * 服务端校验 x-sign：请求参数被篡改后复算签名与请求头不一致，服务端拒绝，客户端降级为认证失败
     */
    @Test
    void approveRejectedWhenSignMismatch() {
        props.setEnableEncryption(false);
        verifySignOnServer = true;
        responseCode = 200;
        responseBody = "{\"code\":\"A0401S0000\",\"msg\":\"签名校验失败\"}";

        AuthenticationInformation result = approve();
        assertEquals(Information.AUTHENTICATION_FAILURE.getCode(), result.getInformation().getCode());
        assertTrue(tamperRejected, "服务端应能识别被篡改的请求（x-sign 与参数不匹配）");
    }
}


