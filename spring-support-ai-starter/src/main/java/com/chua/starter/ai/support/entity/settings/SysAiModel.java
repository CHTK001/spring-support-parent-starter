package com.chua.starter.ai.support.entity.settings;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableLogic;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 模型定义表，存储模型标识、关联供应商与能力标签。
 *
 * @author CH
 */
@Data
@TableName("sys_ai_model")
@Schema(name = "AI 模型定义")
public class SysAiModel implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 模型主键
     */
    @TableId(value = "sys_ai_model_id", type = IdType.AUTO)
    @Schema(description = "模型主键")
    /** 模型ID */
    private Long modelId;

    /**
     * 模型显示名称
     */
    @TableField("sys_ai_model_name")
    @Schema(description = "模型显示名称")
    /** 模型名称 */
    private String modelName;

    /**
     * 模型标识（gpt-4o, deepseek-chat）
     */
    @TableField("sys_ai_model_code")
    @Schema(description = "模型标识（gpt-4o, deepseek-chat）")
    /** 模型代码 */
    private String modelCode;

    /**
     * 关联供应商 ID
     */
    @TableField("sys_ai_model_provider_id")
    @Schema(description = "关联供应商 ID")
    /** 模型提供者ID */
    private Long modelProviderId;

    /**
     * 能力标签（逗号分隔: chat,vision,think,search）
     */
    @TableField("sys_ai_model_capabilities")
    @Schema(description = "能力标签（逗号分隔: chat,vision,think,search）")
    /** 模型capabilities */
    private String modelCapabilities;

    /**
     * 模型描述
     */
    @TableField("sys_ai_model_description")
    @Schema(description = "模型描述")
    /** 模型描述 */
    private String modelDescription;

    /**
     * 是否启用
     */
    @TableField("sys_ai_model_enabled")
    @Schema(description = "是否启用")
    /** 模型是否启用 */
    private Boolean modelEnabled;

    /**
     * 排序值
     */
    @TableField("sys_ai_model_sort")
    @Schema(description = "排序值")
    /** 模型sort */
    private Integer modelSort;

    /**
     * 创建时间
     */
    @TableField(value = "sys_ai_model_create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    /** 模型create时间 */
    private LocalDateTime modelCreateTime;

    /**
     * 更新时间
     */
    @TableField(value = "sys_ai_model_update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    /** 模型update时间 */
    private LocalDateTime modelUpdateTime;

    /**
     * 逻辑删除标记
     */
    @TableField("sys_ai_model_deleted")
    @TableLogic
    @Schema(description = "逻辑删除标记")
    /** 模型删除标记 */
    private Integer modelDeleted;

    /**
     * 输入单价（每 1k tokens）
     */
    @TableField("sys_ai_model_input_price")
    @Schema(description = "输入单价（每 1k tokens）")
    /** 模型输入price */
    private java.math.BigDecimal modelInputPrice;

    /**
     * 输出单价（每 1k tokens）
     */
    @TableField("sys_ai_model_output_price")
    @Schema(description = "输出单价（每 1k tokens）")
    /** 模型输出price */
    private java.math.BigDecimal modelOutputPrice;

    /**
     * 缓存命中单价（每 1k tokens）
     */
    @TableField("sys_ai_model_cache_price")
    @Schema(description = "缓存命中单价（每 1k tokens）")
    /** 模型缓存price */
    private java.math.BigDecimal modelCachePrice;
}
