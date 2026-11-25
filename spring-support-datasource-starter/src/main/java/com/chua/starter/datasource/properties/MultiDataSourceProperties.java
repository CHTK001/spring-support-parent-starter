package com.chua.starter.datasource.properties;

import lombok.Data;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 多数据源配置
 * <p>
 * 支持通过 {@code plugin.multi-datasource.*} 或 {@code spring.multi-datasource.*} 配置。
 * 条目同时具备 {@code name} 与 {@code url} 时才会注册为数据源；{@code name} 形如
 * {@code 组名_数字} 的条目自动归入同一负载均衡组。
 * </p>
 * 配置示例:
 * <pre>
 * plugin:
 *   multi-datasource:
 *     enable: true                     # 属性开关(注册仍由条目 name+url 齐全决定)
 *     data-source:
 *       - name: master
 *         url: jdbc:mysql://localhost:3306/app
 *         username: root
 *         password: your-password      # 占位符；支持 ENC(密文)
 *         driver-class-name: com.mysql.cj.jdbc.Driver
 *       - name: slave_1                # 组名_数字 → 自动归入 slave 组
 *         url: jdbc:mysql://localhost:3307/app
 *         username: root
 *         password: your-password
 * </pre>
 *
 * @since 2021-07-19
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = MultiDataSourceProperties.PRE, ignoreInvalidFields = true)
public class MultiDataSourceProperties {
    /**
     * PRE
    */
    public static final String PRE = "plugin.multi-datasource";
    /**
     * Legacy_pre
    */
    public static final String LEGACY_PRE = "spring.multi-datasource";

    /**
     * 是否启用多数据源自动配置
     * <p>是否真正注册仍由条目 {@code name}+{@code url} 齐全决定（见 README Q2）。</p>
     * 默认值: false
     * 示例: plugin.multi-datasource.enable=true
     */
    private boolean enable = false;

    /**
     * 数据源条目列表（Spring Boot {@link DataSourceProperties} 条目）
     * <p>条目需同时具备 {@code name} 与 {@code url} 才会注册为数据源；
     * {@code name} 形如 {@code 组名_数字} 时自动归入同一负载均衡组。</p>
     * 示例: plugin.multi-datasource.data-source[0].name=master
     */
    private List<DataSourceProperties> dataSource;

    // Getter 方法（Lombok 在 Java 25 下可能不工作，手动添加）
    /**
     * 获取数据源
     *
     * @return 获取数据源的结果
     */
    public List<DataSourceProperties> getDataSource() {
        return dataSource;
    }
}
