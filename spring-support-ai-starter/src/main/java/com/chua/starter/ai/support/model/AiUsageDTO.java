package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 用量统计 DTO，字段命名与前端面板对齐。
 *
 * @since 2026-08-03
 * @author CH
 */
@Data
@Schema(name = "用量统计 DTO")
public class AiUsageDTO implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * 模型名称
     */
    @Schema(description = "模型名称")
    /** 模型 */
    private String model;

    /**
     * 请求次数
     */
    @Schema(description = "请求次数")
    /** Requests */
    private Long requests;

    /**
     * 提示 令牌
     */
    @Schema(description = "提示 tokens")
    /** Prompttokens */
    private Long promptTokens;

    /**
     * 生成 令牌
     */
    @Schema(description = "生成 tokens")
    /** Completiontokens */
    private Long completionTokens;

    /**
     * 总 令牌
     */
    @Schema(description = "总 tokens")
    /** 总数令牌 */
    private Long totalTokens;

    /**
     * 统计时间戳
     */
    @Schema(description = "统计时间戳")
    /** 时间戳 */
    private String timestamp;
}
