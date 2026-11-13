package com.chua.starter.common.support.properties;

import com.chua.starter.common.support.constant.CaptchaTypeEnum;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.awt.*;

/**
 * 校验码
 *
 * @author CH
 * @since 4.0.0
 */
@ConfigurationProperties(prefix = CaptchaProperties.PRE, ignoreInvalidFields = true)
public class CaptchaProperties {
    /**
     * 是否启用
     */
    private boolean enable = true;


    /**
     * 配置前缀
     */
    public static final String PRE = "plugin.captcha";


    /**
     * 验证码类型
     */
    private CaptchaTypeEnum type = CaptchaTypeEnum.SPEC;


    /**
     * 验证码缓存过期时间单位:秒
     */
    // [P3C A20 豁免]
    private long ttl = 120L;

    /**
     * 验证码内容长度
     */
    private int length = 4;
    /**
     * 验证码宽度
     */
    // [P3C A20 豁免]
    private int width = 120;
    /**
     * 验证码高度
     */
    // [P3C A20 豁免]
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
    // [P3C A20 豁免]
    private int fontSize = 20;

    /**
     * 获取 enable
     *
     * @return enable
     */
    public boolean isEnable() {
        return enable;
    }

    /**
     * 设置 enable
     *
     * @param enable enable
     */
    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    /**
     * 获取 类型
     *
     * @return type
     */
    public CaptchaTypeEnum getType() {
        return type;
    }

    /**
     * 设置 类型
     *
     * @param type 类型
     */
    public void setType(CaptchaTypeEnum type) {
        this.type = type;
    }

    /**
     * 获取 ttl
     *
     * @return ttl
     */
    public long getTtl() {
        return ttl;
    }

    /**
     * 设置 ttl
     *
     * @param ttl ttl
     */
    public void setTtl(long ttl) {
        this.ttl = ttl;
    }

    /**
     * 获取 长度
     *
     * @return length
     */
    public int getLength() {
        return length;
    }

    /**
     * 设置 长度
     *
     * @param length 长度
     */
    public void setLength(int length) {
        this.length = length;
    }

    /**
     * 获取 width
     *
     * @return width
     */
    public int getWidth() {
        return width;
    }

    /**
     * 设置 width
     *
     * @param width width
     */
    public void setWidth(int width) {
        this.width = width;
    }

    /**
     * 获取 height
     *
     * @return height
     */
    public int getHeight() {
        return height;
    }

    /**
     * 设置 height
     *
     * @param height height
     */
    public void setHeight(int height) {
        this.height = height;
    }

    /**
     * 获取 font名称
     *
     * @return fontName
     */
    public String getFontName() {
        return fontName;
    }

    /**
     * 设置 font名称
     *
     * @param fontName font名称
     */
    public void setFontName(String fontName) {
        this.fontName = fontName;
    }

    /**
     * 获取 fontstyle
     *
     * @return fontStyle
     */
    public Integer getFontStyle() {
        return fontStyle;
    }

    /**
     * 设置 fontstyle
     *
     * @param fontStyle fontstyle
     */
    public void setFontStyle(Integer fontStyle) {
        this.fontStyle = fontStyle;
    }

    /**
     * 获取 font大小
     *
     * @return fontSize
     */
    public int getFontSize() {
        return fontSize;
    }

    /**
     * 设置 font大小
     *
     * @param fontSize font大小
     */
    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
    }
}
