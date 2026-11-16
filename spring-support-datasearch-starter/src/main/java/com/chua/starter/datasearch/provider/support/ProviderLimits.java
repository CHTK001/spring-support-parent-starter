package com.chua.starter.datasearch.provider.support;

import com.chua.starter.datasearch.provider.model.ProviderLimitInfo;
import com.chua.starter.datasearch.provider.spi.ProviderLimitProvider;
import com.chua.starter.datasearch.support.SpiProviders;

import java.util.ArrayList;
import java.util.List;

/**
 * 限制查询出口：按顺序问各个 {@link ProviderLimitProvider}，第一个命中的算数。
 *
 * <p>与余额查询同构：实现全部走 {@code META-INF/services} 登记，这里只做装载缓存与
 * 选择，不承载任何厂商口径。</p>
 *
 * @author CH
 * @since 4.0.0.55
 */
public final class ProviderLimits {

    private static volatile List<ProviderLimitProvider> cached;

    private ProviderLimits() {
    }

    /**
     * 全部实现（按登记顺序）。
     *
     * @return 实现列表；装载失败时为空列表
     */
    public static List<ProviderLimitProvider> providers() {
        List<ProviderLimitProvider> current = cached;
        if (current == null || current.isEmpty()) {
            current = SpiProviders.list(ProviderLimitProvider.class);
            cached = current;
        }
        return current;
    }

    /**
     * 按平台 / 地址查登记项。
     *
     * @param platform 平台类型
     * @param baseUrl  上游基础地址
     * @return 登记项；未命中返回 null
     */
    public static ProviderLimitInfo find(String platform, String baseUrl) {
        for (ProviderLimitProvider provider : providers()) {
            ProviderLimitInfo info = provider.query(platform, baseUrl);
            if (info != null) {
                return info;
            }
        }
        return null;
    }

    /**
     * 官方文档入口（只有文档、没有数值时也能拿到）。
     *
     * @param platform 平台类型
     * @param baseUrl  上游基础地址
     * @return 文档地址；未收录返回 null
     */
    public static String docUrl(String platform, String baseUrl) {
        ProviderLimitInfo info = find(platform, baseUrl);
        return info == null ? null : info.getDoc();
    }

    /**
     * 命中的厂商键。
     *
     * @param platform 平台类型
     * @param baseUrl  上游基础地址
     * @return 厂商键；未收录返回 null
     */
    public static String docSource(String platform, String baseUrl) {
        ProviderLimitInfo info = find(platform, baseUrl);
        return info == null ? null : info.getId();
    }

    /**
     * 全部登记项（各实现合计）。
     *
     * @return 登记项
     */
    public static List<ProviderLimitInfo> entries() {
        List<ProviderLimitInfo> all = new ArrayList<>(256);
        for (ProviderLimitProvider provider : providers()) {
            all.addAll(provider.entries());
        }
        return all;
    }

    /**
     * 登记条目数（各实现自报，取合计）。
     *
     * @return 条目数
     */
    public static int size() {
        int total = 0;
        for (ProviderLimitProvider provider : providers()) {
            total += provider.size();
        }
        return total;
    }

    /**
     * 实现标识清单。
     *
     * @return 标识
     */
    public static List<String> kinds() {
        List<String> kinds = new ArrayList<>(4);
        for (ProviderLimitProvider provider : providers()) {
            kinds.add(provider.name());
        }
        return kinds;
    }
}
