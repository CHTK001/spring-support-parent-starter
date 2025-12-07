package com.chua.starter.common.support.api.annotations;

import com.chua.starter.common.support.api.rule.ApiCryptoSerializer;
import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段加密密钥标记注解
 * <p>
 * 用于标记实体中存储加密密钥的字段，与 @ApiFieldCrypto 配合使用�?
 * 序列化时该字段会被特殊处理�?
 * </p>
 *
 * <h3>使用场景�?/h3>
 * <ul>
 *   <li>动态密钥加密：不同记录使用不同的加密密�?/li>
 *   <li>密钥字段标识：标记实体中哪个字段存储密钥</li>
 * </ul>
 *
 * <h3>使用示例�?/h3>
 * <pre>
 * public class SecureData {
 *     // 标记此字段为加密密钥
 *     &#64;ApiFieldCryptoKey
 *     private String encryptKey;
 *
 *     // 使用动态密钥加�?
 *     &#64;ApiFieldCrypto(key = "")  // key为空时使�?@ApiFieldCryptoKey 标记的字�?
 *     private String sensitiveData;
 * }
 * </pre>
 *
 * @author CH
 * @since 2023-01-01
 * @version 1.0.0
 * @see ApiFieldCrypto
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = ApiCryptoSerializer.class)
public @interface ApiFieldCryptoKey {
}

