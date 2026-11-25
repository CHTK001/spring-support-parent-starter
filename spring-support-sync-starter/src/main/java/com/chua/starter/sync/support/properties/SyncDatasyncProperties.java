package com.chua.starter.sync.support.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * 数据同步 桥接配置。
 * <p>
 * 对应配置前缀：{@code plugin.sync.datasync}
 * </p>
 *
 * <p>该属性类本身未注册为 Spring Bean；桥接是否生效由
 * {@code @ConditionalOnProperty(prefix = "plugin.sync.datasync", name = "enabled",
 * havingValue = "true", matchIfMissing = true)} 判定，且仅当 DataSync 桥接配置类
 * 被组件扫描或 {@code @Import} 加载时才会读取（未登记进 {@code AutoConfiguration.imports}）。</p>
 *
 * <p>YAML 配置示例：</p>
 * <pre>{@code
 * plugin:
 *   sync:
 *     datasync:
 *       enabled: true               # 是否启用 DataSync 桥接，默认 true
 * }</pre>
 *
 * @since 2026/01/15
 * @author CH
 */
@ConfigurationProperties(prefix = SyncDatasyncProperties.PRE, ignoreInvalidFields = true)
public class SyncDatasyncProperties {

    /**
     * 配置前缀
     */
    public static final String PRE = "plugin.sync.datasync";

    /**
     * 是否启用 数据同步 桥接
     * <p>默认值：{@code true}；未配置时按 {@code matchIfMissing = true} 视为开启。
     * 置为 {@code false} 可关闭 DataSync 桥接（服务信息标志与桥接 Bean 均不生效）。</p>
     * <pre>{@code
     * plugin:
     *   sync:
     *     datasync:
     *       enabled: true
     * }</pre>
     */
    private boolean enabled = true;

    /**
     * 是否已启用
     *
     * @return 是否已启用的结果
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置已启用
     *
     * @param enabled 已启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
