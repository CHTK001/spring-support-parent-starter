package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * AI 模型定义 DTO，字段命名与前端面板对齐。
 *
 * @author CH
 */
@Data
@Schema(name = "AI 模型定义 DTO")
public class AiModelDTO implements Serializable {

    /**
     * 模型主键
     */
    @Schema(description = "模型主键")
    private Long id;

    /**
     * 模型显示名称
     */
    @Schema(description = "模型显示名称")
    private String name;

    /**
     * 模型标识（gpt-4o, deepseek-chat）
     */
    @Schema(description = "模型标识（gpt-4o, deepseek-chat）")
    private String code;

    /**
     * 关联供应商 ID
     */
    @Schema(description = "关联供应商 ID")
    private Long providerId;

    /**
     * 供应商名称（只读，列表时展示）
     */
    @Schema(description = "供应商名称（只读，列表时展示）")
    private String providerName;

    /**
     * 能力标签列表
     */
    @Schema(description = "能力标签列表")
    private List<String> capabilities;

    /**
     * 模型描述
     */
    @Schema(description = "模型描述")
    private String description;

    /**
     * 是否启用
     */
    @Schema(description = "是否启用")
    private Boolean enabled;

    /**
     * 排序值
     */
    @Schema(description = "排序值")
    private Integer sort;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private String createdAt;

    /**
     * 输入单价（每 1k tokens）
     */
    @Schema(description = "输入单价（每 1k tokens）")
    private java.math.BigDecimal inputPrice;

    /**
     * 输出单价（每 1k tokens）
     */
    @Schema(description = "输出单价（每 1k tokens）")
    private java.math.BigDecimal outputPrice;

    /**
     * 缓存命中单价（每 1k tokens）
     */
    @Schema(description = "缓存命中单价（每 1k tokens）")
    private java.math.BigDecimal cachePrice;
}
