package com.chua.starter.mybatis.event;

import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * MyBatis 慢 SQL 事件。
 *
 * <p>由慢 SQL 拦截器在检测到超阈值 SQL 时发布，业务侧可通过 {@code @EventListener} 订阅处理。</p>
 *
 * @since 2026/4/27
 * @author CH
 */
public class MybatisSlowSqlEvent extends ApplicationEvent {

    /**
     * Mapper 方法标识（MappedStatement#id）。
     */
    private final String statementId;
    /**
     * SQL 命令类型（SELECT/UPDATE/INSERT/DELETE）。
     */
    private final String commandType;
    /**
     * 耗时（毫秒）。
     */
    private final long elapsedMs;
    /**
     * TraceId（若存在）。
     */
    private final String traceId;
    /**
     * SQL 文本（可选，可能被截断）。
     */
    private final String sql;
    /**
     * 参数映射信息（可选）。
     */
    private final List<String> parameters;

    /**
     * 构造事件。
     *
     * @param source      事件源
     * @param statementId Mapper 方法标识
     * @param commandType SQL 命令类型
     * @param elapsedMs   耗时（毫秒）
     * @param traceId     TraceId
     * @param sql         SQL 文本
     * @param parameters  参数映射信息
     */
    public MybatisSlowSqlEvent(Object source,
                              String statementId,
                              String commandType,
                              long elapsedMs,
                              String traceId,
                              String sql,
                              List<String> parameters) {
        super(source);
        this.statementId = statementId;
        this.commandType = commandType;
        this.elapsedMs = elapsedMs;
        this.traceId = traceId;
        this.sql = sql;
        this.parameters = parameters;
    }

    /** 获取StatementId */
    public String getStatementId() {
        return statementId;
    }

    /** 获取CommandType */
    public String getCommandType() {
        return commandType;
    }

    /** 获取ElapsedMs */
    public long getElapsedMs() {
        return elapsedMs;
    }

    /** 获取TraceId */
    public String getTraceId() {
        return traceId;
    }

    /** 获取Sql */
    public String getSql() {
        return sql;
    }

    /** 获取Parameters */
    public List<String> getParameters() {
        return parameters;
    }
}

