package com.chua.starter.server.support.model;

import lombok.Data;

/**
 * 全局 APM 兜底配置。
 *
 * <p>用于给所有已开启 APM 注入的发布提供统一默认值：发布自身填写的字段优先，
 * 发布留空的字段才取这里。项目 jvmArgs 中已经声明的 APM 参数仍然最优先，
 * 平台不会覆盖。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
public class ApmGlobalConfig {

    /**
     * 是否启用全局兜底。
     */
    private Boolean enabled;

    /**
     * SkyWalking agent jar 在目标主机上的绝对路径。
     *
     * <p>发布自身未填 jar 路径时使用；不同操作系统路径通常不同，
     * 因此只有各目标主机路径一致时才适合配置。</p>
     */
    private String jarPath;

    /**
     * OAP 上报地址（collector.backend_service）。
     *
     * <p>单机填 {@code host:11800}；OAP 集群/聚合填统一 VIP、负载均衡或域名。
     * 发布自身未填 OAP 地址时使用。</p>
     */
    private String backendService;

    /**
     * service_name 前缀；发布自身未填时使用。
     */
    private String serviceNamePrefix;
}
