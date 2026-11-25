package com.chua.starter.common.support.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 可选功能配置属性
 * <p>
 * 用于配置系统中的可选功能开关。
 * </p>
 *
 * <h3>配置示例</h3>
 * <pre>
 * plugin:
 *   optional:
 *     enable: true   # 是否启用可选功能（字段默认 true；装配条件 matchIfMissing=false，需显式开启）
 * </pre>
 *
 * @author CH
 * @since 4.0.0
 */
@ConfigurationProperties(prefix = OptionalProperties.PRE, ignoreInvalidFields = true)
public class OptionalProperties {

    /**
     * 配置前缀
     */
    public static final String PRE = "plugin.optional";

    /**
     * 是否启用可选功能。
     * <p>YAML：{@code plugin.optional.enable}，字段默认 {@code true}；装配条件为
     * {@code matchIfMissing = false}，需显式配置 {@code plugin.optional.enable=true} 才会装配。</p>
     */
    private boolean enable = true;

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
}
