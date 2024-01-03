package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 表插件特性
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = TablePluginProperties.PRE, ignoreInvalidFields = true)
public class TablePluginProperties {

    public static final String PRE = "plugin.table";

    /**
     * 是否开启
     */
    private boolean open;

    /**
     * 异步
     */
    private boolean async;

    /**
     * 包装
     */
    private String[] packages;
}
