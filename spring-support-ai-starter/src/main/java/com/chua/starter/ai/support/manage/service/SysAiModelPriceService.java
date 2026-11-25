// [P3C A20 豁免] A20/A30/A31: 含日志/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.chua.starter.ai.support.manage.mapper.SysAiModelPriceMapper;
import com.chua.starter.ai.support.manage.store.SysAiModelPrice;
import com.chua.starter.ai.support.util.AiModelMetricKeys;
import com.chua.starter.datasearch.provider.support.ProviderModelMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模型价格库服务（{@code sys_ai_model_price}）。
 *
 * <p>价格业务从 {@link SysAiModelMetricsService} 拆出：上游 / 目录同步的
 * 价格 upsert 与先删后增、价格侧手动编辑与锁定、峰谷定价规则合并。
 * 指标表旧价格列仅作历史回退，不再新增写入。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiModelPriceService {

    /**
     * 属于价格库的字段键（与指标表旧价格键同名，便于快照直接分流）。
     */
    public static final Set<String> PRICE_FIELDS = Set.of(
            "price_input_per_musd", "price_output_per_musd",
            "price_cache_hit_per_musd", "price_cache_write_per_musd", "cost_per_task");

    /**
     * manualLocked 序列化：wrapper.set 不自动应用字段 TypeHandler，需手动转 JSON
     */
    private static final com.fasterxml.jackson.databind.ObjectMapper LOCK_OM =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * 价格库 映射器
     */
    private final SysAiModelPriceMapper priceMapper;

    /**
     * 判断字段是否属于价格库。
     *
     * @param field 指标 / 价格字段键
     * @return 属于价格库返回 {@code true}
     */
    public static boolean isPriceField(String field) {
        return field != null && PRICE_FIELDS.contains(field);
    }

    /**
     * 按归一化模型键查价格行。
     *
     * @param modelKey 归一化模型键
     * @return 价格行；不存在返回 {@code null}
     */
    public SysAiModelPrice findByKey(String modelKey) {
        if (modelKey == null || modelKey.isBlank()) {
            return null;
        }
        return priceMapper.selectOne(new LambdaQueryWrapper<SysAiModelPrice>()
                .eq(SysAiModelPrice::getModelKey, modelKey).last("limit 1"));
    }

    /**
     * 全表价格行按模型键建索引。
     *
     * @return 模型键 → 价格行
     */
    public Map<String, SysAiModelPrice> findAllAsMap() {
        Map<String, SysAiModelPrice> map = new HashMap<>();
        for (SysAiModelPrice row : priceMapper.selectList(null)) {
            if (row.getModelKey() != null) {
                map.put(row.getModelKey(), row);
            }
        }
        return map;
    }

    /**
     * 上游 / 目录计费快照 → 价格库（upsert，逐行调用）。
     *
     * <p>语义与指标侧 {@code syncUpstreamPricing} 的价格部分一致：
     * 新模型直接插入；已有行按「手动锁定 &gt; 他源已有值 &gt; 本源覆盖」的顺序让步，
     * 定价规则按 {@code provider_id} 合并（保留其它厂商规则）。</p>
     *
     * @param modelId    上游模型 id（未归一化）
     * @param providerId 服务商标识
     * @param snapshot   价格快照（{@code UPSTREAM_PRICE_KEYS} + {@code pricing_rules}）
     * @param dataSource 数据源标识
     * @return {@code inserted} / {@code updated}；无变化返回 {@code null}
     */
    @Transactional(rollbackFor = Exception.class)
    public String upsertFromSnapshot(String modelId, String providerId,
                                     Map<String, Object> snapshot, String dataSource) {
        String key = truncate(AiModelMetricKeys.normalizeKey(modelId), 160);
        if (key.isEmpty() || snapshot == null || snapshot.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        SysAiModelPrice cur = findByKey(key);
        if (cur == null) {
            SysAiModelPrice entity = new SysAiModelPrice();
            entity.setModelKey(key);
            entity.setModelName(truncate(modelId, 160));
            entity.setProvider(truncate(providerId, 64));
            applySnapshotValues(entity, snapshot);
            entity.setPricingRules(rulesOf(snapshot.get("pricing_rules")));
            entity.setSource(dataSource);
            entity.setStatus(1);
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            priceMapper.insert(entity);
            return "inserted";
        }
        Set<String> locked = lockedPriceFields(cur);
        boolean owned = dataSource.equals(cur.getSource());
        LambdaUpdateWrapper<SysAiModelPrice> uw = new LambdaUpdateWrapper<>();
        uw.eq(SysAiModelPrice::getId, cur.getId());
        boolean changed = false;
        for (String priceKey : PRICE_FIELDS) {
            if (!snapshot.containsKey(priceKey) || locked.contains(priceKey)) {
                continue;
            }
            if (priceValue(cur, priceKey) != null && !owned) {
                continue;
            }
            BigDecimal value = bd(snapshot, priceKey);
            if (value == null) {
                continue;
            }
            uw.set(priceColumn(priceKey), value);
            changed = true;
        }
        List<Map<String, Object>> rules = rulesOf(snapshot.get("pricing_rules"));
        if (rules != null && !locked.contains("pricing_rules")) {
            // 同一模型可能由多家上游提供，价格不同；价格行按 model_key 全局唯一，
            // 按 provider_id 合并，保留其它厂商的规则，只替换本厂商这一组。
            List<Map<String, Object>> merged = mergeProviderRules(
                    cur.getPricingRules(), rules, providerId);
            if (!merged.equals(cur.getPricingRules())) {
                // JSON 字段不能用 Wrapper.set（不走 TypeHandler），单独走 updateById
                SysAiModelPrice rulesUpdate = new SysAiModelPrice();
                rulesUpdate.setId(cur.getId());
                rulesUpdate.setPricingRules(merged);
                priceMapper.updateById(rulesUpdate);
                changed = true;
            }
        }
        if (!changed) {
            return null;
        }
        uw.set(SysAiModelPrice::getUpdateTime, now);
        priceMapper.update(null, uw);
        return "updated";
    }

    /**
     * 目录计费快照 → 价格库（<b>先删后增</b>，按数据源整体替换，逐行调用）。
     *
     * <p>语义与指标侧 {@code replaceUpstreamPricing} 的价格部分一致：
     * 来源就是本次 {@code dataSource} 且价格字段未被手动锁定的行先删后增；
     * 其它来源或已锁定的行不动。</p>
     *
     * @param modelId    上游模型 id（未归一化）
     * @param providerId 服务商标识
     * @param snapshot   价格快照
     * @param dataSource 数据源标识
     * @return {@code replaced} / {@code skipped}
     */
    @Transactional(rollbackFor = Exception.class)
    public String replaceFromSnapshot(String modelId, String providerId,
                                      Map<String, Object> snapshot, String dataSource) {
        String key = truncate(AiModelMetricKeys.normalizeKey(modelId), 160);
        if (key.isEmpty() || snapshot == null || snapshot.isEmpty()) {
            return "skipped";
        }
        SysAiModelPrice cur = findByKey(key);
        if (cur != null) {
            boolean sameSource = cur.getSource() != null && cur.getSource().equals(dataSource);
            if (!sameSource || !lockedPriceFields(cur).isEmpty()) {
                return "skipped";
            }
            priceMapper.deleteById(cur.getId());
        }
        long now = System.currentTimeMillis();
        SysAiModelPrice entity = new SysAiModelPrice();
        entity.setModelKey(key);
        entity.setModelName(truncate(modelId, 160));
        entity.setProvider(truncate(providerId, 64));
        applySnapshotValues(entity, snapshot);
        entity.setPricingRules(rulesOf(snapshot.get("pricing_rules")));
        entity.setSource(dataSource);
        entity.setStatus(1);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        priceMapper.insert(entity);
        return "replaced";
    }

    /**
     * 价格侧手动修改并锁定（与指标侧 {@code applyManual} 的价格拆分配套）。
     *
     * <p>只处理 {@link #PRICE_FIELDS} 中的字段；显式赋值的字段自动进入锁定集，
     * 被解锁的字段立即用指标源（datasearch 目录）当前值回填。</p>
     *
     * @param key      归一化模型键
     * @param values   价格字段 → 数值（可为 {@code null}）
     * @param locked   要锁定的字段集合（完整覆盖，可为 {@code null}）
     * @param operator 操作人
     * @return 更新后的价格行；无价格字段改动返回 {@code null}
     */
    @Transactional(rollbackFor = Exception.class)
    public SysAiModelPrice applyManualPrice(String key, Map<String, BigDecimal> values,
                                            List<String> locked, String operator) {
        Map<String, BigDecimal> priceValues = new java.util.LinkedHashMap<>();
        if (values != null) {
            for (Map.Entry<String, BigDecimal> en : values.entrySet()) {
                if (isPriceField(en.getKey())) {
                    priceValues.put(en.getKey(), en.getValue());
                }
            }
        }
        List<String> lockedPrice = new ArrayList<>();
        if (locked != null) {
            for (String f : locked) {
                if (isPriceField(f) && !lockedPrice.contains(f)) {
                    lockedPrice.add(f);
                }
            }
        }
        if (priceValues.isEmpty() && lockedPrice.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        SysAiModelPrice cur = findByKey(key);
        if (cur == null) {
            // 指标行存在但价格行尚未建立：首次手动定价即建档
            SysAiModelPrice entity = new SysAiModelPrice();
            entity.setModelKey(key);
            entity.setStatus(1);
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            for (Map.Entry<String, BigDecimal> en : priceValues.entrySet()) {
                setPriceValue(entity, en.getKey(), en.getValue());
            }
            entity.setManualLocked(lockedPrice.isEmpty() ? null : lockedPrice);
            if (!lockedPrice.isEmpty()) {
                entity.setManualUpdatedAt(now);
                entity.setManualUpdatedBy(operator == null ? "admin" : operator);
            }
            priceMapper.insert(entity);
            return entity;
        }
        // 锁定集 = 显式声明锁定的 + 本次显式赋值的（与指标侧口径一致）
        Set<String> normLocked = new LinkedHashSet<>(lockedPrice);
        normLocked.addAll(priceValues.keySet());
        // 旧锁定里的价格字段，本轮未继续锁定的视为解锁 → 用指标源当前值回填
        List<String> oldLocked = lockedPriceFields(cur).stream().toList();
        List<String> unlocked = new ArrayList<>();
        for (String f : oldLocked) {
            if (!normLocked.contains(f)) {
                unlocked.add(f);
            }
        }
        Map<String, Object> sourceMetrics = unlocked.isEmpty()
                ? Map.of() : ProviderModelMetrics.metricsOf(ProviderModelMetrics.of(key));
        LambdaUpdateWrapper<SysAiModelPrice> uw = new LambdaUpdateWrapper<>();
        uw.eq(SysAiModelPrice::getId, cur.getId());
        for (Map.Entry<String, BigDecimal> en : priceValues.entrySet()) {
            uw.set(priceColumn(en.getKey()), en.getValue());
        }
        for (String field : unlocked) {
            uw.set(priceColumn(field), sourcePriceValue(sourceMetrics, field));
        }
        List<String> lockedList = new ArrayList<>(normLocked);
        uw.set(SysAiModelPrice::getManualLocked, writeLocked(lockedList));
        if (lockedList.isEmpty()) {
            uw.set(SysAiModelPrice::getManualUpdatedAt, null);
            uw.set(SysAiModelPrice::getManualUpdatedBy, null);
        } else {
            uw.set(SysAiModelPrice::getManualUpdatedAt, now);
            uw.set(SysAiModelPrice::getManualUpdatedBy, operator == null ? "admin" : operator);
        }
        uw.set(SysAiModelPrice::getUpdateTime, now);
        priceMapper.update(null, uw);
        return findByKey(key);
    }

    /**
     * 按模型键删除价格行。
     *
     * @param key 归一化模型键
     * @return 是否删除成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByKey(String key) {
        SysAiModelPrice cur = findByKey(key);
        if (cur == null) {
            return false;
        }
        return priceMapper.deleteById(cur.getId()) > 0;
    }

    /**
     * 价格行的手动锁定字段（只认价格字段名，历史遗留的其它条目忽略）。
     *
     * @param row 价格行
     * @return 锁定的价格字段集合
     */
    public static Set<String> lockedPriceFields(SysAiModelPrice row) {
        if (row == null || row.getManualLocked() == null) {
            return Set.of();
        }
        Set<String> locked = new HashSet<>();
        for (String f : row.getManualLocked()) {
            if (isPriceField(f)) {
                locked.add(f);
            }
        }
        return locked;
    }

    /**
     * 价格键 → 价格库列。
     *
     * @param key 价格键
     * @return 列引用
     */
    public static SFunction<SysAiModelPrice, ?> priceColumn(String key) {
        return switch (key) {
            case "price_input_per_musd" -> SysAiModelPrice::getPriceInput;
            case "price_output_per_musd" -> SysAiModelPrice::getPriceOutput;
            case "price_cache_hit_per_musd" -> SysAiModelPrice::getPriceCacheHit;
            case "price_cache_write_per_musd" -> SysAiModelPrice::getPriceCacheWrite;
            default -> SysAiModelPrice::getCostPerTask;
        };
    }

    /**
     * 读价格行当前值。
     *
     * @param row 价格行
     * @param key 价格键
     * @return 当前值
     */
    public static BigDecimal priceValue(SysAiModelPrice row, String key) {
        return switch (key) {
            case "price_input_per_musd" -> row.getPriceInput();
            case "price_output_per_musd" -> row.getPriceOutput();
            case "price_cache_hit_per_musd" -> row.getPriceCacheHit();
            case "price_cache_write_per_musd" -> row.getPriceCacheWrite();
            default -> row.getCostPerTask();
        };
    }

    /**
     * 把快照中的价格键值写入实体（仅存在的键）。
     *
     * @param entity   价格实体
     * @param snapshot 价格快照
     */
    private static void applySnapshotValues(SysAiModelPrice entity, Map<String, Object> snapshot) {
        for (String priceKey : PRICE_FIELDS) {
            BigDecimal value = bd(snapshot, priceKey);
            if (value != null) {
                setPriceValue(entity, priceKey, value);
            }
        }
    }

    /**
     * 按价格键写实体字段。
     *
     * @param entity 价格实体
     * @param key    价格键
     * @param value  数值
     */
    private static void setPriceValue(SysAiModelPrice entity, String key, BigDecimal value) {
        switch (key) {
            case "price_input_per_musd" -> entity.setPriceInput(value);
            case "price_output_per_musd" -> entity.setPriceOutput(value);
            case "price_cache_hit_per_musd" -> entity.setPriceCacheHit(value);
            case "price_cache_write_per_musd" -> entity.setPriceCacheWrite(value);
            default -> entity.setCostPerTask(value);
        }
    }

    /**
     * 指标源快照中的价格值（解锁回填用；目录未给返回 {@code null}）。
     *
     * @param metrics 指标源快照
     * @param field   价格键
     * @return 数值
     */
    private static BigDecimal sourcePriceValue(Map<String, Object> metrics, String field) {
        if (metrics == null) {
            return null;
        }
        return bd(metrics, field);
    }

    /**
     * 合并定价规则：保留其它厂商规则，只替换本厂商这一组。
     *
     * @param current    数据库现有规则
     * @param incoming   本次同步规则
     * @param providerId 本次厂商
     * @return 合并后的规则
     */
    public static List<Map<String, Object>> mergeProviderRules(List<Map<String, Object>> current,
                                                               List<Map<String, Object>> incoming,
                                                               String providerId) {
        if (current == null || current.isEmpty()) {
            return incoming;
        }
        // providerId 是网关注册的临时/实例 id，规则里的 provider_id 是定价登记表的厂商 id
        // （如 anthropic / opencode）。合并时以规则自带厂商 id 为准，否则同一厂商会
        // 在每次同步时被当成「其它厂商」保留下来，越叠越多。
        String ruleOwner = providerId;
        for (Map<String, Object> rule : incoming) {
            Object id = rule == null ? null : rule.get("provider_id");
            if (id != null && !String.valueOf(id).isBlank()) {
                ruleOwner = String.valueOf(id);
                break;
            }
        }
        List<Map<String, Object>> merged = new ArrayList<>(current.size() + incoming.size());
        for (Map<String, Object> rule : current) {
            Object id = rule == null ? null : rule.get("provider_id");
            if (id == null || !ruleOwner.equals(String.valueOf(id))) {
                merged.add(rule);
            }
        }
        merged.addAll(incoming);
        return merged;
    }

    /**
     * 把定价规则 JSON 转成可落 JSON 列的 Map 列表。
     *
     * @param value 原始值
     * @return 规则列表；非数组或为空时返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> rulesOf(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> rules = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                rules.add((Map<String, Object>) map);
            }
        }
        return rules.isEmpty() ? null : rules;
    }

    /**
     * 序列化锁定字段列表为 JSON 字符串。
     *
     * @param locked 锁定字段
     * @return JSON 串；序列化失败返回 {@code null}
     */
    private static String writeLocked(List<String> locked) {
        try {
            return LOCK_OM.writeValueAsString(locked);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("[ModelPrice] 锁定字段序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从快照取 BigDecimal，缺省返回 {@code null}。
     *
     * @param m   快照
     * @param key 键
     * @return 数值
     */
    private static BigDecimal bd(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number num) {
            return BigDecimal.valueOf(num.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 截断字符串到最大长度。
     *
     * @param value  原值
     * @param maxLen 最大长度
     * @return 截断后的值；入参为空时返回空串
     */
    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}
