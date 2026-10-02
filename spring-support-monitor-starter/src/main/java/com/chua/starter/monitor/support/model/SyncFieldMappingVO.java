package com.chua.starter.monitor.support.model;

/**
 * 数据同步的一条字段映射。
 *
 * <p>与引擎的 {@code DataSyncFieldMapping} 同构，但只保留控制台需要表达的两端字段名；
 * 类型转换器在界面上不开放，固定走引擎的默认转换（原样透传），需要转换时应由源 SQL
 * 用 {@code CAST} 完成，避免把转换语义分散到两处。</p>
 *
 * @param source 源字段名
 * @param target 目标字段名
 * @author CH
 * @since 4.0.0.43
 */
public record SyncFieldMappingVO(String source, String target) {
}