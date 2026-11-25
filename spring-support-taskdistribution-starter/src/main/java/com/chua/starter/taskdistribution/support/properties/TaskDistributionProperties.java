package com.chua.starter.taskdistribution.support.properties;

import com.chua.common.support.taskdistribution.strategy.DispatchStrategy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 任务分发配置属性。
 *
 * <p>对应 application.yml 中的 {@code plugin.taskdistribution.*} 配置项。</p>
 *
 * <p>完整配置示例（所有项均可选，未配置时使用各字段说明中的默认值）：</p>
 *
 * <pre>{@code
 * plugin:
 *   taskdistribution:
 *     enabled: true                  # 模块总开关，缺省即开启
 *     node-id: node-01               # 节点标识，缺省自动生成 node-<时间戳>
 *     strategy: ROUND                # 派发策略：FIRST / LAST / RANDOM / ROUND / WEIGHT
 *     batch-size: 1                  # 每次批量处理的任务数（<=0 回落为 1）
 *     consumer-threads: 1            # 消费线程数（<=0 回落为 1）
 *     store-type: memory             # 存储类型：memory / file / db
 *     store-path: ./data/taskstore/  # 文件存储目录，仅 store-type=file 生效
 *     server:                        # 服务端声明（当前运行期未消费，不监听端口）
 *       enabled: true
 *       port: 19390
 *       host: 0.0.0.0
 *       protocol: tcp
 *     task:                          # 任务默认值声明（实际以每个 Task 的字段为准）
 *       timeout-ms: 30000
 *       max-retries: 3
 * }</pre>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.taskdistribution")
public class TaskDistributionProperties {

    /**
     * 节点 标识（默认自动生成，建议显式配置）。
     *
     * <p>含义：本节点在任务分发体系中的唯一标识，启动时写入 {@code TaskIdGenerator}
     * 作为任务 ID 前缀（格式 {@code nodeId-timestamp-seq}）。</p>
     * <p>默认值：{@code null}；为空时由初始化器自动生成 {@code node-<时间戳>} 并回写到本配置。
     * 多实例部署建议显式配置且全局唯一。</p>
     *
     * <pre>{@code
     * plugin:
     *   taskdistribution:
     *     node-id: node-01
     * }</pre>
     */
    private String nodeId;

    /**
     * 派发策略。
     *
     * <p>含义：从候选工作端中选择执行者的策略，可选
     * {@code FIRST / LAST / RANDOM / ROUND / WEIGHT}。</p>
     * <p>默认值：{@link DispatchStrategy#ROUND}（轮询）。注意：当前仅用于初始化日志与
     * 服务信息登记，未传入内存派发器。</p>
     *
     * <pre>{@code
     * plugin:
     *   taskdistribution:
     *     strategy: ROUND
     * }</pre>
     */
    private DispatchStrategy strategy = DispatchStrategy.ROUND;

    /**
     * 是否启用任务分发。
     *
     * <p>含义：模块总开关；{@code false} 时整个自动配置不加载，所有任务分发 Bean 均不装配。</p>
     * <p>默认值：{@code true}（未配置时缺省即开启）。</p>
     *
     * <pre>{@code
     * plugin:
     *   taskdistribution:
     *     enabled: true
     * }</pre>
     */
    private boolean enabled = true;

    /**
     * 每次批量处理的任务数（默认 1）。
     *
     * <p>含义：内存派发器单次批量出队的任务数，传入 {@code InMemoryDispatcherProvider}。</p>
     * <p>默认值：{@code 1}；配置 ≤0 时派发器内部回落为 {@code 1}。</p>
     *
     * <pre>{@code
     * plugin:
     *   taskdistribution:
     *     batch-size: 1
     * }</pre>
     */
    private int batchSize = 1;

    /**
     * 消费线程数（默认 1）。
     *
     * <p>含义：内存派发器的消费线程数（任务经桥接后在这些线程内执行），
     * 传入 {@code InMemoryDispatcherProvider}。</p>
     * <p>默认值：{@code 1}；配置 ≤0 时派发器内部回落为 {@code 1}。</p>
     *
     * <pre>{@code
     * plugin:
     *   taskdistribution:
     *     consumer-threads: 1
     * }</pre>
     */
    private int consumerThreads = 1;

    /**
     * 持久化存储类型：内存 / 文件 / db（默认 内存）。
     *
     * <p>含义：任务与结果的持久化实现，{@code memory}（内存）/ {@code file}（文件落盘）/
     * {@code db}（数据库，需 MyBatis-Plus 支持）。</p>
     * <p>默认值：{@code memory}；配置为 {@code db} 但容器中无 {@code TaskStoreMapper} 时
     * 回落为内存存储，其它非法值同样按内存处理。</p>
     *
     * <pre>{@code
     * plugin:
     *   taskdistribution:
     *     store-type: memory
     * }</pre>
     */
    private String storeType = "memory";

    /**
     * 文件存储数据目录（存储类型 = 文件 时生效）。
     *
     * <p>含义：{@code store-type=file} 时任务数据的落盘目录。</p>
     * <p>默认值：{@code ./data/taskstore/}；{@code store-type} 非 {@code file} 时不生效。</p>
     *
     * <pre>{@code
     * plugin:
     *   taskdistribution:
     *     store-path: ./data/taskstore/
     * }</pre>
     */
    private String storePath = "./data/taskstore/";

    /**
     * 服务端配置。
     *
     * <p>含义：服务端模式（接收任务）相关声明，见 {@link ServerConfig}。
     * 当前这些项为仅声明配置，运行期未读取、不会监听端口。</p>
     * <p>默认值：{@code new ServerConfig()}（各字段默认值见嵌套类说明）。</p>
     *
     * <pre>{@code
     * plugin:
     *   taskdistribution:
     *     server:
     *       enabled: true
     *       port: 19390
     *       host: 0.0.0.0
     *       protocol: tcp
     * }</pre>
     */
    private ServerConfig server = new ServerConfig();

    /**
     * 任务默认配置。
     *
     * <p>含义：任务的默认超时与重试声明，见 {@link TaskConfig}。
     * 当前为仅声明配置，实际超时/重试取每个 {@code Task} 的对应字段。</p>
     * <p>默认值：{@code new TaskConfig()}（各字段默认值见嵌套类说明）。</p>
     *
     * <pre>{@code
     * plugin:
     *   taskdistribution:
     *     task:
     *       timeout-ms: 30000
     *       max-retries: 3
     * }</pre>
     */
    private TaskConfig task = new TaskConfig();

    /**
     * 服务端配置。
     *
     * <p>完整配置示例：</p>
     *
     * <pre>{@code
     * plugin:
     *   taskdistribution:
     *     server:
     *       enabled: true     # 服务端模式开关
     *       port: 19390       # 监听端口
     *       host: 0.0.0.0     # 监听主机
     *       protocol: tcp     # 传输协议：tcp / udp
     * }</pre>
     *
     * <p>注意：当前自动配置未读取本组配置，不会监听任何端口；保留用于后续服务端模式扩展。</p>
     *
     * @since 4.0.0.42
     * @author CH
     */
    @Data
    public static class ServerConfig {

        /**
         * 是否启用服务端模式（接收任务）。
         *
         * <p>含义：声明本节点是否作为服务端接收外部任务。</p>
         * <p>默认值：{@code true}；当前运行期未读取（仅声明）。</p>
         *
         * <pre>{@code
         * plugin:
         *   taskdistribution:
         *     server:
         *       enabled: true
         * }</pre>
         */
        private boolean enabled = true;

        /**
         * 监听端口。
         *
         * <p>含义：服务端模式声明监听的端口。</p>
         * <p>默认值：{@code 19390}；当前运行期未读取，不会实际监听该端口。</p>
         *
         * <pre>{@code
         * plugin:
         *   taskdistribution:
         *     server:
         *       port: 19390
         * }</pre>
         */
        // [P3C A20 豁免]
        private int port = 19390;

        /**
         * 监听主机。
         *
         * <p>含义：服务端模式声明绑定的主机地址。</p>
         * <p>默认值：{@code 0.0.0.0}；当前运行期未读取。</p>
         *
         * <pre>{@code
         * plugin:
         *   taskdistribution:
         *     server:
         *       host: 0.0.0.0
         * }</pre>
         */
        private String host = "0.0.0.0";

        /**
         * 传输协议：tcp / udp。
         *
         * <p>含义：服务端模式声明的传输协议。</p>
         * <p>默认值：{@code tcp}；当前运行期未读取。</p>
         *
         * <pre>{@code
         * plugin:
         *   taskdistribution:
         *     server:
         *       protocol: tcp
         * }</pre>
         */
        private String protocol = "tcp";
    }

    /**
     * 任务默认配置。
     *
     * <p>完整配置示例：</p>
     *
     * <pre>{@code
     * plugin:
     *   taskdistribution:
     *     task:
     *       timeout-ms: 30000   # 默认超时（毫秒）
     *       max-retries: 3      # 默认最大重试次数
     * }</pre>
     *
     * <p>注意：当前自动配置未读取本组配置；实际超时/重试取每个 {@code Task} 的
     * {@code timeoutMs} / {@code maxRetries} 字段（框架默认同为 30000ms / 3 次）。</p>
     *
     * @since 4.0.0.42
     * @author CH
     */
    @Data
    public static class TaskConfig {

        /**
         * 默认超时（毫秒）。
         *
         * <p>含义：任务执行超时阈值（毫秒）。</p>
         * <p>默认值：{@code 30000}；当前为仅声明配置，实际取每个 {@code Task} 的
         * {@code timeoutMs} 字段（框架默认同为 30000）。</p>
         *
         * <pre>{@code
         * plugin:
         *   taskdistribution:
         *     task:
         *       timeout-ms: 30000
         * }</pre>
         */
        // [P3C A20 豁免]
        private long timeoutMs = 30000;

        /**
         * 默认最大重试次数。
         *
         * <p>含义：任务失败后的最大重试次数。</p>
         * <p>默认值：{@code 3}；当前为仅声明配置，实际取每个 {@code Task} 的
         * {@code maxRetries} 字段（框架默认同为 3）。</p>
         *
         * <pre>{@code
         * plugin:
         *   taskdistribution:
         *     task:
         *       max-retries: 3
         * }</pre>
         */
        private int maxRetries = 3;
    }
}
