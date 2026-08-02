package com.chua.starter.ai.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 全局设置 DTO，字段命名与前端面板对齐。
 *
 * <p>前端提交的字段为 {@code chatClientEnabled}、{@code usageReportInterval} 等，
 * 与实体 {@code settingXxx} 前缀不同，故通过 DTO 隔离。</p>
 *
 * @author CH
 * @since 2026-08-03
 */
@Data
@ApiModel("全局设置 DTO")
public class AiGlobalSettingDTO implements Serializable {

    /**
     * 是否启用 ChatClient 聚合
     */
    @ApiModelProperty("是否启用 ChatClient 聚合")
    private Boolean chatClientEnabled;

    /**
     * 是否启用用量分析
     */
    @ApiModelProperty("是否启用用量分析")
    private Boolean enableUsageAnalysis;

    /**
     * 用量上报间隔（秒）
     */
    @ApiModelProperty("用量上报间隔(秒)")
    private Integer usageReportInterval;

    /**
     * 默认模型
     */
    @ApiModelProperty("默认模型")
    private String defaultModel;

    /**
     * 默认温度
     */
    @ApiModelProperty("默认温度")
    private BigDecimal temperature;

    /**
     * 默认最大 tokens
     */
    @ApiModelProperty("默认最大 tokens")
    private Integer maxTokens;

    /**
     * 默认请求超时（秒）
     */
    @ApiModelProperty("默认请求超时(秒)")
    private Integer timeout;

    /**
     * 是否启用缓存
     */
    @ApiModelProperty("是否启用缓存")
    private Boolean enableCache;

    /**
     * 是否流式输出
     */
    @ApiModelProperty("是否流式输出")
    private Boolean stream;
}
