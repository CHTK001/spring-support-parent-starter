package com.chua.starter.datasourcehub.support.model;

import java.util.List;
import java.util.Map;

/**
 * Elasticsearch 索引详情：相当于关系库的「表结构」
 *
 * @param name     索引名
 * @param health   健康度
 * @param status   状态（open/close）
 * @param shards   主分片数
 * @param replicas 副本数
 * @param docs     文档数
 * @param storeSize 存储大小
 * @param fields   字段列表（来自 mapping.properties，含多级点号名）
 * @param settings 索引设置（扁平化后的设置项）
 * @author CH
 * @since 4.0.0.42
 */
public record ElasticsearchIndexDetailVO(
        String name,
        String health,
        String status,
        Integer shards,
        Integer replicas,
        Long docs,
        String storeSize,
        List<ElasticsearchFieldVO> fields,
        Map<String, Object> settings
) {
}
