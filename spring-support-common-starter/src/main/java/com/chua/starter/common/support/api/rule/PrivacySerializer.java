package com.chua.starter.common.support.api.rule;

import com.chua.common.support.utils.PrivacyUtils;
import com.chua.starter.common.support.api.annotations.ApiFieldPrivacyEncrypt;
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
public class PrivacySerializer extends JsonSerializer<String> implements ContextualSerializer {

    /**
     * 脱敏类型
     */
    private PrivacyTypeEnum privacyTypeEnum;
    /**
     * 前几位不脱敏
     */
    private Integer prefixNoMaskLen;
    /**
     * 最后几位不脱敏
     */
    private Integer suffixNoMaskLen;
    /**
     * 用什么打�?
     */
    private String symbol;

    @Override
    public void serialize(final String origin, final JsonGenerator jsonGenerator,
                          final SerializerProvider serializerProvider) throws IOException {
        if (!Strings.isNullOrEmpty(origin) && null != privacyTypeEnum) {
            switch (privacyTypeEnum) {
                case CUSTOMER:
                    jsonGenerator.writeString(PrivacyUtils.desValue(origin, prefixNoMaskLen, suffixNoMaskLen, symbol));
                    break;
                case NAME:
                    jsonGenerator.writeString(PrivacyUtils.hideChineseName(origin));
                    break;
                case ID_CARD:
                    jsonGenerator.writeString(PrivacyUtils.hideCard(origin));
                    break;
                case PHONE:
                    jsonGenerator.writeString(PrivacyUtils.hidePhone(origin));
                    break;
                case EMAIL:
                    jsonGenerator.writeString(PrivacyUtils.hideEmail(origin));
                    break;
                case ADDRESS:
                    jsonGenerator.writeString(PrivacyUtils.hideAddress(origin, 8));
                    break;
                case BANK_CARD:
                    jsonGenerator.writeString(PrivacyUtils.hideBankCard(origin));
                    break;
                case PASSWORD:
                    jsonGenerator.writeString(PrivacyUtils.hidePassword(origin));
                    break;
                case CAR_NUMBER:
                    jsonGenerator.writeString(PrivacyUtils.hideCarNumber(origin));
                    break;
                case NONE:
                    jsonGenerator.writeString(origin);
                    break;
                default:
                    throw new IllegalArgumentException("unknown privacy type enum " + privacyTypeEnum);
            }
            return;
        }

        jsonGenerator.writeString(origin);
    }

    @Override
    public JsonSerializer<?> createContextual(final SerializerProvider serializerProvider,
                                              final BeanProperty beanProperty) throws JsonMappingException {
        if (beanProperty != null) {
            if (Objects.equals(beanProperty.getType().getRawClass(), String.class)) {
                ApiFieldPrivacyEncrypt apiFieldPrivacyEncrypt = beanProperty.getAnnotation(ApiFieldPrivacyEncrypt.class);
                if (apiFieldPrivacyEncrypt == null) {
                    apiFieldPrivacyEncrypt = beanProperty.getContextAnnotation(ApiFieldPrivacyEncrypt.class);
                }
                if (apiFieldPrivacyEncrypt != null) {
                    return new PrivacySerializer(apiFieldPrivacyEncrypt.type(), apiFieldPrivacyEncrypt.prefixNoMaskLen(),
                            apiFieldPrivacyEncrypt.suffixNoMaskLen(), apiFieldPrivacyEncrypt.symbol());
                }
            }
            return serializerProvider.findValueSerializer(beanProperty.getType(), beanProperty);
        }
        return serializerProvider.findNullValueSerializer(null);
    }
}


