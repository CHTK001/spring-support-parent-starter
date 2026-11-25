package com.chua.starter.common.support.jackson.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Jackson 配置属性。
 *
 * <p>前缀 {@code plugin.jackson}。控制 Jackson 序列化行为；本配置类随 Web 容器
 * 恒定装配（无独立装配开关），{@code include-null} 决定 ObjectMapper 是否序列化
 * null 值字段。</p>
 *
 * <h3>配置示例</h3>
 * <pre>
 * plugin:
 *   jackson:
 *     enable: false        # 功能开关（默认 false；不控制本配置类的装配）
 *     include-null: false  # 序列化是否包含 null 值字段（默认 false）
 * </pre>
 *
 * @since 2024/12/3
 * @author CH
 */
@ConfigurationProperties(prefix = JacksonProperties.PRE, ignoreInvalidFields = true)
public class JacksonProperties {
    /**
     * 功能开关（当前不控制本配置类的装配）。
     * <p>YAML：{@code plugin.jackson.enable}，默认 {@code false}。</p>
     */
    private boolean enable = false;


    /**
     * PRE
    */
    public static final String PRE = "plugin.jackson";

    /**
     * 是否包含 null 值字段。
     * <p>YAML：{@code plugin.jackson.include-null}，默认 {@code false}；
     * 由 JacksonConfiguration 创建 ObjectMapper 时消费。</p>
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
