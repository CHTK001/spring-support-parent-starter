package com.chua.starter.datasourcehub.support.model;

/**
 * 表列元数据。
 *
 * <p>供入库节点配置字段映射、表结构浏览使用；字段与前端 {@code CollectColumn} 对齐，
 * 前端 / 采集模块无需再各自实现一份列元数据接口。</p>
 *
 * @param name       列名
 * @param label      列注释（REMARKS），可为空
 * @param position   列序号（1 起），可为空
 * @param typeName   列类型名，可为空
 * @param size       列长度，可为空
 * @param nullable   是否可空，未知按可空处理
 * @param primaryKey 是否主键
 * @author CH
 * @since 4.0.0.42
 */
public record SchemaColumnVO(
        String name,
        String label,
        Integer position,
        String typeName,
        Integer size,
        Boolean nullable,
        Boolean primaryKey) {
}
