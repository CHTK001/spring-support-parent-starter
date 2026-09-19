package com.chua.starter.ai.support.service;

import com.chua.common.support.ai.AiUsage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.ai.support.entity.SysAiUsage;
import com.chua.starter.ai.support.entity.SysAiUsageDaily;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 用量服务接口
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface SysAiUsageService {

    /**
     * 记录一次调用用量
     *
     * @param groupName 分组
     * @param provider 服务商
     * @param model 模型
     * @param callType 调用类型
     * @param usage 用量信息
     * @param requestId 请求标识
     * @param userId 用户标识
     */
    void record(String groupName, String provider, String model, String callType,
                AiUsage usage, String requestId, String userId);

    /**
     * 查询用量列表
     *
     * @param groupName 分组
     * @param provider 服务商
     * @param model 模型
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param page 页码
     * @param size 每页大小
     * @return 用量列表
     */
    Page<SysAiUsage> list(String groupName, String provider, String model,
                          LocalDateTime startTime, LocalDateTime endTime, int page, int size);

    /**
     * 查询总用量统计
     *
     * @param groupName 分组
     * @param provider 服务商
     * @param model 模型
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 统计结果
     */
    UsageStatistics getStatistics(String groupName, String provider, String model,
                                  LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 查询按天趋势（令牌 使用趋势）。
     *
     * <p>直接聚合日汇总表 {@code sys_ai_usage_daily}，按统计日期返回每天输入/输出/缓存 Token
     * 合计，保证时间窗口内每天都有数据点（不受明细分页/条数限制）。</p>
     *
     * @param provider 服务商（可为空）
     * @param model    模型（可为空）
     * @param startTime 开始时间（可为空）
     * @param endTime   结束时间（可为空）
     * @return 按天排序的趋势点列表（statDate 升序）
     */
    List<SysAiUsageDaily> getDailyTrend(String provider, String model,
                                        LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 用量统计摘要
     * @author CH
     * @since 4.0.0
     */
    class UsageStatistics {
        /**
         * 总数calls
        */
        public Long totalCalls;
        /**
         * 总数prompttokens
        */
        public Long totalPromptTokens;
        /**
         * 总数completiontokens
        */
        public Long totalCompletionTokens;
        /**
         * 总数令牌
        */
        public Long totalTokens;
        /**
         * 总数cost
        */
        public Double totalCost;
        /**
         * 缓存读取令牌
        */
        public Long totalCachedTokens;
        /**
         * 原始费用(未折扣)
        */
        public Double standardCost;
        /**
         * 平均耗时(ms)
        */
        public Double avgLatency;
        /**
         * 平均首字延迟(ms)
        */
        public Double avgFirstTokenLatency;
    }
}
