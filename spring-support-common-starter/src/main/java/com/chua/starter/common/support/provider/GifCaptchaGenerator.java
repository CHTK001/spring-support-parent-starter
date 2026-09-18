package com.chua.starter.common.support.provider;

import com.chua.common.support.spi.annotations.Spi;
import com.wf.captcha.GifCaptcha;
import com.wf.captcha.base.Captcha;

/**
* 普通闪图（GIF）验证码生成器。
*
* @author CH
* @since 4.0.0.42
 */
@Spi("gif")
public class GifCaptchaGenerator implements CaptchaGenerator {

    /**
    * SPI 扩展名。
    *
    * @return "gif"
    */
    @Override
    public String name() {
        return "gif";
    }

    /**
    * 创建普通闪图验证码。
    *
    * @param width  图片宽度
    * @param height 图片高度
    * @param length 验证码长度
    * @return 闪图验证码实例
    */
    @Override
    public Captcha create(int width, int height, int length) {
        GifCaptcha captcha = new GifCaptcha(width, height);
        captcha.setLen(length);
        return captcha;
    }
}
