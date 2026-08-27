package com.chua.starter.ai.support.entity;

import com.chua.starter.mybatis.pojo.SysBase;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * AI 用量日汇总表。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_ai_usage_daily")
public class SysAiUsageDaily extends SysBase {

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long sysAiUsageDailyId;

    /**
     * 统计日期
     */
    private LocalDate sysAiUsageDailyStatDate;

    /**
     * 分组
     */
    private String sysAiUsageDailyGroupName;

    /**
     * 服务商
     */
    private String sysAiUsageDailyProvider;

    /**
     * 模型
     */
    private String sysAiUsageDailyModel;

    /**
     * 总调用次数
     */
    private Integer sysAiUsageDailyTotalCalls;

    /**
     * 总输入 Token
     */
    private Integer sysAiUsageDailyTotalPromptTokens;

    /**
     * 总输出 Token
     */
    private Integer sysAiUsageDailyTotalCompletionTokens;

    /**
     * 总 Token
     */
    private Integer sysAiUsageDailyTotalTokens;

    /**
     * 总费用（元）
     */
    private Double sysAiUsageDailyTotalCost;
}
