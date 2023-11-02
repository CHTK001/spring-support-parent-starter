package com.chua.starter.device.support.adaptor.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.starter.device.support.adaptor.properties.InfluxProperties.PRE;

/**
 * 注入特性
 *
 * @author CH
 * @since 2023/10/30
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class InfluxProperties {
    static final String PRE = "plugin.server.influx";

    private String url = "http://127.0.0.1:8086";

    private String username = "root";
    private String password = "root1234";

    private String database = "device";
    private String retentionPolicy = "retention";

}