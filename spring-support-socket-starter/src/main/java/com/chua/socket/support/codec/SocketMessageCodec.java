package com.chua.socket.support.codec;

import com.chua.socket.support.model.SocketMessageEnvelope;
import com.chua.socket.support.properties.SocketProperties;
import com.chua.starter.common.support.algorithm.crypto.Codec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Socket 消息编解码包装
 *
 * @since 2026-04-08
 * @author CH
 */
@Slf4j
public class SocketMessageCodec {

    /** Object_mapper */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    /** Encrypted_prefix */
    private static final String ENCRYPTED_PREFIX = "02";
    /** Status_segment */
    private static final String STATUS_SEGMENT = "200";
    /** Suffix */
    private static final String SUFFIX = "ffff";

    /** Encrypt模式 */
    private final SocketEncryptMode encryptMode;
    /** Codec密钥pair */
    private final CodecKeyPair codecKeyPair;
    /** 公开密钥HEX */
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
                Codec codec = Codec.build(codecType, null);
                if (codec instanceof CodecKeyPair) {
                    pair = (CodecKeyPair) codec;
                    publicKey = pair.getPublicKeyHex();
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