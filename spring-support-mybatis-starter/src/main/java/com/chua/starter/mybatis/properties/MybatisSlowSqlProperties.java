package com.chua.starter.mybatis.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.starter.mybatis.properties.MybatisPlusProperties.PRE;

/**
 * MyBatis 慢 SQL 检测配置。
 *
 * <p>当 SQL 执行耗时超过阈值时，拦截器会发布 Spring 事件，供业务侧订阅进行告警/审计。</p>
 *
 * <p>配置前缀：{@code plugin.mybatis.slow-sql}</p>
 *
 * @since 2026/4/27
 * @author CH
 */
@ConfigurationProperties(prefix = PRE + ".slow-sql")
public class MybatisSlowSqlProperties {

    /**
     * 是否启用慢 SQL 检测。
     */
    private boolean enabled = true;

    /**
     * 慢 SQL 阈值（毫秒）。默认 500ms。
     */
    private long thresholdMs = 500;

    /**
     * 是否打印慢 SQL 日志（WARN）。
     */
    private boolean print = true;

    /**
     * 是否在事件中携带 SQL 文本（可能包含敏感信息）。
     */
    private boolean includeSql = true;

    /**
     * 是否在事件中携带参数映射信息（可能包含敏感信息）。
     */
    private boolean includeParameters = false;

    /**
     * SQL 最大长度。超过后会截断，避免事件体过大。
     */
    private int maxSqlLength = 2000;

    public boolean isEnabled() {
        return enabled;
    }

    public long getThresholdMs() {
        return thresholdMs;
    }

    public boolean isPrint() {
        return print;
    }

    public boolean isIncludeSql() {
        return includeSql;
    }

    public boolean isIncludeParameters() {
        return includeParameters;
    }

    public int getMaxSqlLength() {
        return maxSqlLength;
    }
}