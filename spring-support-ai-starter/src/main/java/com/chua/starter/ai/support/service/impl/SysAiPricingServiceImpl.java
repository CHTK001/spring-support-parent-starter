package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.entity.SysAiPricing;
import com.chua.starter.ai.support.mapper.SysAiPricingMapper;
import com.chua.starter.ai.support.service.AiModelMetricsEnrichmentService;
import com.chua.starter.ai.support.service.SysAiPricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 定价服务实现类。
 * <p>优先从 AA（Artificial Analysis）缓存查询官方定价，未命中时回退到数据库。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiPricingServiceImpl implements SysAiPricingService {

    /**
      * 定价 映射器
     */
    private final SysAiPricingMapper pricingMapper;

    /**
     * AA 模型指标服务（含定价缓存）
     */
    private final AiModelMetricsEnrichmentService metricsEnrichment;

    /**
      * 查询指定 提供者 + 模型 的最新官方定价记录。
     * <p>优先从 AA 缓存查找，未命中时回退到数据库。</p>
     *
     * @param provider 服务商标识，如 openai、deepseek，不能为 空
     * @param model    模型标识，如 gpt-4o、deepseek-对话，不能为 空
     * @return 最新定价记录，未找到时返回 空
     */
    @Override
    public SysAiPricing getLatestPricing(String provider, String model) {
 // 1. 优先从 AA 缓存查找（USD/百万令牌 → USD/千令牌）
        var rec = metricsEnrichment.getPricingRecord(model);
        if (rec != null && rec.inputPerMillion() != null && rec.outputPerMillion() != null) {
            SysAiPricing pricing = new SysAiPricing();
            pricing.setProvider(provider);
            pricing.setModel(model);
            BigDecimal inputPer1k = rec.inputPerMillion()
                    .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP);
            BigDecimal outputPer1k = rec.outputPerMillion()
                    .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP);
            pricing.setInputPricePer1k(inputPer1k.doubleValue());
            pricing.setOutputPricePer1k(outputPer1k.doubleValue());
            pricing.setCurrency("USD");
            pricing.setEffectiveDate(LocalDateTime.now());
            pricing.setSource("aa");
            return pricing;
        }

        // 2. 回退数据库
        LambdaQueryWrapper<SysAiPricing> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiPricing::getProvider, provider)
                .eq(SysAiPricing::getModel, model)
                .orderByDesc(SysAiPricing::getEffectiveDate)
                .last("limit 1");
        return pricingMapper.selectOne(wrapper);
    }

    /**
     * 同步官方定价数据到数据库。
     * <p>从 datasearch-starter 的 JSON 定价文件加载全量数据， Upsert 到 sys_ai_pricing 表。</p>
     */
    @Override
    public void syncOfficialPricing() {
        try {
            List<com.chua.common.support.ai.chat.ModelDefinition> models =
                    com.chua.common.support.datasearch.pricing.PricingSyncer.loadAll(null);
            if (models == null || models.isEmpty()) {
                log.info("[Pricing] 无定价数据可同步");
                return;
            }
            int inserted = 0;
            LocalDateTime now = LocalDateTime.now();
            for (com.chua.common.support.ai.chat.ModelDefinition md : models) {
                if (md.getId() == null || md.getProvider() == null) {
                    continue;
                }
                if (md.getInputUnitPrice() == null && md.getOutputUnitPrice() == null) {
                    continue;
                }
                LambdaQueryWrapper<SysAiPricing> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(SysAiPricing::getProvider, md.getProvider())
                        .eq(SysAiPricing::getModel, md.getId());
                SysAiPricing existing = pricingMapper.selectOne(wrapper);
                SysAiPricing pricing = new SysAiPricing();
                pricing.setProvider(md.getProvider());
                pricing.setModel(md.getId());
                if (md.getInputUnitPrice() != null) {
                    pricing.setInputPricePer1k(md.getInputUnitPrice().doubleValue());
                }
                if (md.getOutputUnitPrice() != null) {
                    pricing.setOutputPricePer1k(md.getOutputUnitPrice().doubleValue());
                }
                pricing.setCurrency(md.getCurrency() != null ? md.getCurrency() : "USD");
                pricing.setEffectiveDate(now);
                pricing.setSource("sync");
                if (existing != null) {
                    pricing.setId(existing.getId());
                    pricingMapper.updateById(pricing);
                } else {
                    pricingMapper.insert(pricing);
                }
                inserted++;
            }
            log.info("[Pricing] 官方定价同步完成，共 {} 条", inserted);
        } catch (Exception e) {
            log.warn("[Pricing] 同步官方定价失败: {}", e.getMessage());
        }
    }
}

