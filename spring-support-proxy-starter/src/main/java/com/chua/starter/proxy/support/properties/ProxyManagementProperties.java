package com.chua.starter.proxy.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 代理管理模块配置
 *
 * @author CH
 * @since 2026/03/26
 */
@Data
@ConfigurationProperties(prefix = ProxyManagementProperties.PREFIX)
public class ProxyManagementProperties {

    public static final String PREFIX = "plugin.proxy";

    /**
     * 是否启用代理管理模块。
     */
    private boolean enable = false;

    /**
     * 应用启动时，是否自动恢复数据库中标记为运行中的代理服务器实例。
     */
    private boolean autoRestartRunning = true;
}
