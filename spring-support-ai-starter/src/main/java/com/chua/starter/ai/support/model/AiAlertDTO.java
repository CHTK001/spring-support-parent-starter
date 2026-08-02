package com.chua.starter.ai.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 告警规则 DTO，字段命名与前端面板对齐。
 *
 * @author CH
 * @since 2026-08-03
 */
@Data
@ApiModel("告警规则 DTO")
public class AiAlertDTO implements Serializable {

    /**
     * 告警主键 ID
     */
    @ApiModelProperty("告警主键 ID")
    private Long id;

    /**
     * 告警规则名称
     */
    @ApiModelProperty("告警规则名称")
    private String name;

    /**
     * 关联 AppKey ID
     */
    @ApiModelProperty("关联 AppKey ID")
    private Long appKeyId;

    /**
     * 触发条件表达式
     */
    @ApiModelProperty("触发条件表达式")
    private String condition;

    /**
     * 告警阈值
     */
    @ApiModelProperty("告警阈值")
    private Long threshold;

    /**
     * 通知渠道（JSON 数组字符串）
     */
    @ApiModelProperty("通知渠道(JSON)")
    private String channels;

    /**
     * 是否启用
     */
    @ApiModelProperty("是否启用")
    private Boolean enabled;

    /**
     * 创建时间
     */
    @ApiModelProperty("创建时间")
    private String createdAt;
}
