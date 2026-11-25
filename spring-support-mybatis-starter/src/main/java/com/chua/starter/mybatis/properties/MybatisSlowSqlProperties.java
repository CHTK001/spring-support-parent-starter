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
 * <p>完整 YAML 配置示例（未配置项使用字段默认值，检测默认开启）：</p>
 *
 * <pre>
 * plugin:
 *   mybatis:
 *     slow-sql:
 *       enabled: true             # 是否启用慢 SQL 检测（不配置也默认开启）
 *       threshold-ms: 500         # 慢 SQL 阈值（毫秒），超过即发布 MybatisSlowSqlEvent
 *       print: true               # 是否打印慢 SQL WARN 日志
 *       include-sql: true         # 事件是否携带 SQL 文本（可能含敏感信息）
 *       include-parameters: false # 事件是否携带参数映射（可能含敏感信息）
 *       max-sql-length: 2000      # SQL 文本最大长度，超出截断
 * </pre>
 *
 * @since 2026/4/27
 * @author CH
 */
@ConfigurationProperties(prefix = PRE + ".slow-sql")
public class MybatisSlowSqlProperties {

    /**
     * 是否启用慢 SQL 检测。
     *
     * <p>默认值：{@code true}（Bean 装配条件 {@code matchIfMissing=true}，不配置默认开启）。</p>
     *
     * <pre>
     * plugin:
     *   mybatis:
     *     slow-sql:
     *       enabled: true
     * </pre>
     */
    private boolean enabled = true;

    /**
     * 慢 SQL 阈值（毫秒）。默认 500ms。
     *
     * <p>默认值：{@code 500}。代码未对 ≤0 做回落处理，请配置为正整数；
     * 阈值过小会造成慢 SQL 事件风暴，建议结合实际基线调整。</p>
     *
     * <pre>
     * plugin:
     *   mybatis:
     *     slow-sql:
     *       threshold-ms: 500
     * </pre>
     */
    // [P3C A20 豁免]
    private long thresholdMs = 500;

    /**
     * 是否打印慢 SQL 日志（WARN）。
     *
     * <p>默认值：{@code true}。</p>
     *
     * <pre>
     * plugin:
     *   mybatis:
     *     slow-sql:
     *       print: true
     * </pre>
     */
    private boolean print = true;

    /**
     * 是否在事件中携带 SQL 文本（可能包含敏感信息）。
     *
     * <p>默认值：{@code true}。生产环境如担心 SQL 文本泄露敏感数据，可配置为 {@code false}。</p>
     *
     * <pre>
     * plugin:
     *   mybatis:
     *     slow-sql:
     *       include-sql: true
     * </pre>
     */
    private boolean includeSql = true;

    /**
     * 是否在事件中携带参数映射信息（可能包含敏感信息）。
     *
     * <p>默认值：{@code false}。</p>
     *
     * <pre>
     * plugin:
     *   mybatis:
     *     slow-sql:
     *       include-parameters: false
     * </pre>
     */
    private boolean includeParameters = false;

    /**
     * SQL 最大长度。超过后会截断，避免事件体过大。
     *
     * <p>默认值：{@code 2000}。代码未对 ≤0 做回落处理，请配置为正整数。</p>
     *
     * <pre>
     * plugin:
     *   mybatis:
     *     slow-sql:
     *       max-sql-length: 2000
     * </pre>
     */
    // [P3C A20 豁免]
    private int maxSqlLength = 2000;

    /**
     * 是否已启用
     *
     * @return 是否已启用的结果
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 获取阈值ms
     *
     * @return 获取阈值ms的结果
     */
    public long getThresholdMs() {
        return thresholdMs;
    }

    /**
     * 是否Print
     *
     * @return 是否print的结果
     */
    public boolean isPrint() {
        return print;
    }

    /**
     * 是否includesql
     *
     * @return 是否includesql的结果
     */
    public boolean isIncludeSql() {
        return includeSql;
    }

    /**
     * 是否include参数
     *
     * @return 是否include参数的结果
     */
    public boolean isIncludeParameters() {
        return includeParameters;
    }

    /**
     * 获取最大值SQL获取长度
     *
     * @return 获取最大sql长度的结果
     */
    public int getMaxSqlLength() {
        return maxSqlLength;
    }
}
