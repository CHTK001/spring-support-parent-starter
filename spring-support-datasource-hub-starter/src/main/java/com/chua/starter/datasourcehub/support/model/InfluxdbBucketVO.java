package com.chua.starter.datasourcehub.support.model;

/**
 * InfluxDB 存储桶（Bucket）列表行
 *
 * @param id             Bucket ID
 * @param name           Bucket 名，Flux 里以该名寻址
 * @param retentionHours 保留时长（小时），{@code 0} 表示永久保留，服务端未给规则时为 null
 * @param createdAt      创建时间（可读文本）
 * @author CH
 * @since 4.0.0.42
 */
public record InfluxdbBucketVO(
        String id,
        String name,
        Long retentionHours,
        String createdAt
) {
}
