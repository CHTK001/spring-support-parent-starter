package com.chua.starter.common.support.jackson.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Jackson配置属性
 *
 * @版本 1.0.0
 * @since 2024/12/3
 * @author CH
 */
@ConfigurationProperties(prefix = JacksonProperties.PRE, ignoreInvalidFields = true)
public class JacksonProperties {
    /**
     * 是否启用
     */
    private boolean enable = false;


    /** PRE */
    public static final String PRE = "plugin.jackson";

    /**
     * 是否包含空值
     */
    private boolean includeNull = false;

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
     * 获取 include空
     *
     * @return includeNull
     */
    public boolean getIncludeNull() {
        return includeNull;
    }

    /**
     * 判断是否包含空值
     *
     * @return includeNull
     */
    public boolean isIncludeNull() {
        return includeNull;
    }

    /**
     * 设置 include空
     *
     * @param includeNull include空
     */
    public void setIncludeNull(boolean includeNull) {
        this.includeNull = includeNull;
    }
}
