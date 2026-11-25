package com.chua.starter.datasource.properties;

import com.chua.common.support.data.materialized.MaterializedSqlOptions;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * SQL 物理化路由配置。
 * <p>
 * 支持通过 {@code plugin.datasource.materialized.*} 配置（legacy：{@code spring.datasource.materialized.*}）。
 * </p>
 * 配置示例:
 * <pre>
 * plugin:
 *   datasource:
 *     materialized:
 *       enabled: true                        # 是否启用 SQL 物理化路由
 *       default-threshold: 1000              # 默认表行数阈值
 *       refresh-interval-seconds: 0          # 内存副本自动刷新间隔(秒)，&lt;=0 关闭自动刷新
 *       skip-query-in-transaction: true      # 事务中是否跳过查询物理化
 *       cache-data-source-prefix: materialized#  # 物理化数据源名称前缀
 * </pre>
 *
 * @since 2026/4/2
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = MaterializedRouteProperties.PRE)
public class MaterializedRouteProperties {

    /**
     * PRE
    */
    public static final String PRE = "plugin.datasource.materialized";
    /**
     * Legacy_pre
    */
    public static final String LEGACY_PRE = "spring.datasource.materialized";

    /**
     * 是否启用。
     * 默认值: false
     * 示例: plugin.datasource.materialized.enabled=true
     */
    private boolean enabled = false;

    /**
     * 默认表行数阈值。
     * 默认值: 1000
     * 示例: plugin.datasource.materialized.default-threshold=1000
     */
    // [P3C A20 豁免]
    private long defaultThreshold = 1000L;

    /**
     * 内存副本自动刷新间隔，单位秒。
     * 小于等于 0 时表示关闭自动刷新。
     * 默认值: 0
     * 示例: plugin.datasource.materialized.refresh-interval-seconds=0
     */
    private long refreshIntervalSeconds = 0L;

    /**
     * 事务中是否跳过查询物理化。
     * 默认跳过，避免与事务连接绑定冲突。
     * 默认值: true
     * 示例: plugin.datasource.materialized.skip-query-in-transaction=true
     */
    private boolean skipQueryInTransaction = true;

    /**
     * 物理化数据源名称前缀。
     * 默认值: materialized#
     * 示例: plugin.datasource.materialized.cache-data-source-prefix=materialized#
     */
    private String cacheDataSourcePrefix = "materialized#";

    /**
     * 绑定
     *
     * @param environment 环境
     * @return bind的结果
     */
    public static MaterializedRouteProperties bind(Environment environment) {
        MaterializedRouteProperties current = Binder.get(environment).bindOrCreate(PRE, MaterializedRouteProperties.class);
        MaterializedRouteProperties legacy = Binder.get(environment).bindOrCreate(LEGACY_PRE, MaterializedRouteProperties.class);

        MaterializedRouteProperties result = new MaterializedRouteProperties();
        result.setEnabled(readBoolean(environment, PRE + ".enabled", LEGACY_PRE + ".enabled",
                current.isEnabled(), legacy.isEnabled()));
        result.setDefaultThreshold(readLong(environment, PRE + ".default-threshold", LEGACY_PRE + ".default-threshold",
                current.getDefaultThreshold(), legacy.getDefaultThreshold()));
        result.setRefreshIntervalSeconds(readLong(environment, PRE + ".refresh-interval-seconds", LEGACY_PRE + ".refresh-interval-seconds",
                current.getRefreshIntervalSeconds(), legacy.getRefreshIntervalSeconds()));
        result.setSkipQueryInTransaction(readBoolean(environment, PRE + ".skip-query-in-transaction", LEGACY_PRE + ".skip-query-in-transaction",
                current.isSkipQueryInTransaction(), legacy.isSkipQueryInTransaction()));
        result.setCacheDataSourcePrefix(readString(environment, PRE + ".cache-data-source-prefix", LEGACY_PRE + ".cache-data-source-prefix",
                current.getCacheDataSourcePrefix(), legacy.getCacheDataSourcePrefix()));
        return result;
    }

    /**
     * 读取布尔值
     * @param environment 环境
     * @param currentKey 当前Key
     * @param legacyKey legacyKey
     * @param currentValue 当前值
     * @param legacyValue legacy值
     * @return 布尔配置值
     */
    private static boolean readBoolean(Environment environment,
                                       String currentKey,
                                       String legacyKey,
                                       boolean currentValue,
                                       boolean legacyValue) {
        if (environment.containsProperty(currentKey)) {
            return currentValue;
        }
        if (environment.containsProperty(legacyKey)) {
            return legacyValue;
        }
        return currentValue;
    }

    /**
     * 读取Long
     * @param environment 环境
     * @param currentKey 当前Key
     * @param legacyKey legacyKey
     * @param currentValue 当前值
     * @param legacyValue legacy值
     * @return 长整数配置值
     */
    private static long readLong(Environment environment,
                                 String currentKey,
                                 String legacyKey,
                                 long currentValue,
                                 long legacyValue) {
        if (environment.containsProperty(currentKey)) {
            return currentValue;
        }
        if (environment.containsProperty(legacyKey)) {
            return legacyValue;
        }
        return currentValue;
    }

    /**
     * 读取字符串
     * @param environment 环境
     * @param currentKey 当前Key
     * @param legacyKey legacyKey
     * @param currentValue 当前值
     * @param legacyValue legacy值
     * @return 字符串配置值
     */
    private static String readString(Environment environment,
                                     String currentKey,
                                     String legacyKey,
                                     String currentValue,
                                     String legacyValue) {
        if (environment.containsProperty(currentKey)) {
            return currentValue;
        }
        if (environment.containsProperty(legacyKey)) {
            return legacyValue;
        }
        return currentValue;
    }

    /**
     * 转为期权
     *
     * @return 转为期权的结果
     */
    public MaterializedSqlOptions toOptions() {
        MaterializedSqlOptions options = new MaterializedSqlOptions();
        options.setDefaultThreshold(defaultThreshold);
        options.setRefreshIntervalSeconds(refreshIntervalSeconds);
        options.setCacheDataSourcePrefix(cacheDataSourcePrefix);
        return options;
    }
}
