package com.chua.starter.common.support.annotations;

import com.chua.starter.common.support.rule.ApiCryptoSerializer;
import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API加密注解
 * <p>用于标记需要进行加密处理的字段</p>
 *
 * @author CH
 * @since 2023-01-01
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = ApiCryptoSerializer.class)
public @interface ApiFieldCrypto {

    /**
     * 加密类型
     * <p>指定字段加密使用的算法类型</p>
     * <p>默认使用AES加密算法</p>
     * 
     * @return 加密类型枚举值
     * 
     * @example 使用示例:
     * <pre>
     * // 使用默认AES加密
     * &#064;ApiCrypto(key = "mySecretKey")
     * private String password;
     * 
     * // 使用SM4加密
     * &#064;ApiCrypto(cryptoType = ApiCryptoType.SM4, key = "mySecretKey")
     * private String mobile;
     * </pre>
     */
    ApiCryptoType cryptoType() default ApiCryptoType.AES;

    /**
     * 密钥
     * <p>加密时使用的密钥，不能为空</p>
     * 
     * @return 加密密钥字符串
     * 
     * @see ApiFieldCryptoKey > key 获取密钥的方式
     * 
     * @example 使用示例:
     * <pre>
     * // 直接指定密钥
     * &#064;ApiCrypto(key = "abcdefg123456789")
     * private String sensitiveData;
     * </pre>
     */
    String key();


    /**
     * API加密类型枚举
     * <p>定义支持的加密算法类型</p>
     * 
     * @author CH
     * @since 2023-01-01
     */
    @Getter
    @Setter
    public static enum ApiCryptoType {
        /**
         * AES对称加密算法
         * <p>高级加密标准(Advanced Encryption Standard)</p>
         */
        AES,

        /**
         * SM2非对称加密算法
         * <p>国家商用密码标准中的公钥密码算法</p>
         */
        SM2,

        /**
         * SM4对称加密算法
         * <p>国家商用密码标准中的分组密码算法</p>
         */
        SM4
    }

}
