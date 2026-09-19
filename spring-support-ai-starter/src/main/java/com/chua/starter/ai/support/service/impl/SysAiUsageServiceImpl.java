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
import com.chua.starter.common.support.service.IptablesService;
import com.chua.starter.common.support.utils.IpUtils;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.network.ip.GeoCity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
     * 用量 映射器
     */
    private final SysAiUsageMapper usageMapper;

    /**
     * 日汇总 映射器
     */
    private final SysAiUsageDailyMapper dailyMapper;

    /**
     * 定价服务
     */
    private final SysAiPricingService pricingService;

    /**
     * IP 地址转物理地址服务（spring-支持-通用，带 Redis 缓存）
     */
    private final IptablesService iptablesService;

    @Override
    @Transactional
    /**
     * Record
     * @param groupName 群体名称
     * @param provider 提供者
     * @param model 模型
     * @param callType call类型
     * @param usage usage
     * @param requestId 请求标识
     * @param userId 用户标识
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
        entity.setSysAiUsageInferenceStrength("默认");
        entity.setSysAiUsageEndpoint("/v1/chat/completions");
        entity.setSysAiUsageType("chat");
        entity.setSysAiUsageBillingMode("pay_as_you_go");

 // 本机请求兜底：默认本机 ipv4；非本机部署可由拦截器回填真实客户端 IP
        String clientIp = IpUtils.getLocalIpv4();
        entity.setSysAiUsageIp(clientIp);
 // IP 转物理地址（复用 spring-支持-通用 iptables服务，带 Redis 缓存）
        try {
            ReturnResult<GeoCity> geo = iptablesService.transferAddress(clientIp);
            if (geo != null && geo.isOk() && geo.getData() != null) {
                entity.setSysAiUsageLocation(geo.getData().toString());
            }
        } catch (Exception e) {
            log.debug("[Usage] IP 定位失败: ip={}, err={}", clientIp, e.getMessage());
        }

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
     * @param groupName 群体名称
     * @param provider 提供者
     * @param model 模型
     * @param startTime 启动时间
     * @param endTime 结束时间
     * @param page page
     * @param size 大小
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
     * 获取用量统计。
     * <p>查询日汇总表 sys_ai_usage_daily，避免全量扫描原始明细表。</p>
     *
     * @param groupName 分组名称（可为空）
     * @param provider  服务商（可为空）
     * @param model     模型（可为空）
     * @param startTime 开始时间（可为空）
     * @param endTime   结束时间（可为空）
     * @return 聚合统计结果
     */
    public UsageStatistics getStatistics(String groupName, String provider, String model,
                                          LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<SysAiUsageDaily> wrapper = new LambdaQueryWrapper<>();
        if (groupName != null) {
            wrapper.eq(SysAiUsageDaily::getGroupName, groupName);
        }
        if (provider != null) {
            wrapper.eq(SysAiUsageDaily::getProvider, provider);
        }
        if (model != null) {
            wrapper.eq(SysAiUsageDaily::getModel, model);
        }
        if (startTime != null) {
            wrapper.ge(SysAiUsageDaily::getStatDate, startTime.toLocalDate());
        }
        if (endTime != null) {
            wrapper.le(SysAiUsageDaily::getStatDate, endTime.toLocalDate());
        }
        List<SysAiUsageDaily> list = dailyMapper.selectList(wrapper);
        UsageStatistics stats = new UsageStatistics();
        stats.totalCalls = list.stream().mapToLong(u -> u.getTotalCalls() != null ? u.getTotalCalls() : 0L).sum();
        stats.totalPromptTokens = list.stream().mapToLong(u -> u.getTotalPromptTokens() != null ? u.getTotalPromptTokens() : 0L).sum();
        stats.totalCompletionTokens = list.stream().mapToLong(u -> u.getTotalCompletionTokens() != null ? u.getTotalCompletionTokens() : 0L).sum();
        stats.totalTokens = list.stream().mapToLong(u -> u.getTotalTokens() != null ? u.getTotalTokens() : 0L).sum();
        stats.totalCost = list.stream().mapToDouble(u -> u.getTotalCost() != null ? u.getTotalCost() : 0).sum();
        stats.totalCachedTokens = list.stream().mapToLong(u -> u.getTotalCachedTokens() != null ? u.getTotalCachedTokens() : 0L).sum();
        stats.standardCost = list.stream().mapToDouble(u -> u.getStandardCost() != null ? u.getStandardCost() : 0).sum();
        long totalLatency = list.stream().mapToLong(u -> u.getTotalLatency() != null ? u.getTotalLatency() : 0L).sum();
        stats.avgLatency = stats.totalCalls > 0 ? (double) totalLatency / stats.totalCalls : 0.0;
        long totalFirstTokenLatency = list.stream().mapToLong(u -> u.getTotalFirstTokenLatency() != null ? u.getTotalFirstTokenLatency() : 0L).sum();
        stats.avgFirstTokenLatency = stats.totalCalls > 0 ? (double) totalFirstTokenLatency / stats.totalCalls : 0.0;
        return stats;
    }

    @Override
    public List<SysAiUsageDaily> getDailyTrend(String provider, String model,
                                               LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<SysAiUsageDaily> wrapper = new LambdaQueryWrapper<>();
        if (provider != null && !provider.isBlank()) {
            wrapper.eq(SysAiUsageDaily::getProvider, provider);
        }
        if (model != null && !model.isBlank()) {
            wrapper.eq(SysAiUsageDaily::getModel, model);
        }
        if (startTime != null) {
            wrapper.ge(SysAiUsageDaily::getStatDate, startTime.toLocalDate());
        }
        if (endTime != null) {
            wrapper.le(SysAiUsageDaily::getStatDate, endTime.toLocalDate());
        }
        wrapper.orderByAsc(SysAiUsageDaily::getStatDate);
        return dailyMapper.selectList(wrapper);
    }
}
