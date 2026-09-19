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
 * @since 4.0.0
 */
@Data
@TableName("sys_ai_model")
@Schema(name = "AI 模型定义")
public class SysAiModel implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "sys_ai_model_id", type = IdType.AUTO)
    @Schema(description = "模型主键")
    /** 模型ID */
    private Long modelId;

    @TableField("sys_ai_model_name")
    @Schema(description = "模型显示名称")
    /** 模型名称 */
    private String modelName;

    @TableField("sys_ai_model_code")
    @Schema(description = "模型标识（gpt-4o, deepseek-chat）")
    /** 模型编码 */
    private String modelCode;

    @TableField("sys_ai_model_provider_id")
    @Schema(description = "关联供应商 ID")
    /** 模型提供者ID */
    private Long modelProviderId;

    @TableField("sys_ai_model_capabilities")
    @Schema(description = "能力标签（逗号分隔: chat,vision,think,search）")
    /** 模型Capabilities */
    private String modelCapabilities;

    @TableField("sys_ai_model_description")
    @Schema(description = "模型描述")
    /** 模型描述 */
    private String modelDescription;

    @TableField("sys_ai_model_enabled")
    @Schema(description = "是否启用")
    /** 模型Enabled */
    private Boolean modelEnabled;

    @TableField("sys_ai_model_sort")
    @Schema(description = "排序值")
    /** 模型排序 */
    private Integer modelSort;

    @TableField(value = "sys_ai_model_create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    /** 模型创建时间 */
    private LocalDateTime modelCreateTime;

    @TableField(value = "sys_ai_model_update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    /** 模型更新时间 */
    private LocalDateTime modelUpdateTime;

    @TableField("sys_ai_model_deleted")
    @TableLogic
    @Schema(description = "逻辑删除标记")
    /** 模型Deleted */
    private Integer modelDeleted;

    @TableField("sys_ai_model_required_pipelines")
    @Schema(description = "必填管线标签（JSON 数组）")
    /** 模型RequiredPipelines */
    private String modelRequiredPipelines;

    @TableField("sys_ai_model_optional_pipelines")
    @Schema(description = "非必填管线标签（JSON 数组）")
    /** 模型OptionalPipelines */
    private String modelOptionalPipelines;

    @TableField("sys_ai_model_input_price")
    @Schema(description = "输入单价（每 1k tokens）")
    /** 模型InputPrice */
    private java.math.BigDecimal modelInputPrice;

    @TableField("sys_ai_model_output_price")
    @Schema(description = "输出单价（每 1k tokens）")
    /** 模型OutputPrice */
    private java.math.BigDecimal modelOutputPrice;

    @TableField("sys_ai_model_cache_price")
    @Schema(description = "缓存命中单价（每 1k tokens）")
    /** 模型缓存Price */
    private java.math.BigDecimal modelCachePrice;

    @TableField("sys_ai_model_aliases")
    @Schema(description = "模型别名列表（JSON 数组，请求时别名自动映射到主模型）")
    /** 模型Aliases */
    private String modelAliases;

    @TableField("sys_ai_model_last_latency")
    @Schema(description = "最近一次连通性测试延迟（毫秒）")
    /** 模型最后一个Latency */
    private Long modelLastLatency;

    @TableField("sys_ai_model_last_test_time")
    @Schema(description = "最近一次连通性测试时间")
    /** 模型最后一个测试时间 */
    private LocalDateTime modelLastTestTime;
}
