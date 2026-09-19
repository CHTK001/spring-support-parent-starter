package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * AI 模型定义 DTO，字段命名与前端面板对齐。
 *
 * @author CH
 * @since 4.0.0
 */
@Data
@Schema(name = "AI 模型定义 DTO")
public class AiModelDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "模型主键")
    /**
     * ID
    */
    private Long id;

    @Schema(description = "模型显示名称")
    /**
     * 名称
    */
    private String name;

    @Schema(description = "模型标识（gpt-4o, deepseek-chat）")
    /**
     * 编码
    */
    private String code;

    @Schema(description = "关联供应商 ID")
    /**
     * 提供者ID
    */
    private Long providerId;

    @Schema(description = "供应商名称（只读，列表时展示）")
    /**
     * 提供者名称
    */
    private String providerName;

    @Schema(description = "能力标签列表")
    /**
     * capabilities
    */
    private List<String> capabilities;

    @Schema(description = "必填管线标签列表")
    /**
     * requiredPipelines
    */
    private List<String> requiredPipelines;

    @Schema(description = "非必填管线标签列表")
    /**
     * optionalPipelines
    */
    private List<String> optionalPipelines;

    @Schema(description = "模型描述")
    /**
     * 描述
    */
    private String description;

    @Schema(description = "是否启用")
    /**
     * enabled
    */
    private Boolean enabled;

    @Schema(description = "排序值")
    /**
     * 排序
    */
    private Integer sort;

    @Schema(description = "创建时间")
    /**
     * createdAt
    */
    private String createdAt;

    @Schema(description = "输入单价（每 1k tokens）")
    /**
     * inputPrice
    */
    private java.math.BigDecimal inputPrice;

    @Schema(description = "输出单价（每 1k tokens）")
    /**
     * outputPrice
    */
    private java.math.BigDecimal outputPrice;

    @Schema(description = "缓存命中单价（每 1k tokens）")
    /**
     * 缓存Price
    */
    private java.math.BigDecimal cachePrice;

    @Schema(description = "模型别名列表（请求时别名自动映射到主模型）")
    /**
     * aliases
    */
    private List<String> aliases;

    @Schema(description = "最近一次连通性测试延迟（毫秒）")
    /**
     * 最后一个Latency
    */
    private Long lastLatency;

    @Schema(description = "最近一次连通性测试时间")
    /**
     * 最后一个测试时间
    */
    private String lastTestTime;
}
