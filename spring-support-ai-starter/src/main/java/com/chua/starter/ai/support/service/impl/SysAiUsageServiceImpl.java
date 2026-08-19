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
        entity.setGroupName(groupName);
        entity.setProvider(provider);
        entity.setModel(model);
        entity.setCallType(callType);
        entity.setPromptTokens(usage.getInputTokens());
        entity.setCompletionTokens(usage.getOutputTokens());
        entity.setTotalTokens(usage.getTotalTokens());
        entity.setRequestId(requestId);
        entity.setUserId(userId);
        entity.setCallTime(LocalDateTime.now());

        // 计算费用
        SysAiPricing pricing = pricingService.getLatestPricing(provider, model);
        if (pricing != null) {
            double inputCost = (usage.getInputTokens() != null ? usage.getInputTokens() : 0) / 1000.0 * pricing.getInputPricePer1k();
            double outputCost = (usage.getOutputTokens() != null ? usage.getOutputTokens() : 0) / 1000.0 * pricing.getOutputPricePer1k();
            entity.setInputCost(inputCost);
            entity.setOutputCost(outputCost);
            entity.setTotalCost(inputCost + outputCost);
        } else {
            log.warn("[Usage] 未找到定价: provider={}, model={}", provider, model);
            entity.setInputCost(0.0);
            entity.setOutputCost(0.0);
            entity.setTotalCost(0.0);
        }

        usageMapper.insert(entity);
        log.debug("[Usage] 记录成功: group={}, model={}, tokens={}, cost={}",
                groupName, model, usage.getTotalTokens(), entity.getTotalCost());
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
            wrapper.eq(SysAiUsage::getGroupName, groupName);
        }
        if (provider != null) {
            wrapper.eq(SysAiUsage::getProvider, provider);
        }
        if (model != null) {
            wrapper.eq(SysAiUsage::getModel, model);
        }
        if (startTime != null) {
            wrapper.ge(SysAiUsage::getCallTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(SysAiUsage::getCallTime, endTime);
        }
        wrapper.orderByDesc(SysAiUsage::getCallTime);
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
            wrapper.eq(SysAiUsage::getGroupName, groupName);
        }
        if (provider != null) {
            wrapper.eq(SysAiUsage::getProvider, provider);
        }
        if (model != null) {
            wrapper.eq(SysAiUsage::getModel, model);
        }
        if (startTime != null) {
            wrapper.ge(SysAiUsage::getCallTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(SysAiUsage::getCallTime, endTime);
        }
        List<SysAiUsage> list = usageMapper.selectList(wrapper);
        UsageStatistics stats = new UsageStatistics();
        stats.totalCalls = list.size();
        stats.totalPromptTokens = list.stream().mapToInt(u -> u.getPromptTokens() != null ? u.getPromptTokens() : 0).sum();
        stats.totalCompletionTokens = list.stream().mapToInt(u -> u.getCompletionTokens() != null ? u.getCompletionTokens() : 0).sum();
        stats.totalTokens = list.stream().mapToInt(u -> u.getTotalTokens() != null ? u.getTotalTokens() : 0).sum();
        stats.totalCost = list.stream().mapToDouble(u -> u.getTotalCost() != null ? u.getTotalCost() : 0).sum();
        return stats;
    }
}
