// [P3C A20 豁免] A20/A30/A31: 日志/SQL 字符串字面量场景，豁免。
package com.chua.starter.datasource.runtime.partition;

import com.chua.common.support.lang.datasource.dialect.Dialect;
import com.chua.common.support.lang.datasource.engine.Engine;
import com.chua.common.support.lang.datasource.engine.EngineDataSource;
import com.chua.datasource.support.engine.JdbcEngine;
import com.chua.datasource.support.partition.PartitionRoutingService;
import com.chua.starter.datasource.properties.DsPartitionProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 分区表自动创建服务（Engine 执行 DDL）。
 *
 * <p>按配置（yaml {@code plugin.datasource.partition.tables} 优先，未配置时只读回退
 * {@code pay_partition_config} 中 auto_create_enabled=true 的行）为「当前周期」
 * 幂等补建分区表：</p>
 * <ul>
 *   <li>表名口径与 {@link PartitionRoutingService} 一致：
 *       {@code prefix_yyyyMM}（MONTH）/ {@code prefix_yyyyMMdd}（DAY）；</li>
 *   <li>DDL 语义：MySQL 等 {@code CREATE TABLE IF NOT EXISTS <target> LIKE <source>}，
 *       H2 降级为 {@code CREATE TABLE IF NOT EXISTS <target> AS SELECT * FROM <source> WHERE 1 = 0}；</li>
 *   <li>标识符一律经 {@code SqlName.checkSimple} 白名单校验，杜绝 SQL 注入；</li>
 *   <li>DDL 通过 {@link Engine#executeSql(String, Object...)} 执行（数据源以共享适配器接入，
 *       引擎不拥有连接池、不误关 Spring 连接池）。</li>
 * </ul>
 *
 * <p>定时能力默认关闭（{@code plugin.datasource.partition.auto-create-enabled=false}）；
 * 启用时使用服务自管的守护调度线程，不开启宿主全局 {@code @EnableScheduling}，
 * 避免激活宿主中其它休眠的 @Scheduled 方法。关闭时仍可通过
 * {@code POST /ms/ds-runtime/partition/create-current} 手动触发。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Service
public class DsPartitionAutoCreateService {

    /**
     * 启动预热延迟（秒）：等待数据源/迁移脚本完成初始化后再执行首轮建表。
     */
    private static final long INITIAL_DELAY_SECONDS = 15;

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final DsPartitionProperties properties;
    private final Engine engine;

    /**
     * 自管守护调度器；auto-create-enabled=false 时保持为 null，不产生任何线程。
     */
    private ScheduledExecutorService scheduler;

    public DsPartitionAutoCreateService(JdbcTemplate jdbcTemplate,
                                        DataSource dataSource,
                                        DsPartitionProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.properties = properties;
        Engine jdbcEngine = new JdbcEngine() {
        };
        jdbcEngine.addDataSource("default", new SharedDataSource(dataSource));
        this.engine = jdbcEngine;
    }

    /**
     * 启动周期检查（仅在 auto-create-enabled=true 时创建守护线程）。
     */
    @PostConstruct
    public void startScheduler() {
        if (!properties.isAutoCreateEnabled()) {
            return;
        }
        long intervalMinutes = Math.max(1, properties.getCheckIntervalMinutes());
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ds-partition-auto-create");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.schedule(this::runQuietly, INITIAL_DELAY_SECONDS, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::runQuietly, intervalMinutes, intervalMinutes, TimeUnit.MINUTES);
        log.info("[ds-partition] 分区表自动创建已启用: 启动预热 {}s 后首轮执行，之后每 {} 分钟检查一次",
                INITIAL_DELAY_SECONDS, intervalMinutes);
    }

    /**
     * 停止调度线程。
     */
    @PreDestroy
    public void stopScheduler() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    /**
     * 为当前周期幂等创建全部分区表（手动端点与定时任务共用入口）。
     *
     * @return configSource / total / createdCount / existsCount / failedCount / tables 明细
     */
    public Map<String, Object> createCurrentPartitions() {
        ConfigSnapshot snapshot = loadConfigs();
        List<PartitionTarget> configs = snapshot.targets();
        LocalDate today = LocalDate.now();
        boolean h2 = isH2Database();
        List<Map<String, Object>> rows = new ArrayList<>();
        int created = 0;
        int exists = 0;
        int failed = 0;
        for (PartitionTarget config : configs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sourceTable", config.sourceTable());
            row.put("prefix", config.prefix());
            row.put("granularity", config.granularity());
            try {
                String target = PartitionRoutingService.resolvePartitionTableName(
                        config.prefix(), today, config.granularity());
                row.put("targetTable", target);
                if (!tableExists(config.sourceTable())) {
                    row.put("status", "SOURCE_MISSING");
                    failed++;
                } else if (tableExists(target)) {
                    row.put("status", "EXISTS");
                    exists++;
                } else {
                    executeCreateDdl(config.sourceTable(), target, h2);
                    row.put("status", "CREATED");
                    created++;
                    log.info("[ds-partition] 自动创建分区表成功: {} (源表: {})", target, config.sourceTable());
                }
            } catch (Exception e) {
                row.put("status", "FAILED");
                row.put("message", e.getMessage());
                failed++;
                log.warn("[ds-partition] 自动创建分区表失败: source={}, err={}", config.sourceTable(), e.getMessage());
            }
            rows.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configSource", snapshot.source());
        result.put("total", rows.size());
        result.put("createdCount", created);
        result.put("existsCount", exists);
        result.put("failedCount", failed);
        result.put("tables", rows);
        return result;
    }

    /**
     * 定时执行入口：吞掉异常只告警，保证调度线程不被单次失败终止。
     */
    private void runQuietly() {
        try {
            Map<String, Object> result = createCurrentPartitions();
            log.debug("[ds-partition] 周期自动建表完成: {}", result);
        } catch (Exception e) {
            log.warn("[ds-partition] 周期自动建表失败: {}", e.getMessage());
        }
    }

    /**
     * 汇总分区配置：yaml 优先；yaml 未配置时只读回退 pay_partition_config。
     */
    private ConfigSnapshot loadConfigs() {
        List<PartitionTarget> targets = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (DsPartitionProperties.PartitionTableSpec spec : properties.getTables()) {
            if (spec == null) {
                continue;
            }
            String source = spec.getSourceTable() == null ? "" : spec.getSourceTable().trim();
            String prefix = spec.getPrefix() == null ? "" : spec.getPrefix().trim();
            if (source.isEmpty() || prefix.isEmpty()) {
                throw new IllegalArgumentException("分区配置缺少 source-table 或 prefix: " + spec);
            }
            String grain = spec.getGranularity() == null || spec.getGranularity().isBlank()
                    ? "MONTH" : spec.getGranularity().trim().toUpperCase(Locale.ROOT);
            if (!"MONTH".equals(grain) && !"DAY".equals(grain)) {
                throw new IllegalArgumentException("granularity 仅支持 MONTH / DAY，收到: " + spec.getGranularity());
            }
            if (seen.add(source + "|" + prefix + "|" + grain)) {
                targets.add(new PartitionTarget(source, prefix, grain));
            }
        }
        if (!targets.isEmpty()) {
            return new ConfigSnapshot(targets, "yaml");
        }
        // 回退：只读 pay_partition_config（表不存在/不可读时静默跳过，不影响宿主启动）
        try {
            for (Map<String, Object> config : jdbcTemplate.queryForList("SELECT * FROM pay_partition_config")) {
                String source = strValue(config.get("pay_partition_config_source_table"));
                String prefix = strValue(config.get("pay_partition_config_partition_prefix"));
                Object enabled = config.get("pay_partition_config_auto_create_enabled");
                boolean autoCreate = Boolean.TRUE.equals(enabled) || "true".equalsIgnoreCase(String.valueOf(enabled));
                if (source == null || prefix == null || !autoCreate) {
                    continue;
                }
                if (seen.add(source + "|" + prefix + "|MONTH")) {
                    targets.add(new PartitionTarget(source, prefix, "MONTH"));
                }
            }
            return new ConfigSnapshot(targets, targets.isEmpty() ? "none" : "pay_partition_config");
        } catch (Exception e) {
            log.debug("[ds-partition] pay_partition_config 不可用，跳过回退配置: {}", e.getMessage());
            return new ConfigSnapshot(targets, "none");
        }
    }

    /**
     * 执行幂等建表 DDL（标识符均先经白名单校验）。
     */
    private void executeCreateDdl(String sourceTable, String targetTable, boolean h2) {
        PartitionRoutingService.validateTableName(sourceTable, "源表");
        PartitionRoutingService.validateTableName(targetTable, "目标表");
        // [P3C 1.4 豁免] 拼接值均经 SqlName.checkSimple 白名单校验，非外部可注入输入
        String ddl = h2
                ? "CREATE TABLE IF NOT EXISTS " + targetTable + " AS SELECT * FROM " + sourceTable + " WHERE 1 = 0"
                : "CREATE TABLE IF NOT EXISTS " + targetTable + " LIKE " + sourceTable;
        engine.executeSql(ddl);
    }

    /**
     * 通过 JDBC 元数据确认表存在（兼容不同数据库的大小写行为）。
     */
    private boolean tableExists(String tableName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            for (String candidate : metadataCandidates(tableName)) {
                if (queryTableExists(metaData, connection, candidate, tableName)) {
                    return true;
                }
            }
            return false;
        } catch (SQLException e) {
            throw new IllegalStateException("读取表元数据失败: " + tableName + ", " + e.getMessage(), e);
        }
    }

    /**
     * 单候选表名存在性查询。
     */
    private boolean queryTableExists(DatabaseMetaData metaData, Connection connection,
                                     String candidate, String tableName) throws SQLException {
        try (ResultSet resultSet = metaData.getTables(
                connection.getCatalog(), connection.getSchema(), candidate, new String[]{"TABLE"})) {
            while (resultSet.next()) {
                if (tableName.equalsIgnoreCase(resultSet.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 生成数据库元数据常用大小写候选值。
     */
    private List<String> metadataCandidates(String value) {
        return List.of(value, value.toUpperCase(Locale.ROOT), value.toLowerCase(Locale.ROOT));
    }

    /**
     * 判断目标数据库是否为 H2（H2 不支持 CREATE TABLE ... LIKE）。
     */
    private boolean isH2Database() {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            return product != null && product.toLowerCase(Locale.ROOT).contains("h2");
        } catch (SQLException e) {
            throw new IllegalStateException("无法识别分区数据库类型: " + e.getMessage(), e);
        }
    }

    private static String strValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 单个分区目标（源表 + 前缀 + 粒度）。
     */
    private record PartitionTarget(String sourceTable, String prefix, String granularity) {
    }

    /**
     * 配置快照：目标清单 + 配置来源（yaml / pay_partition_config / none）。
     */
    private record ConfigSnapshot(List<PartitionTarget> targets, String source) {
    }

    /**
     * 共享数据源适配器：不拥有底层连接池，{@link #close()} 为空，避免引擎关闭时
     * 误关 Spring 管理的连接池（与 logger-starter LoggerStores 同口径）。
     */
    private static final class SharedDataSource implements EngineDataSource<DataSource> {

        /**
         * Spring 共享数据源
         */
        private final DataSource dataSource;

        /**
         * 构造共享数据源适配器。
         *
         * @param dataSource Spring 共享数据源
         */
        private SharedDataSource(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public String name() {
            return "default";
        }

        @Override
        public DataSource getSource() {
            return dataSource;
        }

        @Override
        public EngineDataSource<DataSource> setSource(Object source) {
            return this;
        }

        @Override
        public Dialect getDialect() {
            return null;
        }

        @Override
        public EngineDataSource<DataSource> setDialect(Dialect dialect) {
            return this;
        }

        @Override
        public String url() {
            return null;
        }

        @Override
        public String username() {
            return null;
        }

        @Override
        public String password() {
            return null;
        }

        @Override
        public void close() {
            // 不关闭共享连接池
        }
    }
}
