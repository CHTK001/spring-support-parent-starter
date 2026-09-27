package com.chua.starter.monitor.support.model;

/**
 * Neo4j 关系类型统计行
 *
 * <p>类型名取自 {@code CALL db.relationshipTypes()}，计数与属性数靠全关系扫描聚合，
 * 统计不当时对应列留 null。</p>
 *
 * @param type               关系类型名
 * @param relationshipCount  该类型的关系数，未统计时为空
 * @param propertyCount      该类型下出现过的属性键数，未统计时为空
 * @author CH
 * @since 4.0.0.42
 */
public record Neo4jRelationshipVO(
        String type,
        Long relationshipCount,
        Long propertyCount
) {
}
