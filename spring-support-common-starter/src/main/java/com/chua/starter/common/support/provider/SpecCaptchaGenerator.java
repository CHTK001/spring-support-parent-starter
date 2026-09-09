package com.chua.starter.common.support.provider;

import com.chua.common.support.spi.annotations.Spi;
import com.wf.captcha.SpecCaptcha;
import com.wf.captcha.base.Captcha;

/**
 * 特殊类型验证码生成器（默认字符集）。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi("spec")
public class SpecCaptchaGenerator implements CaptchaGenerator {

    /**
     * SPI 扩展名。
     *
     * @return "spec"
     */
    @Override
    public String name() {
        return "spec";
    }

    /**
     * 创建特殊类型验证码。
     *
     * @param width  图片宽度
     * @param height 图片高度
     * @param length 验证码长度
     * @return 特殊验证码实例
     */
    @Override
    public Captcha create(int width, int height, int length) {
        SpecCaptcha captcha = new SpecCaptcha(width, height);
        captcha.setCharType(Captcha.TYPE_DEFAULT);
        captcha.setLen(length);
        return captcha;
    }
}