package com.chua.starter.datasourcehub.support.model.dto;

import java.util.List;

/**
 * 字段映射解析请求。
 *
 * <p>入库节点把"这轮采集产出哪些字段"交给数据源模块，由数据源模块比对目标表结构，
 * 给出"哪些列直接用、哪些要新增、类型是否要转换"的结论。</p>
 *
 * @param datasource 数据源引用：留空或 {@code app} 表示应用主库，否则为台账 ID 或名称
 * @param table      目标表名，必须合法标识符
 * @param fields     来源字段
 * @author CH
 * @since 4.0.0.42
 */
public record FieldMappingDTO(
        String datasource,
        String table,
        List<SourceField> fields) {

    /**
     * 规范化集合组件，阻断外部别名写入。
     *
     * <p>本 record 是 JSON 反序列化的请求体，{@code fields} 允许缺省为 {@code null}，
     * 因此保留 null 语义，不使用 {@code requireNonNull}。</p>
     */
    public FieldMappingDTO {
        fields = fields == null ? null : List.copyOf(fields);
    }
}
