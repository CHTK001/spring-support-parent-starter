package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 告警规则 DTO，字段命名与前端面板对齐。
 *
 * @author CH
 * @since 2026-08-03
 */
@Data
@Schema(name = "告警规则 DTO")
public class AiAlertDTO implements Serializable {

    /**
     * 告警主键 ID
     */
    @Schema(description = "告警主键 ID")
    private Long id;

    /**
     * 告警规则名称
     */
    @Schema(description = "告警规则名称")
    private String name;

    /**
     * 关联 AppKey ID
     */
    @Schema(description = "关联 AppKey ID")
    private Long appKeyId;

    /**
     * 触发条件表达式
     */
    @Schema(description = "触发条件表达式")
    private String condition;

    /**
     * 告警阈值
     */
    @Schema(description = "告警阈值")
    private Long threshold;

    /**
     * 通知渠道(JSON)
     */
    @Schema(description = "通知渠道(JSON)")
    private String channels;

    /**
     * 是否启用
     */
    @Schema(description = "是否启用")
    private Boolean enabled;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private String createdAt;
}
