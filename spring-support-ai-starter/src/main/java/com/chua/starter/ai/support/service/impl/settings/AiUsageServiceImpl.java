package com.chua.starter.ai.support.service.impl.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.entity.SysAiUsageDaily;
import com.chua.starter.ai.support.mapper.SysAiUsageDailyMapper;
import com.chua.starter.ai.support.mapper.SysAiUsageMapper;
import com.chua.starter.ai.support.model.AiUsageDTO;
import com.chua.starter.ai.support.service.settings.AiUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用量统计服务实现类——按 分组+服务商+模型 维度返回用量分布。
 *
 * <p><b>数据源分两段</b>：明细表 {@code sys_ai_usage} 是全量明细（百万行级，
 * 「全部时间」按模型 GROUP BY 实测 47 秒），日汇总表 {@code sys_ai_usage_daily}
 * 是同一口径的按天预聚合（几百行，实测 42 毫秒）。因此区间已被聚合覆盖的部分读汇总表，
 * 只有汇总表还没覆盖的尾巴（通常是最近 1~2 天）走明细，最后按键相加合并。</p>
 *
 * @since 2026-08-03
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiUsageServiceImpl implements AiUsageService {

    /**
     * 用量 映射器（明细分段的来源）
     */
    private final SysAiUsageMapper usageMapper;

    /**
     * 用量日汇总 映射器（汇总分段的来源）
     */
    private final SysAiUsageDailyMapper dailyMapper;

    /**
     * 按 分组+服务商+模型 维度查询用量汇总。
     *
     * @param model     模型名称（可选）
     * @param startTime 开始时间（可选）
     * @param endTime   结束时间（可选）
     * @param userId    归属用户（可为空，为空时统计全部用户）
     * @return 用量 DTO 列表，按令牌降序
     */
    @Override
    public List<AiUsageDTO> query(String model, LocalDateTime startTime, LocalDateTime endTime, String userId) {
        // 无主数据（定时同步的外部工具用量）只对管理员可见，
        // 口径与 SysAiUsageServiceImpl.includeOrphan() 一致。
        boolean includeOrphan = com.chua.starter.ai.support.util.AiUserContext.isAdmin();
        LocalDate coveredEnd = resolveCoveredEnd(startTime, endTime);
        if (coveredEnd == null) {
            return fromDetail(model, startTime, endTime, userId, includeOrphan);
        }
        Map<String, Accumulator> merged = new LinkedHashMap<>();
        collectFromDaily(merged, model, startTime == null ? null : startTime.toLocalDate(), coveredEnd,
                userId, includeOrphan);
        // 汇总表未覆盖的尾巴：从汇总段结束的次日 0 点开始重算
        LocalDateTime tailStart = coveredEnd.plusDays(1).atStartOfDay();
        if (endTime == null || !tailStart.isAfter(endTime)) {
            for (SysAiUsageMapper.UsageSummaryRow row
                    : usageMapper.selectUsageSummary(model, tailStart, endTime, userId, includeOrphan)) {
                accumulate(merged, row.groupName(), row.provider(), row.model(),
                        zero(row.totalCalls()), zero(row.totalPromptTokens()), zero(row.totalCompletionTokens()),
                        zero(row.totalTokens()), zero(row.totalCost()));
            }
        }
        List<AiUsageDTO> result = new ArrayList<>(merged.size());
        for (Accumulator acc : merged.values()) {
            result.add(acc.toDto());
        }
        // 与明细 SQL 的 ORDER BY totalTokens DESC 保持同一顺序
        result.sort((a, b) -> Long.compare(
                b.totalTokens() == null ? 0L : b.totalTokens(),
                a.totalTokens() == null ? 0L : a.totalTokens()));
        return result;
    }

    /**
     * 汇总表可信的最后一天（已减掉一天余量）。
     *
     * <p>为什么不直接用 {@code MAX(stat_date)}：聚合任务按小时重算「当天」，
     * 最后一天随时可能只落了一半的量，读它会少算。留一天余量，把最后一天交给明细段重算。
     * 若汇总表为空、或请求区间整体落在余量之后，返回 {@code null} 表示只能走明细。</p>
     *
     * @param startTime 请求开始时间（可为空）
     * @param endTime   请求结束时间（可为空）
     * @return 汇总段可用的最后一天，不可用时为 {@code null}
     */
    private LocalDate resolveCoveredEnd(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDate maxDaily = dailyMapper.selectMaxStatDate();
        if (maxDaily == null) {
            return null;
        }
        LocalDate coveredEnd = maxDaily.minusDays(1);
        LocalDate endDate = endTime == null ? LocalDate.now() : endTime.toLocalDate();
        if (endDate.isBefore(coveredEnd)) {
            coveredEnd = endDate;
        }
        LocalDate startDate = startTime == null ? null : startTime.toLocalDate();
        if (startDate != null && startDate.isAfter(coveredEnd)) {
            return null;
        }
        return coveredEnd;
    }

    /**
     * 汇总段：读日汇总表并按维度累加（表只有几百行）。
     *
     * @param merged        累加容器
     * @param model         模型名称（可选）
     * @param startDate     开始日期（可选）
     * @param coveredEnd    汇总段最后一天
     * @param userId        归属用户（可为空）
     * @param includeOrphan 是否计入无主明细（管理员口径）
     */
    private void collectFromDaily(Map<String, Accumulator> merged, String model, LocalDate startDate,
                                  LocalDate coveredEnd, String userId, boolean includeOrphan) {
        LambdaQueryWrapper<SysAiUsageDaily> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(SysAiUsageDaily::getGroupName, SysAiUsageDaily::getProvider, SysAiUsageDaily::getModel,
                SysAiUsageDaily::getTotalCalls, SysAiUsageDaily::getTotalPromptTokens,
                SysAiUsageDaily::getTotalCompletionTokens, SysAiUsageDaily::getTotalTokens,
                SysAiUsageDaily::getTotalCost);
        if (model != null && !model.isBlank()) {
            wrapper.eq(SysAiUsageDaily::getModel, model);
        }
        if (startDate != null) {
            wrapper.ge(SysAiUsageDaily::getStatDate, startDate);
        }
        wrapper.le(SysAiUsageDaily::getStatDate, coveredEnd);
        if (userId != null && !userId.isBlank()) {
            if (includeOrphan) {
                wrapper.and(w -> w.eq(SysAiUsageDaily::getUserId, userId)
                        .or().isNull(SysAiUsageDaily::getUserId));
            } else {
                wrapper.eq(SysAiUsageDaily::getUserId, userId);
            }
        }
        for (SysAiUsageDaily row : dailyMapper.selectList(wrapper)) {
            accumulate(merged, row.getGroupName(), row.getProvider(), row.getModel(),
                    zero(row.getTotalCalls()), zero(row.getTotalPromptTokens()), zero(row.getTotalCompletionTokens()),
                    zero(row.getTotalTokens()), zero(row.getTotalCost()));
        }
    }

    /**
     * 明细段：整段都读明细表（汇总表还没覆盖到，或区间太短不值得拆）。
     *
     * @param model         模型名称（可选）
     * @param startTime     开始时间（可选）
     * @param endTime       结束时间（可选）
     * @param userId        归属用户（可为空）
     * @param includeOrphan 是否计入无主明细（管理员口径）
     * @return 用量 DTO 列表
     */
    private List<AiUsageDTO> fromDetail(String model, LocalDateTime startTime, LocalDateTime endTime,
                                        String userId, boolean includeOrphan) {
        List<SysAiUsageMapper.UsageSummaryRow> rows =
                usageMapper.selectUsageSummary(model, startTime, endTime, userId, includeOrphan);
        List<AiUsageDTO> result = new ArrayList<>(rows.size());
        for (SysAiUsageMapper.UsageSummaryRow row : rows) {
            result.add(new AiUsageDTO(row.groupName(), row.provider(), row.model(),
                    zero(row.totalCost()), zero(row.totalCalls()), zero(row.totalPromptTokens()),
                    zero(row.totalCompletionTokens()), zero(row.totalTokens()), null));
        }
        return result;
    }

    /**
     * 把一个分组行累加进容器。
     *
     * @param merged           累加容器
     * @param group            分组名称
     * @param provider         服务商
     * @param model            模型
     * @param totalCalls       调用次数
     * @param promptTokens     输入令牌
     * @param completionTokens 输出令牌
     * @param totalTokens      总令牌
     * @param cost             费用
     */
    private void accumulate(Map<String, Accumulator> merged, String group, String provider, String model,
                            long totalCalls, long promptTokens, long completionTokens,
                            long totalTokens, double cost) {
        String key = String.valueOf(group) + '\u0001' + String.valueOf(provider) + '\u0001' + String.valueOf(model);
        Accumulator acc = merged.computeIfAbsent(key, k -> new Accumulator(group, provider, model));
        acc.calls += totalCalls;
        acc.promptTokens += promptTokens;
        acc.completionTokens += completionTokens;
        acc.totalTokens += totalTokens;
        acc.cost += cost;
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
     * 分组累加容器：两段合并时先累加，最后一次性生成不可变 DTO。
     */
    private static final class Accumulator {

        /**
         * 分组名称
         */
        private final String group;

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
         * 输入令牌
         */
        private long promptTokens;

        /**
         * 输出令牌
         */
        private long completionTokens;

        /**
         * 总令牌
         */
        private long totalTokens;

        /**
         * 费用（元）
         */
        private double cost;

        /**
         * 构造累加容器。
         *
         * @param group    分组名称
         * @param provider 服务商
         * @param model    模型
         */
        private Accumulator(String group, String provider, String model) {
            this.group = group;
            this.provider = provider;
            this.model = model;
        }

        /**
         * 生成对外的用量 DTO。
         *
         * @return 用量 DTO
         */
        private AiUsageDTO toDto() {
            return new AiUsageDTO(group, provider, model, cost, calls, promptTokens, completionTokens, totalTokens, null);
        }
    }
}
