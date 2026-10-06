package com.chua.starter.datasourcehub.support.model;

/**
 * ZooKeeper 子节点树行
 *
 * @param path          绝对路径
 * @param name          节点名（路径最后一段）
 * @param ephemeral     是否临时节点
 * @param childrenCount 直接子节点数量
 * @author CH
 * @since 4.0.0.42
 */
public record ZkNodeVO(
        String path,
        String name,
        Boolean ephemeral,
        Integer childrenCount
) {
}
