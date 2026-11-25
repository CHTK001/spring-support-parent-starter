// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.entity.SysAiPricing;
import com.chua.starter.ai.support.mapper.SysAiPricingMapper;
import com.chua.starter.ai.support.manage.service.SysAiModelMetricsService;
import com.chua.starter.ai.support.manage.service.SysAiModelPriceService;
import com.chua.starter.ai.support.manage.service.SysAiProviderMetricService;
import com.chua.starter.ai.support.manage.store.SysAiModelMetrics;
import com.chua.starter.ai.support.manage.store.SysAiModelPrice;
import com.chua.starter.ai.support.manage.store.SysAiProviderMetric;
import com.chua.starter.ai.support.service.SysAiPricingService;
import com.chua.starter.ai.support.util.AiModelMetricKeys;
import com.chua.starter.datasearch.provider.model.ProviderModelInfo;
import com.chua.starter.datasearch.provider.support.ProviderModelMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 定价服务实现类。
 * <p>优先从 datasearch 模型目录（models.dev 快照）查询官方定价，未命中时回退到数据库。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiPricingServiceImpl implements SysAiPricingService {

    /**
     * 缓存名称
    */
    private static final String CACHE_NAME = "aiPricing";

    /**
     * 定价 映射器
     */
    private final SysAiPricingMapper pricingMapper;

    /**
     * 全量指标库服务（计费取价回退，预估值）
     */
    private final SysAiModelMetricsService modelMetricsService;

    /**
     * 模型价格服务（价格拆分后的权威落库价，优先于指标库旧列）
     */
    private final SysAiModelPriceService modelPriceService;

    /**
     * 厂商指标系数服务（计费取价最终回退，一厂商一系数）
     */
    private final SysAiProviderMetricService providerMetricService;

    /**
     * 查询指定 提供者 + 模型 的最新官方定价记录。
     * <p>优先从 AA 缓存查找，未命中时回退到数据库。结果缓存到 Spring Cache。</p>
     *
     * @param provider 服务商标识，如 openai、deepseek，不能为 空
     * @param model    模型标识，如 gpt-4o、deepseek-对话，不能为 空
     * @return 最新定价记录，未找到时返回 空
     */
    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "#provider + ':' + #model", unless = "#result == null")
    public SysAiPricing getLatestPricing(String provider, String model) {
        // 0. 人工配置单价最高优先：管理员覆盖后不被快照/同步/估算价冲掉
        SysAiPricing manual = pricingMapper.selectOne(new LambdaQueryWrapper<SysAiPricing>()
                .eq(SysAiPricing::getProvider, provider)
                .eq(SysAiPricing::getModel, model)
                .eq(SysAiPricing::getSource, SOURCE_MANUAL)
                .last("limit 1"));
        if (manual != null) {
            return manual;
        }

        // 1. 优先从 datasearch 模型目录查找（USD/百万令牌 → USD/千令牌）
        ProviderModelInfo info = ProviderModelMetrics.of(model);
        if (info != null && info.getPriceInputPerMusd() != null && info.getPriceOutputPerMusd() != null) {
            SysAiPricing pricing = new SysAiPricing();
            pricing.setProvider(provider);
            pricing.setModel(model);
            BigDecimal inputPer1k = BigDecimal.valueOf(info.getPriceInputPerMusd())
                    .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP);
            BigDecimal outputPer1k = BigDecimal.valueOf(info.getPriceOutputPerMusd())
                    .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP);
            pricing.setInputPricePer1k(inputPer1k.doubleValue());
            pricing.setOutputPricePer1k(outputPer1k.doubleValue());
            pricing.setCurrency("USD");
            pricing.setEffectiveDate(LocalDateTime.now());
            pricing.setSource("datasearch");
            return pricing;
        }

        // 2. 回退数据库 sys_ai_pricing
        LambdaQueryWrapper<SysAiPricing> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiPricing::getProvider, provider)
                .eq(SysAiPricing::getModel, model)
                .orderByDesc(SysAiPricing::getEffectiveDate)
                .last("limit 1");
        SysAiPricing dbPricing = pricingMapper.selectOne(wrapper);
        if (dbPricing != null) {
            return dbPricing;
        }

        // 3. 回退价格库（价格拆分后的权威落库价，USD/百万 token → USD/千 token）
        SysAiModelPrice priceRow = modelPriceService.findByKey(
                AiModelMetricKeys.normalizeKey(model));
        if (priceRow != null && (priceRow.getPriceInput() != null
                || priceRow.getPriceOutput() != null)) {
            SysAiPricing priced = new SysAiPricing();
            priced.setProvider(provider);
            priced.setModel(model);
            BigDecimal inputPer1k = priceRow.getPriceInput() == null ? null
                    : priceRow.getPriceInput()
                            .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP);
            BigDecimal outputPer1k = priceRow.getPriceOutput() == null ? null
                    : priceRow.getPriceOutput()
                            .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP);
            priced.setInputPricePer1k(inputPer1k == null ? null : inputPer1k.doubleValue());
            priced.setOutputPricePer1k(outputPer1k == null ? null : outputPer1k.doubleValue());
            priced.setCurrency("USD");
            priced.setEffectiveDate(LocalDateTime.now());
            priced.setSource("model_price");
            return priced;
        }

        // 3.5. 回退全量指标表旧价格列（价格拆分前的历史数据，按模型名关联，价格为预估值）
        SysAiModelMetrics metric = modelMetricsService.findForEstimate(model);
        if (metric != null && metric.getPriceInputPerMusd() != null
                && metric.getPriceOutputPerMusd() != null) {
            SysAiPricing estimated = new SysAiPricing();
            estimated.setProvider(provider);
            estimated.setModel(model);
            BigDecimal inputPer1k = metric.getPriceInputPerMusd()
                    .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP);
            BigDecimal outputPer1k = metric.getPriceOutputPerMusd()
                    .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP);
            estimated.setInputPricePer1k(inputPer1k.doubleValue());
            estimated.setOutputPricePer1k(outputPer1k.doubleValue());
            estimated.setCurrency("USD");
            estimated.setEffectiveDate(LocalDateTime.now());
            estimated.setSource("metrics_estimate");
            return estimated;
        }

        // 4. 最终回退厂商指标系数（一厂商一系数，价格为该厂商全部模型的中位数预估值）
        SysAiProviderMetric providerMetric = providerMetricService.findForEstimate(provider);
        if (providerMetric != null && providerMetric.getPriceInputPerMusd() != null
                && providerMetric.getPriceOutputPerMusd() != null) {
            SysAiPricing estimated = new SysAiPricing();
            estimated.setProvider(provider);
            estimated.setModel(model);
            BigDecimal inputPer1k = providerMetric.getPriceInputPerMusd()
                    .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP);
            BigDecimal outputPer1k = providerMetric.getPriceOutputPerMusd()
                    .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP);
            estimated.setInputPricePer1k(inputPer1k.doubleValue());
            estimated.setOutputPricePer1k(outputPer1k.doubleValue());
            estimated.setCurrency("USD");
            estimated.setEffectiveDate(LocalDateTime.now());
            estimated.setSource("provider_estimate");
            return estimated;
        }
        return null;
    }

    /**
     * 同步官方定价数据到数据库。
     * <p>从 datasearch-starter 的各数据源加载全量数据，每次同步前清空旧数据，
     * 全量插入到 sys_ai_pricing 表。同步完成后清除定价缓存。</p>
     *
     * <p>多源会对同一 (厂商, 模型) 给出多条报价（如 Artificial Analysis 与 OpenRouter 都收录
     * gpt-4o）。这里按数据源优先级（models.dev 官方价 &gt; Artificial Analysis &gt; OpenRouter）
     * 只保留优先级最高的一条，并统一把厂商名归一化为小写，避免因大小写差异被当成不同厂商
     * 而重复入库。厂商与模型均归一化后仍重复的，按先到先得。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public void syncOfficialPricing() {
        try {
            // 人工单价不参与同步覆盖：先留档，清空重插后原样回填
            List<SysAiPricing> manualRows = pricingMapper.selectList(
                    new LambdaQueryWrapper<SysAiPricing>()
                            .eq(SysAiPricing::getSource, SOURCE_MANUAL));
            java.util.Set<String> manualKeys = new java.util.HashSet<>();
            for (SysAiPricing m : manualRows) {
                if (m.getProvider() != null && m.getModel() != null) {
                    manualKeys.add(m.getProvider().trim().toLowerCase(java.util.Locale.ROOT)
                            + " " + m.getModel().trim().toLowerCase(java.util.Locale.ROOT));
                }
            }

            // 逐源加载并按优先级排序，来源优先级高的先写入，同键后者不覆盖前者
            List<com.chua.common.support.ai.chat.ModelDefinition> models =
                    loadBySourcePriority();
            if (models == null || models.isEmpty()) {
                log.info("[Pricing] 无定价数据可同步，清空旧数据（人工单价 {} 条保留）", manualRows.size());
                pricingMapper.delete(null);
                manualRows.forEach(pricingMapper::insert);
                return;
            }

            // 每次同步前清空旧数据
            int deleted = pricingMapper.delete(null);
            log.info("[Pricing] 清空旧定价数据 {} 条", deleted);

            int inserted = 0;
            int skippedDuplicate = 0;
            int skippedManual = 0;
            java.util.Set<String> seen = new java.util.HashSet<>();
            LocalDateTime now = LocalDateTime.now();
            for (com.chua.common.support.ai.chat.ModelDefinition md : models) {
                if (md.getId() == null || md.getProvider() == null) {
                    continue;
                }
                if (md.getInputUnitPrice() == null && md.getOutputUnitPrice() == null) {
                    continue;
                }
                // 厂商归一化为小写，模型保持原样；(厂商, 模型) 唯一
                String provider = md.getProvider().trim().toLowerCase(java.util.Locale.ROOT);
                String model = md.getId().trim();
                String key = provider + " "
                        + model.toLowerCase(java.util.Locale.ROOT);
                if (!seen.add(key)) {
                    skippedDuplicate++;
                    continue;
                }
                // 该 (厂商, 模型) 已有人工单价，跳过同步价，避免覆盖
                if (manualKeys.contains(key)) {
                    skippedManual++;
                    continue;
                }
                SysAiPricing pricing = new SysAiPricing();
                pricing.setProvider(provider);
                pricing.setModel(model);
                // 定价表字段单位为 币种/千令牌，而 ModelDefinition 单价为 币种/百万令牌
                if (md.getInputUnitPrice() != null) {
                    pricing.setInputPricePer1k(md.getInputUnitPrice()
                            .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP).doubleValue());
                }
                if (md.getOutputUnitPrice() != null) {
                    pricing.setOutputPricePer1k(md.getOutputUnitPrice()
                            .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP).doubleValue());
                }
                pricing.setCurrency(md.getCurrency() != null ? md.getCurrency() : "USD");
                pricing.setEffectiveDate(now);
                pricing.setSource("sync");
                pricingMapper.insert(pricing);
                inserted++;
            }
            // 回填人工单价（保持原主键与生效时间，避免被同步价冲掉）
            manualRows.forEach(pricingMapper::insert);
            log.info("[Pricing] 官方定价同步完成，共插入 {} 条，按来源优先级去重 {} 条，人工覆盖跳过 {} 条",
                    inserted, skippedDuplicate, skippedManual);
        } catch (Exception e) {
            log.warn("[Pricing] 同步官方定价失败: {}", e.getMessage());
        }
    }

    /**
     * 按数据源优先级加载全量模型定价（优先级高者在前）。
     *
     * <p>优先级：models.dev（厂商官方目录价）&gt; Artificial Analysis &gt; OpenRouter。
     * 未能识别的数据源排在最后，保证已知权威源的价格优先入库。</p>
     *
     * @return 按优先级排序的模型定价列表
     */
    private List<com.chua.common.support.ai.chat.ModelDefinition> loadBySourcePriority() {
        java.util.Map<String, com.chua.common.support.datasearch.pricing.spi.ModelMetricsProvider> providers;
        try {
            providers = com.chua.common.support.spi.ServiceProvider
                    .of(com.chua.common.support.datasearch.pricing.spi.ModelMetricsProvider.class).list();
        } catch (Exception e) {
            return com.chua.common.support.datasearch.pricing.PricingSyncer.loadAll(null);
        }
        if (providers == null || providers.isEmpty()) {
            return com.chua.common.support.datasearch.pricing.PricingSyncer.loadAll(null);
        }
        List<com.chua.common.support.datasearch.pricing.spi.ModelMetricsProvider> ordered =
                new java.util.ArrayList<>(providers.values());
        ordered.removeIf(java.util.Objects::isNull);
        ordered.sort(java.util.Comparator.comparingInt(p -> sourcePriority(p.name())));
        List<com.chua.common.support.ai.chat.ModelDefinition> all = new java.util.ArrayList<>();
        for (var p : ordered) {
            try {
                var metrics = p.getMetrics();
                if (metrics != null) {
                    all.addAll(metrics);
                }
            } catch (Exception e) {
                log.warn("[Pricing] 读取数据源[{}]失败: {}", p.name(), e.getMessage());
            }
        }
        return all;
    }

    /**
     * 数据源优先级：数字越小越优先（先入库）。
     *
     * @param name 数据源名
     * @return 优先级
     */
    private static int sourcePriority(String name) {
        if (name == null) {
            return 99;
        }
        return switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "modelsdev" -> 0;
            case "artificialanalysis" -> 1;
            case "openrouter" -> 2;
            default -> 50;
        };
    }

    /**
     * 查询人工配置的单价列表（source=manual）。
     *
     * @param keyword 厂商/模型关键字（可空）
     * @return 人工单价列表
     */
    @Override
    public List<SysAiPricing> listManualPricing(String keyword) {
        LambdaQueryWrapper<SysAiPricing> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiPricing::getSource, SOURCE_MANUAL);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(SysAiPricing::getProvider, kw)
                    .or().like(SysAiPricing::getModel, kw));
        }
        wrapper.orderByAsc(SysAiPricing::getProvider).orderByAsc(SysAiPricing::getModel);
        return pricingMapper.selectList(wrapper);
    }

    /**
     * 保存人工单价（按 厂商 + 模型 原行覆盖，source 置为 manual）。
     *
     * <p>厂商统一归一化为小写；币种缺省 USD。覆盖已有行（无论原来源）时保留原主键，
     * 仅更新单价/币种/生效时间并把来源改为 manual。保存后清除定价缓存。</p>
     *
     * @param input 单价数据（价格单位：币种/千令牌）
     * @return 保存后的单价行
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public SysAiPricing saveManualPricing(SysAiPricing input) {
        if (input == null || input.getProvider() == null || input.getProvider().isBlank()
                || input.getModel() == null || input.getModel().isBlank()) {
            throw new IllegalArgumentException("服务商与模型名称不能为空");
        }
        if (input.getInputPricePer1k() == null && input.getOutputPricePer1k() == null) {
            throw new IllegalArgumentException("输入单价与输出单价至少填写一项");
        }
        String provider = input.getProvider().trim().toLowerCase(java.util.Locale.ROOT);
        String model = input.getModel().trim();
        String currency = input.getCurrency() == null || input.getCurrency().isBlank()
                ? "USD" : input.getCurrency().trim().toUpperCase(java.util.Locale.ROOT);
        LocalDateTime now = LocalDateTime.now();

        SysAiPricing existing = pricingMapper.selectOne(new LambdaQueryWrapper<SysAiPricing>()
                .eq(SysAiPricing::getProvider, provider)
                .eq(SysAiPricing::getModel, model)
                .last("limit 1"));
        if (existing == null) {
            SysAiPricing row = new SysAiPricing();
            row.setProvider(provider);
            row.setModel(model);
            row.setInputPricePer1k(input.getInputPricePer1k());
            row.setOutputPricePer1k(input.getOutputPricePer1k());
            row.setCurrency(currency);
            row.setEffectiveDate(now);
            row.setSource(SOURCE_MANUAL);
            pricingMapper.insert(row);
            log.info("[Pricing] 新增人工单价 {}/{} in={} out={} {}", provider, model,
                    input.getInputPricePer1k(), input.getOutputPricePer1k(), currency);
            return row;
        }
        existing.setInputPricePer1k(input.getInputPricePer1k());
        existing.setOutputPricePer1k(input.getOutputPricePer1k());
        existing.setCurrency(currency);
        existing.setEffectiveDate(now);
        existing.setSource(SOURCE_MANUAL);
        pricingMapper.updateById(existing);
        log.info("[Pricing] 覆盖人工单价 {}/{} in={} out={} {}", provider, model,
                input.getInputPricePer1k(), input.getOutputPricePer1k(), currency);
        return existing;
    }

    /**
     * 删除人工单价（仅允许删除 manual 行），删除后该模型恢复同步价/估算价。
     *
     * @param id sys_ai_pricing 主键
     * @return 是否删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public boolean deleteManualPricing(Long id) {
        if (id == null) {
            return false;
        }
        SysAiPricing row = pricingMapper.selectById(id);
        if (row == null || !SOURCE_MANUAL.equals(row.getSource())) {
            return false;
        }
        pricingMapper.deleteById(id);
        log.info("[Pricing] 删除人工单价 {}/{}，该模型恢复同步价/估算价", row.getProvider(), row.getModel());
        return true;
    }
}