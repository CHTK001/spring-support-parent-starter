package com.chua.starter.common.support.properties;

import com.chua.starter.common.support.constant.CaptchaTypeEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.awt.*;

/**
 * 校验码
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = CaptchaProperties.PRE, ignoreInvalidFields = true)
public class CaptchaProperties {
    /**
     * 是否启用
     */
    private boolean enable = false;


    /**
     * 配置前缀
     */
    public static final String PRE = "plugin.captcha";


    /**
     * 验证码类型
     */
    private CaptchaTypeEnum type = CaptchaTypeEnum.SPEC;

    /**
     *  是否打开
     */
    private boolean enable;

    /**
     * 验证码缓存过期时间单位:秒
     */
    private long ttl = 120L;

    /**
     * 验证码内容长度
     */
    private int length = 4;
    /**
     * 验证码宽度
     */
    private int width = 120;
    /**
     * 验证码高度
     */
    private int height = 36;

    /**
     * 验证码字体
     */
    private String fontName = "Verdana";

    /**
     * 字体风格
     */
    private Integer fontStyle = Font.PLAIN;

    /**
     * 字体大小
     */
    private int fontSize = 20;


}

