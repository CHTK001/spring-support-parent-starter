package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 渠道健康探针记录。
 *
 * <p>对标 Sub2API Channel Monitor：以「提供者 + 连接」为粒度，对每个渠道发轻量探针
 * （OAuth 渠道由 {@link com.chua.starter.ai.support.spi.AccountChannelAuthManager} 探活，
 * API Key 渠道走 HTTP GET），回写状态机给路由与安全层消费：</p>
 * <ul>
 *   <li>{@code UNKNOWN} — 从未探测或首次探测前</li>
 *   <li>{@code ONLINE} — 探测成功</li>
 *   <li>{@code WARNING} — 探测慢（延迟超阈值）或间歇失败</li>
 *   <li>{@code DOWN} — 连续失败达到阈值，路由层应剔除</li>
 *   <li>{@code RATE_LIMITED} — 触达上游限流（429），由计费层/重试层处理</li>
 * </ul>
 *
 * @since 4.0.0.54
 * @author CH
 */
@Data
@TableName(value = "sys_ai_channel_health", autoResultMap = true)
public class ChannelHealth implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 健康记录标识
     */
    @TableId(value = "sys_ai_channel_health_id", type = IdType.INPUT)
    private String id;

    /**
     * 提供商标识
     */
    @TableField("provider_id")
    private String providerId;

    /**
     * 连接标识
     */
    @TableField("connection_id")
    private String connectionId;

    /**
     * 渠道标识（平台）
     */
    @TableField("channel")
    private String channel;

    /**
     * 渠道展示名
     */
    @TableField("label")
    private String label;

    /**
     * 状态（UNKNOWN/ONLINE/WARNING/DOWN/RATE_LIMITED）
     */
    @TableField("state")
    private String state = "UNKNOWN";

    /**
     * 人工熔断状态（AUTO 跟随探针 / OPEN 强制熔断 / CLOSED 强制放行）
     */
    @TableField("circuit_state")
    private String circuitState = "AUTO";

    /**
     * 人工熔断/放行的原因说明
     */
    @TableField("circuit_reason")
    private String circuitReason;

    /**
     * 人工熔断状态变更时间（毫秒）
     */
    @TableField("circuit_updated_at")
    private Long circuitUpdatedAt;

    /**
     * 累计失败次数
     */
    @TableField("failures")
    private Long failures = 0L;

    /**
     * 累计成功次数
     */
    @TableField("successes")
    private Long successes = 0L;

    /**
     * 连续失败次数
     */
    @TableField("consecutive_failures")
    private Integer consecutiveFailures = 0;

    /**
     * 连续成功次数
     */
    @TableField("consecutive_successes")
    private Integer consecutiveSuccesses = 0;

    /**
     * 最近探针时间（毫秒）
     */
    @TableField("last_probe_at")
    private Long lastProbeAt;

    /**
     * 最近探针 HTTP 状态码
     */
    @TableField("last_probe_status")
    private Integer lastProbeStatus;

    /**
     * 最近探针延迟（毫秒）
     */
    @TableField("last_probe_latency")
    private Integer lastProbeLatency;

    /**
     * 最近探针错误信息
     */
    @TableField("last_probe_error")
    private String lastProbeError;

    /**
     * 平均延迟（毫秒）
     */
    @TableField("average_latency")
    private Integer averageLatency;

    /**
     * 计费/熔断口径累计失败次数
     */
    @TableField("total_failures")
    private Long totalFailures = 0L;

    /**
     * 探针间隔秒（0=走全局设置）
     */
    @TableField("interval_seconds")
    private Integer intervalSeconds = 0;

    /**
     * 是否启用探针
     */
    @TableField("enabled")
    private Integer enabled = 1;

    /**
     * 创建时间（毫秒）
     */
    @TableField("created_at")
    private Long createdAt;

    /**
     * 更新时间（毫秒）
     */
    @TableField("updated_at")
    private Long updatedAt;
}
