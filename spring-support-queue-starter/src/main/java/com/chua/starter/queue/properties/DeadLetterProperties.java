package com.chua.starter.queue.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 死信队列配置属性（配置前缀：{@code plugin.dead-letter}）。
 * <p>
 * 与 {@code plugin.queue} 同级的独立配置；由 {@code DeadLetterAutoConfiguration} 经
 * {@code @EnableConfigurationProperties} 注册，需 {@code plugin.dead-letter.enable=true} 开启，
 * 且主配置 {@code plugin.queue.enable=true}（死信自动配置靠其组件扫描带入）。
 * </p>
 *
 * <p>完整 YAML 配置示例：</p>
 * <pre>{@code
 * plugin:
 *   dead-letter:
 *     enable: true                    # 死信总开关，默认 false
 *     type: local                     # 死信存储类型，默认 chronicle-queue；仅 local 有实现，其余回落 local 并告警
 *     delay-threads: 2                # 死信延迟调度线程数，默认 2（<=0 回落 2）
 *     max-retries: 3                  # 最大重试次数，默认 3；必须 >0，否则启动抛 IllegalArgumentException
 *     retry-delay-seconds: 5          # 首次重试延迟（秒），默认 5；必须 >0，否则启动抛 IllegalArgumentException
 *     max-retry-delay-seconds: 300    # 重试延迟上限（秒），默认 300；负数启动抛 IllegalArgumentException
 *     exponential-backoff: true       # 是否指数退避，默认 true
 *     backoff-multiplier: 2.0         # 退避乘数，默认 2.0；必须 >0，否则启动抛 IllegalArgumentException
 * }</pre>
 *
 * @since 2025-12-25
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = DeadLetterProperties.PREFIX)
public class DeadLetterProperties {

    /**
     * 前缀
    */
    public static final String PREFIX = "plugin.dead-letter";

    /**
     * 是否启用（plugin.dead-letter.enable）。
     * <p>含义：死信队列总开关；{@code DeadLetterAutoConfiguration} 以
     * {@code @ConditionalOnProperty(name = "enable", havingValue = "true")} 门控。</p>
     * <p>默认值：{@code false}（字段初始化值，无其它回落逻辑）。</p>
     * <pre>{@code
     * plugin:
     *   dead-letter:
     *     enable: true
     * }</pre>
     */
    private boolean enable = false;

    /**
     * 队列类型（plugin.dead-letter.type）。
     * <p>含义：死信底层存储类型；{@code DeadLetterAutoConfiguration#createDlqStorage} 仅实现
     * {@code local}，其余取值（含未识别值）打印 WARN 后回落本地存储。</p>
     * <p>默认值：{@code chronicle-queue}（字段初始化值；仅为默认字符串，实际按 local 处理）。</p>
     * <pre>{@code
     * plugin:
     *   dead-letter:
     *     type: local
     * }</pre>
     */
    private String type = "chronicle-queue";

    /**
     * 延迟消息调度线程数（plugin.dead-letter.delay-threads）。
     * <p>含义：死信底层本地存储的延迟消息调度线程数。</p>
     * <p>默认值：{@code 2}；配置为 0 或负数时，{@code LocalMessageTemplate} 构造时回落为 {@code 2}。</p>
     * <pre>{@code
     * plugin:
     *   dead-letter:
     *     delay-threads: 2
     * }</pre>
     */
    private int delayThreads = 2;

    /**
     * 最大重试次数（plugin.dead-letter.max-retries）。
     * <p>含义：消息消费失败后的最大重试次数。</p>
     * <p>默认值：{@code 3}；必须大于 0，否则 {@code DeadLetterTemplate} 初始化抛
     * {@code IllegalArgumentException}（无回落值）。</p>
     * <pre>{@code
     * plugin:
     *   dead-letter:
     *     max-retries: 3
     * }</pre>
     */
    private int maxRetries = 3;

    /**
     * 重试延迟（秒）（plugin.dead-letter.retry-delay-seconds）。
     * <p>含义：首次重试的延迟时长。</p>
     * <p>默认值：{@code 5} 秒；必须大于 0，否则 {@code DeadLetterTemplate} 初始化抛
     * {@code IllegalArgumentException}（无回落值）。</p>
     * <pre>{@code
     * plugin:
     *   dead-letter:
     *     retry-delay-seconds: 5
     * }</pre>
     */
    private long retryDelaySeconds = 5;

    /**
     * 最大重试延迟（秒）（plugin.dead-letter.max-retry-delay-seconds）。
     * <p>含义：指数退避下单次重试延迟的上限。</p>
     * <p>默认值：{@code 300} 秒；配置为负数时 {@code DeadLetterTemplate} 初始化抛
     * {@code IllegalArgumentException}；0 合法但会把重试延迟上限压为 0（不建议）。</p>
     * <pre>{@code
     * plugin:
     *   dead-letter:
     *     max-retry-delay-seconds: 300
     * }</pre>
     */
    // [P3C A20 豁免]
    private long maxRetryDelaySeconds = 300;

    /**
     * 是否启用指数退避（plugin.dead-letter.exponential-backoff）。
     * <p>含义：重试延迟是否按乘数指数增长；{@code false} 时按固定间隔重试。</p>
     * <p>默认值：{@code true}（字段初始化值，无其它回落逻辑）。</p>
     * <pre>{@code
     * plugin:
     *   dead-letter:
     *     exponential-backoff: true
     * }</pre>
     */
    private boolean exponentialBackoff = true;

    /**
     * 退避乘数（plugin.dead-letter.backoff-multiplier）。
     * <p>含义：指数退避的倍增系数。</p>
     * <p>默认值：{@code 2.0}；必须大于 0，否则 {@code DeadLetterTemplate} 初始化抛
     * {@code IllegalArgumentException}（无回落值）。</p>
     * <pre>{@code
     * plugin:
     *   dead-letter:
     *     backoff-multiplier: 2.0
     * }</pre>
     */
    private double backoffMultiplier = 2.0;

    // Lombok 注解处理器未运行时的手动 getter 方法
    /**
     * 获取最大值Retries
     *
     * @return 获取最大重试的结果
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * 获取重试延迟seconds
     *
     * @return 获取重试延迟seconds的结果
     */
    public long getRetryDelaySeconds() {
        return retryDelaySeconds;
    }

    /**
     * 获取最大值重试延迟seconds
     *
     * @return 获取最大重试延迟seconds的结果
     */
    public long getMaxRetryDelaySeconds() {
        return maxRetryDelaySeconds;
    }

    /**
     * 是否exponential退避
     *
     * @return 是否exponential退避的结果
     */
    public boolean isExponentialBackoff() {
        return exponentialBackoff;
    }

    /**
     * 获取退避multiplier
     *
     * @return 获取退避multiplier的结果
     */
    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    /**
     * 获取类型
     *
     * @return 获取类型的结果
     */
    public String getType() {
        return type;
    }

    /**
     * 获取延迟threads
     *
     * @return 获取延迟threads的结果
     */
    public int getDelayThreads() {
        return delayThreads;
    }
}
