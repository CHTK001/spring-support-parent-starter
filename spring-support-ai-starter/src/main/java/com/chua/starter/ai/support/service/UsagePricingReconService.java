package com.chua.starter.ai.support.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.datasearch.pricing.UsagePricingReconciler;
import com.chua.starter.ai.support.entity.SysAiUsage;
import com.chua.starter.ai.support.mapper.SysAiUsageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用量 / 定价对账服务 —— 从 sys_ai_usage 取实际用量，交给 datasearch 对账器比对各数据源的模型指标。
 *
 * <p>把「用量已入账但费用为 0」「模型有价却缺能力」等计费/能力缺口从「只能手工 SQL 偶然发现」
 * 变成可随时执行的报告，供管理端接口与排查使用。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsagePricingReconService {

    /**
     * 用量 映射器
     */
    private final SysAiUsageMapper usageMapper;

    /**
     * 外部来源类型：三方工具用量
     */
    private static final String TYPE_EXTERNAL = "external";

    /**
     * 对账：按时间范围汇总外部用量并比对定价 / 能力。
     *
     * @param startTime 起始时间（含），可空
     * @param endTime   结束时间（含），可空
     * @return 对账报告
     */
    public UsagePricingReconciler.Report reconcile(LocalDateTime startTime, LocalDateTime endTime) {
        List<UsagePricingReconciler.UsageEntry> entries = new ArrayList<>();
        for (SysAiUsage u : listExternalUsages(startTime, endTime)) {
            entries.add(new UsagePricingReconciler.UsageEntry(
                    u.getSysAiUsageGroupName(),
                    u.getSysAiUsageProvider(),
                    u.getSysAiUsageModel(),
                    u.getSysAiUsageTotalCost() == null ? 0D : u.getSysAiUsageTotalCost(),
                    TYPE_EXTERNAL.equals(u.getSysAiUsageType())));
        }
        UsagePricingReconciler.Report report = UsagePricingReconciler.reconcile(entries);
        log.info("[UsageRecon] 对账完成，用量 {} 条，缺价模型 {} 个，零费用来源 {} 个，重复定价 {} 个",
                report.totalUsages(), report.unpricedModels().size(),
                report.zeroCostGroups().size(), report.duplicatePricings().size());
        return report;
    }

    /**
     * 查询范围内外部来源的用量记录。
     *
     * @param startTime 起始时间（含）
     * @param endTime   结束时间（含）
     * @return 用量记录
     */
    private List<SysAiUsage> listExternalUsages(LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<SysAiUsage> wrapper = new LambdaQueryWrapper<SysAiUsage>()
                .eq(SysAiUsage::getSysAiUsageType, TYPE_EXTERNAL)
                .ge(startTime != null, SysAiUsage::getSysAiUsageCallTime, startTime)
                .le(endTime != null, SysAiUsage::getSysAiUsageCallTime, endTime)
                .select(SysAiUsage::getSysAiUsageGroupName,
                        SysAiUsage::getSysAiUsageProvider,
                        SysAiUsage::getSysAiUsageModel,
                        SysAiUsage::getSysAiUsageType,
                        SysAiUsage::getSysAiUsageTotalCost);
        return usageMapper.selectList(wrapper);
    }
}
