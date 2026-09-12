package com.chua.starter.datasource.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 事务配置
 *
 * @since 2021-07-19
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = MultiDataSourceSettingProperties.PRE, ignoreInvalidFields = true)
public class MultiDataSourceSettingProperties {
    /**
     * 是否启用
     */
    private boolean enable = false;


    /** PRE */
    public static final String PRE = "plugin.multi-datasource";

    /**
     * 是否强制使用注解上的数据源
     */
    private boolean forceAnnotation = true;

    /**
     * 严格模式
     * <p>开启后，如果指定数据源不存在则抛出异常，不回退到默认数据源。</p>
     */
    private boolean strict = false;

    /**
      * 负载均衡策略：round_robin（轮询）/ 随机（随机）
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
