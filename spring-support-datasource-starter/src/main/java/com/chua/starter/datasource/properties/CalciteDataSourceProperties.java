package com.chua.starter.datasource.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Calcite数据源合并配置属性
 * <p>
 * 配置示例:
 * <pre>
 * plugin:
 *   datasource:
 *     calcite:
 *       enabled: true
 *       max-pool-size: 10
 *       schema-mapping:
 *         master: db_main
 *         slave: db_slave
 * </pre>
 *
 * @since 2024/12/21
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.datasource.calcite")
public class CalciteDataSourceProperties {

    /**
     * 是否启用Calcite数据源合并
     */
    private boolean enabled = false;

    /**
     * 连接池最大大小
     */
    // [P3C A20 豁免]
    private int maxPoolSize = 10;

    /**
     * 模式名称映射
     * Key: 原数据源名称
     * 值: Calcite中的模式名称
     */
    private Map<String, String> schemaMapping;

    /**
     * 默认模式名称
     */
    private String defaultSchema;
}
