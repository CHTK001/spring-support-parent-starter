package com.chua.starter.ai.support.entity;

import com.chua.starter.mybatis.pojo.SysBase;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
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
     * 统计 标识
     */
    @TableId(type = IdType.AUTO, value = "sys_ai_usage_daily_id")
    private Long id; // 标识

    /**
     * 统计日期
     */
    @TableField("sys_ai_usage_daily_stat_date")
    private LocalDate statDate; // stat日期

    /**
     * 分组
     */
    @TableField("sys_ai_usage_daily_group_name")
    private String groupName; // 群体名称

    /**
     * 服务商
     */
    @TableField("sys_ai_usage_daily_provider")
    private String provider; // 提供者

    /**
     * 模型
     */
    @TableField("sys_ai_usage_daily_model")
    private String model; // 模型

    /**
     * 归属账号。
     *
     * <p>该列在表里一直存在，但实体里一直缺这个字段，导致日汇总写入时该列恒为 NULL：
     * 表里存的是一份「不分账号」的全局汇总，与明细表口径不一致。补上后按账号分组写入，
     * 才与明细表对齐。</p>
     */
    @TableField("sys_ai_usage_daily_user_id")
    private String userId; // 归属账号

    /**
     * 总调用次数
     */
    @TableField("sys_ai_usage_daily_total_calls")
    private Long totalCalls; // totalcalls

    /**
     * 总输入 令牌
     */
    @TableField("sys_ai_usage_daily_total_prompt_tokens")
    private Long totalPromptTokens; // total提示符令牌

    /**
     * 总输出 令牌
     */
    @TableField("sys_ai_usage_daily_total_completion_tokens")
    private Long totalCompletionTokens; // total完成令牌

    /**
     * 总 令牌
     */
    @TableField("sys_ai_usage_daily_total_tokens")
    private Long totalTokens; // total令牌

    /**
     * 总费用（元）
     */
    @TableField("sys_ai_usage_daily_total_cost")
    private Double totalCost; // totalcost

    /**
     * 缓存读取 令牌 合计
     */
    @TableField("sys_ai_usage_daily_total_cached_tokens")
    private Long totalCachedTokens; // total缓存令牌

    /**
     * 总耗时（毫秒）合计，配合调用次数计算平均耗时
     */
    @TableField("sys_ai_usage_daily_total_latency")
    private Long totalLatency; // total延迟

    /**
     * 总首字延迟（毫秒）合计，配合调用次数计算平均首字延迟
     */
    @TableField("sys_ai_usage_daily_total_first_token_latency")
    private Long totalFirstTokenLatency; // total第一个令牌延迟

    /**
     * 标准费用（官方价格计算，未折扣）
     */
    @TableField("sys_ai_usage_daily_standard_cost")
    private Double standardCost; // 标准cost
}
