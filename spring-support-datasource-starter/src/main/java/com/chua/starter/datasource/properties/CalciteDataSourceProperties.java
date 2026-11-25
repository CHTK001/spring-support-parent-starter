package com.chua.starter.datasource.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
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
 *       bean-name: calciteDataSource
 *       data-sources:
 *         - master
 *         - slave_1
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
     * 聚合数据源 Bean 名称。
     * <p>
     * 规范 Bean 名固定为 {@code calciteDataSource}（运行期联邦管理器
     * {@code CalciteFederationManager.BEAN_NAME} 与热替换注册表都依赖该名称）；
     * 配置为其它名称时，自定义名以<b>别名</b>形式注册到规范 Bean 上，
     * 两个名字都能注入同一聚合数据源。
     */
    private String beanName = "calciteDataSource";

    /**
     * 参与聚合的数据源名称数组。
     * <p>
     * 未配置（空）时聚合全部已注册的普通数据源；配置后只聚合名单内的数据源，
     * 名单中未注册的名称告警跳过，全部未命中则不创建聚合数据源。
     */
    private List<String> dataSources;

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
