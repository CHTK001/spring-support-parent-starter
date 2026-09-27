package com.chua.starter.monitor.support.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * InfluxDB Flux 查询结果
 *
 * <p>列集合随查询语句而变（{@code group}、{@code pivot} 都会改变列），属于动态结构，
 * 因此保留 Map 承载单行数据，不预先建模。</p>
 *
 * @param query           实际执行的 Flux 语句
 * @param executionTimeMs 服务端往返耗时（毫秒）
 * @param rows            行数据，键为列名并保持服务端列序
 * @param rowCount        本次下发的行数，被截断时等于上限
 * @param totalRows       服务端返回的总行数，截断前口径
 * @param truncated       是否因超过行数上限被截断
 * @author CH
 * @since 4.0.0.42
 */
public record InfluxdbQueryResultVO(
        String query,
        Long executionTimeMs,
        List<Map<String, Object>> rows,
        Integer rowCount,
        Integer totalRows,
        Boolean truncated
) {
    /**
     * 规范构造器：行数据做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。行列表恒不为 null 且元素恒不为 null，
     * 故可使用拒绝 null 的 {@link List#copyOf}。行内的 Map 保持原引用，
     * 其 value 为 Flux 返回的字段值，允许为 null。</p>
     *
     * @param rows 行数据，键为列名并保持服务端列序
     */
    public InfluxdbQueryResultVO {
        rows = List.copyOf(Objects.requireNonNull(rows, "rows 不能为 null"));
    }
}
