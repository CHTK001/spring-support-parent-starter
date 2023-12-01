package com.chua.starter.common.support.annotations;

import com.chua.common.support.crypto.CryptoModule;
import com.chua.common.support.crypto.CryptoType;
import com.chua.starter.common.support.rule.CryptoSerializer;
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
     * 加密类型
     */
    CryptoType cryptoType();

    /**
     * 加解密模式
     */
    CryptoModule cryptoModule();
    /**
     * 密钥
     */
    String key();
}
