package com.chua.starter.common.support.jackson.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Jackson配置属性
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/3
 */
@ConfigurationProperties(prefix = JacksonProperties.PRE, ignoreInvalidFields = true)
public class JacksonProperties {
    /**
     * 是否启用
     */
    private boolean enable = false;


    public static final String PRE = "plugin.jackson";

    /**
     * 是否包含null值
     */
    private boolean includeNull = false;
}
    /**
     * 获取 enable
     *
     * @return enable
     */
    public boolean getEnable() {
        return enable;
    }

    /**
     * 设置 enable
     *
     * @param enable enable
     */
    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    /**
     * 获取 includeNull
     *
     * @return includeNull
     */
    public boolean getIncludeNull() {
        return includeNull;
    }

    /**
     * 设置 includeNull
     *
     * @param includeNull includeNull
     */
    public void setIncludeNull(boolean includeNull) {
        this.includeNull = includeNull;
    }


