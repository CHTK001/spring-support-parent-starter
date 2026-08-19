package com.chua.starter.sync.support.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * DataSync 桥接配置。
 * <p>
 * 对应配置前缀：{@code plugin.sync.datasync}
 * </p>
 *
 * @since 2026/01/15
 * @author CH
 */
@ConfigurationProperties(prefix = SyncDatasyncProperties.PRE, ignoreInvalidFields = true)
public class SyncDatasyncProperties {

    /**
     * 配置前缀
     */
    public static final String PRE = "plugin.sync.datasync";

    /**
     * 是否启用 DataSync 桥接
     */
    private boolean enabled = true;

    /** 是否Enabled */
    public boolean isEnabled() {
        return enabled;
    }

    /** 设置Enabled */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
