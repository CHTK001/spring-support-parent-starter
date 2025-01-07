package com.chua.starter.common.support.annotations;

import com.chua.common.support.crypto.CryptoType;
import com.chua.starter.common.support.rule.ApiCryptoSerializer;
import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * api加密的key
 *
 * @author CH
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = ApiCryptoSerializer.class)
public @interface ApiCryptoKey {

}
