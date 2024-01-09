package com.chua.starter.common.support.properties;


import com.alibaba.fastjson2.JSONWriter;
import com.google.common.collect.Lists;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.MediaType;

import java.util.List;

/**
 * 消息转换器属性
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = MessageConverterProperties.PRE, ignoreInvalidFields = true)
public class MessageConverterProperties {

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
     * 特征
     */
    private JSONWriter.Feature[] features;
    /**
     * 媒体类型
     */
    private List<MediaType> mediaTypes = Lists.newArrayList(MediaType.APPLICATION_JSON,
            MediaType.TEXT_XML
            );
}
