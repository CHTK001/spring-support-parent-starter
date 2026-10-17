package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 用量统计 DTO，字段命名与前端面板对齐。
 *
 * @param group            分组名称
 * @param provider         服务商
 * @param model            模型名称
 * @param cost             总费用（元）
 * @param requests         请求次数
 * @param promptTokens     提示 tokens
 * @param completionTokens 生成 tokens
 * @param totalTokens      总 tokens
 * @param timestamp        统计时间戳
 * @since 2026-08-03
 * @author CH
 */
@Schema(name = "用量统计 DTO")
public record AiUsageDTO(
        @Schema(description = "分组名称") String group,
        @Schema(description = "服务商") String provider,
        @Schema(description = "模型名称") String model,
        @Schema(description = "总费用") Double cost,
        @Schema(description = "请求次数") Long requests,
        @Schema(description = "提示 tokens") Long promptTokens,
        @Schema(description = "生成 tokens") Long completionTokens,
        @Schema(description = "总 tokens") Long totalTokens,
        @Schema(description = "统计时间戳") String timestamp) implements Serializable {

    private static final long serialVersionUID = 1L;
}
