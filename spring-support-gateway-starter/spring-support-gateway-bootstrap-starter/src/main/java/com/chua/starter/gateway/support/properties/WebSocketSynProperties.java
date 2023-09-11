package com.chua.starter.gateway.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SynProperties
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = WebSocketSynProperties.PRE, ignoreInvalidFields = true)
public class WebSocketSynProperties {
    public static final String PRE = "plugin.sync.websocket";
    /**
     * admin地址
     */
    private String urls;

    /**
     * allowOrigin.
     */
    private String allowOrigin;

}
