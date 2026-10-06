package com.chua.starter.datasourcehub.support.model;

/**
 * Nacos 配置详情
 *
 * <p>Nacos v1 不通过 HTTP 暴露配置的创建时间，{@code createdAt} 固定不填（为 null 时随
 * 全局 NON_NULL 策略从 JSON 中省略）。</p>
 *
 * @param dataId    配置 Data ID
 * @param group     配置分组
 * @param format    配置格式
 * @param updatedAt 最后更新时间，取自 {@code Last-Modified} 响应头
 * @param namespace 所属命名空间标识
 * @param content   配置正文
 * @param createdAt 创建时间，Nacos 未提供时为 null
 * @author CH
 * @since 4.0.0.42
 */
public record NacosConfigDetailVO(
        String dataId,
        String group,
        String format,
        String updatedAt,
        String namespace,
        String content,
        String createdAt
) {
}
