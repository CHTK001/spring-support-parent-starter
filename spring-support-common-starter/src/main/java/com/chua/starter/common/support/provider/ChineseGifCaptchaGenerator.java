package com.chua.starter.common.support.provider;

import com.chua.common.support.spi.annotations.Spi;
import com.wf.captcha.ChineseGifCaptcha;
import com.wf.captcha.base.Captcha;

/**
 * 中文闪图（GIF）验证码生成器。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi("chinese_gif")
public class ChineseGifCaptchaGenerator implements CaptchaGenerator {

    /**
     * SPI 扩展名。
     *
     * @return "chinese_gif"
     */
    @Override
    public String name() {
        return "chinese_gif";
    }

    /**
     * 创建中文闪图验证码。
     *
     * @param width  图片宽度
     * @param height 图片高度
     * @param length 验证码长度
     * @return 中文闪图验证码实例
     */
    @Override
    public Captcha create(int width, int height, int length) {
        ChineseGifCaptcha captcha = new ChineseGifCaptcha(width, height);
        captcha.setLen(length);
        return captcha;
    }
}