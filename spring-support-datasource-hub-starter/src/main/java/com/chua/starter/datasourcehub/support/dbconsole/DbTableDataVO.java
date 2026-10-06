package com.chua.starter.datasourcehub.support.dbconsole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 数据表分页数据
 *
 * <p>rows 的列名由被查询的表决定，属于动态结构，因此保留 Map 承载单行数据。</p>
 *
 * @param columns  列名，按查询结果顺序
 * @param rows     行数据，键为列名
 * @param total    总行数
 * @param page     当前页码，从 1 开始
 * @param pageSize 每页行数
 * @author CH
 * @since 4.0.0.42
 */
public record DbTableDataVO(
        List<String> columns,
        List<Map<String, Object>> rows,
        long total,
        int page,
        int pageSize
) {
    /**
     * 规范构造器：结果集与行数据做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。行数据由服务端分页查询组装，
     * 恒不为 null 且元素（单行 Map）恒不为 null，故可使用 {@link List#copyOf}；
     * 列标签取自 JDBC 元数据，不排除 null 元素，故采用可容纳 null 元素的不可变包装。
     * 行内的 Map 保持原引用，其 value 为数据库列值，允许为 null。</p>
     *
     * @param columns  列名，按查询结果顺序
     * @param rows     行数据，键为列名
     * @param total    总行数
     * @param page     当前页码，从 1 开始
     * @param pageSize 每页行数
     */
    public DbTableDataVO {
        columns = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(columns, "columns 不能为 null")));
        rows = List.copyOf(Objects.requireNonNull(rows, "rows 不能为 null"));
    }
}
