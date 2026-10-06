package com.chua.starter.datasourcehub.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * etcd 单键操作请求（详情与删除共用）。
 *
 * @param datasourceId 数据源 ID，为空时取第一个 etcd 数据源
 * @param key          键名
 * @param prefix       删除时是否为前缀删除（true 时删除所有以 key 为前缀的键）
 * @author CH
 * @since 4.0.0.43
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EtcdKeyDTO(
        Long datasourceId,
        String key,
        Boolean prefix
) {
}
