package com.chua.starter.common.support.properties;

import com.chua.starter.common.support.constant.CaptchaTypeEnum;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.awt.*;

/**
 * 图形验证码配置属性。
 *
 * <p>前缀 {@code plugin.captcha}，控制验证码生成形态与缓存行为。相关配置键：
 * 验证码端点前缀经 {@code plugin.captcha.context-path} 调整（默认 {@code /v1/}）；
 * 外部打码服务配置见 {@code plugin.captcha.external.*}（由 ExternalCaptchaConfiguration 消费）。</p>
 *
 * <h3>配置示例</h3>
 * <pre>
 * plugin:
 *   captcha:
 *     enable: true        # 是否启用（字段默认 true；端点装配 matchIfMissing=false，需显式开启）
 *     type: SPEC          # 验证码形态（默认 SPEC）
 *     ttl: 120            # 验证码缓存过期秒数（默认 120）
 *     length: 4           # 字符长度（默认 4）
 *     width: 120          # 图片宽度（默认 120）
 *     height: 36          # 图片高度（默认 36）
 *     font-name: Verdana  # 字体（默认 Verdana）
 *     font-style: 0       # 字体风格（默认 Font.PLAIN）
 *     font-size: 20       # 字号（默认 20）
 * </pre>
 *
 * @author CH
 * @since 4.0.0
 */
@ConfigurationProperties(prefix = CaptchaProperties.PRE, ignoreInvalidFields = true)
public class CaptchaProperties {
    /**
     * 是否启用验证码。
     * <p>YAML：{@code plugin.captcha.enable}，字段默认 {@code true}；端点装配条件为
     * {@code matchIfMissing = false}，需显式配置 {@code plugin.captcha.enable=true} 才会装配验证码端点。</p>
     */
    private boolean enable = true;


    /**
     * 配置前缀
     */
    public static final String PRE = "plugin.captcha";


    /**
     * 验证码类型。
     * <p>YAML：{@code plugin.captcha.type}，默认 {@code SPEC}。</p>
     */
    private CaptchaTypeEnum type = CaptchaTypeEnum.SPEC;


    /**
     * 验证码缓存过期时间，单位秒。
     * <p>YAML：{@code plugin.captcha.ttl}，默认 {@code 120} 秒。</p>
     */
    // [P3C A20 豁免]
    private long ttl = 120L;

    /**
     * 验证码内容长度。
     * <p>YAML：{@code plugin.captcha.length}，默认 {@code 4}。</p>
     */
    private int length = 4;
    /**
     * 验证码图片宽度（像素）。
     * <p>YAML：{@code plugin.captcha.width}，默认 {@code 120}。</p>
     */
    // [P3C A20 豁免]
    private int width = 120;
    /**
     * 验证码图片高度（像素）。
     * <p>YAML：{@code plugin.captcha.height}，默认 {@code 36}。</p>
     */
    // [P3C A20 豁免]
    private int height = 36;

    /**
     * 验证码字体名称。
     * <p>YAML：{@code plugin.captcha.font-name}，默认 {@code Verdana}。</p>
     */
    private String fontName = "Verdana";

    /**
     * 字体风格（如 {@link Font#PLAIN} / {@link Font#BOLD} / {@link Font#ITALIC}）。
     * <p>YAML：{@code plugin.captcha.font-style}，默认 {@link Font#PLAIN}。</p>
     */
    private Integer fontStyle = Font.PLAIN;

    /**
     * 字体大小。
     * <p>YAML：{@code plugin.captcha.font-size}，默认 {@code 20}。</p>
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
