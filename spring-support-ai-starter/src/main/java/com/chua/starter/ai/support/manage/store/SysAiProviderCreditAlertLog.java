package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 供应商余额告警推送留痕（追加写）。
 *
 * <p>余额低量告警 / 恢复通知经消息中心 {@code broadcast} 投递成功后，向本表
 * 追加一行：谁（供应商）、什么时候、以什么余额/阈值、推给了哪些接收人、
 * 推了什么内容。回答「告警到底发没发过、上次是什么时候」，供余额面板的
 * 告警设置浮层展示「上次推送时间」，也便于事后核对。
 * 投递失败不写本表——留痕只记真实发出去的推送。</p>
 *
 * @since 4.0.0.59
 * @author CH
 */
@Data
@TableName("sys_ai_provider_credit_alert_log")
public class SysAiProviderCreditAlertLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 推送类型：低量告警
     */
    public static final String TYPE_WARN = "WARN";

    /**
     * 推送类型：恢复通知
     */
    public static final String TYPE_RECOVERY = "RECOVERY";

    /**
     * 主键
     */
    @TableId(value = "sys_ai_provider_credit_alert_log_id", type = IdType.AUTO)
    private Long id;

    /**
     * 供应商 ID
     */
    @TableField("sys_ai_provider_credit_alert_log_provider_id")
    private String providerId;

    /**
     * 推送类型（{@link #TYPE_WARN} / {@link #TYPE_RECOVERY}）
     */
    @TableField("sys_ai_provider_credit_alert_log_type")
    private String type;

    /**
     * 推送时的剩余余额
     */
    @TableField("sys_ai_provider_credit_alert_log_remain")
    private Double remain;

    /**
     * 推送时的告警阈值
     */
    @TableField("sys_ai_provider_credit_alert_log_threshold")
    private Double threshold;

    /**
     * 接收人用户 ID（逗号分隔）
     */
    @TableField("sys_ai_provider_credit_alert_log_receivers")
    private String receivers;

    /**
     * 送达渠道（逗号分隔：inbox=站内信 / email=邮件 / webhook=机器人 Webhook）
     */
    @TableField("sys_ai_provider_credit_alert_log_channels")
    private String channels;

    /**
     * 消息标题（与推送进消息中心的标题一致）
     */
    @TableField("sys_ai_provider_credit_alert_log_title")
    private String title;

    /**
     * 消息摘要（与推送进消息中心的内容一致）
     */
    @TableField("sys_ai_provider_credit_alert_log_content")
    private String content;

    /**
     * 推送时间（毫秒）
     */
    @TableField("sys_ai_provider_credit_alert_log_pushed_at")
    private Long pushedAt;

    /**
     * 创建时间（毫秒）
     */
    @TableField("create_time")
    private Long createTime;
}
