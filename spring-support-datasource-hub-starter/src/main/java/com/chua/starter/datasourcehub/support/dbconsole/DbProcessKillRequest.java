package com.chua.starter.datasourcehub.support.dbconsole;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 关闭数据库进程请求。
 *
 * @param datasourceId 数据源 ID
 * @param pids         要关闭的进程 ID 列表（MySQL Id / PostgreSQL pid）
 * @param pid          单进程关闭时的进程 ID；批量时与 pids 二选一
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DbProcessKillRequest(
        Long datasourceId,
        java.util.List<String> pids,
        String pid
) {
}