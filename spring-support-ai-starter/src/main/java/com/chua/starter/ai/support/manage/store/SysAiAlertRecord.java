package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * AI 告警规则触发留痕（追加写）。
 *
 * <p>告警规则（sys_ai_alert）被 {@code AlertEvaluateScheduler} 定时评估命中、
 * 且至少一个渠道（站内信 / 邮件 / Webhook）投递成功后，向本表追加一行：
 * 哪条规则、哪一天、什么指标超了阈值、实际值多少、推给了谁、走了哪些渠道。
 * {@code (alert_id, stat_date)} 唯一键同时充当「同规则同日只推一次」的去重闸；
 * 投递失败不写本表——留痕只记真实发出去的推送，失败由下轮评估重试。</p>
 *
 * @since 4.0.0.62
 * @author CH
 */
@Data
@TableName("sys_ai_alert_record")
public class SysAiAlertRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "sys_ai_alert_record_id", type = IdType.AUTO)
    private Long id;

    /**
     * 告警规则 ID（sys_ai_alert.sys_ai_alert_id）
     */
    @TableField("sys_ai_alert_record_alert_id")
    private Long alertId;

    /**
     * 规则名称快照
     */
    @TableField("sys_ai_alert_record_alert_name")
    private String alertName;

    /**
     * 统计日期（同规则同日唯一）
     */
    @TableField("sys_ai_alert_record_stat_date")
    private LocalDate statDate;

    /**
     * 触发指标（daily_cost / daily_tokens / daily_requests / error_rate）
     */
    @TableField("sys_ai_alert_record_metric")
    private String metric;

    /**
     * 触发时阈值快照
     */
    @TableField("sys_ai_alert_record_threshold")
    private Long threshold;

    /**
     * 触发时实际值
     */
    @TableField("sys_ai_alert_record_actual")
    private Double actual;

    /**
     * 告警范围（全局 / appKey 名称）
     */
    @TableField("sys_ai_alert_record_scope")
    private String scope;

    /**
     * 接收人用户 ID（逗号分隔，空=全局广播）
     */
    @TableField("sys_ai_alert_record_receivers")
    private String receivers;

    /**
     * 送达渠道（inbox=站内信 / email=邮件 / webhook=机器人 Webhook，逗号分隔）
     */
    @TableField("sys_ai_alert_record_channels")
    private String channels;

    /**
     * 消息标题（与推送进消息中心的标题一致）
     */
    @TableField("sys_ai_alert_record_title")
    private String title;

    /**
     * 消息摘要（与推送进消息中心的内容一致）
     */
    @TableField("sys_ai_alert_record_content")
    private String content;

    /**
     * 推送时间（毫秒）
     */
    @TableField("sys_ai_alert_record_pushed_at")
    private Long pushedAt;

    /**
     * 创建时间（毫秒）
     */
    @TableField("create_time")
    private Long createTime;
}
