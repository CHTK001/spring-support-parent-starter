package com.chua.socketio.support.session;

import com.chua.common.support.crypto.Codec;
import com.chua.common.support.crypto.CodecKeyPair;
import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.ClassUtils;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.RandomUtils;
import com.chua.socketio.support.auth.SocketAuthFactory;
import com.chua.socketio.support.properties.SocketIoProperties;
import com.corundumstudio.socketio.SocketIOClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 会话
 *
 * @author CH
 */
@Slf4j
public class SocketSession {

    private final SocketIOClient client;
    @Getter
    private final String sessionId;
    private final SocketIoProperties socketIoProperties;
    private final SocketAuthFactory socketAuthFactory;

    public SocketSession(SocketIOClient client, SocketIoProperties socketIoProperties) {
        this.client = client;
        this.sessionId = client.getSessionId().toString();
        this.socketIoProperties = socketIoProperties;
        socketAuthFactory = ClassUtils.forObject(socketIoProperties.getAuthFactory(), SocketAuthFactory.class);

    }

    /**
     * 是否匹配
     *
     * @param client 客戶端
     * @return 結果
     */
    public boolean isMatch(SocketIOClient client) {
        return sessionId.equals(client.getSessionId().toString());
    }

    /**
     * 是否匹配
     *
     * @param sessionId sessionId
     * @return 結果
     */
    public boolean isMatch(String sessionId) {
        return this.sessionId.equals(sessionId);
    }

    /**
     * 關閉
     */
    public void close() {
        try {
            client.disconnect();
        } catch (Exception e) {
            log.error("", e);
        }
    }

    /**
     * 下发命令
     *
     * @param event 时间
     * @param msg   消息
     */
    public void send(String event, String msg) {
        send(event, ReturnResult.success(msg));
    }
    /**
     * 下发命令
     *
     * @param event 时间
     * @param returnResult   消息
     */
    public void send(String event, ReturnResult returnResult) {
        Codec codec = Codec.build(socketIoProperties.getCodecType());
        CodecKeyPair codecKeyPair = (CodecKeyPair) codec;
        String publicKeyHex = codecKeyPair.getPublicKeyHex();
        String encode = ((CodecKeyPair) codec).encode(Json.toJSONString(returnResult), publicKeyHex);
        // 使用明文密钥（解密后的密钥）直接拼接到 02 和 200 之间，不再进行 AES 加密
        String key = codecKeyPair.getPrivateKeyHex();
        String keyLength = String.valueOf(key.length());
        client.sendEvent(event,
                new JsonObject()
                        .fluent("data", "02" + key + "200" + encode + "ffff")
                        .fluent("uuid", key)
                        .fluent("timestamp", keyLength).toJSONString()
        );
    }

    /**
     * 獲取用戶信息
     *
     * @return 用戶信息
     */
    public SocketUser getUser() {
        return socketAuthFactory.getUser(client.getHandshakeData());
    }

    public boolean isValid(String sessionId) {
        return sessionId.equals(this.sessionId);
    }
}
