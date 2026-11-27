package com.chua.starter.datasearch.provider.support;

import com.chua.starter.datasearch.provider.model.ProviderCapability;
import com.chua.starter.datasearch.provider.spi.ProviderCapabilityProvider;
import com.chua.starter.datasearch.support.SpiProviders;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import com.chua.common.support.utils.CollectionUtils;

/**
 * 能力位出口：按顺序问各个 {@link ProviderCapabilityProvider}，命中结果<b>按字段合并</b>。
 *
 * <p>合并口径的必要性：能力位只有「流式 / 异步」两项，但两项来源不同——流式看接入协议
 * （{@link ProviderProtocolCapabilities}），异步只认实测端点。若某个实现只给得出流式，
 * 「第一个给出结论的算数」会把异步也一并判成未知；这里改为逐字段合并，
 * 一项有值就取一项。</p>
 *
 * <p>都拿不到时返回 {@code null}，调用方据此区分「未收录」与「不支持」：
 * {@code Boolean.FALSE} 是明确不支持，{@code null} 是未知，不能混用。</p>
 *
 * @author CH
 * @since 4.0.0.57
 */
@Slf4j
public final class ProviderCapabilities {

    private static volatile List<ProviderCapabilityProvider> cached;

    /**
     * 工具类，禁止实例化。
     */
    private ProviderCapabilities() {
    }

    /**
     * 全部实现（按登记顺序）。
     *
     * @return 实现列表；装载失败时为空列表
     */
    public static List<ProviderCapabilityProvider> providers() {
        List<ProviderCapabilityProvider> current = cached;
        if (CollectionUtils.isEmpty(current)) {
            current = SpiProviders.list(ProviderCapabilityProvider.class);
            cached = current;
        }
        return current;
    }

    /**
     * 按平台查能力位（不要求上游地址）。
     *
     * @param platform 平台类型（provider id）
     * @return 能力位；无口径返回 null
     */
    public static ProviderCapability of(String platform) {
        return of(platform, null);
    }

    /**
     * 按平台 / 地址查能力位（各实现按字段合并）。
     *
     * @param platform 平台类型（provider id）
     * @param baseUrl  上游基础地址，可为 null（只按厂商标识匹配）
     * @return 能力位；无口径返回 null
     */
    public static ProviderCapability of(String platform, String baseUrl) {
        ProviderCapability merged = null;
        for (ProviderCapabilityProvider provider : providers()) {
            ProviderCapability capability;
            try {
                capability = provider.capabilities(platform, baseUrl);
            } catch (Exception e) {
                log.debug("[provider-capability] 实现[{}]查询失败 platform={} cause={}",
                        provider.name(), platform, e.getMessage());
                continue;
            }
            if (capability != null && hasValue(capability)) {
                merged = merge(merged, capability);
            }
        }
        return merged;
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
            try {
                all.addAll(provider.entries());
            } catch (Exception e) {
                log.debug("[provider-capability] 实现[{}]登记项读取失败: {}", provider.name(), e.getMessage());
            }
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

    /**
     * 是否给出了任何一项结论。
     *
     * @param capability 能力位
     * @return 至少一项有值返回 true
     */
    private static boolean hasValue(ProviderCapability capability) {
        return capability.getStreaming() != null || capability.getAsyncSupported() != null;
    }

    /**
     * 按字段合并：先命中且非空的占位，后续实现只补空。
     *
     * @param target 已合并结果，可为 null
     * @param src    当前实现的命中结果
     * @return 合并后的能力位（新对象，不共享实现的内部实例）
     */
    private static ProviderCapability merge(ProviderCapability target, ProviderCapability src) {
        if (target == null) {
            ProviderCapability copy = new ProviderCapability();
            copy.setStreaming(src.getStreaming());
            copy.setStreamingSource(src.getStreamingSource());
            copy.setAsyncSupported(src.getAsyncSupported());
            copy.setAsyncSource(src.getAsyncSource());
            copy.setProtocol(src.getProtocol());
            copy.setSource(src.getSource());
            copy.setNotes(src.getNotes());
            return copy;
        }
        if (target.getStreaming() == null) {
            target.setStreaming(src.getStreaming());
            if (isEmpty(target.getStreamingSource())) {
                target.setStreamingSource(src.getStreamingSource());
            }
        }
        if (target.getAsyncSupported() == null) {
            target.setAsyncSupported(src.getAsyncSupported());
            if (isEmpty(target.getAsyncSource())) {
                target.setAsyncSource(src.getAsyncSource());
            }
        }
        if (isEmpty(target.getProtocol())) {
            target.setProtocol(src.getProtocol());
        }
        if (isEmpty(target.getSource())) {
            target.setSource(src.getSource());
        }
        if (isEmpty(target.getNotes())) {
            target.setNotes(src.getNotes());
        }
        return target;
    }

    /**
     * 空串 / null 判定。
     *
     * @param value 文本
     * @return 空返回 true
     */
    private static boolean isEmpty(String value) {
        return value == null || value.isBlank();
    }
}
