package com.chua.starter.monitor.support.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * SQL 执行结果
 *
 * <p>查询语句填充 columns/rows/rowCount/truncated，更新语句填充 affectedRows/message，
 * 未填充的字段为 null 并随全局 NON_NULL 策略从 JSON 中省略。rows 的列名由语句决定，
 * 属于动态结构，因此保留 Map 承载单行数据。</p>
 *
 * @param columns      结果集列名
 * @param rows         结果集行数据
 * @param rowCount     结果集行数
 * @param truncated    结果集是否因超过上限被截断
 * @param affectedRows 更新语句受影响行数
 * @param message      执行说明
 * @author CH
 * @since 4.0.0.42
 */
public record DbSqlResultVO(
        List<String> columns,
        List<Map<String, Object>> rows,
        Integer rowCount,
        Boolean truncated,
        Integer affectedRows,
        String message
) {
    /**
     * 规范构造器：对结果集做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。更新语句不返回结果集，
     * 此时 columns 与 rows 为 null，故此处保留 null 语义而不做非空断言；
     * 列标签与行数据来自 JDBC 元数据，不排除 null 元素，故采用可容纳 null 元素的
     * 不可变包装而非 {@link List#copyOf}。行内的 Map 保持原引用，其 value 允许为 null。</p>
     *
     * @param columns      结果集列名
     * @param rows         结果集行数据
     * @param rowCount     结果集行数
     * @param truncated    结果集是否因超过上限被截断
     * @param affectedRows 更新语句受影响行数
     * @param message      执行说明
     */
    public DbSqlResultVO {
        columns = columns == null ? null : Collections.unmodifiableList(new ArrayList<>(columns));
        rows = rows == null ? null : Collections.unmodifiableList(new ArrayList<>(rows));
    }
}
