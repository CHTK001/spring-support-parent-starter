package com.chua.starter.datasource.properties;

import lombok.Data;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 事务配置
 *
 * @author CH
 * @since 2021-07-19
 */
@Data
@ConfigurationProperties(prefix = MultiDataSourceProperties.PRE, ignoreInvalidFields = true)
public class MultiDataSourceProperties {
    /**
     * 是否启用
     */
    private boolean enable = false;


    public static final String PRE = "spring.multi-datasource";


    /**
     * 数据源
     */
    private List<DataSourceProperties> dataSource;
}
