package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 用量统计 DTO，字段命名与前端面板对齐。
 *
 * @author CH
 * @since 2026-08-03
 */
@Data
@Schema(name = "用量统计 DTO")
public class AiUsageDTO implements Serializable {

    /**
     * 模型名称
     */
    @Schema(description = "模型名称")
    private String model;

    /**
     * 请求次数
     */
    @Schema(description = "请求次数")
    private Long requests;

    /**
     * 提示 tokens
     */
    @Schema(description = "提示 tokens")
    private Long promptTokens;

    /**
     * 生成 tokens
     */
    @Schema(description = "生成 tokens")
    private Long completionTokens;

    /**
     * 总 tokens
     */
    @Schema(description = "总 tokens")
    private Long totalTokens;

    /**
     * 统计时间戳
     */
    @Schema(description = "统计时间戳")
    private String timestamp;
}
