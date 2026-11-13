// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.common.support.api.rule;

import com.chua.common.support.lang.algorithm.cipher.Sm2Cipher;
import com.chua.common.support.lang.algorithm.crypto.Hex;
import com.chua.common.support.reflection.ReflectUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.algorithm.crypto.Codec;
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

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * 字段级加密序列化器。
 *
 * <p>按 {@link ApiFieldCrypto#cryptoType()} 选择真实算法：AES / SM4 走
 * {@link Codec}（SPI 解析实现），SM2 用注解里的公钥（X.509 DER 十六进制）加密。
 * 不认识的类型或缺失密钥直接抛异常，<b>不会再退回明文或十六进制</b>。</p>
 *
 * <p>旧实现有两个问题是本次修掉的：SM2 分支现场生成一对临时密钥、用临时公钥加密
 * （私钥随即丢弃，数据不可恢复）；非 SM2 分支固定按 {@code sm4} 处理，
 * 而当时的 {@code sm4} 只做 hex 编码，等于没有加密。</p>
 *
 * @author CH
 * @since 4.0.0
 */
public class ApiCryptoSerializer extends JsonSerializer<String> implements ContextualSerializer {

    /**
     * 实体类到密钥字段的缓存；类上没有 {@link ApiFieldCryptoKey} 字段时存哨兵
     */
    private static final Map<Class<?>, Object> KEY_FIELD_MAP = new ConcurrentReferenceHashMap<>(512);

    /**
     * 无密钥字段的哨兵值
     */
    private static final Object NO_KEY_FIELD = new Object();

    /**
     * 加密类型
     */
    private final ApiFieldCrypto.ApiCryptoType cryptoType;

    /**
     * 密钥（对称算法）或公钥十六进制（SM2）
     */
    private final String key;

    /**
     * 无参构造器（Jackson 需要）
     */
    public ApiCryptoSerializer() {
        this(null, null);
    }

    /**
     * 构造器
     *
     * @param cryptoType 加密类型
     * @param key        密钥或公钥
     */
    public ApiCryptoSerializer(ApiFieldCrypto.ApiCryptoType cryptoType, String key) {
        this.cryptoType = cryptoType;
        this.key = key;
    }

    @Override
    public void serialize(final String origin, final JsonGenerator jsonGenerator,
                          final SerializerProvider serializerProvider) throws IOException {
        if (origin == null) {
            jsonGenerator.writeNull();
            return;
        }
        ApiFieldCrypto.ApiCryptoType type = cryptoType == null
                ? ApiFieldCrypto.ApiCryptoType.AES
                : cryptoType;
        String effectiveKey = resolveKey(jsonGenerator);
        if (StringUtils.isEmpty(effectiveKey)) {
            throw new IllegalStateException("@ApiFieldCrypto 未配置密钥");
        }
        if (type == ApiFieldCrypto.ApiCryptoType.SM2) {
            jsonGenerator.writeString(sm2Encrypt(origin, effectiveKey));
            String currentName = jsonGenerator.getOutputContext().getCurrentName();
            jsonGenerator.writeFieldName(currentName + "KeyId");
            jsonGenerator.writeString(effectiveKey);
            return;
        }
        String codecType = type == ApiFieldCrypto.ApiCryptoType.SM4 ? "sm4" : "aes";
        jsonGenerator.writeString(Codec.build(codecType, effectiveKey).encodeHex(origin));
    }

    /**
     * SM2 加密。
     *
     * @param data         明文
     * @param publicKeyHex X.509 DER 公钥十六进制
     * @return 密文十六进制
     */
    private String sm2Encrypt(String data, String publicKeyHex) {
        try {
            byte[] encrypted = Sm2Cipher.create("bc")
                    .encrypt(Hex.decodeHex(publicKeyHex), data.getBytes(StandardCharsets.UTF_8));
            return Hex.encodeHexString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("SM2 加密失败（公钥需为 X.509 DER 十六进制）: "
                    + e.getMessage(), e);
        }
    }

    /**
     * 解析当前实体上 {@link ApiFieldCryptoKey} 标记的密钥，没有则用注解配置的密钥。
     *
     * @param jsonGenerator json 生成器
     * @return 密钥
     */
    private String resolveKey(JsonGenerator jsonGenerator) {
        Object currentValue = jsonGenerator.getCurrentValue();
        if (currentValue == null) {
            return key;
        }
        Class<?> type = currentValue.getClass();
        Object cached = KEY_FIELD_MAP.get(type);
        if (cached == null) {
            Field[] fields = FieldUtils.getFieldsWithAnnotation(type, ApiFieldCryptoKey.class);
            cached = fields.length == 0 ? NO_KEY_FIELD : fields[0];
            KEY_FIELD_MAP.put(type, cached);
        }
        if (cached == NO_KEY_FIELD) {
            return key;
        }
        try {
            Object value = ReflectUtils.getField(currentValue, ((Field) cached).getName());
            return value == null ? key : String.valueOf(value);
        } catch (RuntimeException e) {
            return key;
        }
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
                    String cryptoKey = SpringBeanUtils.getEnvironment().resolvePlaceholders(crypto.key());
                    return new ApiCryptoSerializer(crypto.cryptoType(), cryptoKey);
                }
            }
            return serializerProvider.findValueSerializer(beanProperty.getType(), beanProperty);
        }
        return serializerProvider.findNullValueSerializer(null);
    }
}
