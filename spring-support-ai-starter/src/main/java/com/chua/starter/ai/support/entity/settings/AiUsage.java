package com.chua.starter.ai.support.entity.settings;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用量统计表，按模型与 AppKey 维度记录调用次数与 token 消耗。
 *
 * @author CH
 * @since 2026-08-03
 */
@Data
@TableName("ai_usage")
@Schema(name = "用量统计")
public class AiUsage implements Serializable {

    /**
     * 用量主键 ID
     */
    @TableId(type = IdType.AUTO)
    @Schema(description = "用量主键 ID")
    private Long usageId;

    /**
     * 模型名称
     */
    @TableField("usage_model")
    @Schema(description = "模型名称")
    private String usageModel;

    /**
     * 关联的 AppKey ID
     */
    @TableField("usage_app_key_id")
    @Schema(description = "关联的 AppKey ID")
    private Long usageAppKeyId;

    /**
     * 请求次数
     */
    @TableField("usage_request_count")
    @Schema(description = "请求次数")
    private Long usageRequestCount;

    /**
     * 提示 tokens
     */
    @TableField("usage_prompt_tokens")
    @Schema(description = "提示 tokens")
    private Long usagePromptTokens;

    /**
     * 生成 tokens
     */
    @TableField("usage_completion_tokens")
    @Schema(description = "生成 tokens")
    private Long usageCompletionTokens;

    /**
     * 总 tokens
     */
    @TableField("usage_total_tokens")
    @Schema(description = "总 tokens")
    private Long usageTotalTokens;

    /**
     * 统计时间戳
     */
    @TableField("usage_timestamp")
    @Schema(description = "统计时间戳")
    private LocalDateTime usageTimestamp;
}
