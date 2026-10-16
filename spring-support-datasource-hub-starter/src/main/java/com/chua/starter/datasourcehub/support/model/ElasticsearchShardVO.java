package com.chua.starter.datasourcehub.support.model;

/**
 * Elasticsearch 分片分配行
 *
 * @param index  索引名
 * @param shard  分片序号
 * @param prirep p=主分片，r=副本
 * @param state  状态（STARTED/UNASSIGNED/...）
 * @param docs   该分片文档数
 * @param store  该分片存储大小
 * @param node   所在节点
 * @author CH
 * @since 4.0.0.42
 */
public record ElasticsearchShardVO(
        String index,
        String shard,
        String prirep,
        String state,
        String docs,
        String store,
        String node
) {
}
