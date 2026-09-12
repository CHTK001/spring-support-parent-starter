package com.chua.starter.datasource.properties;

import lombok.Data;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
* 事务配置
*
* @since 2021-07-19
* @author CH
 */
@Data
@ConfigurationProperties(prefix = MultiDataSourceProperties.PRE, ignoreInvalidFields = true)
public class MultiDataSourceProperties {
    /** PRE */
    public static final String PRE = "plugin.multi-datasource";
    /** Legacy_pre */
    public static final String LEGACY_PRE = "spring.multi-datasource";

    /**
    * 是否启用
     */
    private boolean enable = false;

    /**
    * 数据源
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
