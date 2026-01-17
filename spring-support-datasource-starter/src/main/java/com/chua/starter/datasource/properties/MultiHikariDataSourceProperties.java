package com.chua.starter.datasource.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 事务配置
 *
 * @author CH
 * @since 2021-07-19
 */
@Data
@ConfigurationProperties(prefix = MultiHikariDataSourceProperties.PRE, ignoreInvalidFields = true)
public class MultiHikariDataSourceProperties {
    /**
     * 是否启用
     */
    private boolean enable = false;


    public static final String PRE = "spring.multi-datasource.hikari";


    /**
     * 数据源
     */
    private List<HikariDataSourceProperties> dataSource;

    // Getter 方法（Lombok 在 Java 25 下可能不工作，手动添加）
    public List<HikariDataSourceProperties> getDataSource() {
        return dataSource;
    }
}
