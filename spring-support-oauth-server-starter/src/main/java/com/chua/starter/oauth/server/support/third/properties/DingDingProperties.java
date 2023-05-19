package com.chua.starter.oauth.server.support.third.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * dingding
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.oauth.third.dingding", ignoreInvalidFields = true)
public class DingDingProperties {

    private String appKey;
    private String appSecret;
    private String domain;
    private String protocol;
    private int timeout;
    private boolean echoLog = true;
}
