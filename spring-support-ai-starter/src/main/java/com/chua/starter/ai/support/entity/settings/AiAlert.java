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
 * @author CH
 * @since 2026-08-03
 */
@Data
@TableName("sys_ai_alert")
@Schema(name = "告警规则")
public class AiAlert implements Serializable {

    /**
     * 告警规则 ID
     */
    @TableId(value = "sys_ai_alert_id", type = IdType.AUTO)
    @Schema(description = "告警规则 ID")
    private Long alertId;

    /**
     * 告警规则名称
     */
    @TableField("sys_ai_alert_name")
    @Schema(description = "告警规则名称")
    private String alertName;

    /**
     * 关联 AppKey ID（0 表示全局）
     */
    @TableField("sys_ai_alert_app_key_id")
    @Schema(description = "关联 AppKey ID")
    private Long alertAppKeyId;

    /**
     * 告警条件（表达式）
     */
    @TableField("sys_ai_alert_condition")
    @Schema(description = "告警条件（表达式）")
    private String alertCondition;

    /**
     * 告警阈值
     */
    @TableField("sys_ai_alert_threshold")
    @Schema(description = "告警阈值")
    private Long alertThreshold;

    /**
     * 通知渠道（JSON 数组字符串）
     */
    @TableField("sys_ai_alert_channels")
    @Schema(description = "通知渠道(JSON)")
    private String alertChannels;

    /**
     * 是否启用
     */
    @TableField("sys_ai_alert_enabled")
    @Schema(description = "是否启用")
    private Boolean alertEnabled;

    /**
     * 创建时间
     */
    @TableField(value = "sys_ai_alert_create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime alertCreateTime;

    /**
     * 更新时间
     */
    @TableField(value = "sys_ai_alert_update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime alertUpdateTime;

    /**
     * 逻辑删除标记
     */
    @TableField("sys_ai_alert_deleted")
    @TableLogic
    @Schema(description = "逻辑删除标记")
    private Integer alertDeleted;
}
