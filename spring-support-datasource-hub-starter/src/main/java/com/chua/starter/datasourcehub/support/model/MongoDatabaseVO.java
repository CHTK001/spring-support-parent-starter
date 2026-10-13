package com.chua.starter.datasourcehub.support.model;

/**
 * MongoDB 逻辑库信息
 *
 * @param name        逻辑库名
 * @param sizeOnDisk  磁盘占用（字节），取不到时为 null
 * @param empty       是否空库
 * @author CH
 * @since 4.0.0.42
 */
public record MongoDatabaseVO(
        String name,
        Long sizeOnDisk,
        Boolean empty
) {
}
