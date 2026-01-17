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
    /**
     * 是否启用
     */
    private boolean enable = false;


    public static final String PRE = "plugin.multi-datasource";

    /**
     * 是否强制使用注解上的数据源
     */
    private boolean forceAnnotation = true;

    // Getter 方法（Lombok 在 Java 25 下可能不工作，手动添加）
    public boolean isForceAnnotation() {
        return forceAnnotation;
    }
}
