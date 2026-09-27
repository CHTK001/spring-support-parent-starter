package com.chua.starter.monitor.support.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Neo4j Cypher 查询结果
 *
 * <p>列集合完全由用户提交的 Cypher 决定（{@code RETURN n} 与 {@code RETURN count(*) AS c}
 * 结构不同），属于动态结构，因此单行数据保留 Map 承载，不预先建模。</p>
 *
 * @param query           实际执行的 Cypher 语句
 * @param executionTimeMs 服务端往返耗时（毫秒）
 * @param rows            行数据，键为列名并保持服务端列序
 * @param rowCount        本次下发的行数，被截断时等于上限
 * @param totalRows       服务端返回的总行数，截断前口径
 * @param truncated       是否因超过行数上限被截断
 * @author CH
 * @since 4.0.0.42
 */
public record Neo4jQueryResultVO(
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
     * 其 value 为 Cypher 返回的属性值，允许为 null。</p>
     *
     * @param rows 行数据，键为列名并保持服务端列序
     */
    public Neo4jQueryResultVO {
        rows = List.copyOf(Objects.requireNonNull(rows, "rows 不能为 null"));
    }
}
