package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;

/**
 * 代理
 *
 * @author CH
 * @since 2022/7/28 13:07
 */
@Data
@ConfigurationProperties(prefix = ProxyProperties.PRE, ignoreInvalidFields = true)
public class ProxyProperties {

    public static final String PRE = "plugin.proxy";

    /**
     * 开启代理
     */
    private boolean openProxy;
    @NestedConfigurationProperty
    private List<ProxyConfiguration> config;

    @Data
    public static
    class ProxyConfiguration {

        private String target;

        private String[] source;

        private Type type;

        private String filter;
    }


    public static enum Type {
        /**
         * http
         */
        HTTP,
        /**
         * tcp
         */
        TCP
    }
}
