package com.chua.starter.datasourcehub.support.model;

/**
 * ZooKeeper 子节点行
 *
 * @param name      节点名
 * @param path      绝对路径
 * @param ephemeral 是否临时节点
 * @param data      节点数据（UTF-8 文本，二进制时以占位说明呈现）
 * @author CH
 * @since 4.0.0.42
 */
public record ZkChildNodeVO(
        String name,
        String path,
        Boolean ephemeral,
        String data
) {
}
