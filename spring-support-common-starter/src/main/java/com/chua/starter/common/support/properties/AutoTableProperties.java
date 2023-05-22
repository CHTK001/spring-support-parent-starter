package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.starter.common.support.properties.AutoTableProperties.PRE;

/**
 * 自动建表
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class AutoTableProperties {

    public static final String PRE = "plugin.auto.table";
    /**
     * 开启
     */
    private boolean open = false;
    /**
     * 扫描包
     */
    private String[] scann;
}
