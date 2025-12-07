package com.chua.starter.common.support.constant;

/**
 * EasyCaptcha 验证码类型枚举
 * <p>
 * 定义了系统支持的各种验证码类型，包括普通字符、算数运算、中文字符等。
 * </p>
 *
 * @author haoxr
 * @author CH
 * @since 2023/03/24
 * @version 1.0.0
 */
public enum CaptchaTypeEnum {

    /**
     * 随机字符验证码
     */
    RANDOM,

    /**
     * 算数运算验证码（如：1+2=?）
     */
    ARITHMETIC,

    /**
     * 中文字符验证码
     */
    CHINESE,

    /**
     * 中文闪图验证码（GIF动画）
     */
    CHINESE_GIF,

    /**
     * 普通闪图验证码（GIF动画）
     */
    GIF,

    /**
     * 特殊类型验证码
     */
    SPEC
}
