package com.chua.starter.common.support.api.rule;

import com.chua.common.support.lang.algorithm.cipher.Sm2Cipher;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.api.annotations.ApiFieldCrypto;
import com.chua.starter.common.support.api.annotations.ApiFieldCryptoKey;
import com.chua.spring.support.configuration.SpringBeanUtils;
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
import java.security.KeyPair;
import java.util.Map;
import java.util.Objects;

/**
 * @author CH
 */
public class ApiCryptoSerializer extends JsonSerializer<String> implements ContextualSerializer {

    /**
     * 公钥
     */
    private final String publicKeyHex;

    /**
     * 加密类型
     */
    private final ApiFieldCrypto.ApiCryptoType cryptoType;

    /**
     * 密钥
     */
    private final String key;

    /**
     * 构造函数
     *
     * @param publicKeyHex 公钥HEX
     * @param cryptoType  加密类型
     * @param key         密钥
     */
    public ApiCryptoSerializer(String publicKeyHex, ApiFieldCrypto.ApiCryptoType cryptoType, String key) {
        this.publicKeyHex = publicKeyHex;
        this.cryptoType = cryptoType;
        this.key = key;
    }

    private ApiCryptoSerializer() {
        this.publicKeyHex = null;
        this.cryptoType = null;
        this.key = null;
    }

    private static final Map<Class<?>, String> KEY_MAP = new ConcurrentReferenceHashMap<>(512);

    @Override
    public void serialize(final String origin, final JsonGenerator jsonGenerator,
                          final SerializerProvider serializerProvider) throws IOException {

        if (null != publicKeyHex && cryptoType == ApiFieldCrypto.ApiCryptoType.SM2) {
            String encrypted = sm2Encrypt(origin, publicKeyHex);
            jsonGenerator.writeString(encrypted);
            String currentName = jsonGenerator.getOutputContext().getCurrentName();
            jsonGenerator.writeFieldName(currentName + "KeyId");
            jsonGenerator.writeString(publicKeyHex);
            return;
        }

        String codeKey = getDynamicKey(jsonGenerator);
        boolean isOldKey = isEquals(codeKey, this.key);
        if (isOldKey && null != this.key) {
            jsonGenerator.writeString(com.chua.starter.common.support.algorithm.crypto.Codec.build("sm4", this.key).encodeHex(origin));
            return;
        }
        if (StringUtils.isNotEmpty(codeKey)) {
            jsonGenerator.writeString(com.chua.starter.common.support.algorithm.crypto.Codec.build("sm4", codeKey).encodeHex(origin));
            return;
        }
        jsonGenerator.writeString(origin);
    }

    private String sm2Encrypt(String data, String publicKeyHex) {
        try {
            Sm2Cipher sm2Cipher = Sm2Cipher.create("bc");
            byte[] publicKeyBytes = hexToBytes(publicKeyHex);
            KeyPair keyPair = sm2Cipher.generateKeyPair();
            byte[] encrypted = sm2Cipher.encrypt(keyPair.getPublic().getEncoded(), data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return bytesToHex(encrypted);
        } catch (Exception e) {
            return data;
        }
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 判断是否相同
     */
    private boolean isEquals(String key, String key1) {
        return StringUtils.equals(key, key1);
    }

    private String getDynamicKey(JsonGenerator jsonGenerator) {
        Object currentValue = jsonGenerator.getCurrentValue();
        if (null == currentValue) {
            return key;
        }

        Class<?> aClass = currentValue.getClass();
        return KEY_MAP.computeIfAbsent(aClass, it -> {
            Field[] fields = FieldUtils.getFieldsWithAnnotation(aClass, ApiFieldCryptoKey.class);
            if (fields.length == 0) {
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
                    return new ApiCryptoSerializer(null, crypto.cryptoType(), key);
                }
            }
            return serializerProvider.findValueSerializer(beanProperty.getType(), beanProperty);
        }
        return serializerProvider.findNullValueSerializer(null);
    }
}

