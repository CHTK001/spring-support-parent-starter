package com.chua.starter.common.support.api.annotations;

import com.chua.starter.common.support.api.rule.PrivacySerializer;
import com.chua.starter.common.support.api.rule.PrivacyTypeEnum;
import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段数据脱敏注解
 * <p>
 * 用于对敏感字段进行脱敏处理，在序列化时自动将敏感信息替换为掩码。
 * </p>
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>用户手机号脱敏：138****8888</li>
 *   <li>身份证号脱敏：110***********1234</li>
 *   <li>银行卡号脱敏：脱敏：6222***********1234</li>
 *   <li>邮箱地址脱敏：ab***@example.com</li>
 *   <li>姓名脱敏：张**</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 * public class UserVO {
 *     // 手机号脱敏
 *     &#64;ApiFieldPrivacyEncrypt(type = PrivacyTypeEnum.MOBILE)
 *     private String mobile;  // 输出：138****8888
 *
 *     // 身份证脱敏
 *     &#64;ApiFieldPrivacyEncrypt(type = PrivacyTypeEnum.ID_CARD)
 *     private String idCard;  // 输出：110***********1234
 *
 *     // 自定义脱敏规则
 *     &#64;ApiFieldPrivacyEncrypt(
 *         type = PrivacyTypeEnum.CUSTOMIZE,
 *         prefixNoMaskLen = 3,  // 前3位不脱敏
 *         suffixNoMaskLen = 4,  // 后4位不脱敏
 *         symbol = "#"          // 使用#号掩码
 *     )
 *     private String customField;  // 输出：abc####1234
 * }
 * </pre>
 *
 * @author CH
 * @since 2023-01-01
 * @version 1.0.0
 * @see PrivacyTypeEnum
 * @see PrivacySerializer
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = PrivacySerializer.class)
public @interface ApiFieldPrivacyEncrypt {

    /**
     * 脱敏数据类型
     * <p>
     * 预定义的脱敏规则，支持手机号、身份证、银行卡、邮箱等常见类型。
     * </p>
     *
     * @return 脱敏类型枚举
     */
    PrivacyTypeEnum type() default PrivacyTypeEnum.NONE;

    /**
     * 前置不需要打码的长度
     * <p>
     * 字符串开头保留的明文字符数量。
     * </p>
     *
     * @return 前置保留长度，默认0
     */
    int prefixNoMaskLen() default 0;

    /**
     * 后置不需要打码的长度
     * <p>
     * 字符串结尾保留的明文字符数量。
     * </p>
     *
     * @return 后置保留长度，默认0
     */
    int suffixNoMaskLen() default 0;

    /**
     * 掩码符号
     * <p>
     * 用于替换敏感字符的符号。
     * </p>
     *
     * @return 掩码符号，默认*
     */
    String symbol() default "*";
}
