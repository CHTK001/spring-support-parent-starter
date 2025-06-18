package com.chua.starter.guacamole.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Guacamole配置属性
 *
 * @author CH
 * @since 2024/7/24
 */
@Data
@ConfigurationProperties(prefix = "plugin.guacamole")
public class GuacamoleProperties {

    /**
     * 是否启用Guacamole
     */
    private boolean enabled = true;

    /**
     * Guacamole服务器主机
     */
    private String host = "localhost";

    /**
     * Guacamole服务器端口
     */
    private int port = 4822;

    /**
     * 连接超时时间(毫秒)
     */
    private int connectTimeout = 10000;

    /**
     * 读取超时时间(毫秒)
     */
    private int readTimeout = 10000;
} 