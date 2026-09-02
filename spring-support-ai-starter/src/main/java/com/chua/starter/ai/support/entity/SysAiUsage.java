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

    /** 推理强度 */
    private String sysAiUsageInferenceStrength;
    /** 端点URL */
    private String sysAiUsageEndpoint;
    /** 请求IP */
    private String sysAiUsageIp;
    /** 请求类型: chat/completion/embedding */
    private String sysAiUsageType;
    /** 计费模式: pay_as_you_go/subscription */
    private String sysAiUsageBillingMode;
    /** 缓存读取 Token */
    private Integer sysAiUsageCachedTokens;
    /** 缓存读取成本 */
    private Double sysAiUsageCachedCost;
    /** 输入单价(每1M Token) */
    private Double sysAiUsageInputPricePer1M;
    /** 输出单价(每1M Token) */
    private Double sysAiUsageOutputPricePer1M;
    /** 缓存读取单价(每1M Token) */
    private Double sysAiUsageCacheReadPricePer1M;
    /** 服务档位 */
    private String sysAiUsageServiceTier;
    /** 倍率 */
    private Double sysAiUsageRatio;
    /** 原始费用(未折扣) */
    private Double sysAiUsageOriginalCost;
    /** 首字延迟(ms) */
    private Long sysAiUsageFirstTokenLatency;
    /** 总耗时(ms) */
    private Long sysAiUsageTotalLatency;
}
