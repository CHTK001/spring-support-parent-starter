package com.chua.starter.datasource.properties;

import com.chua.common.support.data.materialized.MaterializedSqlOptions;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * SQL 物理化路由配置。
 *
 * @author CH
 * @since 2026/4/2
 */
@Data
@ConfigurationProperties(prefix = MaterializedRouteProperties.PRE)
public class MaterializedRouteProperties {

    public static final String PRE = "plugin.datasource.materialized";
    public static final String LEGACY_PRE = "spring.datasource.materialized";

    /**
     * 是否启用。
     */
    private boolean enabled = false;

    /**
     * 默认表行数阈值。
     */
    private long defaultThreshold = 1000L;

    /**
     * 内存副本自动刷新间隔，单位秒。
     * 小于等于 0 时表示关闭自动刷新。
     */
    private long refreshIntervalSeconds = 0L;

    /**
     * 事务中是否跳过查询物理化。
     * 默认跳过，避免与事务连接绑定冲突。
     */
    private boolean skipQueryInTransaction = true;

    /**
     * 物理化数据源名称前缀。
     */
    private String cacheDataSourcePrefix = "materialized#";

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

    public MaterializedSqlOptions toOptions() {
        MaterializedSqlOptions options = new MaterializedSqlOptions();
        options.setDefaultThreshold(defaultThreshold);
        options.setRefreshIntervalSeconds(refreshIntervalSeconds);
        options.setCacheDataSourcePrefix(cacheDataSourcePrefix);
        return options;
    }
}
