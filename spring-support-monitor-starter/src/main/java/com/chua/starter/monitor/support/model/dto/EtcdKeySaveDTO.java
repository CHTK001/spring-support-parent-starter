package com.chua.starter.monitor.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * etcd 键写入请求（新增与覆盖同一语义：etcd 的 put 就是覆盖）。
 *
 * @param datasourceId 数据源 ID，为空时取第一个 etcd 数据源
 * @param key          键名
 * @param value        值（UTF-8 文本）
 * @param lease        绑定租约 ID，为空或 0 表示不绑定
 * @author CH
 * @since 4.0.0.43
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EtcdKeySaveDTO(
        Long datasourceId,
        String key,
        String value,
        Long lease
) {
}
