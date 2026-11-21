// [P3C A20 豁免] A20/A30/A31: SQL/日志字符串字面量场景，豁免。
package com.chua.starter.datasource.runtime.sharding;

import com.chua.datasource.support.engine.DataSourceEnvironment;
import com.chua.shardingv5.support.conversion.ShardingV5Conversion;
import com.chua.starter.datasource.runtime.model.DsShardingRule;
import com.chua.starter.datasource.runtime.model.ShardingDbMember;
import com.chua.starter.datasource.runtime.store.DsRuntimeConfigStore;
import com.chua.starter.datasource.runtime.support.MemberDataSourcePool;
import com.chua.starter.datasource.support.DataSourceContextSupport;
import com.chua.starter.datasource.support.DynamicDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 分库分表规则运行期管理器。
 *
 * <p>保存/刷新规则后立即按新规则重新构建 ShardingSphere 数据源，替换注册表中的
 * shardingDataSource 并重解析动态路由，不重启即生效。分片数/时间范围等参数全部
 * 来自配置表，替代旧链路写死的 MOD=16 / INTERVAL=12。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Service
public class ShardingRuleManager {

    /**
     * 分片数据源在注册表中的固定键
     */
    public static final String BEAN_NAME = "shardingDataSource";

    /**
     * INTERVAL 分片物理表数上限（防误配撑爆内存）
     */
    private static final int MAX_INTERVAL_SHARDS = 1024;

    private final DsRuntimeConfigStore store;
    private final MemberDataSourcePool pool;
    private final ApplicationContext applicationContext;

    /**
     * 当前生效规则快照
     */
    private volatile List<DsShardingRule> activeRules = List.of();

    /**
     * 当前分片物理源（用于关闭旧 ShardingSphere 实例与物理表服务取数）
     */
    private volatile List<DataSource> currentPhysical = List.of();

    private volatile long lastRefreshAt = 0L;
    private volatile String lastRefreshResult = "尚未刷新";

    public ShardingRuleManager(DsRuntimeConfigStore store, MemberDataSourcePool pool,
                               ApplicationContext applicationContext) {
        this.store = store;
        this.pool = pool;
        this.applicationContext = applicationContext;
    }

    /**
     * 按配置表当前启用的规则重建分片数据源（运行期热刷新入口）。
     *
     * @return 生效的逻辑表清单
     */
    public synchronized List<String> refresh() {
        List<DsShardingRule> rules = store.findEnabledShardingRules();
        if (rules.isEmpty()) {
            // 不 close 旧 ShardingSphere 实例：其 close 会连带关闭传入的外部成员数据源；
            // 成员池统一由 MemberDataSourcePool 按命名空间清理
            pool.closeShardingPools();
            DataSourceContextSupport.DATA_SOURCE_MAP.remove(BEAN_NAME);
            activeRules = List.of();
            currentPhysical = List.of();
            lastRefreshAt = System.currentTimeMillis();
            lastRefreshResult = "无启用规则，分片数据源已摘除";
            log.info("[ds-runtime] {}", lastRefreshResult);
            return List.of();
        }

        // 解析分库成员：url 型建池，字符串引用型从注册表取
        Map<String, DataSource> named = new LinkedHashMap<>();
        for (DsShardingRule rule : rules) {
            for (ShardingDbMember m : rule.getDbMemberList()) {
                if (m.getUrl() == null || m.getUrl().isBlank()) {
                    DataSource referenced = DataSourceContextSupport.getDatasource(m.getName());
                    if (referenced != null) {
                        named.putIfAbsent(m.getName(), referenced);
                    } else {
                        log.warn("[ds-runtime] 分库引用的已注册数据源不存在: {}", m.getName());
                    }
                }
            }
        }
        named.putAll(pool.bindSharding(allUrlMembers(rules)));
        if (named.isEmpty()) {
            throw new IllegalStateException("分片规则没有可用分库（url 型成员为空或引用名不存在）");
        }

        try {
            ShardingV5Conversion conversion = new ShardingV5Conversion();
            conversion.autoDiscover(true);
            for (DsShardingRule rule : rules) {
                applyRule(conversion, rule, named.size());
            }

            DataSourceEnvironment environment = new DataSourceEnvironment();
            environment.setDatabaseType("ShardingSphere");
            DataSource sharding = conversion.convert(new ArrayList<>(named.values()), environment);

            // 旧实例不 close（会连带关闭成员池），仅替换引用交由 GC；成员池由池管理器差量清理
            DataSourceContextSupport.addDatasource(BEAN_NAME, sharding);
            currentPhysical = new ArrayList<>(named.values());
            activeRules = List.copyOf(rules);
            reResolveRouting();
            lastRefreshAt = System.currentTimeMillis();
            lastRefreshResult = "生效规则 " + rules.size() + " 条，分库 " + named.size() + " 个: " + named.keySet();
            log.info("[ds-runtime] 分片数据源已刷新: {}", lastRefreshResult);
            return rules.stream().map(DsShardingRule::getLogicTable).toList();
        } catch (Exception e) {
            lastRefreshResult = "刷新失败: " + e.getMessage();
            log.error("[ds-runtime] 分片数据源刷新失败", e);
            throw new IllegalStateException("分片数据源刷新失败: " + e.getMessage(), e);
        }
    }

    /**
     * 启动完成后按配置表自动恢复已启用的分片规则（重启不丢运行时）。
     * 失败只告警不阻断启动，可经刷新接口重试。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        try {
            if (!store.findEnabledShardingRules().isEmpty()) {
                refresh();
            }
        } catch (Exception e) {
            log.warn("[ds-runtime] 启动加载分片规则失败（可调用刷新接口重试）: {}", e.getMessage());
        }
    }

    /**
     * 把一条规则翻译为 ShardingV5Conversion 的算法 + 表 + 库配置（参数化分片数/时间范围）。
     */
    private void applyRule(ShardingV5Conversion conversion, DsShardingRule rule, int dbCount) {
        String table = rule.getLogicTable();
        String column = rule.getShardingColumn();
        boolean interval = DsShardingRule.ALGO_INTERVAL.equalsIgnoreCase(rule.getAlgorithm())
                || (rule.getTimeUnit() != null && !rule.getTimeUnit().isBlank());

        if (interval) {
            String algoName = "interval_" + table;
            Map<String, String> props = new LinkedHashMap<>();
            props.put("datetime-pattern", "yyyy-MM-dd");
            props.put("datetime-lower", rule.getTimeLower() == null ? "2000-01-01" : rule.getTimeLower());
            props.put("datetime-upper", rule.getTimeUpper() == null ? "2099-12-31" : rule.getTimeUpper());
            props.put("datetime-partition-pattern", partitionPattern(rule.getTimeUnit()));
            conversion.algo(algoName, "INTERVAL", props);
            conversion.table(table, column, resolveIntervalShardCount(rule), algoName);
            log.info("[ds-runtime] 时间分片规则: {} 列={} 粒度={} 范围={}~{}",
                    table, column, rule.getTimeUnit(), rule.getTimeLower(), rule.getTimeUpper());
        } else {
            String algoName = "mod_" + table;
            int shardCount = rule.getShardCount() == null || rule.getShardCount() <= 0 ? 16 : rule.getShardCount();
            conversion.algo(algoName, "MOD", Map.of("sharding-count", String.valueOf(shardCount)));
            conversion.table(table, column, shardCount, algoName);
            log.info("[ds-runtime] 取模分片规则: {} 列={} 分片数={}", table, column, shardCount);
        }

        // 分库：成员数 >= 2 时按同分片列对库取模
        if (dbCount >= 2) {
            String dbAlgo = "db_mod_" + table;
            conversion.algo(dbAlgo, "MOD", Map.of("sharding-count", String.valueOf(dbCount)));
            conversion.db(table, column, dbCount, dbAlgo);
        }
    }

    /**
     * INTERVAL 分片数：未显式配置时按时间范围与粒度推算（封顶 1024）。
     */
    private int resolveIntervalShardCount(DsShardingRule rule) {
        if (rule.getShardCount() != null && rule.getShardCount() > 0) {
            return rule.getShardCount();
        }
        try {
            Calendar lower = Calendar.getInstance();
            lower.setTime(new java.text.SimpleDateFormat("yyyy-MM-dd").parse(
                    rule.getTimeLower() == null ? "2000-01-01" : rule.getTimeLower()));
            Calendar upper = Calendar.getInstance();
            upper.setTime(new java.text.SimpleDateFormat("yyyy-MM-dd").parse(
                    rule.getTimeUpper() == null ? "2099-12-31" : rule.getTimeUpper()));
            long months = (upper.get(Calendar.YEAR) - lower.get(Calendar.YEAR)) * 12L
                    + (upper.get(Calendar.MONTH) - lower.get(Calendar.MONTH)) + 1;
            String unit = rule.getTimeUnit() == null ? "MONTH" : rule.getTimeUnit().toUpperCase();
            long periods = switch (unit) {
                case "YEAR" -> months / 12 + 1;
                case "QUARTER" -> months / 3 + 1;
                case "WEEK" -> months * 4L + 1;
                case "DAY" -> months * 30L + 1;
                case "HOUR" -> months * 720L + 1;
                default -> months;
            };
            return (int) Math.min(periods, MAX_INTERVAL_SHARDS);
        } catch (Exception e) {
            return 12;
        }
    }

    /**
     * 时间粒度 -> ShardingSphere 分区格式。
     */
    private String partitionPattern(String timeUnit) {
        if (timeUnit == null) {
            return "yyyyMM";
        }
        return switch (timeUnit.toUpperCase()) {
            case "YEAR" -> "yyyy";
            case "QUARTER" -> "yyyyQ";
            case "WEEK" -> "yyyyww";
            case "DAY" -> "yyyyMMdd";
            case "HOUR" -> "yyyyMMddHH";
            default -> "yyyyMM";
        };
    }

    /**
     * 汇总所有 url 型分库成员（按名字去重，保持顺序）。
     */
    private List<ShardingDbMember> allUrlMembers(List<DsShardingRule> rules) {
        Set<String> seen = new LinkedHashSet<>();
        List<ShardingDbMember> result = new ArrayList<>();
        for (DsShardingRule rule : rules) {
            for (ShardingDbMember m : rule.getDbMemberList()) {
                if (m.getUrl() != null && !m.getUrl().isBlank() && seen.add(m.getName())) {
                    result.add(m);
                }
            }
        }
        return result;
    }

    /**
     * 分片 SQL 试跑（SELECT 类）。
     */
    public Map<String, Object> query(String sql) {
        DataSource sharding = DataSourceContextSupport.getDatasource(BEAN_NAME);
        if (sharding == null) {
            throw new IllegalStateException("当前没有生效的分片数据源，请先保存并刷新分片规则");
        }
        long start = System.currentTimeMillis();
        try (Connection conn = sharding.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(stripTrailingSemicolon(sql))) {
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            List<String> columns = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                columns.add(meta.getColumnLabel(i));
            }
            List<List<Object>> rows = new ArrayList<>();
            int limit = 200;
            while (rs.next() && rows.size() < limit) {
                List<Object> row = new ArrayList<>(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    Object v = rs.getObject(i);
                    row.add(v instanceof java.sql.Timestamp t ? t.toLocalDateTime().toString()
                            : v instanceof java.sql.Date d ? d.toLocalDate().toString()
                            : v instanceof java.sql.Time t2 ? t2.toLocalTime().toString()
                            : v instanceof byte[] b ? "<binary " + b.length + "B>" : v);
                }
                rows.add(row);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("columns", columns);
            result.put("rows", rows);
            result.put("rowCount", rows.size());
            result.put("truncated", rows.size() >= limit);
            result.put("elapsedMs", System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("分片 SQL 执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 当前生效规则。
     */
    public List<DsShardingRule> activeRules() {
        return activeRules;
    }

    /**
     * 当前分片物理源（物理表管理用）。
     */
    public List<DataSource> currentPhysical() {
        return currentPhysical;
    }

    public long lastRefreshAt() {
        return lastRefreshAt;
    }

    public String lastRefreshResult() {
        return lastRefreshResult;
    }

    /**
     * 保存规则并立即刷新。
     */
    public DsShardingRule saveAndRefresh(DsShardingRule rule) {
        if (rule.getLogicTable() == null || rule.getLogicTable().isBlank()) {
            throw new IllegalArgumentException("逻辑表名不能为空");
        }
        if (rule.getShardingColumn() == null || rule.getShardingColumn().isBlank()) {
            throw new IllegalArgumentException("分片列不能为空");
        }
        if (rule.getDbMemberList() == null || rule.getDbMemberList().isEmpty()) {
            throw new IllegalArgumentException("分片规则至少需要一个分库成员");
        }
        rule.setDatabases(store.writeShardingMembers(rule.getDbMemberList()));
        long id = store.saveShardingRule(rule);
        refresh();
        return store.findShardingRuleById(id);
    }

    /**
     * 删除规则并立即刷新。
     */
    public void deleteAndRefresh(Long id) {
        store.deleteShardingRule(id);
        refresh();
    }

    private void reResolveRouting() {
        try {
            DynamicDataSource dynamic = applicationContext.getBean(DynamicDataSource.class);
            dynamic.afterPropertiesSet();
        } catch (Exception e) {
            log.warn("[ds-runtime] 动态路由重解析失败: {}", e.getMessage());
        }
    }

    private static String stripTrailingSemicolon(String sql) {
        String trimmed = sql == null ? "" : sql.trim();
        return trimmed.endsWith(";") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
