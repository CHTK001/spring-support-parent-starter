package com.chua.starter.taskdistribution.support.properties;

import com.chua.common.support.taskdistribution.strategy.DispatchStrategy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 任务分发配置属性。
 *
 * <p>对应 application.yml 中的 {@code plugin.taskdistribution.*} 配置项。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.taskdistribution")
public class TaskDistributionProperties {

    /**
     * 节点 标识（默认自动生成，建议显式配置）
     */
    private String nodeId;

    /**
     * 派发策略
     */
    private DispatchStrategy strategy = DispatchStrategy.ROUND;

    /**
     * 是否启用任务分发
     */
    private boolean enabled = true;

    /**
     * 每次批量处理的任务数（默认 1）
     */
    private int batchSize = 1;

    /**
     * 消费线程数（默认 1）
     */
    private int consumerThreads = 1;

    /**
     * 持久化存储类型：内存 / 文件 / db（默认 内存）
     */
    private String storeType = "memory";

    /**
     * 文件存储数据目录（存储类型 = 文件 时生效）
     */
    private String storePath = "./data/taskstore/";

    /**
     * 服务端配置
     */
    private ServerConfig server = new ServerConfig();

    /**
     * 任务默认配置
     */
    private TaskConfig task = new TaskConfig();

    /**
     * 服务端配置。
     *
     * @since 4.0.0.42
     * @author CH
     */
    @Data
    public static class ServerConfig {

        /**
         * 是否启用服务端模式（接收任务）
         */
        private boolean enabled = true;

        /**
         * 监听端口
         */
        private int port = 19390;

        /**
         * 监听主机
         */
        private String host = "0.0.0.0";

        /**
         * 传输协议：tcp / udp
         */
        private String protocol = "tcp";
    }

    /**
     * 任务默认配置。
     *
     * @since 4.0.0.42
     * @author CH
     */
    @Data
    public static class TaskConfig {

        /**
         * 默认超时（毫秒）
         */
        private long timeoutMs = 30000;

        /**
         * 默认最大重试次数
         */
        private int maxRetries = 3;
    }
}
