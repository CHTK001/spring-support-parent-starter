package com.chua.starter.datasourcehub.support.dbconsole;

/**
 * 数据库进程行（JDBC 控制台「显示进程」）。
 *
 * <p>进程列表按内核差异取列：MySQL 系 {@code SHOW PROCESSLIST} 的 Id/User/Host/db/
 * Command/Time/State/Info；PostgreSQL {@code pg_stat_activity} 的 pid/usename/等。
 * 本 VO 取两者的公共字段，内核专有列放进 {@code extra} 原样透传，避免前端字段写死。</p>
 *
 * @param pid   进程/会话 ID（MySQL Id / PostgreSQL pid），关闭会话时传给 KILL 语句
 * @param user  用户名
 * @param host  客户端地址（MySQL Host / PG client_addr）
 * @param db    所在库/数据库
 * @param command 当前命令（MySQL Command / PG state），可为空
 * @param time  运行时长（秒）
 * @param info  正在执行的语句，可为空且可能很长
 * @param extra 内核专有附加列
 * @author CH
 * @since 4.0.0.42
 */
public record DbProcessVO(
        String pid,
        String user,
        String host,
        String db,
        String command,
        Long time,
        String info,
        java.util.Map<String, Object> extra
) {
}