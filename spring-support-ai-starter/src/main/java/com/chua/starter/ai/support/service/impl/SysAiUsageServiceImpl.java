// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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
import com.chua.starter.ai.support.service.ModelAliasService;
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
import java.util.ArrayList;
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
    private final ModelAliasService modelAliasService;

    /**
     * 日汇总 映射器
     */
    private final SysAiUsageDailyMapper dailyMapper;

    /**
     * 用量实时推送中枢：每次成功写入后通知它「数据变了」，由它合并后经 SSE 推给页面。
     */
    private final com.chua.starter.ai.support.controller.UsageLiveStreamHub usageLiveStreamHub;

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
        entity.setSysAiUsageUserId(SysAiUsageService.resolveOwner(userId));
        entity.setSysAiUsageCallTime(LocalDateTime.now());
        entity.setSysAiUsageFirstTokenLatency(usage.getFirstTokenLatencyMillis());
        entity.setSysAiUsageTotalLatency(usage.getDurationMillis());
        // 解码速度 / 端到端吞吐：AiUsage 派生方法实时计算，未填充时按字段兜底
        entity.setSysAiUsageTokensPerSecond(usage.tokensPerSecond());
        entity.setSysAiUsageTotalTokensPerSecond(usage.totalTokensPerSecond());
        entity.setSysAiUsageCachedTokens(usage.getCacheTokens());
        entity.setSysAiUsageInferenceStrength("默认");
        entity.setSysAiUsageEndpoint("/v1/chat/completions");
        entity.setSysAiUsageType(callType != null ? callType : "chat");
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

        entity.setSysAiUsageSource("chat");
        usageMapper.insert(entity);
        log.debug("[Usage] 记录成功: group={}, model={}, tokens={}, cost={}",
                groupName, model, usage.getTotalTokens(), entity.getSysAiUsageTotalCost());
        notifyLiveStream();
    }

    /**
     * 通知用量实时流「数据变了」。
     *
     * <p>挂在事务方法内部是<b>有意为之</b>的简化：这里只置一个内存标记，
     * 真正的查库与推送发生在 1 秒后的定时任务里，此时上一笔事务早已提交。
     * 万一某个调用路径上事务回滚了，最多让 SSE 多推一帧未提交的数，
     * 下一拍就被真实值覆盖 —— 远不值得为它引入事务同步回调。</p>
     */
    private void notifyLiveStream() {
        try {
            usageLiveStreamHub.markDirty();
        } catch (Exception e) {
            log.debug("[Usage] 实时流通知失败（不影响记录）: {}", e.getMessage());
        }
    }

    @Override
    @Transactional
    public void record(String groupName, String provider, String model, String callType,
                       AiUsage usage, String requestId, String userId, String source) {
        if (usage == null) {
            return;
        }
        // 复用主 record 逻辑：先调用主方法，再更新 source
        record(groupName, provider, model, callType, usage, requestId, userId);
        // 主方法已 insert，这里通过 mapper 按 requestId 更新 source（简化：直接查最新一条）
        try {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysAiUsage> w =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            w.eq(SysAiUsage::getSysAiUsageRequestId, requestId).orderByDesc(SysAiUsage::getSysAiUsageCallTime).last("LIMIT 1");
            SysAiUsage latest = usageMapper.selectOne(w);
            if (latest != null) {
                latest.setSysAiUsageSource(source);
                usageMapper.updateById(latest);
            }
        } catch (Exception e) {
            log.warn("[Usage] 更新 source 失败: {}", e.getMessage());
        }
    }
    /**
     * @param provider 提供者
     * @param model 模型
     * @param startTime 启动时间
     * @param endTime 结束时间
     * @param userId 归属用户，为空时列出全部用户明细
     * @param page page
     * @param size 大小
     */
    public Page<SysAiUsage> list(String groupName, String provider, String model,
                                 LocalDateTime startTime, LocalDateTime endTime, String userId, int page, int size) {
        LambdaQueryWrapper<SysAiUsage> wrapper = new LambdaQueryWrapper<>();
        if (groupName != null) {
            wrapper.eq(SysAiUsage::getSysAiUsageGroupName, groupName);
        }
        if (userId != null && !userId.isBlank()) {
            // 与聚合查询同口径：管理员额外可见无主数据（定时同步的外部工具用量），
            // 普通账号仍是严格 eq —— eq 在 SQL 里本就匹配不到 NULL，无需额外处理。
            if (includeOrphan()) {
                wrapper.and(w -> w.eq(SysAiUsage::getSysAiUsageUserId, userId)
                        .or().isNull(SysAiUsage::getSysAiUsageUserId));
            } else {
                wrapper.eq(SysAiUsage::getSysAiUsageUserId, userId);
            }
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
     * <p>全量统计读日汇总表 sys_ai_usage_daily；指定 userId 时读汇总表会连带别人的量，
     * 因此改走明细表的数据库端聚合。</p>
     *
     * @param groupName 分组名称（可为空）
     * @param provider  服务商（可为空）
     * @param model     模型（可为空）
     * @param startTime 开始时间（可为空）
     * @param endTime   结束时间（可为空）
     * @param userId    归属用户（可为空，为空时统计全部用户）
     * @return 聚合统计结果
     */
    public UsageStatistics getStatistics(String groupName, String provider, String model,
                                          LocalDateTime startTime, LocalDateTime endTime, String userId) {
        if (userId != null && !userId.isBlank()) {
            return statisticsFromDetail(groupName, provider, model, startTime, endTime, userId);
        }
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

    /**
     * 当前请求是否应把**无主数据**（{@code sys_ai_usage_user_id IS NULL}）计入结果。
     *
     * <p>这是<b>兜底网而非主路径</b>：写入侧已统一走
     * {@link SysAiUsageService#resolveOwner(String)}，取不到归属时兜底成 {@code admin}，
     * 历史 NULL 也已回填，正常情况下这里恒为「无数据可加」。保留它是为了万一将来某个
     * 新写入路径漏了兜底，管理员仍能看到这批数据，而不是像以前那样静默消失。</p>
     *
     * <p>与 {@link com.chua.starter.ai.support.util.AiUserContext#canAccessOthersData()} 是两件事：
     * 后者管的是「能否读他人的具名数据」（恒为 false，管理员也不跨账号），
     * 这里管的是「能否读<b>没有主人</b>的数据」。管理员依然看不到 alice / bob 的具名记录。</p>
     *
     * <p>定时任务线程里没有登录态，{@code isAdmin()} 天然为 false，因此回填类调用
     * （如 {@link #getDailyTrend} 被聚合任务复用）不会误放行。</p>
     *
     * @return 管理员账号为 true，其余为 false
     */
    private boolean includeOrphan() {
        return com.chua.starter.ai.support.util.AiUserContext.isAdmin();
    }

    @Override
    public List<SysAiUsageDaily> getDailyTrend(String provider, String model,
                                               LocalDateTime startTime, LocalDateTime endTime, String userId) {
        if (userId != null && !userId.isBlank()) {
            List<SysAiUsageMapper.DailyAggregateRow> rows =
                    usageMapper.selectDailyAggregate(startTime, endTime, userId, includeOrphan());
            List<SysAiUsageDaily> trend = new ArrayList<>(rows.size());
            for (SysAiUsageMapper.DailyAggregateRow row : rows) {
                SysAiUsageDaily daily = new SysAiUsageDaily();
                daily.setStatDate(row.statDate());
                daily.setGroupName(row.groupName());
                daily.setProvider(row.provider());
                daily.setModel(row.model());
                daily.setUserId(SysAiUsageService.resolveOwner(row.userId()));
                daily.setTotalCalls(row.totalCalls());
                daily.setTotalPromptTokens(row.totalPromptTokens());
                daily.setTotalCompletionTokens(row.totalCompletionTokens());
                daily.setTotalTokens(row.totalTokens());
                daily.setTotalCost(row.totalCost());
                daily.setTotalCachedTokens(row.totalCachedTokens());
                daily.setTotalLatency(row.totalLatency());
                daily.setTotalFirstTokenLatency(row.totalFirstTokenLatency());
                trend.add(daily);
            }
            return trend;
        }
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

    @Override
    public List<com.chua.starter.ai.support.model.vo.AiUsageTrendPointVO> getHourlyTrend(
            LocalDateTime startTime, LocalDateTime endTime, String userId) {
        List<SysAiUsageMapper.HourlyAggregateRow> rows =
                usageMapper.selectHourlyAggregate(startTime, endTime, userId, includeOrphan());
        List<com.chua.starter.ai.support.model.vo.AiUsageTrendPointVO> result = new ArrayList<>(rows.size());
        for (SysAiUsageMapper.HourlyAggregateRow r : rows) {
            result.add(new com.chua.starter.ai.support.model.vo.AiUsageTrendPointVO(
                    r.statHour(),
                    r.totalCalls() == null ? 0L : r.totalCalls(),
                    r.totalPromptTokens() == null ? 0L : r.totalPromptTokens(),
                    r.totalCompletionTokens() == null ? 0L : r.totalCompletionTokens(),
                    r.totalTokens() == null ? 0L : r.totalTokens(),
                    r.totalCachedTokens() == null ? 0L : r.totalCachedTokens()
            ));
        }
        return result;
    }

    @Override
    public List<com.chua.starter.ai.support.model.vo.AiUsageModelTrendPointVO> getDailyModelTrend(
            LocalDateTime startTime, LocalDateTime endTime, String userId) {
        List<SysAiUsageMapper.DailyModelRow> rows =
                usageMapper.selectDailyModelAggregate(startTime, endTime, userId, includeOrphan());
        List<com.chua.starter.ai.support.model.vo.AiUsageModelTrendPointVO> result =
                new ArrayList<>(rows.size());
        for (SysAiUsageMapper.DailyModelRow r : rows) {
            result.add(new com.chua.starter.ai.support.model.vo.AiUsageModelTrendPointVO(
                    r.statDate() != null ? r.statDate().toString() : null,
                    r.model(),
                    r.provider(),
                    r.totalCalls() == null ? 0L : r.totalCalls(),
                    r.totalTokens() == null ? 0L : r.totalTokens(),
                    r.totalCost() == null ? 0D : r.totalCost(),
                    // 延迟原样透传 null：0 会被前端画成「延迟极低」，而 null 能表达「当天没测到」
                    r.avgFirstTokenLatency(),
                    r.avgTotalLatency()
            ));
        }
        return result;
    }

    /**
     * 由明细表聚合出统计摘要（普通用户口径：日汇总表没有用户维度，不能给它读汇总表）。
     *
     * @param groupName 分组名称（可为空）
     * @param provider  服务商（可为空）
     * @param model     模型（可为空）
     * @param startTime 开始时间（可为空）
     * @param endTime   结束时间（可为空）
     * @param userId    归属用户，不能为空
     * @return 聚合统计结果
     */
    private UsageStatistics statisticsFromDetail(String groupName, String provider, String model,
                                                 LocalDateTime startTime, LocalDateTime endTime, String userId) {
        SysAiUsageMapper.UsageStatsRow row =
                usageMapper.selectStatistics(groupName, provider, model, startTime, endTime,
                        userId, includeOrphan());
        UsageStatistics stats = new UsageStatistics();
        if (row == null) {
            return stats;
        }
        stats.totalCalls = row.totalCalls() == null ? 0L : row.totalCalls().longValue();
        stats.totalPromptTokens = row.totalPromptTokens() == null ? 0L : row.totalPromptTokens().longValue();
        stats.totalCompletionTokens = row.totalCompletionTokens() == null ? 0L : row.totalCompletionTokens().longValue();
        stats.totalTokens = row.totalTokens() == null ? 0L : row.totalTokens().longValue();
        stats.totalCachedTokens = row.totalCachedTokens() == null ? 0L : row.totalCachedTokens().longValue();
        stats.totalCost = row.totalCost() == null ? 0D : row.totalCost();
        stats.standardCost = row.standardCost() == null ? 0D : row.standardCost();
        stats.avgLatency = stats.totalCalls > 0 && row.totalLatency() != null
                ? (double) row.totalLatency() / stats.totalCalls : 0.0;
        stats.avgFirstTokenLatency = stats.totalCalls > 0 && row.totalFirstTokenLatency() != null
                ? (double) row.totalFirstTokenLatency() / stats.totalCalls : 0.0;
        return stats;
    }
}