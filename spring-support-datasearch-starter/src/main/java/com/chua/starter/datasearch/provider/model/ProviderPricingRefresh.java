package com.chua.starter.datasearch.provider.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 一次定价刷新结果。
 *
 * <p>区分三种情况：</p>
 * <ul>
 *   <li>{@code supported=false}：这家厂商没有实时抓取实现，调用方沿用已有规则；</li>
 *   <li>{@code supported=true, error=null}：已实时抓官方页面，{@code rules} 是当前价；</li>
 *   <li>{@code supported=true, error!=null}：抓取或解析失败，{@code rules} 为空，
 *       调用方<b>不得</b>用旧快照覆盖库里的价格。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.56
 */
@Data
public class ProviderPricingRefresh implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否有实时抓取实现（不代表抓取成功）
     */
    private boolean supported;

    /**
     * 本次是否真的从官方页面抓取
     */
    private boolean live;

    /**
     * 抓取 / 解析失败原因；成功时为 null
     */
    private String error;

    /**
     * 抓取时间（ISO-8601）
     */
    private String fetchedAt;

    /**
     * 规则；失败时为空列表
     */
    private List<ProviderPricingRule> rules = Collections.emptyList();

    /**
     * 无实时实现，交给调用方沿用旧规则。
     *
     * @return 结果
     */
    public static ProviderPricingRefresh unsupported() {
        return new ProviderPricingRefresh();
    }

    /**
     * 构建成功结果。
     *
     * @param rules     规则
     * @param fetchedAt 抓取时间
     * @return 结果
     */
    public static ProviderPricingRefresh ok(List<ProviderPricingRule> rules, String fetchedAt) {
        ProviderPricingRefresh refresh = new ProviderPricingRefresh();
        refresh.supported = true;
        refresh.live = true;
        refresh.fetchedAt = fetchedAt;
        refresh.rules = rules == null ? Collections.emptyList() : rules;
        return refresh;
    }

    /**
     * 构建失败结果（保留失败原因，不返回旧价）。
     *
     * @param error 失败原因
     * @return 结果
     */
    public static ProviderPricingRefresh fail(String error) {
        ProviderPricingRefresh refresh = new ProviderPricingRefresh();
        refresh.supported = true;
        refresh.error = error;
        return refresh;
    }
}
