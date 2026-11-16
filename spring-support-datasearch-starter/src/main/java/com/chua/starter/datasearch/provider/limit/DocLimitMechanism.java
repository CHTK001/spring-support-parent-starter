package com.chua.starter.datasearch.provider.limit;

/**
 * 官方文档登记表机制：按平台 / 地址命中 {@link ProviderLimitPresets}。
 *
 * <p>登记表覆盖模板库全部提供商（200+ 家），但只有核对过官方原文的条目才带
 * 数值 / 计费口径；其余条目只有文档入口，由 {@link ProviderLimitCollector#collect}
 * 单独回传 {@code doc_url}，不会把「有文档」说成「有限制」。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public class DocLimitMechanism implements ProviderLimitMechanism {

    @Override
    public String kind() {
        return "doc";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public ProviderLimit query(ProviderLimitContext context) {
        if (context == null) {
            return null;
        }
        ProviderLimit preset = ProviderLimitPresets.find(context.getPlatform(), context.getBaseUrl());
        if (preset == null || !preset.hasValues()) {
            return null;
        }
        preset.setKind(kind());
        preset.setCapturedAt(System.currentTimeMillis());
        return preset;
    }
}
