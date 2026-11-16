package com.chua.starter.datasearch.provider.spi;

import com.chua.starter.datasearch.provider.model.ProviderModelCatalog;

/**
 * 厂商模型目录扩展接口。
 *
 * <p>「拿模型信息」不只有上游 {@code GET /models} 一条路：公开目录（models.dev）、
 * 厂商控制台、本地缓存都能提供模型说明 / 价格 / 上下文 / 能力位。新增来源加一个实现
 * 并在 {@code META-INF/services} 登记即可。</p>
 *
 * <p>实现约定：查不到返回 {@code null}；目录里没有的字段留空，不猜值。</p>
 *
 * @author CH
 * @since 4.0.0.55
 */
public interface ProviderModelCatalogProvider {

    /**
     * 实现标识（modelsdev / console 等）
     *
     * @return 标识
     */
    String name();

    /**
     * 判断本次查询是否归本实现处理。
     *
     * @param platform 平台类型（provider id）
     * @param baseUrl  上游基础地址
     * @return 能处理时为 true
     */
    boolean supports(String platform, String baseUrl);

    /**
     * 查询厂商模型目录。
     *
     * @param platform 平台类型
     * @param baseUrl  上游基础地址
     * @return 模型目录；未收录返回 null
     */
    ProviderModelCatalog query(String platform, String baseUrl);

    /**
     * 本实现收录的厂商数（用于覆盖率统计，非必须）。
     *
     * @return 厂商数
     */
    default int providerCount() {
        return 0;
    }

    /**
     * 本实现收录的模型数（用于覆盖率统计，非必须）。
     *
     * @return 模型数
     */
    default int modelCount() {
        return 0;
    }
}
