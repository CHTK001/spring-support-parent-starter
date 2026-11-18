package com.chua.starter.datasearch.provider.support;

import com.chua.starter.datasearch.provider.model.ProviderPricingRefresh;
import com.chua.starter.datasearch.provider.model.ProviderPricingRule;
import com.chua.starter.datasearch.provider.spi.ProviderPricingProvider;
import com.chua.starter.datasearch.support.SpiProviders;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 定价规则出口：各实现按登记顺序合并，同一 (厂商, 模型模式, 计费项, 单位) 以先命中为准。
 *
 * <p>与目录 / 指标出口同一套口径：<b>合并</b>而不是「第一个命中算数」。定价数据分成
 * 多份来源（登记表静态规则、官方价格页抓取、厂商账单），某个来源没给某个计费项时，
 * 其余来源的值仍然可用；单个实现抛异常只丢它自己那份，不影响整体。</p>
 *
 * @author CH
 * @since 4.0.0.56
 */
@Slf4j
public final class ProviderPricings {

    private static volatile List<ProviderPricingProvider> cached;

    /**
     * 工具类，禁止实例化。
     */
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
     * 查一家厂商的定价规则（不要求上游地址）。
     *
     * @param platform 平台类型
     * @return 规则列表（未收录为空）
     */
    public static List<ProviderPricingRule> of(String platform) {
        return of(platform, null);
    }

    /**
     * 查一家厂商的定价规则（各实现合并）。
     *
     * @param platform 平台类型
     * @param baseUrl  上游基础地址，可为 null（只按厂商标识匹配）
     * @return 规则列表（未收录为空）
     */
    public static List<ProviderPricingRule> of(String platform, String baseUrl) {
        List<ProviderPricingRule> merged = Collections.emptyList();
        for (ProviderPricingProvider provider : providers()) {
            try {
                merged = union(merged, provider.query(platform, baseUrl));
            } catch (Exception e) {
                log.debug("[provider-pricing] 实现[{}]查询失败 platform={} cause={}",
                        provider.name(), platform, e.getMessage());
            }
        }
        return merged;
    }

    /**
     * 点击同步时刷新价格（实现按顺序取第一个有实时抓取能力的来源）。
     *
     * @param platform 平台类型
     * @param baseUrl  上游基础地址
     * @return 刷新结果；没有实时实现时 supported=false
     */
    public static ProviderPricingRefresh refresh(String platform, String baseUrl) {
        for (ProviderPricingProvider provider : providers()) {
            try {
                ProviderPricingRefresh refresh = provider.refresh(platform, baseUrl);
                if (refresh != null && refresh.isSupported()) {
                    return refresh;
                }
            } catch (Exception e) {
                log.debug("[provider-pricing] 实现[{}]实时抓取失败 platform={} cause={}",
                        provider.name(), platform, e.getMessage());
            }
        }
        return ProviderPricingRefresh.unsupported();
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
     * 规则去重后拼接：同一 (厂商, 模型模式, 计费项, 单位) 只保留先到的一条。
     *
     * <p>判断口径与 {@code ProviderPricingRule} 的定位一致——一条规则就是「某个厂商的
     * 某个计费项按某个模式命中的单价」，键相同即重复；价格不同不覆盖，先到的来源优先。</p>
     *
     * @param left  已合并规则，可为 null
     * @param right 新规则，可为 null
     * @return 合并后的新清单（元素不复制，保持不可变来源只读）
     */
    public static List<ProviderPricingRule> union(List<ProviderPricingRule> left,
                                                 List<ProviderPricingRule> right) {
        int leftSize = left == null ? 0 : left.size();
        int rightSize = right == null ? 0 : right.size();
        if (rightSize == 0) {
            return left == null ? Collections.emptyList() : left;
        }
        if (leftSize == 0) {
            return right;
        }
        List<ProviderPricingRule> merged = new ArrayList<>(leftSize + rightSize);
        Set<String> keys = new HashSet<>(Math.max(16, leftSize + rightSize));
        append(left, merged, keys);
        append(right, merged, keys);
        return merged;
    }

    /**
     * 追加规则，跳过已有键。
     *
     * @param source 源规则
     * @param out    结果
     * @param keys   已出现的键
     */
    private static void append(List<ProviderPricingRule> source, List<ProviderPricingRule> out,
                               Set<String> keys) {
        if (source == null) {
            return;
        }
        for (ProviderPricingRule rule : source) {
            if (rule == null) {
                continue;
            }
            if (keys.add(keyOf(rule))) {
                out.add(rule);
            }
        }
    }

    /**
     * 规则去重键。
     *
     * @param rule 规则
     * @return 键
     */
    private static String keyOf(ProviderPricingRule rule) {
        return String.valueOf(rule.getProviderId()) + '|' + rule.getModelPattern() + '|'
                + rule.getItem() + '|' + rule.getUnit();
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
