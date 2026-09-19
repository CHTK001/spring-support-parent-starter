package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 告警规则 DTO，字段命名与前端面板对齐。
 *
 * @since 2026-08-03
 * @author CH
 */
@Data
@Schema(name = "告警规则 DTO")
public class AiAlertDTO implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * 告警主键 标识
     */
    @Schema(description = "告警主键 ID")
    /**
     * 标识
    */
    private Long id;

    /**
     * 告警规则名称
     */
    @Schema(description = "告警规则名称")
    /**
     * 名称
    */
    private String name;

    /**
     * 关联 appKey 标识
     */
    @Schema(description = "关联 AppKey ID")
    /**
     * APP密钥标识
    */
    private Long appKeyId;

    /**
     * 触发条件表达式
     */
    @Schema(description = "触发条件表达式")
    /**
     * 条件
    */
    private String condition;

    /**
     * 告警阈值
     */
    @Schema(description = "告警阈值")
    /**
     * 阈值
    */
    private Long threshold;

    /**
     * 通知渠道(JSON)
     */
    @Schema(description = "通知渠道(JSON)")
    /**
     * 通道
    */
    private String channels;

    /**
     * 是否启用
     */
    @Schema(description = "是否启用")
    /**
     * 是否启用
    */
    private Boolean enabled;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    /**
     * 创建时间AT
    */
    private String createdAt;
}
