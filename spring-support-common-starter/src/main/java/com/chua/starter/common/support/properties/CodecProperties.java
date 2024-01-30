package com.chua.starter.common.support.properties;

import com.google.common.collect.Lists;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 全局常数
 *
 * @author CH
 */

@Data
@ConfigurationProperties(prefix = CodecProperties.PRE, ignoreInvalidFields = true)
public class CodecProperties {

    public static final String PRE = "plugin.codec";

    /**
     * 开放式编解码器
     */
    private boolean enable = false;

    /**
     * 编解码器类型
     */
    private String codecType = "sm2";


    private List<String> whiteList ;
    
}
