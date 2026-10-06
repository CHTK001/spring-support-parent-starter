package com.chua.starter.datasourcehub.support.dbconsole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 批量 SQL 执行结果（查询控制台按脚本执行）。
 *
 * <p>与 {@link DbSqlResultVO} 分开而不是往里加字段，是为了**不动既有单条执行端点**：
 * 单条执行有 5 个 UI 套件与 4 个接口套件在读 {@code data.columns} / {@code data.rows}，
 * 改它的形状等于让所有既有断言失效。批量走新端点，形状可以自由设计。</p>
 *
 * <p>「逐条执行、逐条汇报」而不是「要么全成要么全败」：调试脚本时能一眼看出
 * 第几条开始出错、错在什么语句上，而不是只知道整个脚本失败了。</p>
 *
 * @param results       每条语句的执行结果，顺序与脚本里语句顺序一致
 * @param statementCount 实际执行的语句条数
 * @param failedIndex   首个失败语句的下标（从 0 开始）；全部成功时为 -1
 * @param failedSql     首个失败语句的原文
 * @param message       整体说明
 * @author CH
 * @since 4.0.0.45
 */
public record DbSqlBatchVO(
        List<DbSqlResultVO> results,
        Integer statementCount,
        Integer failedIndex,
        String failedSql,
        String message
) {
    /**
     * 规范构造器：结果列表做防御性拷贝。
     *
     * @param results        结果列表
     * @param statementCount 语句条数
     * @param failedIndex    失败下标
     * @param failedSql      失败语句
     * @param message        整体说明
     */
    public DbSqlBatchVO {
        results = results == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(results));
        failedIndex = failedIndex == null ? -1 : failedIndex;
        statementCount = statementCount == null ? results.size() : statementCount;
    }

    /**
     * 是否有语句失败。
     *
     * @return true=有失败
     */
    public boolean hasFailure() {
        return failedIndex >= 0;
    }

    /**
     * 全部成功的快捷构造。
     *
     * @param results        结果列表
     * @param statementCount 语句条数
     * @param message        整体说明
     * @return 批量结果
     */
    public static DbSqlBatchVO allOk(List<DbSqlResultVO> results, int statementCount, String message) {
        return new DbSqlBatchVO(results, statementCount, -1, null, message);
    }
}