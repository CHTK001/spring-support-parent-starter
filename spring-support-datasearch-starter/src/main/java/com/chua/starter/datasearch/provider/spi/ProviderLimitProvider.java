package com.chua.starter.datasearch.provider.spi;

import com.chua.starter.datasearch.provider.model.ProviderLimitInfo;

import java.util.List;

/**
 * 厂商限制查询扩展接口。
 *
 * <p>限制口径差异很大（有的官方公布并发、有的只公布计费、有的什么都不给），
 * 这里把差异收进实现：调用方只给「平台 + 地址」，实现返回登记项或 null。
 * 新增一种来源（官方文档、厂商控制台接口、自建登记表）只要加一个实现并在
 * {@code META-INF/services} 登记，其它模块无需改动。</p>
 *
 * <p>实现约定：查不到返回 {@code null}，不抛异常；只有文档入口、没有数值的条目
 * 也要原样返回，由调用方决定「有文档不等于有限制」。</p>
 *
 * @author CH
 * @since 4.0.0.55
 */
public interface ProviderLimitProvider {

    /**
     * 实现标识（registry / console 等）
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
     * 查询限制登记项。
     *
     * @param platform 平台类型（provider id）
     * @param baseUrl  上游基础地址
     * @return 登记项；未收录返回 null
     */
    ProviderLimitInfo query(String platform, String baseUrl);

    /**
     * 本实现收录的全部登记项（用于覆盖率展示；无法枚举时返回空列表）。
     *
     * @return 登记项
     */
    default List<ProviderLimitInfo> entries() {
        return List.of();
    }

    /**
     * 本实现登记条目数（用于覆盖率统计，非必须）。
     *
     * @return 条目数
     */
    default int size() {
        return entries().size();
    }
}
