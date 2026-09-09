package com.chua.starter.common.support.provider;

import com.chua.common.support.spi.annotations.Spi;
import com.wf.captcha.ChineseCaptcha;
import com.wf.captcha.base.Captcha;

/**
 * 中文字符验证码生成器。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi("chinese")
public class ChineseCaptchaGenerator implements CaptchaGenerator {

    /**
     * SPI 扩展名。
     *
     * @return "chinese"
     */
    @Override
    public String name() {
        return "chinese";
    }

    /**
     * 创建中文字符验证码。
     *
     * @param width  图片宽度
     * @param height 图片高度
     * @param length 验证码长度
     * @return 中文验证码实例
     */
    @Override
    public Captcha create(int width, int height, int length) {
        ChineseCaptcha captcha = new ChineseCaptcha(width, height);
        captcha.setLen(length);
        return captcha;
    }
}