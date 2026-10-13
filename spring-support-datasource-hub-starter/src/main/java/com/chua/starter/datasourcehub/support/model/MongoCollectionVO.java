package com.chua.starter.datasourcehub.support.model;

/**
 * MongoDB 集合信息
 *
 * @param name            集合名
 * @param type            集合类型（collection/view），取不到时为 null
 * @param estimatedCount  估算文档数，拿不到时为 null
 * @author CH
 * @since 4.0.0.42
 */
public record MongoCollectionVO(
        String name,
        String type,
        Long estimatedCount
) {
}
