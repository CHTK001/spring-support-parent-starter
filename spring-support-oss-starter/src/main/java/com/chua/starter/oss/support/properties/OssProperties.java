package com.chua.starter.oss.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.starter.oss.support.properties.OssProperties.PRE;

/**
 * 跨域
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class OssProperties {

    public static final String PRE = "plugin.oss";
    /**
     * 开启oss
     */
    private boolean open = true;
}