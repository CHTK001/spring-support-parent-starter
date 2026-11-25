package com.chua.starter.datasource.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 多数据源行为配置
 * <p>
 * 与 {@link MultiDataSourceProperties} 共用前缀 {@code plugin.multi-datasource}
 * （legacy：{@code spring.multi-datasource}），承载数据源路由与负载均衡行为开关。
 * </p>
 * 配置示例:
 * <pre>
 * plugin:
 *   multi-datasource:
 *     load-balance: round_robin      # 负载均衡策略: round_robin(轮询) / random(随机)
 *     strict: false                  # 严格模式：@DS 指向不存在的数据源时抛异常
 *     force-annotation: true         # 无可用数据源时仍按注解值处理
 * </pre>
 *
 * @since 2021-07-19
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = MultiDataSourceSettingProperties.PRE, ignoreInvalidFields = true)
public class MultiDataSourceSettingProperties {
    /**
     * 是否启用多数据源自动配置
     * 默认值: false
     * 示例: plugin.multi-datasource.enable=true
     */
    private boolean enable = false;


    /**
     * PRE
    */
    public static final String PRE = "plugin.multi-datasource";

    /**
     * 是否强制使用注解上的数据源（无可用数据源时仍按注解值处理）
     * 默认值: true
     * 示例: plugin.multi-datasource.force-annotation=true
     */
    private boolean forceAnnotation = true;

    /**
     * 严格模式
     * <p>开启后，如果指定数据源（如 {@code @DS} 注解值）不存在则抛出异常，不回退到默认数据源；
     * 关闭（默认）时回退到默认数据源——名为 {@code master} 的数据源，未命名 master 时为
     * 按注册顺序第一个注册的数据源。</p>
     * 默认值: false
     * 示例: plugin.multi-datasource.strict=true
     */
    private boolean strict = false;

    /**
     * 负载均衡策略：round_robin（轮询）/ random（随机）
     * 默认值: round_robin
     * 示例: plugin.multi-datasource.load-balance=random
     */
    private String loadBalance = "round_robin";

    // Getter 方法
    /**
     * 是否force注解
     *
     * @return 是否force注解的结果
     */
    public boolean isForceAnnotation() {
        return forceAnnotation;
    }

    /**
     * 是否Strict
     *
     * @return 是否strict的结果
     */
    public boolean isStrict() {
        return strict;
    }

    /**
     * 设置Strict
     *
     * @param strict strict
     */
    public void setStrict(boolean strict) {
        this.strict = strict;
    }

    /**
     * 获取加载Balance
     *
     * @return 获取加载balance的结果
     */
    public String getLoadBalance() {
        return loadBalance;
    }

    /**
     * 设置加载Balance
     *
     * @param loadBalance 加载balance
     */
    public void setLoadBalance(String loadBalance) {
        this.loadBalance = loadBalance;
    }
}
