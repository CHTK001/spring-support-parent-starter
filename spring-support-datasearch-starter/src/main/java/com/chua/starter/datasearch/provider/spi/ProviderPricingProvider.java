package com.chua.starter.datasearch.provider.spi;

import com.chua.starter.datasearch.provider.model.ProviderPricingRefresh;
import com.chua.starter.datasearch.provider.model.ProviderPricingRule;

import java.util.List;

/**
 * 厂商定价规则扩展接口。
 *
 * <p>模型目录只提供「标准价」，但真实计费还有两类维度：峰谷时段（同一模型不同时段
 * 不同价）与按 N 次计价（搜索 / 抓取 / 解析 / 语音按千次、万字符、小时计价）。
 * 新增一个来源（官方价格页、渠道账单、自建登记表）实现本接口并在
 * {@code META-INF/services} 登记即可。</p>
 *
 * <p>实现约定：查不到返回空列表，不返回猜测的价格；{@code verified=false} 表示
 * 价格来自官方页面但未逐项核对，调用方展示时要区分。</p>
 *
 * @author CH
 * @since 4.0.0.56
 */
public interface ProviderPricingProvider {

    /**
     * 实现标识（registry / invoice 等）
     *
     * @return 标识
     */
    String name();

    /**
     * 判断本次查询是否归本实现处理。
     *
     * @param platform 平台类型（provider id）
     * @param baseUrl  上游基础地址
     * @return 能处理时为 true
     */
    boolean supports(String platform, String baseUrl);

    /**
     * 查询定价规则。
     *
     * @param platform 平台类型
     * @param baseUrl  上游基础地址
     * @return 规则列表；未收录返回空列表
     */
    List<ProviderPricingRule> query(String platform, String baseUrl);

    /**
     * 点击同步时刷新价格：有官方价格页抓取实现的厂商覆盖本方法，实时抓取；
     * 没有实现的返回 {@link ProviderPricingRefresh#unsupported()}，调用方沿用旧规则。
     *
     * @param platform 平台类型
     * @param baseUrl  上游基础地址
     * @return 刷新结果
     */
    default ProviderPricingRefresh refresh(String platform, String baseUrl) {
        return ProviderPricingRefresh.unsupported();
    }

    /**
     * 本实现收录的规则条数（覆盖率统计，非必须）。
     *
     * @return 条数
     */
    default int size() {
        return 0;
    }
}
