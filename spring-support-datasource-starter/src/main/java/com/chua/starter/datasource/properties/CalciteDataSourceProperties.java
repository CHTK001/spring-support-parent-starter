package com.chua.starter.datasource.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Calcite数据源合并配置属性
 * <p>
 * 配置示例:
 * <pre>
 * spring:
 *   datasource:
 *     calcite:
 *       enabled: true
 *       max-pool-size: 10
 *       schema-mapping:
 *         master: db_main
 *         slave: db_slave
 * </pre>
 *
 * @author CH
 * @since 2024/12/21
 */
@Data
@ConfigurationProperties(prefix = "spring.datasource.calcite")
public class CalciteDataSourceProperties {

    /**
     * 是否启用Calcite数据源合并
     */
    private boolean enabled = false;

    /**
     * 连接池最大大小
     */
    private int maxPoolSize = 10;

    /**
     * Schema名称映射
     * key: 原数据源名称
     * value: Calcite中的Schema名称
     */
    private Map<String, String> schemaMapping;

    /**
     * 默认Schema名称
     */
    private String defaultSchema;
}
