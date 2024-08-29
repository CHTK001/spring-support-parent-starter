package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
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
     * 是否由其它对象注入参数 {@link #enable}
     */
    private boolean extInject = false;

    /**
     * 编解码器类型
     */
    private String codecType = "sm2";


    /**
     * 白名单
     */
    private List<String> whiteList = Collections.emptyList();
    
}
