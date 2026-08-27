package com.chua.starter.ai.support.entity;

import com.chua.starter.mybatis.pojo.SysBase;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * AI 用量记录表。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_ai_usage")
public class SysAiUsage extends SysBase {

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long sysAiUsageId;

    /**
     * 分组：chat / image / video / colorize / vision / rag
     */
    private String sysAiUsageGroupName;

    /**
     * 服务商：openai / zhipu / baidu 等
     */
    private String sysAiUsageProvider;

    /**
     * 模型名称
     */
    private String sysAiUsageModel;

    /**
     * 调用类型：sync / async
     */
    private String sysAiUsageCallType;

    /**
     * 输入 Token 数
     */
    private Integer sysAiUsagePromptTokens;

    /**
     * 输出 Token 数
     */
    private Integer sysAiUsageCompletionTokens;

    /**
     * 总 Token 数
     */
    private Integer sysAiUsageTotalTokens;

    /**
     * 输入费用（单位：元）
     */
    private Double sysAiUsageInputCost;

    /**
     * 输出费用（单位：元）
     */
    private Double sysAiUsageOutputCost;

    /**
     * 总费用（单位：元）
     */
    private Double sysAiUsageTotalCost;

    /**
     * 请求 ID（关联调用日志）
     */
    private String sysAiUsageRequestId;

    /**
     * 用户标识（可选）
     */
    private String sysAiUsageUserId;

    /**
     * 调用时间
     */
    private LocalDateTime sysAiUsageCallTime;
}
