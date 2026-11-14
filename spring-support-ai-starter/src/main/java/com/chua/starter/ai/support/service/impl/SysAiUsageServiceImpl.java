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
import java.util.Comparator;
import java.util.LinkedHashMap;
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
       * 用量 映射器
       */
    private final SysAiUsageMapper usageMapper;
    private final ModelAliasService modelAliasService;

      /**
       * 日汇总 映射器
       */
    private final SysAiUsageDailyMapper dailyMapper;

    /**
     * 无主明细是否真的存在（配合 {@link #includeOrphanEffective()} 决定要不要拼
     * {@code OR user_id IS NULL}）；{@code volatile} 是因为它由请求线程刷新、多线程读。
     */
    private volatile boolean orphanExists = true;

    /**
     * 上一次检查无主明细的时间戳（毫秒），用于 TTL 内复用结论。
     */
    private volatile long orphanCheckedAt;

    /**
     * 无主明细存在性检查的缓存时长：5 分钟。
     */
    private static final long ORPHAN_CHECK_TTL_MS = 300_000L;

      /**
       * 用量实时推送中枢：每次成功写入后通知它「数据变了」，由它合并后经 SSE 推给页面。
       */
    private final com.chua.starter.ai.support.controller.UsageLiveStreamHub usageLiveStreamHub;

      /**
       * 定价服务
       */
    private final SysAiPricingService pricingService;

      /**
       * 模型指标：每轮实测 tok/s 回写，模型管理「速度」格读这一份。
       */
    private final com.chua.starter.ai.support.manage.service.SysAiModelMetricsService sysAiModelMetricsService;

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
        // 实测输出速度回写模型指标：模型管理「速度」格直接读它（官方指标源未收录的
        // 自建通道也有值）。失败只降级为「没有速度」，不影响用量记账。
        sysAiModelMetricsService.recordObservedSpeed(provider, model, usage.tokensPerSecond());
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
            if (includeOrphanEffective()) {
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
        // 有 userId 时只统计本人（管理员额外含无主明细）；userId 为空表示「全部账号」，
        // 两种口径都由 statisticsFromDetail 统一处理：日汇总段 + 未覆盖的明细尾巴。
        return statisticsFromDetail(groupName, provider, model, startTime, endTime, userId);
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

    /**
     * 是否**真的**需要把无主明细并进查询（管理员 + 表里确实存在 NULL 行）。
     *
     * <p><b>为什么不直接用 {@link #includeOrphan()} 拼 SQL</b>：
     * {@code user_id = ? OR user_id IS NULL} 会让 MySQL 放弃
     * {@code (user_id, call_time)} 复合索引，退化成全表扫描 + filesort ——
     * 明细分页「全部时间」实测 11~25 秒、单页 20 行也是秒级起步。
     * 而写入侧早已统一兜底成 admin、历史 NULL 也已回填，这两支实际匹配到的无主行是 0。</p>
     *
     * <p>所以先确认「真的有 NULL 行」才拼 OR：数据干净时走纯 {@code user_id = ?}，
     * 索引生效；万一将来某个新写入路径又漏了兜底，最迟一个 {@value #ORPHAN_CHECK_TTL_MS}ms
     * 周期后自动恢复可见，不会静默丢数据。检查本身走 user_id 索引，实测几十毫秒，
     * 且结果在 TTL 内复用。</p>
     *
     * @return 是否需要把无主明细并入查询
     */
    private boolean includeOrphanEffective() {
        if (!includeOrphan()) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - orphanCheckedAt > ORPHAN_CHECK_TTL_MS) {
            try {
                Long count = usageMapper.selectCount(new LambdaQueryWrapper<SysAiUsage>()
                        .isNull(SysAiUsage::getSysAiUsageUserId));
                orphanExists = count != null && count > 0;
            } catch (Exception e) {
                // 查不出来就按「有」处理：宁可慢一点，也不要静默少算
                orphanExists = true;
            }
            orphanCheckedAt = now;
        }
        return orphanExists;
    }

    @Override
    public List<SysAiUsageDaily> getDailyTrend(String provider, String model,
                                               LocalDateTime startTime, LocalDateTime endTime, String userId) {
        if (userId != null && !userId.isBlank()) {
            List<SysAiUsageMapper.DailyAggregateRow> rows =
                    usageMapper.selectDailyAggregate(startTime, endTime, userId, includeOrphanEffective());
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
                usageMapper.selectHourlyAggregate(startTime, endTime, userId, includeOrphanEffective());
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

    /**
     * 每日模型用量趋势（按天 × 模型）。
     *
     * <p><b>为什么要拆两段</b>：明细表按「天 + 模型」做 GROUP BY，在「全部时间」下要扫
     * 120 万行、实测 42 秒；日汇总表存的就是同一口径的按天聚合（几百行、实测 0.3 秒），
     * 并且带输出速度合计与样本数，能还原速度均值。因此汇总表已覆盖的区间读汇总表，
     * 未覆盖的尾巴读明细，最后按 (天, 服务商, 模型) 合并。</p>
     *
     * @param startTime 开始时间（可为空）
     * @param endTime   结束时间（可为空）
     * @param userId    归属用户（可为空，为空时聚合全部用户）
     * @return 按天升序、同天内按令牌降序的趋势点
     */
    @Override
    public List<com.chua.starter.ai.support.model.vo.AiUsageModelTrendPointVO> getDailyModelTrend(
            LocalDateTime startTime, LocalDateTime endTime, String userId) {
        LocalDate coveredEnd = coveredDailyEnd(endTime);
        LocalDate startDate = startTime == null ? null : startTime.toLocalDate();
        if (coveredEnd == null || (startDate != null && startDate.isAfter(coveredEnd))) {
            return dailyModelTrendFromRaw(startTime, endTime, userId);
        }
        Map<String, TrendAccumulator> merged = new LinkedHashMap<>();
        collectModelTrendFromDaily(merged, startDate, coveredEnd, userId);
        LocalDateTime tailStart = coveredEnd.plusDays(1).atStartOfDay();
        if (endTime == null || !tailStart.isAfter(endTime)) {
            for (SysAiUsageMapper.DailyModelRow r : usageMapper.selectDailyModelAggregate(
                    tailStart, endTime, userId, includeOrphanEffective())) {
                accumulateModelTrend(merged, r.statDate(), r.provider(), r.model(),
                        zero(r.totalCalls()), zero(r.totalTokens()), zero(r.totalCost()),
                        r.avgFirstTokenLatency(), r.avgTotalLatency(), r.avgTokensPerSecond());
            }
        }
        List<com.chua.starter.ai.support.model.vo.AiUsageModelTrendPointVO> result = new ArrayList<>(merged.size());
        for (TrendAccumulator acc : merged.values()) {
            result.add(acc.toVo());
        }
        result.sort(TREND_ORDER);
        return result;
    }

    /**
     * 汇总段：读日汇总表（几百行），按 (天, 服务商, 模型) 累加。
     *
     * @param merged    累加容器
     * @param startDate 开始日期（可为空）
     * @param coveredEnd 汇总段最后一天
     * @param userId    归属用户（可为空）
     */
    private void collectModelTrendFromDaily(Map<String, TrendAccumulator> merged, LocalDate startDate,
                                            LocalDate coveredEnd, String userId) {
        LambdaQueryWrapper<SysAiUsageDaily> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(SysAiUsageDaily::getStatDate, SysAiUsageDaily::getProvider, SysAiUsageDaily::getModel,
                SysAiUsageDaily::getTotalCalls, SysAiUsageDaily::getTotalTokens, SysAiUsageDaily::getTotalCost,
                SysAiUsageDaily::getTotalLatency, SysAiUsageDaily::getTotalFirstTokenLatency,
                SysAiUsageDaily::getTotalTokensPerSecond, SysAiUsageDaily::getTpsSamples);
        if (startDate != null) {
            wrapper.ge(SysAiUsageDaily::getStatDate, startDate);
        }
        wrapper.le(SysAiUsageDaily::getStatDate, coveredEnd);
        if (userId != null && !userId.isBlank()) {
            if (includeOrphanEffective()) {
                wrapper.and(w -> w.eq(SysAiUsageDaily::getUserId, userId)
                        .or().isNull(SysAiUsageDaily::getUserId));
            } else {
                wrapper.eq(SysAiUsageDaily::getUserId, userId);
            }
        }
        for (SysAiUsageDaily u : dailyMapper.selectList(wrapper)) {
            long calls = zero(u.getTotalCalls());
            TrendAccumulator acc = accumulateModelTrend(merged, u.getStatDate(), u.getProvider(), u.getModel(),
                    calls, zero(u.getTotalTokens()), zero(u.getTotalCost()), null, null, null);
            // 汇总表存的是可加的合计，直接相加；均值在输出时再按样本数还原
            acc.latencySum += zero(u.getTotalLatency());
            acc.latencySamples += calls;
            acc.firstLatencySum += zero(u.getTotalFirstTokenLatency());
            acc.firstLatencySamples += calls;
            acc.tpsSum += u.getTotalTokensPerSecond() == null ? 0D : u.getTotalTokensPerSecond();
            acc.tpsSamples += zero(u.getTpsSamples());
        }
    }

    /**
     * 明细段：整段读明细表（汇总表没覆盖到，或区间太短不值得拆）。
     *
     * @param startTime 开始时间（可为空）
     * @param endTime   结束时间（可为空）
     * @param userId    归属用户（可为空）
     * @return 按天升序、同天内按令牌降序的趋势点
     */
    private List<com.chua.starter.ai.support.model.vo.AiUsageModelTrendPointVO> dailyModelTrendFromRaw(
            LocalDateTime startTime, LocalDateTime endTime, String userId) {
        List<SysAiUsageMapper.DailyModelRow> rows =
                usageMapper.selectDailyModelAggregate(startTime, endTime, userId, includeOrphanEffective());
        List<com.chua.starter.ai.support.model.vo.AiUsageModelTrendPointVO> result =
                new ArrayList<>(rows.size());
        for (SysAiUsageMapper.DailyModelRow r : rows) {
            result.add(new com.chua.starter.ai.support.model.vo.AiUsageModelTrendPointVO(
                    r.statDate() != null ? r.statDate().toString() : null,
                    r.model(),
                    r.provider(),
                    zero(r.totalCalls()),
                    zero(r.totalTokens()),
                    zero(r.totalCost()),
                      // 延迟原样透传 null：0 会被前端画成「延迟极低」，而 null 能表达「当天没测到」
                      // 速率同理：无实测样本时不填，前端只对有样本的模型画线。
                    r.avgFirstTokenLatency(),
                    r.avgTotalLatency(),
                    r.avgTokensPerSecond()
            ));
        }
        return result;
    }

    /**
     * 把一行趋势数据累加进容器；已有均值时按样本数还原成合计再加（AVG 不能跨行相加）。
     *
     * @param merged           累加容器
     * @param statDate         统计日期
     * @param provider         服务商
     * @param model            模型
     * @param totalCalls       调用次数
     * @param totalTokens      令牌合计
     * @param totalCost        费用
     * @param avgFirstTokenLatency 平均首字延迟（明细段给值，汇总段给 null）
     * @param avgTotalLatency  平均总耗时（明细段给值，汇总段给 null）
     * @param avgTokensPerSecond 平均输出速度（明细段给值，汇总段给 null）
     * @return 该分组的累加容器
     */
    private TrendAccumulator accumulateModelTrend(Map<String, TrendAccumulator> merged, LocalDate statDate,
                                                  String provider, String model, long totalCalls,
                                                  long totalTokens, double totalCost,
                                                  Double avgFirstTokenLatency, Double avgTotalLatency,
                                                  Double avgTokensPerSecond) {
        String key = String.valueOf(statDate) + '\u0001' + String.valueOf(provider) + '\u0001'
                + String.valueOf(model);
        TrendAccumulator acc = merged.computeIfAbsent(key,
                k -> new TrendAccumulator(statDate, provider, model));
        acc.calls += totalCalls;
        acc.tokens += totalTokens;
        acc.cost += totalCost;
        if (avgFirstTokenLatency != null) {
            acc.firstLatencySum += avgFirstTokenLatency * totalCalls;
            acc.firstLatencySamples += totalCalls;
        }
        if (avgTotalLatency != null) {
            acc.latencySum += avgTotalLatency * totalCalls;
            acc.latencySamples += totalCalls;
        }
        if (avgTokensPerSecond != null) {
            acc.tpsSum += avgTokensPerSecond * totalCalls;
            acc.tpsSamples += totalCalls;
        }
        return acc;
    }

    /**
     * 空安全：把包装类型还原成基本类型，{@code null} 记 0。
     *
     * @param value 原始值
     * @return 非空值
     */
    private static long zero(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 空安全：把包装类型还原成基本类型，{@code null} 记 0。
     *
     * @param value 原始值
     * @return 非空值
     */
    private static double zero(Double value) {
        return value == null ? 0D : value;
    }

    /**
     * 趋势输出顺序：按天升序，同一天内按令牌降序（与明细 SQL 的 ORDER BY 一致）。
     */
    private static final Comparator<com.chua.starter.ai.support.model.vo.AiUsageModelTrendPointVO> TREND_ORDER =
            Comparator.comparing((com.chua.starter.ai.support.model.vo.AiUsageModelTrendPointVO p) ->
                            p.date() == null ? "" : p.date())
                    .thenComparing(p -> -(p.totalTokens() == null ? 0L : p.totalTokens()));

    /**
     * 趋势累加容器：两段合并时先累加合计，输出时再还原均值。
     */
    private static final class TrendAccumulator {

        /**
         * 统计日期
         */
        private final LocalDate statDate;

        /**
         * 服务商
         */
        private final String provider;

        /**
         * 模型
         */
        private final String model;

        /**
         * 调用次数
         */
        private long calls;

        /**
         * 令牌合计
         */
        private long tokens;

        /**
         * 费用（元）
         */
        private double cost;

        /**
         * 总耗时合计（毫秒）
         */
        private double latencySum;

        /**
         * 总耗时的样本数
         */
        private long latencySamples;

        /**
         * 首字延迟合计（毫秒）
         */
        private double firstLatencySum;

        /**
         * 首字延迟的样本数
         */
        private long firstLatencySamples;

        /**
         * 输出速度合计（Token/秒）
         */
        private double tpsSum;

        /**
         * 输出速度的样本数
         */
        private long tpsSamples;

        /**
         * 构造累加容器。
         *
         * @param statDate 统计日期
         * @param provider 服务商
         * @param model    模型
         */
        private TrendAccumulator(LocalDate statDate, String provider, String model) {
            this.statDate = statDate;
            this.provider = provider;
            this.model = model;
        }

        /**
         * 生成对外的趋势点；无实测样本的均值保持 null（0 会被前端画成「极低」）。
         *
         * @return 趋势点
         */
        private com.chua.starter.ai.support.model.vo.AiUsageModelTrendPointVO toVo() {
            return new com.chua.starter.ai.support.model.vo.AiUsageModelTrendPointVO(
                    statDate == null ? null : statDate.toString(),
                    model,
                    provider,
                    calls,
                    tokens,
                    cost,
                    firstLatencySamples > 0 ? firstLatencySum / firstLatencySamples : null,
                    latencySamples > 0 ? latencySum / latencySamples : null,
                    tpsSamples > 0 ? tpsSum / tpsSamples : null
            );
        }
    }

      /**
       * 统计摘要（按区间自动选源）。
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
        LocalDate coveredEnd = coveredDailyEnd(endTime);
        LocalDate startDate = startTime == null ? null : startTime.toLocalDate();
        if (coveredEnd == null || (startDate != null && startDate.isAfter(coveredEnd))) {
            return statisticsFromRaw(groupName, provider, model, startTime, endTime, userId);
        }
        UsageStatistics stats = statisticsFromDaily(groupName, provider, model, startDate, coveredEnd, userId);
        LocalDateTime tailStart = coveredEnd.plusDays(1).atStartOfDay();
        if (endTime == null || !tailStart.isAfter(endTime)) {
            mergeStatistics(stats, statisticsFromRaw(groupName, provider, model, tailStart, endTime, userId));
        }
        return stats;
    }

    /**
     * 日汇总表可信的最后一天（已留一天余量）。
     *
     * <p>不直接用 {@code MAX(stat_date)}：聚合任务按小时重算「当天」，最后一天随时可能
     * 只落了一半，读它会少算。留一天余量，把最后一天交给明细段重算。</p>
     *
     * @param endTime 请求结束时间（可为空）
     * @return 汇总段可用的最后一天；汇总表为空时为 {@code null}
     */
    private LocalDate coveredDailyEnd(LocalDateTime endTime) {
        LocalDate maxDaily = dailyMapper.selectMaxStatDate();
        if (maxDaily == null) {
            return null;
        }
        LocalDate coveredEnd = maxDaily.minusDays(1);
        LocalDate endDate = endTime == null ? LocalDate.now() : endTime.toLocalDate();
        return endDate.isBefore(coveredEnd) ? endDate : coveredEnd;
    }

    /**
     * 汇总段：读日汇总表（几百行，毫秒级）。
     */
    private UsageStatistics statisticsFromDaily(String groupName, String provider, String model,
                                                LocalDate startDate, LocalDate endDate, String userId) {
        LambdaQueryWrapper<SysAiUsageDaily> wrapper = new LambdaQueryWrapper<>();
        if (groupName != null && !groupName.isBlank()) {
            wrapper.eq(SysAiUsageDaily::getGroupName, groupName);
        }
        if (provider != null && !provider.isBlank()) {
            wrapper.eq(SysAiUsageDaily::getProvider, provider);
        }
        if (model != null && !model.isBlank()) {
            wrapper.eq(SysAiUsageDaily::getModel, model);
        }
        if (startDate != null) {
            wrapper.ge(SysAiUsageDaily::getStatDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(SysAiUsageDaily::getStatDate, endDate);
        }
        if (includeOrphanEffective()) {
            wrapper.and(w -> w.eq(SysAiUsageDaily::getUserId, userId).or().isNull(SysAiUsageDaily::getUserId));
        } else {
            wrapper.eq(SysAiUsageDaily::getUserId, userId);
        }
        List<SysAiUsageDaily> list = dailyMapper.selectList(wrapper);
        // 注意：UsageStatistics 的字段是包装类型（Long/Double），直接 += 会从 null 拆箱，
        // 先累加到局部基本类型，最后一次性赋值。
        long calls = 0L;
        long prompt = 0L;
        long completion = 0L;
        long tokens = 0L;
        long cached = 0L;
        long latency = 0L;
        long firstLatency = 0L;
        double cost = 0D;
        double standard = 0D;
        for (SysAiUsageDaily u : list) {
            calls += u.getTotalCalls() != null ? u.getTotalCalls() : 0L;
            prompt += u.getTotalPromptTokens() != null ? u.getTotalPromptTokens() : 0L;
            completion += u.getTotalCompletionTokens() != null ? u.getTotalCompletionTokens() : 0L;
            tokens += u.getTotalTokens() != null ? u.getTotalTokens() : 0L;
            cached += u.getTotalCachedTokens() != null ? u.getTotalCachedTokens() : 0L;
            cost += u.getTotalCost() != null ? u.getTotalCost() : 0D;
            standard += u.getStandardCost() != null ? u.getStandardCost() : 0D;
            latency += u.getTotalLatency() != null ? u.getTotalLatency() : 0L;
            firstLatency += u.getTotalFirstTokenLatency() != null ? u.getTotalFirstTokenLatency() : 0L;
        }
        UsageStatistics stats = new UsageStatistics();
        stats.totalCalls = calls;
        stats.totalPromptTokens = prompt;
        stats.totalCompletionTokens = completion;
        stats.totalTokens = tokens;
        stats.totalCachedTokens = cached;
        stats.totalCost = cost;
        stats.standardCost = standard;
        stats.avgLatency = calls > 0 ? (double) latency / calls : 0.0;
        stats.avgFirstTokenLatency = calls > 0 ? (double) firstLatency / calls : 0.0;
        return stats;
    }

    /**
     * 增量段：明细表聚合（原路径，只用于汇总表未覆盖的那几天）。
     */
    private UsageStatistics statisticsFromRaw(String groupName, String provider, String model,
                                              LocalDateTime startTime, LocalDateTime endTime, String userId) {
        SysAiUsageMapper.UsageStatsRow row =
                usageMapper.selectStatistics(groupName, provider, model, startTime, endTime,
                        userId, includeOrphanEffective());
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

    /**
     * 两段统计相加：计数直接加；均值先还原成总和（均值 × 调用数）再加，最后除以新的调用数。
     */
    private void mergeStatistics(UsageStatistics target, UsageStatistics extra) {
        if (extra.totalCalls <= 0 && extra.totalTokens <= 0) {
            return;
        }
        double latencySum = target.avgLatency * target.totalCalls;
        double firstSum = target.avgFirstTokenLatency * target.totalCalls;
        target.totalCalls += extra.totalCalls;
        target.totalPromptTokens += extra.totalPromptTokens;
        target.totalCompletionTokens += extra.totalCompletionTokens;
        target.totalTokens += extra.totalTokens;
        target.totalCachedTokens += extra.totalCachedTokens;
        target.totalCost += extra.totalCost;
        target.standardCost += extra.standardCost;
        latencySum += extra.avgLatency * extra.totalCalls;
        firstSum += extra.avgFirstTokenLatency * extra.totalCalls;
        target.avgLatency = target.totalCalls > 0 ? latencySum / target.totalCalls : 0.0;
        target.avgFirstTokenLatency = target.totalCalls > 0 ? firstSum / target.totalCalls : 0.0;
    }
}