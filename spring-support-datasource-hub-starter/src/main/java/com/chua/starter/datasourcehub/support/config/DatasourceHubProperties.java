package com.chua.starter.datasourcehub.support.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据源中心配置属性。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@ConfigurationProperties(prefix = "datasource.hub")
public class DatasourceHubProperties {

    /**
     * 服务信息面板上「数据源中心」模块的展示旗标（非模块开关）
     */
    private boolean enabled = true;

    /**
     * JDBC 连接空闲存活时长（分钟），超时自动关闭（小于 1 按 1 处理）
     */
    private long connectionTtlMinutes = 10L;

    /**
     * 连通性探测超时（毫秒）
     */
    private int probeTimeoutMs = 5000;
}