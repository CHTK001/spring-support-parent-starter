package com.chua.starter.ai.support.entity.settings;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 告警规则表，针对 AppKey 用量/费用/异常的阈值告警。
 *
 * @since 2026-08-03
 * @author CH
 */
@Data
@TableName("sys_ai_alert")
@Schema(name = "告警规则")
public class AiAlert implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 告警规则 ID
     */
    /**
     * 告警规则 ID
     */
    @TableId(value = "sys_ai_alert_id", type = IdType.AUTO)
    @Schema(description = "告警规则 ID")
    /** AlertID */
    private Long alertId;

    /**
     * 告警规则名称
     */
    /**
     * 告警规则名称
     */
    @TableField("sys_ai_alert_name")
    @Schema(description = "告警规则名称")
    /** Alert名称 */
    private String alertName;

    /**
     * 关联 AppKey ID（0 表示全局）
     */
    /**
     * 关联 AppKey ID
     */
    @TableField("sys_ai_alert_app_key_id")
    @Schema(description = "关联 AppKey ID")
    /** AlertAPP密钥ID */
    private Long alertAppKeyId;

    /**
     * 告警条件（表达式）
     */
    /**
     * 告警条件（表达式）
     */
    @TableField("sys_ai_alert_condition")
    @Schema(description = "告警条件（表达式）")
    /** Alert条件 */
    private String alertCondition;

    /**
     * 告警阈值
     */
    /**
     * 告警阈值
     */
    @TableField("sys_ai_alert_threshold")
    @Schema(description = "告警阈值")
    /** Alert阈值 */
    private Long alertThreshold;

    /**
     * 通知渠道（JSON 数组字符串）
     */
    /**
     * 通知渠道(JSON)
     */
    @TableField("sys_ai_alert_channels")
    @Schema(description = "通知渠道(JSON)")
    /** Alertchannels */
    private String alertChannels;

    /**
     * 是否启用
     */
    /**
     * 是否启用
     */
    @TableField("sys_ai_alert_enabled")
    @Schema(description = "是否启用")
    /** Alert是否启用 */
    private Boolean alertEnabled;

    /**
     * 创建时间
     */
    /**
     * 创建时间
     */
    @TableField(value = "sys_ai_alert_create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    /** Alertcreate时间 */
    private LocalDateTime alertCreateTime;

    /**
     * 更新时间
     */
    /**
     * 更新时间
     */
    @TableField(value = "sys_ai_alert_update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    /** Alertupdate时间 */
    private LocalDateTime alertUpdateTime;

    /**
     * 逻辑删除标记
     */
    /**
     * 逻辑删除标记
     */
    @TableField("sys_ai_alert_deleted")
    @TableLogic
    @Schema(description = "逻辑删除标记")
    /** Alert删除标记 */
    private Integer alertDeleted;
}