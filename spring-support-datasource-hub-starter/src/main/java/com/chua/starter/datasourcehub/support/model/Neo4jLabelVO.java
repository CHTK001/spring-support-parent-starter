package com.chua.starter.datasourcehub.support.model;

/**
 * Neo4j 节点标签统计行
 *
 * <p>标签名取自 {@code CALL db.labels()}，计数靠全图扫描聚合；扫描失败或被规模阈值拦住时
 * 对应列留 null，而不是把「不知道」显示成 0。</p>
 *
 * @param name          标签名
 * @param nodeCount     带该标签的节点数，未统计时为空
 * @param propertyCount 该标签下出现过的属性键数，未统计时为空
 * @author CH
 * @since 4.0.0.42
 */
public record Neo4jLabelVO(
        String name,
        Long nodeCount,
        Long propertyCount
) {
}
