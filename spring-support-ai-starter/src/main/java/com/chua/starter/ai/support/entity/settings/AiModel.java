package com.chua.starter.ai.support.entity.settings;

import com.baomidou.mybatisplus.annotation.*;
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
@TableName("ai_model")
@Schema(name = "AI 模型定义")
public class AiModel implements Serializable {

    @TableId(type = IdType.AUTO)
    @Schema(description = "模型主键")
    private Long modelId;

    @TableField("model_name")
    @Schema(description = "模型显示名称")
    private String modelName;

    @TableField("model_code")
    @Schema(description = "模型标识（gpt-4o, deepseek-chat）")
    private String modelCode;

    @TableField("model_provider_id")
    @Schema(description = "关联供应商 ID")
    private Long modelProviderId;

    @TableField("model_capabilities")
    @Schema(description = "能力标签（逗号分隔: chat,vision,think,search）")
    private String modelCapabilities;

    @TableField("model_description")
    @Schema(description = "模型描述")
    private String modelDescription;

    @TableField("model_enabled")
    @Schema(description = "是否启用")
    private Boolean modelEnabled;

    @TableField("model_sort")
    @Schema(description = "排序值")
    private Integer modelSort;

    @TableField(value = "model_create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime modelCreateTime;

    @TableField(value = "model_update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime modelUpdateTime;

    @TableField("model_deleted")
    @TableLogic
    @Schema(description = "逻辑删除标记")
    private Integer modelDeleted;
}