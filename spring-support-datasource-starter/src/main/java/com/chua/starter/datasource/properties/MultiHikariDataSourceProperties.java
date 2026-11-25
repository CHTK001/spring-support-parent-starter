package com.chua.starter.datasource.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 多数据源 Hikari 专项配置
 * <p>
 * 支持通过 {@code plugin.multi-datasource.hikari.*} 或 {@code spring.multi-datasource.hikari.*} 配置，
 * 在通用多数据源条目之上扩展 Hikari 连接池参数。
 * </p>
 * 配置示例:
 * <pre>
 * plugin:
 *   multi-datasource:
 *     hikari:
 *       enable: true
 *       data-source:
 *         - name: slave_1
 *           url: jdbc:mysql://localhost:3307/app
 *           username: root
 *           password: your-password      # 占位符
 *           max-pool-size: 100
 *           min-idle: 10
 *           idle-timeout: 600000
 *           max-lifetime: 1800000
 *           leak-detection-threshold: 0  # 0=不检测连接泄漏
 * </pre>
 *
 * @since 2021-07-19
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = MultiHikariDataSourceProperties.PRE, ignoreInvalidFields = true)
public class MultiHikariDataSourceProperties {
    /**
     * PRE
    */
    public static final String PRE = "plugin.multi-datasource.hikari";
    /**
     * Legacy_pre
    */
    public static final String LEGACY_PRE = "spring.multi-datasource.hikari";

    /**
     * 是否启用 Hikari 专项数据源列表
     * 默认值: false
     * 示例: plugin.multi-datasource.hikari.enable=true
     */
    private boolean enable = false;

    /**
     * Hikari 数据源条目列表（{@link HikariDataSourceProperties}）
     * <p>条目需同时具备 {@code name} 与 {@code url} 才会注册为数据源。</p>
     * 示例: plugin.multi-datasource.hikari.data-source[0].name=slave_1
     */
    private List<HikariDataSourceProperties> dataSource;

    // Getter 方法（Lombok 在 Java 25 下可能不工作，手动添加）
    /**
     * 获取数据源
     *
     * @return 获取数据源的结果
     */
    public List<HikariDataSourceProperties> getDataSource() {
        return dataSource;
    }
}
