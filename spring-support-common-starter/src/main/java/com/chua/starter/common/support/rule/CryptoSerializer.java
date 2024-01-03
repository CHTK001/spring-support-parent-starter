package com.chua.starter.common.support.rule;

import com.chua.common.support.crypto.Codec;
import com.chua.common.support.crypto.CryptoModule;
import com.chua.common.support.crypto.CryptoType;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.annotations.Crypto;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.google.common.base.Strings;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.util.Objects;

/**
 */
@NoArgsConstructor
@AllArgsConstructor
public class CryptoSerializer extends JsonSerializer<String> implements ContextualSerializer {

    /**
     * 加密类型
     */
    private CryptoType cryptoType;

    /**
     * 加解密模式
     */
    private CryptoModule cryptoModule;
    /**
     * 密钥
     */
    private String key;

    @Override
    public void serialize(final String origin, final JsonGenerator jsonGenerator,
                          final SerializerProvider serializerProvider) throws IOException {

        if (!Strings.isNullOrEmpty(origin) && null != cryptoType && StringUtils.isNotEmpty(key) && null != cryptoModule) {
            Codec codec = ServiceProvider.of(Codec.class).getExtension(cryptoModule);
            if(cryptoModule == CryptoModule.DECODE) {
                jsonGenerator.writeString(codec.encodeHex(origin, key));
                return;
            }
            jsonGenerator.writeString(codec.decodeHex(origin, key));
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
                    return new CryptoSerializer(crypto.cryptoType(), crypto.cryptoModule(), SpringBeanUtils.getEnvironment().resolvePlaceholders(crypto.key()));
                }
            }
            return serializerProvider.findValueSerializer(beanProperty.getType(), beanProperty);
        }
        return serializerProvider.findNullValueSerializer(null);
    }
}

