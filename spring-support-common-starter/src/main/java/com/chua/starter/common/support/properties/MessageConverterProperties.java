package com.chua.starter.common.support.properties;


import com.google.common.collect.Lists;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.MediaType;

import java.util.List;

/**
 * 消息转换器属性
 *
 * @author CH
 * @since 4.0.0
 */
@ConfigurationProperties(prefix = MessageConverterProperties.PRE, ignoreInvalidFields = true)
public class MessageConverterProperties {
    /**
     * 是否启用
     */
    private boolean enable = false;


    /** PRE */
    public static final String PRE = "plugin.message";

    /**
     * 日期默认格式
     */
    private String dataFormat = "yyyy-MM-dd HH:mm:ss";


    /**
     * 开启脱敏
     */
    private boolean openDesensitize = true;

    /**
     * 开启加解密
     */
    private boolean openCrypto = true;
    /**
     * 媒体类型
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
