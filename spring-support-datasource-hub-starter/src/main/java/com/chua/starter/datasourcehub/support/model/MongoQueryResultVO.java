package com.chua.starter.datasourcehub.support.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MongoDB 查询结果
 *
 * <p>MongoDB 文档是动态结构（同一集合的文档键可以不同），因此单行数据保留 Map 承载，
 * 不预先建模；列名由前端按行键并集推导。总行数为截断前口径：find 未显式 limit
 * 命中默认上限时补一次计数，聚合管道无法预知总数时回落到下发行数。</p>
 *
 * @param query           实际执行的 shell 表达式
 * @param executionTimeMs 服务端往返耗时（毫秒）
 * @param rows            行数据，键为文档字段
 * @param rowCount        本次下发的行数，被截断时等于上限
 * @param totalRows       截断前口径的总行数
 * @param truncated       是否因行数上限被截断
 * @author CH
 * @since 4.0.0.42
 */
public record MongoQueryResultVO(
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
     * 其 value 为归一化后的文档字段值，允许为 null。</p>
     *
     * @param rows 行数据，键为文档字段
     */
    public MongoQueryResultVO {
        rows = List.copyOf(Objects.requireNonNull(rows, "rows 不能为 null"));
    }
}
