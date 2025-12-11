package com.chua.support.prometheus.starter;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * prometheus配置
 *
 * @author CH
 * @since 2025/5/20 8:44
 */
@Data
@ConfigurationProperties(prefix = PrometheusProperties.PRE, ignoreInvalidFields = true)
public class PrometheusProperties {
    /**
     * 是否启用
     */
    private boolean enable = false;


    public static final String PRE = "plugin.prometheus";

    private String name = "prometheus";


}
