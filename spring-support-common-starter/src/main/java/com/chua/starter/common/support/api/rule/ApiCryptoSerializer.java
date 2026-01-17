package com.chua.starter.common.support.api.rule;

import com.chua.common.support.crypto.Codec;
import com.chua.common.support.crypto.CodecKeyPair;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.starter.common.support.api.annotations.ApiFieldCrypto;
import com.chua.starter.common.support.api.annotations.ApiFieldCryptoKey;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;

/**
 */
public class ApiCryptoSerializer extends JsonSerializer<String> implements ContextualSerializer {

    /**
     * 加密类型
     */
    /**
     * 构造函数
     *
     * @param codec Codec
     * @param name String
     * @param key String
     */
    public ApiCryptoSerializer(Codec codec, String name, String key) {
        this.codec = codec;
        this.name = name;
        this.key = key;
    }

    private final Codec codec;
    private final String name;
    private final String key;

    private ApiCryptoSerializer() {
        this.codec = null;
        this.name = null;
        this.key = null;
    }

    private static final Map<Class<?>, String> KEY_MAP = new ConcurrentReferenceHashMap<>(512);

    @Override
    public void serialize(final String origin, final JsonGenerator jsonGenerator,
                          final SerializerProvider serializerProvider) throws IOException {

        if(codec instanceof CodecKeyPair codecKeyPair) {
            jsonGenerator.writeString(codec.encodeHex(origin));
            String currentName = jsonGenerator.getOutputContext().getCurrentName();
            jsonGenerator.writeFieldName(currentName + "KeyId");
            jsonGenerator.writeString(codecKeyPair.getPrivateKeyHex());
            return;
        }

        String key = getDynamicKey(jsonGenerator);
        boolean isOldKey = isEquals(key, this.key);
        Codec codec = create(key, isOldKey);
        if (null != codec) {
            jsonGenerator.writeString(codec.encodeHex(origin));
            return;
        }
        jsonGenerator.writeString(origin);
    }

    private Codec create(String key, boolean isOldKey) {
        if(isOldKey) {
            return this.codec;
        }

        return Codec.build(name, key);
    }

    /**
     * 判断是否相同
     */
    private boolean isEquals(String key, String key1) {
        return StringUtils.equals(key, key1);
    }

    private String getDynamicKey(JsonGenerator jsonGenerator) {
        Object currentValue = jsonGenerator.getCurrentValue();
        if(null == currentValue) {
            return key;
        }

        Class<?> aClass = currentValue.getClass();
        return KEY_MAP.computeIfAbsent(aClass, it -> {
            Field[] fields = FieldUtils.getFieldsWithAnnotation(aClass, ApiFieldCryptoKey.class);
            if(fields.length == 0) {
                return key;
            }
            Field fields1 = fields[0];
            ReflectionUtils.makeAccessible(fields1);

            try {
                return String.valueOf(fields1.get(currentValue));
            } catch (IllegalAccessException e) {
                return key;
            }
        });
    }

    @Override
    public JsonSerializer<?> createContextual(final SerializerProvider serializerProvider,
                                              final BeanProperty beanProperty) throws JsonMappingException {
        if (beanProperty != null) {
            if (Objects.equals(beanProperty.getType().getRawClass(), String.class)) {
                ApiFieldCrypto crypto = beanProperty.getAnnotation(ApiFieldCrypto.class);
                if (crypto == null) {
                    crypto = beanProperty.getContextAnnotation(ApiFieldCrypto.class);
                }
                if (crypto != null) {
                    String key = SpringBeanUtils.getEnvironment().resolvePlaceholders(crypto.key());
                    Codec codec = Codec.build(crypto.cryptoType().name(), key);
                    return new ApiCryptoSerializer(codec, crypto.cryptoType().name(), key);
                }
            }
            return serializerProvider.findValueSerializer(beanProperty.getType(), beanProperty);
        }
        return serializerProvider.findNullValueSerializer(null);
    }
}


