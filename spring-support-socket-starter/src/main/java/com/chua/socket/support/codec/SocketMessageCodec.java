package com.chua.socket.support.codec;

import com.chua.common.support.crypto.Codec;
import com.chua.common.support.crypto.CodecKeyPair;
import com.chua.socket.support.model.SocketMessageEnvelope;
import com.chua.socket.support.properties.SocketProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Socket 消息编解码包装
 *
 * @author CH
 * @since 2026-04-08
 */
@Slf4j
public class SocketMessageCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String ENCRYPTED_PREFIX = "02";
    private static final String STATUS_SEGMENT = "200";
    private static final String SUFFIX = "ffff";

    private final SocketEncryptMode encryptMode;
    private final CodecKeyPair codecKeyPair;
    private final String publicKeyHex;

    public SocketMessageCodec(SocketProperties properties) {
        String codecType = properties == null || properties.getCodecType() == null
                ? "json"
                : properties.getCodecType();
        this.encryptMode = properties == null
                ? SocketEncryptMode.AUTO
                : properties.resolveEncryptMode();

        CodecKeyPair pair = null;
        String publicKey = null;
        if (SocketEncryptMode.PLAIN != this.encryptMode) {
            try {
                Codec codec = Codec.build(codecType);
                if (codec instanceof CodecKeyPair candidate) {
                    pair = candidate;
                    publicKey = candidate.getPublicKeyHex();
                } else {
                    log.warn("[Socket] 当前 codecType={} 不支持 Socket 非对称加密，已回退为明文发送", codecType);
                }
            } catch (Exception e) {
                log.warn("[Socket] 初始化消息加密器失败，已回退为明文发送", e);
            }
        }
        this.codecKeyPair = pair;
        this.publicKeyHex = publicKey;
    }

    public Object encode(String payload) {
        return encode(payload, null);
    }

    public Object encode(String payload, Boolean encryptRequested) {
        if (!shouldEncrypt(payload, encryptRequested)) {
            return payload;
        }

        try {
            String encryptedData = codecKeyPair.encode(payload, publicKeyHex);
            String transportKey = codecKeyPair.getPrivateKeyHex();
            String keyLength = String.valueOf(transportKey.length());
            return SocketMessageEnvelope.builder()
                    .encrypted(true)
                    .data(ENCRYPTED_PREFIX + transportKey + STATUS_SEGMENT + encryptedData + SUFFIX)
                    .timestamp(keyLength)
                    .uuid(transportKey)
                    .dataId(resolveDataId(payload))
                    .build();
        } catch (Exception e) {
            log.warn("[Socket] 消息加密失败，已回退为明文发送", e);
            return payload;
        }
    }

    private boolean shouldEncrypt(String payload, Boolean encryptRequested) {
        return shouldEncryptForCurrentSession(encryptRequested)
                && payload != null
                && codecKeyPair != null
                && publicKeyHex != null
                && !publicKeyHex.isBlank();
    }

    private boolean shouldEncryptForCurrentSession(Boolean encryptRequested) {
        return switch (encryptMode) {
            case ENCRYPTED -> true;
            case PLAIN -> false;
            case AUTO -> !Boolean.FALSE.equals(encryptRequested);
        };
    }

    private String resolveDataId(String payload) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(payload);
            JsonNode dataId = root.get("dataId");
            if (dataId == null || dataId.isNull()) {
                return null;
            }
            return dataId.isTextual() ? dataId.asText() : dataId.toString();
        } catch (Exception ignored) {
            return null;
        }
    }
}
