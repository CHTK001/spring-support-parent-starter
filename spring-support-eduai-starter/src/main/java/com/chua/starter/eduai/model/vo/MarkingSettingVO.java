package com.chua.starter.eduai.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 阅卷运行时设置。
 *
 * <p>对应系统配置弹窗「AI 阅卷设置」可维护的两组项：</p>
 * <ul>
 *   <li>模型调用重试 —— {@code plugin.eduai.ai.retry.*} 的运行时覆盖；</li>
 *   <li>任意卷识别策略 —— {@code plugin.eduai.marking.extract-strategy} 的运行时覆盖。</li>
 * </ul>
 *
 * <p>落库位置为 {@code edu_app_config} 键值表（见 {@code EduAppConfigService}），
 * 未配置的项回落到 application.yml 的 {@code plugin.eduai.*} 默认值。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@Schema(description = "阅卷运行时设置")
public class MarkingSettingVO {

    /**
     * 模型调用最大重试次数（0 = 不重试）
     */
    @Schema(description = "模型调用最大重试次数（0 = 不重试）")
    private int retryTimes = 3;

    /**
     * 初始退避毫秒
     */
    @Schema(description = "初始退避毫秒")
    private long retryBackoffMillis = 1000L;

    /**
     * 退避倍数（<=1 按 2 处理）
     */
    @Schema(description = "退避倍数（<=1 按 2 处理）")
    private double retryBackoffMultiplier = 2.0D;

    /**
     * 退避上限毫秒（<=0 按 15000 处理）
     */
    @Schema(description = "退避上限毫秒（<=0 按 15000 处理）")
    private long retryMaxBackoffMillis = 15000L;

    /**
     * 除限流外，是否也重试瞬时故障（超时 / 502 / 503 / 连接异常）
     */
    @Schema(description = "除限流外，是否也重试瞬时故障")
    private boolean retryOnTransient = true;

    /**
     * 任意卷识别策略：{@code whole}（整卷，默认）/ {@code region}（按版面区域分块）
     */
    @Schema(description = "任意卷识别策略：whole（整卷，默认）/ region（按版面区域分块）")
    private String extractStrategy = "whole";

    /**
     * 阅卷判分 System Prompt（主观题 LLM 判分指令）。
     *
     * <p>为空时使用内置默认 prompt（资深阅卷老师角色 + JSON 输出约束）；
     * 配置后覆盖默认，用于贴合学科/评分粒度调整。User Prompt 的占位拼接
     * （题型/题干/标准答案/学生作答/满分）保持不变。</p>
     */
    @Schema(description = "阅卷判分 System Prompt（空 = 使用内置默认）")
    private String markingPrompt = "";
}
