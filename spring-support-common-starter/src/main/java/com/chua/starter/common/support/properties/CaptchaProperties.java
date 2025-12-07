package com.chua.starter.common.support.properties;

import com.chua.starter.common.support.constant.CaptchaTypeEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.awt.*;

/**
 * 校验�?
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = CaptchaProperties.PRE, ignoreInvalidFields = true)
public class CaptchaProperties {

    /**
     * 配置前缀
     */
    public static final String PRE = "plugin.captcha";


    /**
     * 验证码类�?
     */
    private CaptchaTypeEnum type = CaptchaTypeEnum.SPEC;

    /**
     *  是否打开
     */
    private boolean enable;

    /**
     * 验证码缓存过期时�?单位:�?
     */
    private long ttl = 120L;

    /**
     * 验证码内容长�?
     */
    private int length = 4;
    /**
     * 验证码宽�?
     */
    private int width = 120;
    /**
     * 验证码高�?
     */
    private int height = 36;

    /**
     * 验证码字�?
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

