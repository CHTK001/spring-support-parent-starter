package com.chua.starter.common.support.provider;

import com.chua.common.support.spi.annotations.Spi;
import com.wf.captcha.ArithmeticCaptcha;
import com.wf.captcha.base.Captcha;

/**
* 算术运算验证码生成器。
*
* @author CH
* @since 4.0.0.42
 */
@Spi("arithmetic")
public class ArithmeticCaptchaGenerator implements CaptchaGenerator {

    /**
    * SPI 扩展名。
    *
    * @return "arithmetic"
     */
    @Override
    public String name() {
        return "arithmetic";
    }

    /**
    * 创建算术验证码，长度固定两位。
    *
    * @param width  图片宽度
    * @param height 图片高度
    * @param length 验证码长度
    * @return 算术验证码实例
     */
    @Override
    public Captcha create(int width, int height, int length) {
        ArithmeticCaptcha captcha = new ArithmeticCaptcha(width, height);
        captcha.setLen(length);
        return captcha;
    }
}