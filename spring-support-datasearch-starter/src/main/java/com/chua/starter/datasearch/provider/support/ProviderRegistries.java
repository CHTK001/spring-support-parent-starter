package com.chua.starter.datasearch.provider.support;

import com.chua.starter.datasearch.provider.model.ProviderDescriptor;
import com.chua.starter.datasearch.provider.spi.ProviderRegistryProvider;
import com.chua.starter.datasearch.support.SpiProviders;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.utils.CollectionUtils;

/**
 * 供应商登记表出口：按顺序问各个 {@link ProviderRegistryProvider}，第一个命中的算数。
 *
 * <p>与限制 / 目录 / 定价 / 余额出口同构：实现全部走 {@code META-INF/services}
 * 登记，这里只做装载缓存与选择，不承载厂商口径。查不到、或某个实现抛异常时继续问
 * 下一个——登记表是很多判定（官方站点、协议、限制、目录键）的共用底座，不能因为
 * 一个实现出问题就整体失效。</p>
 *
 * @author CH
 * @since 4.0.0.57
 */
@Slf4j
public final class ProviderRegistries {

    private static volatile List<ProviderRegistryProvider> cached;

    /**
     * 工具类，禁止实例化。
     */
    private ProviderRegistries() {
    }

    /**
     * 全部实现（按登记顺序）。
     *
     * @return 实现列表；装载失败时为空列表
     */
    public static List<ProviderRegistryProvider> providers() {
        List<ProviderRegistryProvider> current = cached;
        if (CollectionUtils.isEmpty(current)) {
            current = SpiProviders.list(ProviderRegistryProvider.class);
            cached = current;
        }
        return current;
    }

    /**
     * 按平台查登记项（不要求上游地址）。
     *
     * <p>免登录渠道 / 未绑定账号的登录渠道都没有 {@code baseUrl}，只能按厂商标识或别名
     * 查；本重载就是给这类调用方用的。</p>
     *
     * @param platform 平台类型（provider id）
     * @return 登记项；未命中返回 null
     */
    public static ProviderDescriptor find(String platform) {
        return find(platform, null);
    }

    /**
     * 按平台 / 地址查登记项。
     *
     * @param platform 平台类型（provider id）
     * @param baseUrl  上游基础地址，可为 null（只按厂商标识匹配）
     * @return 登记项；未命中返回 null
     */
    public static ProviderDescriptor find(String platform, String baseUrl) {
        for (ProviderRegistryProvider provider : providers()) {
            try {
                ProviderDescriptor descriptor = provider.find(platform, baseUrl);
                if (descriptor != null) {
                    return descriptor;
                }
            } catch (Exception e) {
                log.debug("[provider-registry] 实现[{}]查询失败 platform={} cause={}",
                        provider.name(), platform, e.getMessage());
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
            try {
                ProviderDescriptor descriptor = provider.byId(providerId);
                if (descriptor != null) {
                    return descriptor;
                }
            } catch (Exception e) {
                log.debug("[provider-registry] 实现[{}]byId 失败 id={} cause={}",
                        provider.name(), providerId, e.getMessage());
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
            try {
                all.addAll(provider.entries());
            } catch (Exception e) {
                log.debug("[provider-registry] 实现[{}]登记项读取失败: {}", provider.name(), e.getMessage());
            }
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
        if (CollectionUtils.isEmpty(hosts)) {
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

    /**
     * 推断上游地址的 API 路径后缀并补全。
     *
     * <p>用户只给域名（如 {@code api.openai.com}）时，按登记表的 host→suffix 映射
     * 自动补全路径（如 {@code /v1}）。已带路径后缀的保持原样；未知 host 补
     * {@code /v1}（OpenAI 兼容默认）。</p>
     *
     * <p>匹配策略：先按 host 精确匹配，再按子域匹配（{@code host.endsWith("." + candidate)}），
     * 与 {@link #isOfficialSite(String, String)} 同一口径。取到登记项后从其
     * {@code baseUrl} 提取路径部分作为后缀。</p>
     *
     * @param baseUrl 上游基础地址（可空）
     * @return 补全后的地址；入参为空时返回空串
     */
    public static String resolveBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        String trimmed = baseUrl.trim();
        // 已带路径后缀的保持原样
        if (hasPath(trimmed)) {
            return trimmed;
        }
        String host = hostOf(trimmed);
        if (host == null) {
            return trimmed;
        }
        // 查登记表
        ProviderDescriptor descriptor = find(null, trimmed);
        String suffix = descriptor == null ? null : pathOf(descriptor.getBaseUrl());
        if (StringUtils.isEmpty(suffix)) {
            suffix = "/v1";
        }
        // 拼接：去掉末尾斜杠再补
        String base = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        return base + suffix;
    }

    /**
     * 判断 URL 是否已带路径后缀（不含尾部斜杠）。
     *
     * @param url 地址
     * @return 有路径后缀返回 true
     */
    private static boolean hasPath(String url) {
        String noScheme = url;
        int schemeEnd = url.indexOf("://");
        if (schemeEnd > 0) {
            noScheme = url.substring(schemeEnd + 3);
        }
        int slashIdx = noScheme.indexOf('/');
        if (slashIdx <= 0) {
            return false;
        }
        return noScheme.substring(slashIdx).length() > 1;
    }

    /**
     * 从登记表的 baseUrl 提取路径部分。
     *
     * @param url 登记表地址（始终带协议）
     * @return 路径部分；无路径时返回空串
     */
    private static String pathOf(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String noScheme = url;
        int schemeEnd = url.indexOf("://");
        if (schemeEnd > 0) {
            noScheme = url.substring(schemeEnd + 3);
        }
        int slashIdx = noScheme.indexOf('/');
        if (slashIdx <= 0) {
            return "";
        }
        String path = noScheme.substring(slashIdx);
        return path.length() <= 1 ? "" : path;
    }
}
