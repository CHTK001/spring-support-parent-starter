package com.chua.starter.account.channel.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 渠道健康探针历史（追加式时序行）。
 *
 * <p><b>归属</b>：渠道健康数据随渠道管理整体归属账号模块。本表是
 * {@link ChannelHealth}（最新态，一连接一行 upsert）的时序副本：每次
 * {@code AccountChannelHealthService#save} 落最新态时同事务追加一行，
 * 供渠道页「健康」页签画延迟趋势与状态时间线。</p>
 *
 * <p>行只增不改，按保留期（默认 7 天）由定时任务清理；不要在本表上做
 * 状态机判定——何时算失败、连续几次下线，属于 AI 侧探针业务。</p>
 *
 * @since 4.0.0.66
 * @author CH
 */
@Data
@TableName(value = "sys_account_channel_health_log", autoResultMap = true)
public class ChannelHealthLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 历史行标识（插入时由 MyBatis-Plus 自动生成）
     */
    @TableId(value = "sys_account_channel_health_log_id", type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 渠道标识（provider 行 id）
     */
    @TableField("provider_id")
    private String providerId;

    /**
     * 连接标识
     */
    @TableField("connection_id")
    private String connectionId;

    /**
     * 渠道键（平台）
     */
    @TableField("channel")
    private String channel;

    /**
     * 本次探针结论（UNKNOWN/ONLINE/WARNING/DOWN/RATE_LIMITED）
     */
    @TableField("state")
    private String state = "UNKNOWN";

    /**
     * 本次探针延迟（毫秒）
     */
    @TableField("latency_ms")
    private Integer latencyMs;

    /**
     * 本次探针 HTTP 状态码
     */
    @TableField("http_status")
    private Integer httpStatus;

    /**
     * 本次探针错误摘要（截断）
     */
    @TableField("error")
    private String error;

    /**
     * 记录时间（毫秒）
     */
    @TableField("created_at")
    private Long createdAt;
}
