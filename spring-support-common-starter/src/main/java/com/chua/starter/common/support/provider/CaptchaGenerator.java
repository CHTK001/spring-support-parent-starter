package com.chua.starter.common.support.provider;

import com.wf.captcha.base.Captcha;

/**
* 验证码生成器 SPI。
*
* <p>每个验证码类型（算术/中文/闪图等）通过 {@code @Spi} 注册扩展名，
* {@link CaptchaProvider} 按类型分发到对应实现，避免硬编码 switch。</p>
*
* @author CH
* @since 4.0.0.42
 */
public interface CaptchaGenerator {

    /**
    * SPI 扩展名（小写类型名，如 arithmetic / chinese / chinese_gif / gif / spec）。
    *
    * @return 扩展名
    */
    String name();

    /**
    * 创建验证码实例。
    *
    * @param width  图片宽度
    * @param height 图片高度
    * @param length 验证码长度
    * @return 验证码实例
    */
    Captcha create(int width, int height, int length);
}
