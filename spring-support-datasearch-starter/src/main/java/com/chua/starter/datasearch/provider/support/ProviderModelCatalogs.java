package com.chua.starter.datasearch.provider.support;

import com.chua.starter.datasearch.provider.model.ProviderModelCatalog;
import com.chua.starter.datasearch.provider.spi.ProviderModelCatalogProvider;
import com.chua.starter.datasearch.support.SpiProviders;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import com.chua.common.support.utils.CollectionUtils;

/**
 * 模型目录出口：按顺序问各个 {@link ProviderModelCatalogProvider}，命中结果<b>合并</b>。
 *
 * <p>模型数据天然分散：公开目录（models.dev）有说明 / 价格 / 上下文 / 模态，
 * 厂商控制台有真实牌价，本地缓存有离线副本。若只取第一个命中，先命中的实现没给的
 * 字段就永久为空；这里按模型 id 合并各实现的目录（先到为主，后续源只补空），
 * 合并口径统一在 {@link ProviderModelInfos}。</p>
 *
 * <p>单个实现抛异常不影响整体：装载到的实现只要有一个能给出目录，调用方就能拿到数据。</p>
 *
 * @author CH
 * @since 4.0.0.55
 */
@Slf4j
public final class ProviderModelCatalogs {

    private static volatile List<ProviderModelCatalogProvider> cached;

    /**
     * 工具类，禁止实例化。
     */
    private ProviderModelCatalogs() {
    }

    /**
     * 全部实现（按登记顺序）。
     *
     * @return 实现列表
     */
    public static List<ProviderModelCatalogProvider> providers() {
        List<ProviderModelCatalogProvider> current = cached;
        if (CollectionUtils.isEmpty(current)) {
            current = SpiProviders.list(ProviderModelCatalogProvider.class);
            cached = current;
        }
        return current;
    }

    /**
     * 查一家厂商的模型目录（不要求上游地址）。
     *
     * <p>免登录渠道与未绑定账号的登录渠道都没有 {@code baseUrl}，只能按厂商标识 /
     * 别名查；本重载就是给这类调用方用的。</p>
     *
     * @param platform 平台类型
     * @return 模型目录；未收录返回 null
     */
    public static ProviderModelCatalog of(String platform) {
        return of(platform, null);
    }

    /**
     * 查一家厂商的模型目录。
     *
     * @param platform 平台类型
     * @param baseUrl  上游基础地址，可为 null（只按厂商标识匹配）
     * @return 模型目录；未收录返回 null
     */
    public static ProviderModelCatalog of(String platform, String baseUrl) {
        ProviderModelCatalog merged = null;
        for (ProviderModelCatalogProvider provider : providers()) {
            ProviderModelCatalog catalog = query(provider, platform, baseUrl);
            if (catalog != null) {
                merged = merge(merged, catalog);
            }
        }
        return merged;
    }

    /**
     * 合并两份目录：身份字段先到为主，模型清单按 id 合并，定价规则去重后拼接。
     *
     * @param target 已合并结果，可为 null
     * @param src    当前实现的命中结果
     * @return 合并后的目录（新对象，不共享实现的内部实例）
     */
    private static ProviderModelCatalog merge(ProviderModelCatalog target, ProviderModelCatalog src) {
        if (target == null) {
            ProviderModelCatalog copy = new ProviderModelCatalog();
            copy.setProviderId(src.getProviderId());
            copy.setName(src.getName());
            copy.setDoc(src.getDoc());
            copy.setSource(src.getSource());
            copy.setPricingLive(src.getPricingLive());
            copy.setPricingFetchedAt(src.getPricingFetchedAt());
            copy.setPricingError(src.getPricingError());
            copy.setModels(ProviderModelInfos.mergeModels(null, src.getModels()));
            copy.setPricingRules(new ArrayList<>(ProviderPricings.union(null, src.getPricingRules())));
            return copy;
        }
        if (isEmpty(target.getProviderId())) {
            target.setProviderId(src.getProviderId());
        }
        if (isEmpty(target.getName())) {
            target.setName(src.getName());
        }
        if (isEmpty(target.getDoc())) {
            target.setDoc(src.getDoc());
        }
        if (isEmpty(target.getSource())) {
            target.setSource(src.getSource());
        }
        if (target.getPricingLive() == null) {
            target.setPricingLive(src.getPricingLive());
        }
        if (isEmpty(target.getPricingFetchedAt())) {
            target.setPricingFetchedAt(src.getPricingFetchedAt());
        }
        if (isEmpty(target.getPricingError())) {
            target.setPricingError(src.getPricingError());
        }
        target.setModels(ProviderModelInfos.mergeModels(target.getModels(), src.getModels()));
        target.setPricingRules(ProviderPricings.union(target.getPricingRules(), src.getPricingRules()));
        return target;
    }

    /**
     * 单实现查询，异常不外抛。
     *
     * @param provider 实现
     * @param platform 平台类型
     * @param baseUrl  上游基础地址
     * @return 模型目录；未收录或失败返回 null
     */
    private static ProviderModelCatalog query(ProviderModelCatalogProvider provider, String platform,
                                              String baseUrl) {
        try {
            return provider.query(platform, baseUrl);
        } catch (Exception e) {
            log.debug("[provider] 实现[{}]查询失败 platform={} cause={}",
                    provider.name(), platform, e.getMessage());
            return null;
        }
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
