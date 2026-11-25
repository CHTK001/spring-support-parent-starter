package com.chua.starter.lowcode.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 低代码表单管理配置。
 *
 * <p>配置前缀 {@code plugin.lowcode}。每一项都在业务服务中被真实读取，
 * 不接受只写不读的参数。</p>
 *
 * <p>完整 YAML 配置示例：</p>
 *
 * <pre>
 * plugin:
 *   lowcode:
 *     enable: true   # 功能总开关；false 时不装配本模块任何能力（默认 true，不配置也视为开启）
 * </pre>
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
     * <p>含义：控制低代码模块是否装配。{@code true}（或缺省）时
     * {@link com.chua.starter.lowcode.configuration.LowcodeAutoConfiguration} 生效，
     * 宿主获得目录 / 表单 / 页面全套接口；{@code false} 时不装配低代码的任何能力，
     * 接口自然也不暴露，已保存的 Schema 保留在库里不动。</p>
     *
     * <p>默认值：{@code true}（与字段初始化值一致；{@code @ConditionalOnProperty} 为
     * {@code matchIfMissing = true}，配置缺失也按开启处理）。本项是 {@code boolean}，
     * 不存在「≤0 或空串回落」语义：未配置时即取初始化值 {@code true}。</p>
     *
     * <p>YAML 配置示例：</p>
     *
     * <pre>
     * plugin:
     *   lowcode:
     *     enable: true
     * </pre>
     */
    private boolean enable = true;
}
