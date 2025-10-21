package com.chua.starter.common.support.annotations;

import com.chua.starter.common.support.rule.ApiCryptoSerializer;
import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API加密密钥注解
 * <p>用于标记需要进行加密处理的字段</p>
 * @see ApiFieldCrypto
 * @author CH
 * @since 2023-01-01
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = ApiCryptoSerializer.class)
public @interface ApiFieldCryptoKey {

}
