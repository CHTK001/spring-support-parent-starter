package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.starter.common.support.algorithm.crypto.Codec;
import com.chua.starter.common.support.utils.SignUtils;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.core.RSocketServer;
import io.rsocket.transport.netty.server.CloseableChannel;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.rsocket.util.DefaultPayload;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RSocket 协议端到端测试
 * <p>通过本地 RSocketServer 模拟认证服务器，验证：元数据携带 x-oauth-sign 签名与
 * x-oauth-encode 开关、请求体经 createData 加密、服务端解密后校验 x-sign、
 * 篡改请求被拒绝、响应解密与异常降级。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class RSocketProtocolE2ETest {

    private CloseableChannel server;
    private AuthClientProperties props;

    private final AtomicReference<JsonObject> capturedMeta = new AtomicReference<>();
    private final AtomicReference<String> capturedBody = new AtomicReference<>();
    private volatile String responseBody = "";
    private volatile String responseSerial = null;
    private volatile boolean serverError = false;
    private volatile boolean verifySignOnServer = false;
    private volatile boolean tamperRejected = false;

    /**
     * 启动本地 RSocket 认证服务器
     */
    @BeforeEach
    void setUp() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        server = RSocketServer.create()
                .acceptor((setup, sendingSocket) -> Mono.just(new RSocket() {
                    @Override
                    public Mono<Payload> requestResponse(Payload payload) {
                        return Mono.fromCallable(() -> handle(payload));
                    }
                }))
                .bind(TcpServerTransport.create("127.0.0.1", port))
                .block();

        props = new AuthClientProperties();
        props.setProtocol("rsocket");
        props.setAddress("rsocket://127.0.0.1:" + port);
        props.setOauthUrl("/verify");
    }

    /**
     * 处理认证请求：解析元数据与请求体，按加密开关解密并校验 x-sign
     */
    private Payload handle(Payload payload) {
        String requestBody = payload.getDataUtf8();
        String metadataStr = payload.getMetadataUtf8();
        payload.release();

        if (serverError) {
            // 模拟认证服务器内部异常，RSocket 将以 ApplicationError 帧返回
            throw new IllegalStateException("模拟认证服务器异常");
        }

        JsonObject meta = Json.fromJson(metadataStr, JsonObject.class);
        capturedMeta.set(meta);
        capturedBody.set(requestBody);

        // 按 x-oauth-uuid + x-oauth-serial 还原会话密钥，再解密请求体
        String dataHex = Json.fromJson(requestBody, JsonObject.class).getObject("data", String.class);
        String bodyJson;
        if ("true".equals(meta.getObject("x-oauth-encode", String.class))) {
            String key1 = meta.getObject("x-oauth-uuid", String.class);
            String key = Codec.build("aes", key1).decodeHex(meta.getObject("x-oauth-serial", String.class));
            bodyJson = Codec.build("aes", key).decodeHex(dataHex);
        } else {
            // 未加密时请求体即明文，x-oauth-serial 也为明文密钥，无需解密
            bodyJson = dataHex;
        }
        JsonObject data = Json.fromJson(bodyJson, JsonObject.class);

        // 模拟服务端校验 x-sign：篡改请求参数后复算签名，若与元数据不一致则判定请求被篡改
        if (verifySignOnServer) {
            String token = String.valueOf(data.get("x-oauth-token"));
            data.put("x-oauth-token", token + "-tampered");
            String tamperedSign = SignUtils.generateSignFromMap(data);
            tamperRejected = !tamperedSign.equals(meta.getObject("x-oauth-sign", String.class));
        }

        byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
        String responseMetadata = responseSerial == null ? null
                : JsonObject.create().fluentPut("x-oauth-response-serial", responseSerial).toJSONString();
        return DefaultPayload.create(new String(out, StandardCharsets.UTF_8), responseMetadata);
    }

    /**
     * 停止服务器并清空缓存
     */
    @AfterEach
    void tearDown() {
        if (server != null) {
            server.dispose();
        }
        AbstractProtocol.invalidateAllCache();
    }

    private AuthenticationInformation approve() {
        RSocketProtocol protocol = new RSocketProtocol(props);
        return protocol.approve((Cookie[]) null, "AT-token-123", "WEB");
    }

    /**
     * 加密关闭：元数据携带 x-oauth-sign 且与请求参数签名一致，响应解析成功
     */
    @Test
    void approveSuccessWithEncryptionDisabled() {
        props.setEnableEncryption(false);
        responseBody = "{\"code\":\"00000\",\"data\":\"{\\\"username\\\":\\\"admin\\\",\\\"userId\\\":\\\"1\\\"}\"}";

        AuthenticationInformation result = approve();

        assertEquals(Information.OK.getCode(), result.getInformation().getCode());
        assertEquals("admin", result.getReturnResult().getUsername());
        assertEquals("1", result.getReturnResult().getUserId());

        JsonObject meta = capturedMeta.get();
        assertNotNull(meta);
        assertEquals("oauth", meta.getObject("route", String.class), "元数据应携带路由 oauth");
        assertEquals("false", meta.getObject("x-oauth-encode", String.class), "未开启加密时 x-oauth-encode 应为 false");
        assertNotNull(meta.getObject("x-oauth-sign", String.class), "元数据应携带 x-oauth-sign");
        assertNotNull(meta.getObject("x-oauth-uuid", String.class), "元数据应携带 x-oauth-uuid");
        assertNotNull(meta.getObject("x-oauth-serial", String.class), "元数据应携带 x-oauth-serial");

        String body = capturedBody.get();
        assertTrue(body.contains("x-oauth-token"), "明文请求体应包含 x-oauth-token");

        // 明文请求体直接解析，校验 x-sign 与参数一致
        String dataJson = Json.fromJson(body, JsonObject.class).getObject("data", String.class);
        JsonObject jsonObject = Json.fromJson(dataJson, JsonObject.class);
        assertEquals(SignUtils.generateSignFromMap(jsonObject), meta.getObject("x-oauth-sign", String.class),
                "x-oauth-sign 应等于请求参数的 MD5 签名");
    }

    /**
     * 加密开启：请求体为密文，元数据携带 x-oauth-encode=true，响应按 x-oauth-response-serial 解密
     */
    @Test
    void approveSuccessWithEncryptionEnabled() {
        props.setEnableEncryption(true);
        props.setEncryption("aes");
        responseSerial = "srv-rs-serial-001";
        String userJson = "{\"username\":\"admin\",\"userId\":\"1\"}";
        String dataHex = Codec.build("aes", responseSerial).encodeHex(userJson);
        responseBody = "{\"code\":\"00000\",\"data\":\"" + dataHex + "\"}";

        AuthenticationInformation result = approve();

        assertEquals(Information.OK.getCode(), result.getInformation().getCode());
        assertEquals("admin", result.getReturnResult().getUsername());

        JsonObject meta = capturedMeta.get();
        assertEquals("true", meta.getObject("x-oauth-encode", String.class), "开启加密时 x-oauth-encode 应为 true");
        assertNotNull(meta.getObject("x-oauth-sign", String.class), "加密请求仍应携带 x-oauth-sign");

        String body = capturedBody.get();
        assertFalse(body.contains("x-oauth-token"), "加密请求体不应包含明文 token");

        // 解密请求体并校验 x-sign
        String dataHexBody = Json.fromJson(body, JsonObject.class).getObject("data", String.class);
        String key1 = meta.getObject("x-oauth-uuid", String.class);
        String key = Codec.build("aes", key1).decodeHex(meta.getObject("x-oauth-serial", String.class));
        String dataJson = Codec.build("aes", key).decodeHex(dataHexBody);
        JsonObject jsonObject = Json.fromJson(dataJson, JsonObject.class);
        assertEquals(SignUtils.generateSignFromMap(jsonObject), meta.getObject("x-oauth-sign", String.class),
                "解密后参数计算出的签名应与元数据一致");
    }

    /**
     * 服务端校验 x-sign：请求参数被篡改后复算签名与元数据不一致，服务端拒绝，客户端降级为认证失败
     */
    @Test
    void approveRejectedWhenSignMismatch() {
        props.setEnableEncryption(false);
        verifySignOnServer = true;
        responseBody = "{\"code\":\"A0401S0000\",\"msg\":\"签名校验失败\"}";

        AuthenticationInformation result = approve();
        assertEquals(Information.AUTHENTICATION_FAILURE.getCode(), result.getInformation().getCode());
        assertTrue(tamperRejected, "服务端应能识别被篡改的请求（x-sign 与参数不匹配）");
    }

    /**
     * 服务器内部异常（ApplicationError）时降级为鉴权服务器异常
     */
    @Test
    void approveServerErrorFallsBack() {
        serverError = true;

        AuthenticationInformation result = approve();
        assertEquals(Information.AUTHENTICATION_SERVER_EXCEPTION.getCode(), result.getInformation().getCode());
    }

    /**
     * 服务器返回未授权 code 时降级为认证失败
     */
    @Test
    void approveUnauthorizedCodeFallsBack() {
        responseBody = "{\"code\":\"A0401S0000\",\"msg\":\"未登录或登录已过期\"}";

        AuthenticationInformation result = approve();
        assertEquals(Information.AUTHENTICATION_FAILURE.getCode(), result.getInformation().getCode());
    }
}
