// [P3C A20 豁免] A20/A30/A31: 池名等字符串字面量场景，豁免。
package com.chua.starter.datasource.runtime.support;

import com.chua.starter.datasource.runtime.model.FederationMember;
import com.chua.starter.datasource.runtime.model.ShardingDbMember;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行时成员数据源池管理。
 *
 * <p>联邦成员 / 分库成员的连接参数由页面提交，本组件按「成员键」懒建 HikariCP
 * 小池并复用；配置变更（refresh）时按新成员集合做差量：新增建池、移除/变更关池，
 * 保证运行期热刷新不泄漏连接。联邦与分库使用互相独立的池命名空间，
 * 一侧刷新不会误关另一侧的池。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Component
public class MemberDataSourcePool {

    /**
     * 联邦成员池：成员键 -> 池
     */
    private final Map<String, HikariDataSource> federationPools = new ConcurrentHashMap<>();

    /**
     * 分库成员池：成员键 -> 池
     */
    private final Map<String, HikariDataSource> shardingPools = new ConcurrentHashMap<>();

    /**
     * 按联邦成员列表绑定数据源（schema 名 -> DataSource，保持顺序）。
     * 调用方必须一次性传入当前全部联邦成员；不在其中的既有联邦池会被关闭。
     *
     * @param members 联邦成员
     * @return schema 名映射；成员参数不完整或建池失败的被跳过
     */
    public Map<String, DataSource> bindFederation(Iterable<FederationMember> members) {
        Map<String, DataSource> result = new LinkedHashMap<>();
        if (members != null) {
            for (FederationMember m : members) {
                if (m == null || isBlank(m.getSchemaName()) || isBlank(m.getUrl())) {
                    log.warn("[ds-runtime] 联邦成员参数不完整，跳过: {}", m == null ? "null" : m.getSchemaName());
                    continue;
                }
                String key = memberKey(m.getSchemaName(), m.getUrl(), m.getUsername());
                HikariDataSource ds = federationPools.computeIfAbsent(key,
                        k -> createPool(m.getSchemaName(), m.getUrl(),
                                m.getUsername(), m.getPassword(), m.getDriverClassName()));
                if (ds != null) {
                    result.putIfAbsent(m.getSchemaName(), ds);
                }
            }
        }
        evictExcept(federationPools, result.values());
        return result;
    }

    /**
     * 按分库成员列表绑定数据源（分库名 -> DataSource，保持顺序）。
     * 字符串引用形式（无 url）的成员交由调用方从已注册数据源解析，不在此建池。
     *
     * @param members 分库成员
     * @return 分库名映射；引用型/不完整/建池失败的成员不包含在内
     */
    public Map<String, DataSource> bindSharding(Iterable<ShardingDbMember> members) {
        Map<String, DataSource> result = new LinkedHashMap<>();
        if (members != null) {
            for (ShardingDbMember m : members) {
                if (m == null || isBlank(m.getName()) || isBlank(m.getUrl())) {
                    continue;
                }
                String key = memberKey(m.getName(), m.getUrl(), m.getUsername());
                HikariDataSource ds = shardingPools.computeIfAbsent(key,
                        k -> createPool(m.getName(), m.getUrl(),
                                m.getUsername(), m.getPassword(), m.getDriverClassName()));
                if (ds != null) {
                    result.putIfAbsent(m.getName(), ds);
                }
            }
        }
        evictExcept(shardingPools, result.values());
        return result;
    }

    /**
     * 关闭全部联邦成员池。
     */
    public void closeFederationPools() {
        closeAllOf(federationPools);
    }

    /**
     * 关闭全部分库成员池。
     */
    public void closeShardingPools() {
        closeAllOf(shardingPools);
    }

    /**
     * 关闭全部池（应用停机时调用）。
     */
    public void closeAll() {
        closeFederationPools();
        closeShardingPools();
    }

    /**
     * 关闭指定命名空间中不在保留集合里的池（配置移除/变更后清理）。
     */
    private void evictExcept(Map<String, HikariDataSource> namespace, Iterable<DataSource> keep) {
        for (Map.Entry<String, HikariDataSource> e : namespace.entrySet()) {
            if (!contains(keep, e.getValue())) {
                if (namespace.remove(e.getKey(), e.getValue())) {
                    closeQuietly(e.getKey(), e.getValue());
                }
            }
        }
    }

    private void closeAllOf(Map<String, HikariDataSource> namespace) {
        for (Map.Entry<String, HikariDataSource> e : namespace.entrySet()) {
            if (namespace.remove(e.getKey(), e.getValue())) {
                closeQuietly(e.getKey(), e.getValue());
            }
        }
    }

    private static boolean contains(Iterable<DataSource> list, DataSource ds) {
        for (DataSource d : list) {
            if (d == ds) {
                return true;
            }
        }
        return false;
    }

    /**
     * 创建 HikariCP 小池（元数据/DDL 场景，2 连接足够）。
     */
    private HikariDataSource createPool(String name, String url, String username, String password,
                                        String driverClassName) {
        try {
            HikariConfig config = new HikariConfig();
            config.setPoolName("ds-runtime-" + sanitize(name));
            config.setJdbcUrl(url);
            if (!isBlank(username)) {
                config.setUsername(username);
            }
            if (!isBlank(password)) {
                config.setPassword(password);
            }
            if (!isBlank(driverClassName)) {
                config.setDriverClassName(driverClassName);
            }
            config.setMaximumPoolSize(2);
            config.setMinimumIdle(0);
            config.setConnectionTimeout(8000);
            config.setValidationTimeout(5000);
            HikariDataSource ds = new HikariDataSource(config);
            log.info("[ds-runtime] 成员数据源池已创建: {} ({})", name, url);
            return ds;
        } catch (Exception e) {
            log.error("[ds-runtime] 成员数据源池创建失败: {} ({}): {}", name, url, e.getMessage());
            return null;
        }
    }

    /**
     * 直接打开物理连接（DDL 预览/执行等低频操作使用，不走池）。
     */
    public static java.sql.Connection openDirect(String url, String username, String password,
                                                 String driverClassName) throws Exception {
        if (!isBlank(driverClassName)) {
            Class.forName(driverClassName);
        }
        if (isBlank(username)) {
            return java.sql.DriverManager.getConnection(url);
        }
        return java.sql.DriverManager.getConnection(url, username, password);
    }

    private void closeQuietly(String key, HikariDataSource ds) {
        try {
            ds.close();
            log.info("[ds-runtime] 成员数据源池已关闭: {}", key);
        } catch (Exception e) {
            log.warn("[ds-runtime] 成员数据源池关闭失败: {}: {}", key, e.getMessage());
        }
    }

    private static String memberKey(String name, String url, String username) {
        return name + "|" + url + "|" + (username == null ? "" : username);
    }

    private static String sanitize(String name) {
        return name == null ? "member" : name.replaceAll("[^0-9a-zA-Z_\\-]", "_");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
