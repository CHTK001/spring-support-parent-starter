package com.chua.starter.datasourcehub.support.model;

/**
 * Elasticsearch 索引列表行
 *
 * @param name       索引名
 * @param health     健康度（green/yellow/red），关闭分配时为 null
 * @param status     状态（open/close）
 * @param uuid       索引 UUID
 * @param docs       文档数（文本，服务端按 _cat 口径给）
 * @param storeSize  存储大小（文本，如 9.3kb）
 * @author CH
 * @since 4.0.0.42
 */
public record ElasticsearchIndexVO(
        String name,
        String health,
        String status,
        String uuid,
        String docs,
        String storeSize
) {
}
