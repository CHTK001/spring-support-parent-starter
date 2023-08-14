package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对象配置
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = BeanProperties.PRE, ignoreInvalidFields = true)
public class BeanProperties {

    public static final String PRE = "plugin.bean";

    /**
     * 存放目录(磁盘目录, 不支持压缩包)
     */
    private String store;


}
