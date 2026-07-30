package com.chua.starter.common.support.annotations;

import com.chua.starter.common.support.api.rule.CryptoSerializer;
import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 加密
 *
 * @author CH
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = CryptoSerializer.class)
public @interface Crypto {

    /**
     * 密钥
     */
    String key();

    /**
     * 密钥类型
     */
    KeyType keyType() default KeyType.NONE;

    public static enum KeyType {
        /**
         * 不使用
         */
        NONE,

        /**
         * spel
         */
        SPEL
    }
}
