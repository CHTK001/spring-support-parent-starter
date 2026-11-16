package com.chua.starter.ai.support.manage.limit;

/**
 * 官方文档预设机制：按平台 / 地址命中 {@link ProviderLimitPresets}。
 *
 * @since 4.0.0.51
 * @author CH
 */
public class DocProviderLimitProvider implements ProviderLimitProvider {

    @Override
    public String kind() {
        return "doc";
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
