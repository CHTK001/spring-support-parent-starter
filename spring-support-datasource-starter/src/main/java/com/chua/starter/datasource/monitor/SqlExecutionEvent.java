package com.chua.starter.datasource.monitor;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
* SQL 执行事件
*
* <p>每条被监控的 SQL 执行完成后发布一次，携带执行耗时、数据源名、结果信息。
* 可通过 Spring 事件监听或 {@link SqlExecutionListener} 消费。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Getter
public class SqlExecutionEvent extends ApplicationEvent {

    /**
    * 数据源名称
    */
    private final String dataSourceName;

    /**
    * SQL 文本（可能被截断）
    */
    private final String sql;

    /**
    * SQL 类型（选择/插入/更新/删除/其他）
    */
    private final String sqlType;

    /**
    * 执行耗时（毫秒）
    */
    private final long costMs;

    /**
    * 是否慢 SQL
    */
    private final boolean slowSql;

    /**
    * 是否执行成功
    */
    private final boolean success;

    /**
    * 失败原因（失败时不为空）
    */
    private final String errorMessage;

    /**
    * 执行完成时间
    */
    private final Instant finishedAt;

    /**
    * 创建执行事件
    *
    * @param source         事件源
    * @param dataSourceName 数据源名称
    * @param sql            SQL 文本
    * @param sqlType        SQL 类型
    * @param costMs         执行耗时（毫秒）
    * @param slowSql        是否慢 SQL
    * @param success        是否执行成功
    * @param errorMessage   失败原因
    */
    public SqlExecutionEvent(Object source, String dataSourceName, String sql, String sqlType,
                             long costMs, boolean slowSql, boolean success, String errorMessage) {
        /**
        * 父。
        * @param source 源
        */
        super(source);
        this.dataSourceName = dataSourceName;
        this.sql = sql;
        this.sqlType = sqlType;
        this.costMs = costMs;
        this.slowSql = slowSql;
        this.success = success;
        this.errorMessage = errorMessage;
        this.finishedAt = Instant.now();
    }
}
