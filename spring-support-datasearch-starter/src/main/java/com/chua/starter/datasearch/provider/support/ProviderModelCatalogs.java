package com.chua.starter.datasearch.provider.support;

import com.chua.starter.datasearch.provider.model.ProviderModelCatalog;
import com.chua.starter.datasearch.provider.spi.ProviderModelCatalogProvider;
import com.chua.starter.datasearch.support.SpiProviders;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型目录出口：按顺序问各个 {@link ProviderModelCatalogProvider}，第一个命中的算数。
 *
 * @author CH
 * @since 4.0.0.55
 */
public final class ProviderModelCatalogs {

    private static volatile List<ProviderModelCatalogProvider> cached;

    private ProviderModelCatalogs() {
    }

    /**
     * 全部实现（按登记顺序）。
     *
     * @return 实现列表
     */
    public static List<ProviderModelCatalogProvider> providers() {
        List<ProviderModelCatalogProvider> current = cached;
        if (current == null || current.isEmpty()) {
            current = SpiProviders.list(ProviderModelCatalogProvider.class);
            cached = current;
        }
        return current;
    }

    /**
     * 查一家厂商的模型目录。
     *
     * @param platform 平台类型
     * @param baseUrl  上游基础地址
     * @return 模型目录；未收录返回 null
     */
    public static ProviderModelCatalog of(String platform, String baseUrl) {
        for (ProviderModelCatalogProvider provider : providers()) {
            ProviderModelCatalog catalog = provider.query(platform, baseUrl);
            if (catalog != null) {
                return catalog;
            }
        }
        return null;
    }

    /**
     * 实现标识清单。
     *
     * @return 标识
     */
    public static List<String> kinds() {
        List<String> kinds = new ArrayList<>(4);
        for (ProviderModelCatalogProvider provider : providers()) {
            kinds.add(provider.name());
        }
        return kinds;
    }

    /**
     * 收录厂商数（各实现自报）。
     *
     * @return 厂商数
     */
    public static int providerCount() {
        int total = 0;
        for (ProviderModelCatalogProvider provider : providers()) {
            total += provider.providerCount();
        }
        return total;
    }

    /**
     * 收录模型数（各实现自报）。
     *
     * @return 模型数
     */
    public static int modelCount() {
        int total = 0;
        for (ProviderModelCatalogProvider provider : providers()) {
            total += provider.modelCount();
        }
        return total;
    }
}
