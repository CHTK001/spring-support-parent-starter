package com.chua.starter.datasource.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 事务配置
 *
 * @author CH
 * @since 2021-07-19
 */
@Data
@ConfigurationProperties(prefix = MultiDataSourceSettingProperties.PRE, ignoreInvalidFields = true)
public class MultiDataSourceSettingProperties {

    public static final String PRE = "plugin.multi-datasource";

    /**
     * 是否强制使用注解上的数据源
     */
    private boolean forceAnnotation = true;
}
