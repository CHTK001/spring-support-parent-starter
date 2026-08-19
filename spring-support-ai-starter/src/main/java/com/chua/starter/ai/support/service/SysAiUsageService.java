package com.chua.starter.ai.support.service;

import com.chua.common.support.ai.AiUsage;
import com.chua.starter.ai.support.entity.SysAiUsage;

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
     * @param requestId 请求ID
     * @param userId 用户ID
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
    List<SysAiUsage> list(String groupName, String provider, String model,
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
     * 用量统计摘要
     */
    class UsageStatistics {
        /** 总数calls */
        public Integer totalCalls;
        /** 总数prompttokens */
        public Integer totalPromptTokens;
        /** 总数completiontokens */
        public Integer totalCompletionTokens;
        /** 总数tokens */
        public Integer totalTokens;
        /** 总数cost */
        public Double totalCost;
    }
}
