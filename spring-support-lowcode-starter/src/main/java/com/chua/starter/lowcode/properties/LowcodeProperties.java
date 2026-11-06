package com.chua.starter.lowcode.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 低代码表单管理配置。
 *
 * <p>配置前缀 {@code plugin.lowcode}。每一项都在业务服务中被真实读取，
 * 不接受只写不读的参数。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@ConfigurationProperties(prefix = LowcodeProperties.PRE)
public class LowcodeProperties {

    /**
     * 配置前缀。
     *
     * <p>与 {@link LowcodeAutoConfiguration} 的 {@code @ConditionalOnProperty} 共用同一前缀，
     * 改名时不会漏改。</p>
     */
    public static final String PRE = "plugin.lowcode";

    /**
     * 功能总开关。
     *
     * <p>关闭后不装配低代码的任何能力，接口自然也不排除，已保存的 Schema 保留在库里不动。</p>
     */
    private boolean enable = true;
}
