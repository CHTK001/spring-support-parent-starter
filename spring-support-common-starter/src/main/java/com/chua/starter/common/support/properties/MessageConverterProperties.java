package com.chua.starter.common.support.properties;


import com.google.common.collect.Lists;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.MediaType;

import java.util.List;

/**
 * 消息转换器配置属性。
 *
 * <p>前缀 {@code plugin.message}。控制 Web 消息转换增强：日期格式化、脱敏、
 * 加解密及生效的媒体类型。</p>
 *
 * <h3>配置示例</h3>
 * <pre>
 * plugin:
 *   message:
 *     enable: false                    # 是否启用（默认 false）
 *     data-format: yyyy-MM-dd HH:mm:ss # 日期格式（默认 yyyy-MM-dd HH:mm:ss）
 *     open-desensitize: true           # 脱敏（默认 true）
 *     open-crypto: true                # 加解密（默认 true）
 *     media-types:                     # 生效媒体类型（默认 application/json、text/xml）
 *       - application/json
 *       - text/xml
 * </pre>
 *
 * @author CH
 * @since 4.0.0
 */
@ConfigurationProperties(prefix = MessageConverterProperties.PRE, ignoreInvalidFields = true)
public class MessageConverterProperties {
    /**
     * 是否启用消息转换增强。
     * <p>YAML：{@code plugin.message.enable}，默认 {@code false}。</p>
     */
    private boolean enable = false;


    /**
     * PRE
    */
    public static final String PRE = "plugin.message";

    /**
     * 日期默认格式。
     * <p>YAML：{@code plugin.message.data-format}，默认 {@code yyyy-MM-dd HH:mm:ss}。</p>
     */
    private String dataFormat = "yyyy-MM-dd HH:mm:ss";


    /**
     * 是否开启脱敏。
     * <p>YAML：{@code plugin.message.open-desensitize}，默认 {@code true}。</p>
     */
    private boolean openDesensitize = true;

    /**
     * 是否开启加解密。
     * <p>YAML：{@code plugin.message.open-crypto}，默认 {@code true}。</p>
     */
    private boolean openCrypto = true;
    /**
     * 生效的媒体类型。
     * <p>YAML：{@code plugin.message.media-types}，默认 {@code application/json}、{@code text/xml}。</p>
     */
    private List<MediaType> mediaTypes = Lists.newArrayList(MediaType.APPLICATION_JSON,
            MediaType.TEXT_XML
            );

    /**
     * 获取 enable
     *
     * @return enable
     */
    public boolean getEnable() {
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
     * 获取 数据格式化
     *
     * @return dataFormat
     */
    public String getDataFormat() {
        return dataFormat;
    }

    /**
     * 设置 数据格式化
     *
     * @param dataFormat 数据格式化
     */
    public void setDataFormat(String dataFormat) {
        this.dataFormat = dataFormat;
    }

    /**
     * 获取 打开desensitize
     *
     * @return openDesensitize
     */
    public boolean getOpenDesensitize() {
        return openDesensitize;
    }

    /**
     * 设置 打开desensitize
     *
     * @param openDesensitize 打开desensitize
     */
    public void setOpenDesensitize(boolean openDesensitize) {
        this.openDesensitize = openDesensitize;
    }

    /**
     * 获取是否开启加密
     *
     * @return openCrypto
     */
    public boolean getOpenCrypto() {
        return openCrypto;
    }

    /**
     * 设置是否开启加密
     *
     * @param openCrypto 是否开启加密
     */
    public void setOpenCrypto(boolean openCrypto) {
        this.openCrypto = openCrypto;
    }

    /**
     * 获取 media类型
     *
     * @return mediaTypes
     */
    public List<MediaType> getMediaTypes() {
        return mediaTypes;
    }

    /**
     * 设置 media类型
     *
     * @param mediaTypes media类型
     */
    public void setMediaTypes(List<MediaType> mediaTypes) {
        this.mediaTypes = mediaTypes;
    }
}
