package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.ai.AiUsage;
import com.chua.starter.ai.support.entity.SysAiPricing;
import com.chua.starter.ai.support.entity.SysAiUsage;
import com.chua.starter.ai.support.mapper.SysAiUsageMapper;
import com.chua.starter.ai.support.service.SysAiPricingService;
import com.chua.starter.ai.support.service.SysAiUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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

        // 计算费用
        SysAiPricing pricing = pricingService.getLatestPricing(provider, model);
        if (pricing != null) {
            double inputCost = (usage.getInputTokens() != null ? usage.getInputTokens() : 0) / 1000.0 * pricing.getInputPricePer1k();
            double outputCost = (usage.getOutputTokens() != null ? usage.getOutputTokens() : 0) / 1000.0 * pricing.getOutputPricePer1k();
            entity.setSysAiUsageInputCost(inputCost);
            entity.setSysAiUsageOutputCost(outputCost);
            entity.setSysAiUsageTotalCost(inputCost + outputCost);
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
    public List<SysAiUsage> list(String groupName, String provider, String model,
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
        return usageMapper.selectPage(p, wrapper).getRecords();
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
        List<SysAiUsage> list = usageMapper.selectList(wrapper);
        UsageStatistics stats = new UsageStatistics();
        stats.totalCalls = list.size();
        stats.totalPromptTokens = list.stream().mapToInt(u -> u.getSysAiUsagePromptTokens() != null ? u.getSysAiUsagePromptTokens() : 0).sum();
        stats.totalCompletionTokens = list.stream().mapToInt(u -> u.getSysAiUsageCompletionTokens() != null ? u.getSysAiUsageCompletionTokens() : 0).sum();
        stats.totalTokens = list.stream().mapToInt(u -> u.getSysAiUsageTotalTokens() != null ? u.getSysAiUsageTotalTokens() : 0).sum();
        stats.totalCost = list.stream().mapToDouble(u -> u.getSysAiUsageTotalCost() != null ? u.getSysAiUsageTotalCost() : 0).sum();
        return stats;
    }
}
