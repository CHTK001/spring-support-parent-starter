package com.chua.starter.datasearch.provider.spi;

import com.chua.starter.datasearch.provider.model.ProviderCapability;

import java.util.List;

/**
 * 供应商能力位扩展接口（流式 / 异步）。
 *
 * <p>公开目录（models.dev）不提供这两个字段，协议文档又只对部分厂商写明，
 * 所以能力位单独成一条 SPI：登记表里有明确口径就用登记表，没有就按协议推断
 * 流式、异步留空，绝不凭协议把「不支持」写成「支持」。</p>
 *
 * <p>实现约定：查不到返回 {@code null} 或两个字段都为 {@code null} 的能力位，
 * 不抛异常。</p>
 *
 * @author CH
 * @since 4.0.0.57
 */
public interface ProviderCapabilityProvider {

    /**
     * 实现标识（registry / protocol 等）
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
     * 查询能力位。
     *
     * @param platform 平台类型（provider id）
     * @param baseUrl  上游基础地址
     * @return 能力位；无口径返回 null
     */
    ProviderCapability capabilities(String platform, String baseUrl);

    /**
     * 本实现能枚举的能力位（用于覆盖率展示；无法枚举时返回空列表）。
     *
     * @return 能力位
     */
    default List<ProviderCapability> entries() {
        return List.of();
    }
}
