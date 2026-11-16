package com.chua.starter.datasearch.provider.spi.impl;

import com.chua.starter.datasearch.provider.model.ProviderCapability;
import com.chua.starter.datasearch.provider.model.ProviderDescriptor;
import com.chua.starter.datasearch.provider.spi.ProviderCapabilityProvider;
import com.chua.starter.datasearch.provider.support.ProviderProtocolCapabilities;
import com.chua.starter.datasearch.provider.support.ProviderRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * 能力位实现：按 {@link ProviderProtocolCapabilities} 的口径计算，登记表只提供
 * 「是哪一家、用什么协议」。
 *
 * <p>流式按协议判定（openai / anthropic / gemini 的 SSE 是协议必备行为）；
 * 异步只认官方批量端点实测过的厂商（openai / anthropic / google），
 * 中转 / 聚合站即使自称兼容也不推断。两者都没有口径时返回 {@code null}，
 * 表示「未收录」，不写 {@code false}。</p>
 *
 * <p>登记表覆盖模板库全部供应商，未登记的供应商拿不到协议，直接返回 {@code null}。</p>
 *
 * @author CH
 * @since 4.0.0.57
 */
public class ProtocolCapabilityProvider implements ProviderCapabilityProvider {

    @Override
    public String name() {
        return "protocol";
    }

    @Override
    public boolean supports(String platform, String baseUrl) {
        return capabilities(platform, baseUrl) != null;
    }

    @Override
    public ProviderCapability capabilities(String platform, String baseUrl) {
        ProviderDescriptor descriptor = ProviderRegistries.find(platform, baseUrl);
        if (descriptor == null || descriptor.getProtocol() == null) {
            return null;
        }
        // 没有上游地址的模板（免费渠道 / 路由组 / 自填地址 / 本地 ONNX）不发请求，
        // 也不该给出「支持流式」这种调用侧结论
        String url = baseUrl == null || baseUrl.isBlank() ? descriptor.getBaseUrl() : baseUrl;
        if (!isHttp(url)) {
            return null;
        }
        String protocol = descriptor.getProtocol();
        String vendor = descriptor.getId() == null ? platform : descriptor.getId();
        Boolean streaming = ProviderProtocolCapabilities.supportsStreaming(protocol);
        Boolean asyncSupported = ProviderProtocolCapabilities.supportsAsync(vendor, protocol);
        if (streaming == null && asyncSupported == null) {
            return null;
        }
        ProviderCapability capability = new ProviderCapability();
        capability.setProtocol(protocol);
        capability.setStreaming(streaming);
        capability.setStreamingSource(ProviderProtocolCapabilities.streamingSource(protocol));
        capability.setAsyncSupported(asyncSupported);
        capability.setAsyncSource(ProviderProtocolCapabilities.asyncSource(vendor, protocol));
        capability.setSource("registry");
        return capability;
    }

    /**
     * 地址是否为可请求的 HTTP(S) 地址。
     *
     * @param url 地址
     * @return 是 http / https 为 true
     */
    private static boolean isHttp(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String value = url.trim().toLowerCase(java.util.Locale.ROOT);
        return value.startsWith("http://") || value.startsWith("https://");
    }

    @Override
    public List<ProviderCapability> entries() {
        List<ProviderCapability> entries = new ArrayList<>(256);
        for (ProviderDescriptor descriptor : ProviderRegistries.entries()) {
            if (descriptor.getCapability() != null) {
                entries.add(descriptor.getCapability());
            }
        }
        return entries;
    }
}
