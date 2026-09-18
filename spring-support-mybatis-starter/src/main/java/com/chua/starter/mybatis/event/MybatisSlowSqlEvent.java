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
    * 映射器 方法标识（mapped对账单#标识）。
    */
    private final String statementId;
    /**
    * SQL 命令类型（选择/更新/插入/删除）。
    */
    private final String commandType;
    /**
    * 耗时（毫秒）。
    */
    private final long elapsedMs;
    /**
    * 追踪标识（若存在）。
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
    * @param statementId 映射器 方法标识
    * @param commandType SQL 命令类型
    * @param elapsedMs   耗时（毫秒）
    * @param traceId     追踪标识
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
        /**
        * 父。
        * @param source 源
        */
        super(source);
        this.statementId = statementId;
        this.commandType = commandType;
        this.elapsedMs = elapsedMs;
        this.traceId = traceId;
        this.sql = sql;
        this.parameters = parameters;
    }

    /**
    * 获取对账单标识
    *
    * @return 获取对账单id的结果
    */
    public String getStatementId() {
        return statementId;
    }

    /**
    * 获取命令类型
    *
    * @return 获取命令类型的结果
    */
    public String getCommandType() {
        return commandType;
    }

    /**
    * 获取elapsedms
    *
    * @return 获取elapsedms的结果
    */
    public long getElapsedMs() {
        return elapsedMs;
    }

    /**
    * 获取追踪标识
    *
    * @return 获取追踪id的结果
    */
    public String getTraceId() {
        return traceId;
    }

    /**
    * 获取SQL
    *
    * @return 获取sql的结果
    */
    public String getSql() {
        return sql;
    }

    /**
    * 获取参数
    *
    * @return 获取参数的结果
    */
    public List<String> getParameters() {
        return parameters;
    }
}

