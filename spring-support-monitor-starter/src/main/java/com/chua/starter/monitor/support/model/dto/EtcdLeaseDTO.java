package com.chua.starter.monitor.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * etcd 租约操作请求（授予与撤销共用）。
 *
 * @param datasourceId 数据源 ID，为空时取第一个 etcd 数据源
 * @param leaseId      租约 ID，撤销时必填
 * @param ttl          授予时的 TTL 秒数，撤销时忽略
 * @author CH
 * @since 4.0.0.43
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EtcdLeaseDTO(
        Long datasourceId,
        Long leaseId,
        Long ttl
) {
}
