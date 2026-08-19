package com.chua.starter.common.support.api.rule;

import com.chua.starter.common.support.algorithm.crypto.Codec;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.annotations.Crypto;
import com.chua.spring.support.configuration.SpringBeanUtils;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.google.common.base.Strings;
import java.io.IOException;
import java.util.Objects;

/**
 * @author CH
 */
public class CryptoSerializer extends JsonSerializer<String> implements ContextualSerializer {

    /**
     * 编解码器实例
     */
    private final Codec codec;

    /**
     * 密钥
     */
    private final String key;

    /** 密钥类型 */
    private Crypto.KeyType keyType;

    /**
     * 无参构造函数
     */
    public CryptoSerializer() {
        this.codec = null;
        this.key = null;
        this.keyType = null;
    }

    /**
     * 构造函数
     *
     * @param codec    编解码器实例
     * @param key      密钥
     * @param keyType 密钥类型
     */
    public CryptoSerializer(Codec codec, String key, Crypto.KeyType keyType) {
        this.codec = codec;
        this.key = key;
        this.keyType = keyType;
    }


    @Override
    /**
     * 序列化
     * @param origin origin
     * @param jsonGenerator jsonGenerator
     * @param serializerProvider serializerProvider
     */
    public void serialize(final String origin, final JsonGenerator jsonGenerator,
                          final SerializerProvider serializerProvider) throws IOException {

        if (!Strings.isNullOrEmpty(origin) && null != codec && StringUtils.isNotEmpty(key)) {
            jsonGenerator.writeString(codec.encodeHex(origin));
            return;
        }
        jsonGenerator.writeString(origin);
    }

    @Override
    public JsonSerializer<?> createContextual(final SerializerProvider serializerProvider,
                                              final BeanProperty beanProperty) throws JsonMappingException {
        if (beanProperty != null) {
            if (Objects.equals(beanProperty.getType().getRawClass(), String.class)) {
                Crypto crypto = beanProperty.getAnnotation(Crypto.class);
                if (crypto == null) {
                    crypto = beanProperty.getContextAnnotation(Crypto.class);
                }
                if (crypto != null) {
                    String key = SpringBeanUtils.getEnvironment().resolvePlaceholders(crypto.key());
                    return new CryptoSerializer(Codec.build("sm4", key), key, crypto.keyType());
                }
            }
            return serializerProvider.findValueSerializer(beanProperty.getType(), beanProperty);
        }
        return serializerProvider.findNullValueSerializer(null);
    }
}

