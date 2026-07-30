package com.chua.starter.sync.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * DataSync 桥接配置。
 * <p>
 * 对应配置前缀：{@code plugin.sync.datasync}
 * </p>
 *
 * @author CH
 * @since 2026/01/15
 */
@Data
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
}
