package com.chua.starter.common.support.api.annotations;

import com.chua.starter.common.support.api.rule.ApiCryptoSerializer;
import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段加密注解
 * <p>
 * 用于对敏感字段进行加密处理，在序列化时自动加密输出�?
 * 支持多种加密算法，包括AES、SM2、SM4等�?
 * </p>
 *
 * <h3>使用场景�?/h3>
 * <ul>
 *   <li>用户密码等敏感信息加密存�?/li>
 *   <li>传输过程中的数据加密保护</li>
 *   <li>符合安全合规要求的数据加�?/li>
 *   <li>国密算法加密需�?/li>
 * </ul>
 *
 * <h3>使用示例�?/h3>
 * <pre>
 * public class UserVO {
 *     // 使用AES加密（默认）
 *     &#64;ApiFieldCrypto(key = "abcdefg123456789")
 *     private String password;
 *
 *     // 使用SM4国密加密
 *     &#64;ApiFieldCrypto(cryptoType = ApiCryptoType.SM4, key = "1234567890abcdef")
 *     private String mobile;
 *
 *     // 使用SM2非对称加�?
 *     &#64;ApiFieldCrypto(cryptoType = ApiCryptoType.SM2, key = "公钥")
 *     private String idCard;
 * }
 * </pre>
 *
 * <h3>加密算法说明�?/h3>
 * <ul>
 *   <li><b>AES</b>: 对称加密，密钥长�?6/24/32字节，速度�?/li>
 *   <li><b>SM4</b>: 国密对称加密，密钥长�?6字节，符合国家标�?/li>
 *   <li><b>SM2</b>: 国密非对称加密，需要公私钥对，安全性高</li>
 * </ul>
 *
 * @author CH
 * @since 2023-01-01
 * @version 1.0.0
 * @see ApiFieldCryptoKey
 * @see ApiCryptoSerializer
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = ApiCryptoSerializer.class)
public @interface ApiFieldCrypto {

    /**
     * 加密类型
     * <p>
     * 指定字段加密使用的算法类型�?
     * </p>
     *
     * @return 加密类型枚举值，默认AES
     */
    ApiCryptoType cryptoType() default ApiCryptoType.AES;

    /**
     * 加密密钥
     * <p>
     * 加密时使用的密钥，不能为空�?
     * 密钥长度需要符合对应加密算法的要求�?
     * </p>
     *
     * @return 加密密钥字符�?
     */
    String key();

    /**
     * 加密算法类型枚举
     */
    @Getter
    enum ApiCryptoType {
        /**
         * AES对称加密算法
         * <p>高级加密标准(Advanced Encryption Standard)，密钥长�?6/24/32字节</p>
         */
        AES,

        /**
         * SM2非对称加密算�?
         * <p>国家商用密码标准中的公钥密码算法</p>
         */
        SM2,

        /**
         * SM4对称加密算法
         * <p>国家商用密码标准中的分组密码算法，密钥长�?6字节</p>
         */
        SM4
    }
}

