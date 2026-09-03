package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.ai.AiUsage;
import com.chua.starter.ai.support.entity.SysAiPricing;
import com.chua.starter.ai.support.entity.SysAiUsage;
import com.chua.starter.ai.support.entity.SysAiUsageDaily;
import com.chua.starter.ai.support.mapper.SysAiUsageDailyMapper;
import com.chua.starter.ai.support.mapper.SysAiUsageMapper;
import com.chua.starter.ai.support.service.SysAiPricingService;
import com.chua.starter.ai.support.service.SysAiUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI 用量服务实现类
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiUsageServiceImpl implements SysAiUsageService {

    /**
     * 用量 Mapper
     */
    private final SysAiUsageMapper usageMapper;

    /**
     * 日汇总 Mapper
     */
    private final SysAiUsageDailyMapper dailyMapper;

    /**
     * 定价服务
     */
    private final SysAiPricingService pricingService;

    @Override
    @Transactional
    /**
     * Record
     * @param groupName groupName
     * @param provider provider
     * @param model model
     * @param callType callType
     * @param usage usage
     * @param requestId requestId
     * @param userId userId
     */
    public void record(String groupName, String provider, String model, String callType,
                       AiUsage usage, String requestId, String userId) {
        if (usage == null) {
            return;
        }
        SysAiUsage entity = new SysAiUsage();
        entity.setSysAiUsageGroupName(groupName);
        entity.setSysAiUsageProvider(provider);
        entity.setSysAiUsageModel(model);
        entity.setSysAiUsageCallType(callType);
        entity.setSysAiUsagePromptTokens(usage.getInputTokens());
        entity.setSysAiUsageCompletionTokens(usage.getOutputTokens());
        entity.setSysAiUsageTotalTokens(usage.getTotalTokens());
        entity.setSysAiUsageRequestId(requestId);
        entity.setSysAiUsageUserId(userId);
        entity.setSysAiUsageCallTime(LocalDateTime.now());
        entity.setSysAiUsageFirstTokenLatency(usage.getFirstTokenLatencyMillis());
        entity.setSysAiUsageTotalLatency(usage.getDurationMillis());
        entity.setSysAiUsageCachedTokens(usage.getCacheTokens());

        // 计算费用
        SysAiPricing pricing = pricingService.getLatestPricing(provider, model);
        if (pricing != null && pricing.getInputPricePer1k() != null && pricing.getOutputPricePer1k() != null) {
            int inputTokens = usage.getInputTokens() != null ? usage.getInputTokens() : 0;
            int outputTokens = usage.getOutputTokens() != null ? usage.getOutputTokens() : 0;
            BigDecimal inputPrice = BigDecimal.valueOf(pricing.getInputPricePer1k());
            BigDecimal outputPrice = BigDecimal.valueOf(pricing.getOutputPricePer1k());
            BigDecimal inputCost = BigDecimal.valueOf(inputTokens)
                    .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP)
                    .multiply(inputPrice);
            BigDecimal outputCost = BigDecimal.valueOf(outputTokens)
                    .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP)
                    .multiply(outputPrice);
            BigDecimal total = inputCost.add(outputCost);
            entity.setSysAiUsageInputCost(inputCost.doubleValue());
            entity.setSysAiUsageOutputCost(outputCost.doubleValue());
            entity.setSysAiUsageTotalCost(total.doubleValue());
            entity.setSysAiUsageOriginalCost(total.doubleValue());
            entity.setSysAiUsageInputPricePer1M(pricing.getInputPricePer1k() * 1000.0);
            entity.setSysAiUsageOutputPricePer1M(pricing.getOutputPricePer1k() * 1000.0);
            if (usage.getCacheTokens() != null && usage.getCacheTokens() > 0) {
                BigDecimal cacheCost = BigDecimal.valueOf(usage.getCacheTokens())
                        .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP)
                        .multiply(inputPrice)
                        .multiply(BigDecimal.valueOf(0.5));
                entity.setSysAiUsageCachedCost(cacheCost.doubleValue());
                entity.setSysAiUsageTotalCost(total.add(cacheCost).doubleValue());
                entity.setSysAiUsageOriginalCost(total.add(cacheCost).doubleValue());
                entity.setSysAiUsageCacheReadPricePer1M(pricing.getInputPricePer1k() * 1000.0 * 0.5);
            }
        } else {
            log.warn("[Usage] 未找到定价: provider={}, model={}", provider, model);
            entity.setSysAiUsageInputCost(0.0);
            entity.setSysAiUsageOutputCost(0.0);
            entity.setSysAiUsageTotalCost(0.0);
        }

        usageMapper.insert(entity);
        log.debug("[Usage] 记录成功: group={}, model={}, tokens={}, cost={}",
                groupName, model, usage.getTotalTokens(), entity.getSysAiUsageTotalCost());
    }

    @Override
    /**
     * 列表
     * @param groupName groupName
     * @param provider provider
     * @param model model
     * @param startTime startTime
     * @param endTime endTime
     * @param page page
     * @param size size
     */
    public Page<SysAiUsage> list(String groupName, String provider, String model,
                                 LocalDateTime startTime, LocalDateTime endTime, int page, int size) {
        LambdaQueryWrapper<SysAiUsage> wrapper = new LambdaQueryWrapper<>();
        if (groupName != null) {
            wrapper.eq(SysAiUsage::getSysAiUsageGroupName, groupName);
        }
        if (provider != null) {
            wrapper.eq(SysAiUsage::getSysAiUsageProvider, provider);
        }
        if (model != null) {
            wrapper.eq(SysAiUsage::getSysAiUsageModel, model);
        }
        if (startTime != null) {
            wrapper.ge(SysAiUsage::getSysAiUsageCallTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(SysAiUsage::getSysAiUsageCallTime, endTime);
        }
        wrapper.orderByDesc(SysAiUsage::getSysAiUsageCallTime);
        Page<SysAiUsage> p = new Page<>(page, size);
        usageMapper.selectPage(p, wrapper);
        return p;
    }

    @Override
    /**
     * 获取Statistics
     * @param groupName groupName
     * @param provider provider
     * @param model model
     * @param startTime startTime
     * @param endTime endTime
     */
    public UsageStatistics getStatistics(String groupName, String provider, String model,
                                          LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> row = usageMapper.selectStatistics(groupName, provider, model, startTime, endTime);
        UsageStatistics stats = new UsageStatistics();
        stats.totalCalls = toInt(row.get("totalCalls"));
        stats.totalPromptTokens = toInt(row.get("totalPromptTokens"));
        stats.totalCompletionTokens = toInt(row.get("totalCompletionTokens"));
        stats.totalTokens = toInt(row.get("totalTokens"));
        stats.totalCost = toDouble(row.get("totalCost"));
        stats.totalCachedTokens = toInt(row.get("totalCachedTokens"));
        stats.standardCost = toDouble(row.get("standardCost"));
        long totalLatency = toLong(row.get("totalLatency"));
        stats.avgLatency = stats.totalCalls > 0 ? (double) totalLatency / stats.totalCalls : 0.0;
        return stats;
    }

    private static Integer toInt(Object v) { return v == null ? 0 : ((Number) v).intValue(); }
    private static Double toDouble(Object v) { return v == null ? 0.0 : ((Number) v).doubleValue(); }
    private static long toLong(Object v) { return v == null ? 0L : ((Number) v).longValue(); }
}
