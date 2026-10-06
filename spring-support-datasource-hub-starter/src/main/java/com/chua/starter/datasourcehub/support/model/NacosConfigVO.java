package com.chua.starter.datasourcehub.support.model;

/**
 * Nacos 配置列表行
 *
 * <p>Nacos 的列表接口只在较新版本回传 {@code type} 与修改时间，旧版本对应字段为 null，
 * 打开详情时由配置内容的响应头补齐。</p>
 *
 * @param dataId    配置 Data ID
 * @param group     配置分组
 * @param format    配置格式（yaml/properties/json/xml/text）
 * @param updatedAt 最后更新时间文本
 * @author CH
 * @since 4.0.0.42
 */
public record NacosConfigVO(
        String dataId,
        String group,
        String format,
        String updatedAt
) {
}
