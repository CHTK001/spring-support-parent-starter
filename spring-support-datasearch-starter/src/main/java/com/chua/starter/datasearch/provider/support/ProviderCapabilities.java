package com.chua.starter.datasearch.provider.support;

import com.chua.starter.datasearch.provider.model.ProviderCapability;
import com.chua.starter.datasearch.provider.spi.ProviderCapabilityProvider;
import com.chua.starter.datasearch.support.SpiProviders;

import java.util.ArrayList;
import java.util.List;

/**
 * 能力位出口：按顺序问各个 {@link ProviderCapabilityProvider}，第一个给出结论的算数。
 *
 * <p>「给出结论」指能力位不为 null 且至少有一个字段有值；都拿不到时返回
 * {@code null}，调用方据此区分「未收录」与「不支持」。</p>
 *
 * @author CH
 * @since 4.0.0.57
 */
public final class ProviderCapabilities {

    private static volatile List<ProviderCapabilityProvider> cached;

    private ProviderCapabilities() {
    }

    /**
     * 全部实现（按登记顺序）。
     *
     * @return 实现列表；装载失败时为空列表
     */
    public static List<ProviderCapabilityProvider> providers() {
        List<ProviderCapabilityProvider> current = cached;
        if (current == null || current.isEmpty()) {
            current = SpiProviders.list(ProviderCapabilityProvider.class);
            cached = current;
        }
        return current;
    }

    /**
     * 按平台 / 地址查能力位。
     *
     * @param platform 平台类型（provider id）
     * @param baseUrl  上游基础地址
     * @return 能力位；无口径返回 null
     */
    public static ProviderCapability of(String platform, String baseUrl) {
        for (ProviderCapabilityProvider provider : providers()) {
            ProviderCapability capability = provider.capabilities(platform, baseUrl);
            if (capability != null
                    && (capability.getStreaming() != null || capability.getAsyncSupported() != null)) {
                return capability;
            }
        }
        return null;
    }

    /**
     * 是否支持流式。
     *
     * @param platform 平台类型（provider id）
     * @param baseUrl  上游基础地址
     * @return 支持为 true；未收录返回 null
     */
    public static Boolean streaming(String platform, String baseUrl) {
        ProviderCapability capability = of(platform, baseUrl);
        return capability == null ? null : capability.getStreaming();
    }

    /**
     * 是否支持异步调用。
     *
     * @param platform 平台类型（provider id）
     * @param baseUrl  上游基础地址
     * @return 支持为 true；未收录返回 null
     */
    public static Boolean asyncSupported(String platform, String baseUrl) {
        ProviderCapability capability = of(platform, baseUrl);
        return capability == null ? null : capability.getAsyncSupported();
    }

    /**
     * 全部能力位（各实现合计）。
     *
     * @return 能力位
     */
    public static List<ProviderCapability> entries() {
        List<ProviderCapability> all = new ArrayList<>(256);
        for (ProviderCapabilityProvider provider : providers()) {
            all.addAll(provider.entries());
        }
        return all;
    }

    /**
     * 实现标识清单。
     *
     * @return 标识
     */
    public static List<String> kinds() {
        List<String> kinds = new ArrayList<>(4);
        for (ProviderCapabilityProvider provider : providers()) {
            kinds.add(provider.name());
        }
        return kinds;
    }
}
