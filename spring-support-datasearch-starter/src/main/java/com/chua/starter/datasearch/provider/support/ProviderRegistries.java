package com.chua.starter.datasearch.provider.support;

import com.chua.starter.datasearch.provider.model.ProviderDescriptor;
import com.chua.starter.datasearch.provider.spi.ProviderRegistryProvider;
import com.chua.starter.datasearch.support.SpiProviders;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 供应商登记表出口：按顺序问各个 {@link ProviderRegistryProvider}，第一个命中的算数。
 *
 * <p>与限制 / 目录 / 定价 / 余额出口同构：实现全部走 {@code META-INF/services}
 * 登记，这里只做装载缓存与选择，不承载厂商口径。</p>
 *
 * @author CH
 * @since 4.0.0.57
 */
public final class ProviderRegistries {

    private static volatile List<ProviderRegistryProvider> cached;

    private ProviderRegistries() {
    }

    /**
     * 全部实现（按登记顺序）。
     *
     * @return 实现列表；装载失败时为空列表
     */
    public static List<ProviderRegistryProvider> providers() {
        List<ProviderRegistryProvider> current = cached;
        if (current == null || current.isEmpty()) {
            current = SpiProviders.list(ProviderRegistryProvider.class);
            cached = current;
        }
        return current;
    }

    /**
     * 按平台 / 地址查登记项。
     *
     * @param platform 平台类型（provider id）
     * @param baseUrl  上游基础地址
     * @return 登记项；未命中返回 null
     */
    public static ProviderDescriptor find(String platform, String baseUrl) {
        for (ProviderRegistryProvider provider : providers()) {
            ProviderDescriptor descriptor = provider.find(platform, baseUrl);
            if (descriptor != null) {
                return descriptor;
            }
        }
        return null;
    }

    /**
     * 按厂商标识精确查登记项。
     *
     * @param providerId 厂商标识
     * @return 登记项；未命中返回 null
     */
    public static ProviderDescriptor byId(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return null;
        }
        for (ProviderRegistryProvider provider : providers()) {
            ProviderDescriptor descriptor = provider.byId(providerId);
            if (descriptor != null) {
                return descriptor;
            }
        }
        return null;
    }

    /**
     * 全部登记项（各实现合计）。
     *
     * @return 登记项
     */
    public static List<ProviderDescriptor> entries() {
        List<ProviderDescriptor> all = new ArrayList<>(256);
        for (ProviderRegistryProvider provider : providers()) {
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
        for (ProviderRegistryProvider provider : providers()) {
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
        for (ProviderRegistryProvider provider : providers()) {
            kinds.add(provider.name());
        }
        return kinds;
    }

    /**
     * 上游地址是否是该供应商自己的官方站点。
     *
     * <p>余额查询用它分流：官方站点上没有 OpenAI 兼容计费接口，拿官方 Key 去打
     * {@code /v1/dashboard/billing/*} 只会 404，所以必须先认出「这是官方站点」，
     * 再决定要不要按中转站探测。</p>
     *
     * <p>判定依据是登记表的 {@code hosts}：主机名相等或互为主子域即算官方站点；
     * 上游地址为空（用户只选了厂商）视为一致。登记表未收录该供应商时返回
     * {@code false}——未知按中转处理，维持原口径。</p>
     *
     * @param platform 平台类型（供应商 id）
     * @param baseUrl  上游基础地址
     * @return 官方站点为 true
     */
    public static boolean isOfficialSite(String platform, String baseUrl) {
        ProviderDescriptor descriptor = find(platform, baseUrl);
        if (descriptor == null) {
            return false;
        }
        List<String> hosts = descriptor.getHosts();
        if (hosts == null || hosts.isEmpty()) {
            return false;
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            return true;
        }
        String host = hostOf(baseUrl);
        if (host == null) {
            return false;
        }
        for (String candidate : hosts) {
            String official = candidate == null ? "" : candidate.trim().toLowerCase(Locale.ROOT);
            if (official.isEmpty()) {
                continue;
            }
            if (host.equals(official) || host.endsWith("." + official) || official.endsWith("." + host)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 取地址的主机名。
     *
     * @param url 地址
     * @return 小写主机名；地址非法或缺失时为 null
     */
    private static String hostOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String host = URI.create(url.trim()).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
