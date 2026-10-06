package com.chua.starter.datasourcehub.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * etcd 键查询请求。
 *
 * @param datasourceId 数据源 ID，为空时取第一个 etcd 数据源
 * @param prefix       键前缀，空表示从根开始列举
 * @param limit        最多返回条数，为空或非正时用服务端默认上限
 * @param keysOnly     只取键名不取值（浏览树时用，避免把大值全拉回来）
 * @author CH
 * @since 4.0.0.43
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EtcdKeyQueryDTO(
        Long datasourceId,
        String prefix,
        Integer limit,
        Boolean keysOnly
) {
}
