package com.chua.starter.redis.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 服务端配置属性，配置前缀 {@code plugin.redis.server}。
 *
 * <p>开关语义：{@code enable} 字段默认值为 {@code false}，但自动装配条件为
 * {@code @ConditionalOnProperty(prefix = "plugin.redis.server", name = "enable",
 * havingValue = "true", matchIfMissing = true)}，即<strong>不配置该键即视为开启</strong>；
 * 只有显式配置 {@code plugin.redis.server.enable=false} 才会关闭
 * {@code RedisConfiguration} 的全部 Bean。</p>
 *
 * <p>消费说明：{@code openEmbedded}/{@code port}/{@code host}/{@code database}/
 * {@code maxMemory}/{@code maxHeap} 当前均为预留字段，无连接代码消费；
 * 实际连接信息（host/port/database/username/password/url/client-name/timeout 等）
 * 取自 Spring Boot 的 {@code spring.data.redis.*}，由
 * {@code RedisConfiguration#redissonClient} 消费。本类字段自身不含 ≤0/为空回落逻辑；
 * Redisson 侧的超时回落（10000ms）针对的是 {@code spring.data.redis.timeout}/{@code connect-timeout}。</p>
 *
 * <p>完整配置示例（YAML，键均为本类真实存在的可绑定项）：</p>
 * <pre>{@code
 * plugin:
 *   redis:
 *     server:
 *       enable: true          # 总开关；不配置即生效（matchIfMissing=true），仅显式 false 关闭
 *       open-embedded: false  # 预留：嵌入式 Redis，当前无代码消费
 *       port: 6379            # 预留：实际端口取 spring.data.redis.port
 *       database: 0           # 预留：实际库号取 spring.data.redis.database
 *       host: 0.0.0.0         # 预留：实际主机取 spring.data.redis.host
 *       max-memory: 128M      # 预留：当前无代码消费
 *       max-heap: 100M        # 预留：当前无代码消费
 * }</pre>
 *
 * @author CH
 * @since 4.0.0
 */
@Data
@ConfigurationProperties(prefix = "plugin.redis.server")
public class RedisServerProperties {

    /**
     * 模块总开关。
     *
     * <p>含义：控制 {@code RedisConfiguration} 是否装配 Redisson 客户端、
     * RedisTemplate、监听容器、缓存管理器等全部 Bean。</p>
     *
     * <p>默认值：{@code false}（字段初始值）；但装配条件含 {@code matchIfMissing=true}，
     * 即不配置该键也生效，仅当显式配置为 {@code false} 时才整体关闭。</p>
     *
     * <p>YAML 示例：{@code plugin.redis.server.enable: true}（可省略，效果相同）。</p>
     */
    private boolean enable = false;

    /**
     * 是否启用嵌入式 Redis 服务（预留）。
     *
     * <p>含义：预留的嵌入式 Redis 开关；当前无代码消费，嵌入式 Redis 尚未实现。</p>
     *
     * <p>默认值：{@code false}（无 ≤0/为空回落逻辑）。</p>
     *
     * <p>YAML 示例：{@code plugin.redis.server.open-embedded: false}。</p>
     */
    private boolean openEmbedded = false;

    /**
     * 端口（预留）。
     *
     * <p>含义：预留的 Redis 端口；当前无连接代码消费该字段，
     * 实际连接端口取 {@code spring.data.redis.port}。</p>
     *
     * <p>默认值：{@code 6379}（无 ≤0/为空回落逻辑）。</p>
     *
     * <p>YAML 示例：{@code plugin.redis.server.port: 6379}。</p>
     */
    // [P3C A20 豁免]
    private Integer port = 6379;

    /**
     * 数据库编号（预留）。
     *
     * <p>含义：预留的 Redis 逻辑库编号；当前无连接代码消费该字段，
     * 实际库号取 {@code spring.data.redis.database}。</p>
     *
     * <p>默认值：{@code 0}（无 ≤0/为空回落逻辑）。</p>
     *
     * <p>YAML 示例：{@code plugin.redis.server.database: 0}。</p>
     */
    private Integer database = 0;

    /**
     * 主机地址（预留）。
     *
     * <p>含义：预留的 Redis 主机；当前无连接代码消费该字段，
     * 实际主机取 {@code spring.data.redis.host}。</p>
     *
     * <p>默认值：{@code "0.0.0.0"}（无为空回落逻辑）。</p>
     *
     * <p>YAML 示例：{@code plugin.redis.server.host: 0.0.0.0}。</p>
     */
    private String host = "0.0.0.0";

    /**
     * 最大内存（预留）。
     *
     * <p>含义：预留的 Redis 最大内存上限（如 {@code 128M}）；当前无代码消费该字段。</p>
     *
     * <p>默认值：{@code "128M"}（无为空回落逻辑）。</p>
     *
     * <p>YAML 示例：{@code plugin.redis.server.max-memory: 128M}。</p>
     */
    private String maxMemory = "128M";

    /**
     * 最大堆内存（预留）。
     *
     * <p>含义：预留的 Redis 最大堆内存上限（如 {@code 100M}）；当前无代码消费该字段。</p>
     *
     * <p>默认值：{@code "100M"}（无为空回落逻辑）。</p>
     *
     * <p>YAML 示例：{@code plugin.redis.server.max-heap: 100M}。</p>
     */
    private String maxHeap = "100M";
}
