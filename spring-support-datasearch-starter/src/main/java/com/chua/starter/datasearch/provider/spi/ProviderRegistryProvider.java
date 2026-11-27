package com.chua.starter.datasearch.provider.spi;

import com.chua.starter.datasearch.provider.model.ProviderDescriptor;

import java.util.List;
import com.chua.common.support.utils.StringUtils;

/**
 * 供应商登记表扩展接口。
 *
 * <p>前端模板库、models.dev 目录、官方文档入口原本分散在多处，调用方要判断
 * 「这家是谁、目录键是什么、默认地址是什么」时只能各拼一套。这里把一家供应商
 * 归一成一条 {@link ProviderDescriptor}：限制 / 目录 / 定价 / 余额 / 能力各实现
 * 都从这里取同一份描述。</p>
 *
 * <p>实现约定：查不到返回 {@code null}，不抛异常；{@link #entries()} 无法枚举时
 * 返回空列表。</p>
 *
 * @author CH
 * @since 4.0.0.57
 */
public interface ProviderRegistryProvider {

    /**
     * 实现标识（registry 等）
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
     * 按平台 / 地址查登记项。
     *
     * @param platform 平台类型（provider id）
     * @param baseUrl  上游基础地址
     * @return 登记项；未收录返回 null
     */
    ProviderDescriptor find(String platform, String baseUrl);

    /**
     * 按厂商标识精确查登记项。
     *
     * @param providerId 厂商标识
     * @return 登记项；未收录返回 null
     */
    default ProviderDescriptor byId(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return null;
        }
        for (ProviderDescriptor entry : entries()) {
            if (StringUtils.equalsIgnoreCase(entry.getId(), providerId.trim())) {
                return entry;
            }
        }
        return null;
    }

    /**
     * 本实现收录的全部登记项。
     *
     * @return 登记项
     */
    List<ProviderDescriptor> entries();

    /**
     * 本实现登记条目数。
     *
     * @return 条目数
     */
    default int size() {
        return entries().size();
    }
}
