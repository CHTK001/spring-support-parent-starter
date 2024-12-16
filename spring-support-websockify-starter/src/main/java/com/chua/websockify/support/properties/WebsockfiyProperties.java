package com.chua.websockify.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * socket.io
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = WebsockfiyProperties.PRE, ignoreInvalidFields = true)
public class WebsockfiyProperties {


    public static final String PRE = "plugin.websockify";
    /**
     * 是否开启
     */
    private boolean enable;
    /**
     * 本地IP
     */
    private String host = "0.0.0.0";

    /**
     * 端口
     */
    private int port = 15900;
    /**
     * 目标地址
     */
    private String targetHost = "127.0.0.1";

    /**
     * 目标端口
     */
    private int targetPort = 5900;

}
