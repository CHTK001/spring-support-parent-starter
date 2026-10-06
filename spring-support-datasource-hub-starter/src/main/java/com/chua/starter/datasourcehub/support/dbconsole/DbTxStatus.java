package com.chua.starter.datasourcehub.support.dbconsole;

/**
 * 事务会话状态（给 UI 用）。
 *
 * <p>value class 前置条件：4 个组件全是不可变值类型，逐个按语义判定 ——
 * {@code txId} 与 {@code datasourceId} 是必填的标识，
 * {@code database} 合法取值就是 null（数据源默认库），加 {@code requireNonNull}
 * 会把「没选库」这个正常状态变成 NPE；
 * {@code idleSeconds} 是原始值类型，加任何防御性拷贝都没有意义。</p>
 *
 * @param txId         事务标识
 * @param datasourceId 数据源 ID
 * @param database     库名，可为空
 * @param idleSeconds  空闲秒数
 * @author CH
 * @since 4.0.0
 */
public record DbTxStatus(String txId, Long datasourceId, String database, long idleSeconds) {
}
