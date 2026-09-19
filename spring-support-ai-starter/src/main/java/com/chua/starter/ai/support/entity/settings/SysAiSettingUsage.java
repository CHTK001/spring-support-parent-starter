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
 * 用量统计实体——读取 {@code sys_ai_usage} 明细表，按模型维度查询调用次数与 令牌 消耗。
 *
 * @since 2026-08-03
 * @author CH
 */
@Data
@TableName("sys_ai_usage")
@Schema(name = "用量统计")
public class SysAiSettingUsage implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * 用量主键 标识
     */
    /**
     * 用量主键 标识
     */
    @TableId(value = "sys_ai_usage_id", type = IdType.AUTO)
    @Schema(description = "用量主键 ID")
    /** usageid */
    private Long usageId;

    /**
    * 模型名称
    */
    /**
     * 模型名称
     */
    @TableField("sys_ai_usage_model")
    @Schema(description = "模型名称")
    /** Usage模型 */
    private String usageModel;

    /**
    * 请求次数（无独立列，统计时按明细行数计算）
    */
    /**
     * 请求次数
     */
    @TableField(exist = false)
    @Schema(description = "请求次数")
    /** Usage请求数量 */
    private Long usageRequestCount;

    /**
    * 提示 令牌
    */
    /**
     * 提示 令牌
     */
    @TableField("sys_ai_usage_prompt_tokens")
    @Schema(description = "提示 tokens")
    /** Usageprompttokens */
    private Long usagePromptTokens;

    /**
    * 生成 令牌
    */
    /**
     * 生成 令牌
     */
    @TableField("sys_ai_usage_completion_tokens")
    @Schema(description = "生成 tokens")
    /** Usagecompletiontokens */
    private Long usageCompletionTokens;

    /**
    * 总 令牌
    */
    /**
     * 总 令牌
     */
    @TableField("sys_ai_usage_total_tokens")
    @Schema(description = "总 tokens")
    /** Usage总数令牌 */
    private Long usageTotalTokens;

    /**
    * 统计时间戳
    */
    /**
     * 统计时间戳
     */
    @TableField("sys_ai_usage_call_time")
    @Schema(description = "统计时间戳")
    /** Usage时间戳 */
    private LocalDateTime usageTimestamp;
}
