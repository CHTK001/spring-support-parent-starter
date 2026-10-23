package com.chua.starter.datasourcehub.support.dbconsole;

/**
 * 慢查询条目（JDBC 控制台「慢查询」）。
 *
 * <p>不同内核的慢查询口径不同：MySQL / MariaDB 从
 * {@code performance_schema.events_statements_summary_by_digest} 聚合，
 * PostgreSQL 从 {@code pg_stat_statements} 读；本 VO 取两者的公共字段，
 * 单位统一为毫秒，前端按列直接渲染。</p>
 *
 * @param digest  语句指纹（去掉字面量后的归一化文本），可能很长且允许含换行
 * @param avgMs   平均执行耗时（毫秒）
 * @param maxMs   最大执行耗时（毫秒）
 * @param calls   执行次数
 * @param lastAt  最近一次执行时间（ISO-8601 文本）；取不到为 null
 * @author CH
 * @since 4.0.0.42
 */
public record DbSlowQueryVO(
        String digest,
        Long avgMs,
        Long maxMs,
        Long calls,
        String lastAt
) {
}