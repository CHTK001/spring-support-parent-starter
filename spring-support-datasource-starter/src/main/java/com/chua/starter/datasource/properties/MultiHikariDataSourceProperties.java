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

    public static final String PRE = "spring.multi-datasource.hikari";


    /**
     * 数据源
     */
    private List<HikariDataSourceProperties> dataSource;
}
