package com.chua.starter.datasearch.provider.spi;

import com.chua.starter.datasearch.provider.model.ProviderModelInfo;

/**
 * 按<b>模型 id</b> 查模型指标的扩展接口。
 *
 * <p>与 {@link ProviderModelCatalogProvider} 的区别是查询键：目录实现按「平台 + 上游地址」
 * 找一家厂商，只在该厂商下遍历模型；而免登录渠道（DuckDuckGo / Pollinations / OpenCode 等）
 * 的模型行<b>没有 baseUrl</b>，登录型渠道在未绑定账号时也没有，按厂商查一律落空，
 * 于是模型卡的上下文、输出上限、输入/输出模态全是空白。这类渠道只需要知道
 * 「这个模型 id 是什么模型」，与渠道挂在哪家公司无关，因此单开一条按模型 id 查的 SPI。</p>
 *
 * <p>实现约定：查不到返回 {@code null}；目录里没有的字段留空，<b>不猜值</b>。
 * 同一模型可能被多家厂商收录，实现应按入参平台优先挑对应厂商的条目，挑不到再给任意一条。</p>
 *
 * @author CH
 * @since 4.0.0.58
 */
public interface ProviderModelMetricsProvider {

    /**
     * 实现标识（modelsdev / console 等）。
     *
     * @return 标识
     */
    String name();

    /**
     * 判断本实现是否收录了该模型（只查索引，不解析）。
     *
     * <p>用于跳过无关实现，避免每次查询都让所有实现做一次字符串解析。</p>
     *
     * @param model 模型 id
     * @return 可能收录时返回 true
     */
    default boolean supportsModel(String model) {
        return true;
    }

    /**
     * 按模型 id 查指标。
     *
     * @param platform 平台 / 渠道标识（可为 {@code null}，用于在同一模型的多条厂商记录里择优）
     * @param model    模型 id（可带厂商前缀）
     * @return 模型信息；未收录返回 {@code null}
     */
    ProviderModelInfo query(String platform, String model);

    /**
     * 本实现收录的模型条目数（用于覆盖率统计，非必须）。
     *
     * @return 模型数
     */
    default int modelCount() {
        return 0;
    }

    /**
     * 全量枚举本实现收录的模型指标（跨厂商去重后的模型条目）。
     *
     * <p>给「显式同步」这类管理动作使用：一次拿到目录里的所有模型，不逐个 id
     * 试探。实现没有全量目录时返回空列表（默认），调用方按「暂无数据」处理。</p>
     *
     * @return 模型信息列表；无全量能力或加载失败返回空列表
     */
    default java.util.List<ProviderModelInfo> listAll() {
        return java.util.List.of();
    }
}
