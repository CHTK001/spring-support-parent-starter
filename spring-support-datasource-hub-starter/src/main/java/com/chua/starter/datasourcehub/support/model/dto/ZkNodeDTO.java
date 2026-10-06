package com.chua.starter.datasourcehub.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * ZooKeeper 节点写请求，创建/改数据共用
 *
 * <p>改数据只用到 {@code path} 与 {@code data}；{@code ephemeral} 仅创建时生效，
 * 因为节点类型在 ZooKeeper 里创建后不可变更。</p>
 *
 * @param path         节点绝对路径
 * @param data         节点数据（UTF-8 文本）
 * @param ephemeral    是否创建为临时节点，仅创建时生效
 * @param datasourceId 数据源 ID，可空时取第一个 zookeeper 数据源
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ZkNodeDTO(
        String path,
        String data,
        Boolean ephemeral,
        Long datasourceId
) {
}
