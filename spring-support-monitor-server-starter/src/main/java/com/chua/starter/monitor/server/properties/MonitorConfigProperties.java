package com.chua.starter.monitor.server.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 监视服务器属性
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/31
 */
@Data
@ConfigurationProperties(prefix = MonitorConfigProperties.PRE, ignoreInvalidFields = true)
public class MonitorConfigProperties {

    public static final String PRE = "plugin.monitor";


    /**
     * GIS
     */
    private GisProperties gis;

    /**
     * 代理
     */
    private ProxyProperties proxy;
    /**
     * 数据
     */
    private DataProperties data;


    @Data
    static class GisProperties {

        /**
         * 是否启用
         */
        private boolean enable = true;
    }

    @Data
    static class DataProperties {

        /**
         * 是否启用
         */
        private boolean enable = true;
    }

    @Data
    static class ProxyProperties {

        /**
         * 是否启用
         */
        private boolean enable = true;
    }
}
