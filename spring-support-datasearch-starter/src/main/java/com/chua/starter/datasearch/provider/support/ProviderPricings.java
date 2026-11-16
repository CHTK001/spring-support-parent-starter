package com.chua.starter.datasearch.provider.support;

import com.chua.starter.datasearch.provider.model.ProviderPricingRule;
import com.chua.starter.datasearch.provider.spi.ProviderPricingProvider;
import com.chua.starter.datasearch.support.SpiProviders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 定价规则出口：各实现按登记顺序合并，同一 (item, unit, modelPattern) 以先命中为准。
 *
 * @author CH
 * @since 4.0.0.56
 */
public final class ProviderPricings {

    private static volatile List<ProviderPricingProvider> cached;

    private ProviderPricings() {
    }

    /**
     * 全部实现。
     *
     * @return 实现列表
     */
    public static List<ProviderPricingProvider> providers() {
        List<ProviderPricingProvider> current = cached;
        if (current == null || current.isEmpty()) {
            current = SpiProviders.list(ProviderPricingProvider.class);
            cached = current;
        }
        return current;
    }

    /**
     * 查一家厂商的定价规则。
     *
     * @param platform 平台类型
     * @param baseUrl  上游基础地址
     * @return 规则列表（未收录为空）
     */
    public static List<ProviderPricingRule> of(String platform, String baseUrl) {
        for (ProviderPricingProvider provider : providers()) {
            List<ProviderPricingRule> rules = provider.query(platform, baseUrl);
            if (rules != null && !rules.isEmpty()) {
                return rules;
            }
        }
        return Collections.emptyList();
    }

    /**
     * 过滤出命中指定模型的规则。
     *
     * @param rules   全部规则
     * @param modelId 模型标识
     * @return 命中规则
     */
    public static List<ProviderPricingRule> forModel(List<ProviderPricingRule> rules, String modelId) {
        if (rules == null || rules.isEmpty()) {
            return Collections.emptyList();
        }
        List<ProviderPricingRule> hits = new ArrayList<>(rules.size());
        for (ProviderPricingRule rule : rules) {
            if (rule.matches(modelId)) {
                hits.add(rule);
            }
        }
        return hits;
    }

    /**
     * 实现标识。
     *
     * @return 标识
     */
    public static List<String> kinds() {
        List<String> kinds = new ArrayList<>(4);
        for (ProviderPricingProvider provider : providers()) {
            kinds.add(provider.name());
        }
        return kinds;
    }

    /**
     * 规则总数。
     *
     * @return 条数
     */
    public static int size() {
        int total = 0;
        for (ProviderPricingProvider provider : providers()) {
            total += provider.size();
        }
        return total;
    }
}
